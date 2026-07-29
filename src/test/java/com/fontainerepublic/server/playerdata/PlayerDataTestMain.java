package com.fontainerepublic.server.playerdata;

import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.server.playerdata.model.PlayerData;
import com.fontainerepublic.server.playerdata.model.PlayerIdentity;
import com.fontainerepublic.server.playerdata.model.PlayerProfileUpdate;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataNbtCodec;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataNbtException;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataRepository;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataStore;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataStoreSnapshot;
import com.fontainerepublic.server.playerdata.persistence.StalePlayerDataRevisionException;
import com.fontainerepublic.server.playerdata.service.DefaultPlayerDataService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-DATA-002.
 */
public final class PlayerDataTestMain {
    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private PlayerDataTestMain() {
    }

    public static void main(String[] args) {
        testFirstLoginAndUuidUniqueness();
        testEnsurePlayer();
        testRevisionControl();
        testLogoutMutation();
        testNbtRoundTripAndValidation();
        testUuidKeyIdentityMismatch();
        testSaveFailureAtomicity();
        testRestartPersistence();
        System.out.println("[FR-DATA-002] Player-data validation passed");
    }

    private static void testFirstLoginAndUuidUniqueness() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        PlayerData first = service.recordLogin(ALPHA_ID, "Alpha");
        require(first.revision() == 1, "first record revision");
        require(first.identity().playerId().equals(ALPHA_ID), "first record UUID");
        require(repository.size() == 1, "new login creates exactly one record");

        clock.advance(100);
        PlayerData secondLogin = service.recordLogin(ALPHA_ID, "Alpha");
        require(secondLogin.revision() == 2, "repeat login updates the same record");
        require(repository.size() == 1, "repeat UUID does not create a duplicate");

        clock.advance(100);
        service.recordLogin(BRAVO_ID, "Alpha");
        require(repository.size() == 2, "same game name does not merge different UUIDs");
    }

    private static void testEnsurePlayer() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_500);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        PlayerData first = service.ensurePlayer(ALPHA_ID, "Alpha");
        require(first.revision() == 1, "ensurePlayer creates new record");
        require(first.identity().playerId().equals(ALPHA_ID), "ensurePlayer sets UUID");
        require(
                first.identity().lastKnownGameName().equals("Alpha"),
                "ensurePlayer records game name"
        );

        clock.advance(200);
        PlayerData second = service.ensurePlayer(ALPHA_ID, "AlphaUpdated");
        require(
                second.equals(first),
                "ensurePlayer on existing UUID returns same record unchanged"
        );
        require(
                second.identity().lastKnownGameName().equals("Alpha"),
                "ensurePlayer does not mutate lastKnownGameName"
        );
        require(
                second.identity().lastSeenAt() == 1_500,
                "ensurePlayer does not advance lastSeenAt on existing record"
        );

        clock.advance(100);
        PlayerData third = service.ensurePlayer(BRAVO_ID, "Bravo");
        require(
                !third.identity().playerId().equals(first.identity().playerId()),
                "ensurePlayer keeps different UUIDs separate"
        );
        require(repository.size() == 2, "ensurePlayer creates distinct records");
    }

    private static void testRevisionControl() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(2_000);
        DefaultPlayerDataService service = new DefaultPlayerDataService(
                repository(store),
                clock
        );
        PlayerData created = service.recordLogin(ALPHA_ID, "Alpha");

        clock.advance(100);
        PlayerData updated = service.updateProfile(
                ALPHA_ID,
                created.revision(),
                new PlayerProfileUpdate(
                        Optional.of("Duke Alpha"),
                        Optional.of("zh-CN"),
                        Optional.of("A test profile")
                )
        );
        require(updated.revision() == created.revision() + 1, "revision increments once");
        require(
                updated.profile().locale().orElseThrow().equals("zh-CN"),
                "locale is normalized"
        );

        expectThrows(
                StalePlayerDataRevisionException.class,
                () -> service.updateProfile(
                        ALPHA_ID,
                        created.revision(),
                        new PlayerProfileUpdate(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()
                        )
                ),
                "stale revision is rejected"
        );
    }

    private static void testLogoutMutation() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(2_500);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        PlayerData created = service.recordLogin(ALPHA_ID, "Alpha");
        PlayerData profiled = service.updateProfile(
                ALPHA_ID,
                created.revision(),
                new PlayerProfileUpdate(
                        Optional.of("Duke Alpha"),
                        Optional.of("fr-FR"),
                        Optional.of("Profile retained across logout")
                )
        );
        PlayerDataStoreSnapshot beforeLogout = repository.snapshot();

        clock.advance(250);
        service.recordLogout(ALPHA_ID);

        PlayerData afterLogout = repository.require(ALPHA_ID);
        PlayerIdentity beforeIdentity = profiled.identity();
        PlayerIdentity afterIdentity = afterLogout.identity();
        require(afterIdentity.lastSeenAt() == 2_750, "logout records observed time");
        require(
                afterLogout.revision() == profiled.revision() + 1,
                "logout increments player revision exactly once"
        );
        require(
                repository.snapshot().storeRevision()
                        == beforeLogout.storeRevision() + 1,
                "logout increments store revision exactly once"
        );
        require(
                afterIdentity.firstSeenAt() == beforeIdentity.firstSeenAt(),
                "logout preserves firstSeenAt"
        );
        require(
                afterIdentity.playerId().equals(beforeIdentity.playerId()),
                "logout preserves UUID"
        );
        require(
                afterIdentity.lastKnownGameName().equals(
                        beforeIdentity.lastKnownGameName()
                ),
                "logout preserves game name"
        );
        require(
                afterLogout.profile().equals(profiled.profile()),
                "logout preserves profile"
        );
    }

    private static void testNbtRoundTripAndValidation() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(3_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        service.recordLogin(ALPHA_ID, "Alpha");

        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        CompoundTag encoded = codec.encode(repository.snapshot());
        require(
                codec.decode(encoded).equals(repository.snapshot()),
                "NBT round trip preserves the snapshot"
        );

        CompoundTag record = encoded.getCompound("Players")
                .getCompound(ALPHA_ID.toString());
        require(
                record.getAllKeys().equals(
                        Set.of("RecordVersion", "Revision", "Identity", "Profile")
                ),
                "record contains no business fields"
        );

        CompoundTag unexpectedBusinessField = encoded.copy();
        unexpectedBusinessField.getCompound("Players")
                .getCompound(ALPHA_ID.toString())
                .putLong("Balance", 100);
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(unexpectedBusinessField),
                "unknown business field is rejected"
        );

        CompoundTag incomplete = new CompoundTag();
        incomplete.putInt("StoreVersion", 1);
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(incomplete),
                "missing required NBT fields are rejected"
        );

        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("StoreVersion", "not-an-int");
        wrongType.putLong("StoreRevision", 1);
        wrongType.put("Players", new CompoundTag());
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(wrongType),
                "wrong NBT type for StoreVersion is rejected"
        );

        CompoundTag wrongPlayerIdType = new CompoundTag();
        wrongPlayerIdType.putInt("StoreVersion", 1);
        wrongPlayerIdType.putLong("StoreRevision", 1);
        CompoundTag badPlayers = new CompoundTag();
        CompoundTag badRecord = new CompoundTag();
        badRecord.putInt("RecordVersion", 1);
        badRecord.putLong("Revision", 1);
        CompoundTag badIdentity = new CompoundTag();
        badIdentity.putString("PlayerId", "not-a-uuid");
        badIdentity.putString("LastKnownGameName", "TestPlayer");
        badIdentity.putLong("FirstSeenAt", 3000);
        badIdentity.putLong("LastSeenAt", 3000);
        badRecord.put("Identity", badIdentity);
        badRecord.put("Profile", new CompoundTag());
        badPlayers.put(ALPHA_ID.toString(), badRecord);
        wrongPlayerIdType.put("Players", badPlayers);
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(wrongPlayerIdType),
                "wrong NBT type for PlayerId is rejected"
        );
    }

    private static void testUuidKeyIdentityMismatch() {
        InMemoryStore store = new InMemoryStore();
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(
                repository,
                new MutableClock(3_500)
        );
        service.recordLogin(ALPHA_ID, "Alpha");

        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        CompoundTag mismatched = codec.encode(repository.snapshot());
        mismatched.getCompound("Players")
                .getCompound(ALPHA_ID.toString())
                .getCompound("Identity")
                .putUUID("PlayerId", BRAVO_ID);

        PlayerDataNbtException failure = expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(mismatched),
                "valid but mismatched UUID key and identity are rejected"
        );
        require(
                failure.getMessage().contains(
                        "Player key " + ALPHA_ID + " does not match identity " + BRAVO_ID
                ),
                "UUID mismatch reports the key and identity conflict"
        );
    }

    private static void testSaveFailureAtomicity() {
        FailingStore store = new FailingStore();
        MutableClock clock = new MutableClock(3_750);
        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        PlayerDataRepository repository = new PlayerDataRepository(store, codec);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        PlayerData created = service.recordLogin(ALPHA_ID, "Alpha");
        PlayerDataStoreSnapshot beforeFailure = repository.snapshot();
        store.setSaveFailureEnabled(true);
        clock.advance(100);

        TestPersistenceException failure = expectThrows(
                TestPersistenceException.class,
                () -> service.updateProfile(
                        ALPHA_ID,
                        created.revision(),
                        new PlayerProfileUpdate(
                                Optional.of("Uncommitted Alpha"),
                                Optional.of("en-US"),
                                Optional.empty()
                        )
                ),
                "save failure is propagated"
        );
        require(
                failure.getMessage().equals("injected player-data save failure"),
                "the deterministic persistence failure is observed"
        );

        PlayerDataStoreSnapshot afterFailure = repository.snapshot();
        require(
                afterFailure.players().equals(beforeFailure.players()),
                "failed save leaves repository players unchanged"
        );
        require(
                repository.require(ALPHA_ID).revision() == created.revision(),
                "failed save leaves player revision unchanged"
        );
        require(
                afterFailure.storeRevision() == beforeFailure.storeRevision(),
                "failed save leaves store revision unchanged"
        );
        require(
                codec.decode(store.load()).equals(beforeFailure),
                "failed save leaves persisted snapshot unchanged"
        );

        store.setSaveFailureEnabled(false);
        PlayerData committed = service.updateProfile(
                ALPHA_ID,
                created.revision(),
                new PlayerProfileUpdate(
                        Optional.of("Committed Alpha"),
                        Optional.of("en-US"),
                        Optional.empty()
                )
        );
        require(
                committed.revision() == created.revision() + 1,
                "mutation succeeds after save failure is disabled"
        );
        require(
                repository.snapshot().storeRevision()
                        == beforeFailure.storeRevision() + 1,
                "successful retry increments store revision exactly once"
        );
        require(
                repository.require(ALPHA_ID).equals(committed),
                "successful retry publishes the committed player record"
        );
    }

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock firstClock = new MutableClock(4_000);
        DefaultPlayerDataService firstService = new DefaultPlayerDataService(
                repository(store),
                firstClock
        );
        PlayerData created = firstService.recordLogin(ALPHA_ID, "Alpha");
        firstClock.advance(100);
        PlayerData updated = firstService.updateProfile(
                ALPHA_ID,
                created.revision(),
                new PlayerProfileUpdate(
                        Optional.of("Persistent Alpha"),
                        Optional.of("en-US"),
                        Optional.empty()
                )
        );

        require(
                ModSavedData.DATA_NAME.equals("fontainerepublic"),
                "SavedData file name remains fontainerepublic"
        );
        require(
                PlayerDataRepository.MODULE_DATA_KEY.equals("player-data"),
                "player-data uses the approved module namespace"
        );

        PlayerDataRepository restartedRepository = repository(store.restart());
        DefaultPlayerDataService restartedService = new DefaultPlayerDataService(
                restartedRepository,
                new MutableClock(5_000)
        );
        PlayerData reloaded = restartedService.require(ALPHA_ID);
        require(reloaded.equals(updated), "record survives repository restart");
        require(restartedRepository.size() == 1, "restart preserves UUID uniqueness");
    }

    private static PlayerDataRepository repository(PlayerDataStore store) {
        return new PlayerDataRepository(store, new PlayerDataNbtCodec());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expected,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return expected.cast(throwable);
            }
            throw new AssertionError(
                    message + ": expected " + expected.getSimpleName()
                            + ", got " + throwable.getClass().getSimpleName(),
                    throwable
            );
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private static final class InMemoryStore implements PlayerDataStore {
        private CompoundTag data = new CompoundTag();

        @Override
        public CompoundTag load() {
            return data.copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            data = snapshot.copy();
        }
    }

    private static final class FailingStore implements PlayerDataStore {
        private CompoundTag data = new CompoundTag();
        private boolean saveFailureEnabled;

        @Override
        public CompoundTag load() {
            return data.copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            if (saveFailureEnabled) {
                throw new TestPersistenceException(
                        "injected player-data save failure"
                );
            }
            data = snapshot.copy();
        }

        private void setSaveFailureEnabled(boolean enabled) {
            saveFailureEnabled = enabled;
        }
    }

    private static final class TestPersistenceException extends RuntimeException {
        private TestPersistenceException(String message) {
            super(message);
        }
    }

    private static final class SavedDataBackedTestStore implements PlayerDataStore {
        private ModSavedData savedData;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(
                    PlayerDataRepository.MODULE_DATA_KEY
            ).copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            savedData.putModuleData(
                    PlayerDataRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
        }

        private SavedDataBackedTestStore restart() {
            CompoundTag root = savedData.save(new CompoundTag());
            return new SavedDataBackedTestStore(ModSavedData.load(root));
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        private void advance(long milliseconds) {
            now += milliseconds;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
