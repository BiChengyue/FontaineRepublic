package com.fontainerepublic.server.playerdata;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.server.playerdata.api.PlayerDirectoryService;
import com.fontainerepublic.server.playerdata.model.DirectoryEntry;
import com.fontainerepublic.server.playerdata.model.MigrationProvenance;
import com.fontainerepublic.server.playerdata.model.PlayerData;
import com.fontainerepublic.server.playerdata.model.PlayerIdentity;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolution;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolutionKind;
import com.fontainerepublic.server.playerdata.model.PlayerProfile;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataCommitException;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataNbtCodec;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataNbtException;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataRepository;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataStore;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataStoreSnapshot;
import com.fontainerepublic.server.playerdata.persistence.StalePlayerDataRevisionException;
import com.fontainerepublic.server.playerdata.service.DefaultPlayerDataService;
import com.fontainerepublic.server.playerdata.service.DefaultPlayerDirectoryService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Dependency-free validation entry point for FR-DATA-003
 * (safe exact-name player directory).
 */
public final class PlayerDirectoryFoundationTestMain {
    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHARLIE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private PlayerDirectoryFoundationTestMain() {
    }

    public static void main(String[] args) {
        testFirstLoginCommitsUniqueEntry();
        testUnchangedLoginKeepsDirectoryRevision();
        testCaseOnlyChangeUpdatesSpelling();
        testRenameIsAtomic();
        testRenameBackToUniqueRecoverable();
        testRenameBackAfterOtherUuidKeepsAmbiguous();
        testTwoHistoricalUuidsArePermanentlyAmbiguous();
        testMigrationGroupingDeterministic();
        testMigrationFailurePreservesOriginal();
        testInjectedCommitFailurePublishesNothing();
        testRestartPersistence();
        testStrictCodecRejections();
        testDeterministicEncoding();
        testNoEnumerationApi();
        testPublicMessagesMerged();
        testInvalidInputs();
        testCaseInsensitiveLookup();
        testStaleProposalRejected();
        System.out.println("[FR-DATA-003] Player directory validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance scenarios (FR-DATA-003-A §15)
    // ------------------------------------------------------------------

    private static void testFirstLoginCommitsUniqueEntry() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        PlayerData first = service.recordLogin(ALPHA_ID, "Alpha");
        require(first.revision() == 1, "first login creates the base record");
        require(repository.size() == 1, "first login creates exactly one player");
        require(repository.snapshot().storeRevision() == 1, "first login advances store revision once");

        DirectoryEntry entry = repository.findDirectoryEntry("alpha").orElseThrow();
        require(!entry.permanentlyAmbiguous(), "first login entry is not ambiguous");
        require(entry.currentOwners().equals(Set.of(ALPHA_ID)), "first login owns the alias");
        require(entry.uniqueHistoricalOwner().orElseThrow().equals(ALPHA_ID), "first owner is historical");
        require(entry.entryRevision() == 1, "new entry starts at revision 1");
        require(entry.lastVerifiedSpelling().equals("Alpha"), "verified spelling preserved");

        PlayerNameResolution resolution = directory.resolveExactGameName("Alpha");
        require(resolution.kind() == PlayerNameResolutionKind.UNIQUE_CURRENT, "first name resolves unique");
        require(resolution.playerId().orElseThrow().equals(ALPHA_ID), "resolution exposes the one UUID");
    }

    private static void testUnchangedLoginKeepsDirectoryRevision() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        service.recordLogin(ALPHA_ID, "Alpha");
        clock.advance(100);
        PlayerData second = service.recordLogin(ALPHA_ID, "Alpha");

        DirectoryEntry entry = repository.findDirectoryEntry("alpha").orElseThrow();
        require(second.revision() == 2, "repeat login updates the player record (lastSeenAt)");
        require(entry.entryRevision() == 1, "unchanged login does not bump the entry revision");
        require(entry.lastVerifiedSpelling().equals("Alpha"), "unchanged login keeps the spelling");
        require(repository.snapshot().storeRevision() == 2, "store revision follows player updates");
    }

    private static void testCaseOnlyChangeUpdatesSpelling() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        service.recordLogin(ALPHA_ID, "Alpha");
        clock.advance(100);
        PlayerData updated = service.recordLogin(ALPHA_ID, "ALPHA");

        DirectoryEntry entry = repository.findDirectoryEntry("alpha").orElseThrow();
        require(entry.lastVerifiedSpelling().equals("ALPHA"), "case-only change updates the spelling");
        require(entry.entryRevision() == 2, "case-only change bumps the entry revision once");
        require(updated.identity().lastKnownGameName().equals("ALPHA"), "identity updates atomically");
        require(
                directory.resolveExactGameName("ALPHA").kind()
                        == PlayerNameResolutionKind.UNIQUE_CURRENT,
                "case variant addresses the same entry"
        );
    }

    private static void testRenameIsAtomic() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        service.recordLogin(ALPHA_ID, "Alpha");
        PlayerData before = service.require(ALPHA_ID);
        long storeRevisionBefore = repository.snapshot().storeRevision();
        clock.advance(100);
        PlayerData renamed = service.recordLogin(ALPHA_ID, "Bravo");

        require(renamed.revision() == before.revision() + 1, "rename bumps player revision once");
        require(
                repository.snapshot().storeRevision() == storeRevisionBefore + 1,
                "rename advances store revision exactly once"
        );
        require(renamed.identity().lastKnownGameName().equals("Bravo"), "identity updated");

        DirectoryEntry alpha = repository.findDirectoryEntry("alpha").orElseThrow();
        require(alpha.currentOwners().isEmpty(), "old alias is retired");
        require(alpha.uniqueHistoricalOwner().orElseThrow().equals(ALPHA_ID), "old alias keeps history");
        require(!alpha.permanentlyAmbiguous(), "retired alias is not ambiguous");
        require(alpha.entryRevision() == 2, "retired entry revision bumped once");

        DirectoryEntry bravo = repository.findDirectoryEntry("bravo").orElseThrow();
        require(bravo.currentOwners().equals(Set.of(ALPHA_ID)), "new alias is current");
        require(bravo.uniqueHistoricalOwner().orElseThrow().equals(ALPHA_ID), "new alias history set");

        require(
                directory.resolveExactGameName("Alpha").kind() == PlayerNameResolutionKind.RETIRED,
                "old alias resolves retired"
        );
        require(
                directory.resolveExactGameName("Bravo").kind()
                        == PlayerNameResolutionKind.UNIQUE_CURRENT,
                "new alias resolves unique current"
        );
        require(
                directory.resolveExactGameName("Alpha").playerId().isEmpty(),
                "retired result exposes no UUID"
        );
    }

    private static void testRenameBackToUniqueRecoverable() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        service.recordLogin(ALPHA_ID, "Alpha");
        clock.advance(100);
        service.recordLogin(ALPHA_ID, "Bravo");
        clock.advance(100);
        service.recordLogin(ALPHA_ID, "Alpha");

        DirectoryEntry alpha = repository.findDirectoryEntry("alpha").orElseThrow();
        require(!alpha.permanentlyAmbiguous(), "rename back stays unambiguous");
        require(alpha.currentOwners().equals(Set.of(ALPHA_ID)), "alias owned again");
        require(
                directory.resolveExactGameName("Alpha").kind()
                        == PlayerNameResolutionKind.UNIQUE_CURRENT,
                "rename back to uniquely owned alias is unique current again"
        );
    }

    private static void testRenameBackAfterOtherUuidKeepsAmbiguous() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        service.recordLogin(ALPHA_ID, "Alpha");
        clock.advance(100);
        service.recordLogin(BRAVO_ID, "Alpha");
        clock.advance(100);
        service.recordLogin(ALPHA_ID, "Bravo");
        clock.advance(100);
        service.recordLogin(ALPHA_ID, "Alpha");

        DirectoryEntry alpha = repository.findDirectoryEntry("alpha").orElseThrow();
        require(alpha.permanentlyAmbiguous(), "another UUID used the alias");
        require(alpha.uniqueHistoricalOwner().isEmpty(), "ambiguous entry keeps no unique owner");
        require(
                directory.resolveExactGameName("Alpha").kind() == PlayerNameResolutionKind.AMBIGUOUS,
                "rename back after another UUID keeps the alias ambiguous"
        );
        require(
                directory.resolveExactGameName("Alpha").playerId().isEmpty(),
                "ambiguous result exposes no UUID"
        );
    }

    private static void testTwoHistoricalUuidsArePermanentlyAmbiguous() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        service.recordLogin(ALPHA_ID, "Alpha");
        clock.advance(100);
        service.recordLogin(BRAVO_ID, "Alpha");
        clock.advance(100);
        service.recordLogin(BRAVO_ID, "Bravo");

        DirectoryEntry alpha = repository.findDirectoryEntry("alpha").orElseThrow();
        require(alpha.permanentlyAmbiguous(), "second UUID makes the alias permanently ambiguous");
        require(
                directory.resolveExactGameName("Alpha").kind() == PlayerNameResolutionKind.AMBIGUOUS,
                "ambiguity is not cleared when one owner leaves"
        );
        require(
                directory.resolveExactGameName("Alpha").playerId().isEmpty(),
                "no UUID is exposed for an ambiguous alias"
        );
    }

    // ------------------------------------------------------------------
    // migration (FR-DATA-003-A §8)
    // ------------------------------------------------------------------

    private static void testMigrationGroupingDeterministic() {
        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        CompoundTag v1 = new CompoundTag();
        v1.putInt("StoreVersion", 1);
        v1.putLong("StoreRevision", 7);
        CompoundTag players = new CompoundTag();
        players.put(ALPHA_ID.toString(), playerRecord(ALPHA_ID, "Alpha", 1_000));
        players.put(BRAVO_ID.toString(), playerRecord(BRAVO_ID, "Bravo", 1_100));
        players.put(CHARLIE_ID.toString(), playerRecord(CHARLIE_ID, "Alpha", 1_200));
        v1.put("Players", players);

        PlayerDataStoreSnapshot migrated = codec.decode(v1);
        require(
                migrated.storeVersion() == PlayerDataStoreSnapshot.CURRENT_STORE_VERSION,
                "migration produces the current store version"
        );
        require(migrated.storeRevision() == 8, "migration advances store revision exactly once");
        require(
                migrated.migrationProvenance() == MigrationProvenance.MIGRATED_FROM_LAST_KNOWN_ONLY,
                "migration records provenance"
        );

        DirectoryEntry alpha = migrated.directory().get("alpha");
        require(alpha.permanentlyAmbiguous(), "multi-UUID group becomes permanently ambiguous");
        require(alpha.uniqueHistoricalOwner().isEmpty(), "ambiguous group keeps no unique owner");
        require(alpha.currentOwners().equals(Set.of(ALPHA_ID, CHARLIE_ID)), "all group owners listed");
        require(alpha.firstObservedAt() == 0 && alpha.lastObservedAt() == 0,
                "migration uses the documented sentinel timestamps");

        DirectoryEntry bravo = migrated.directory().get("bravo");
        require(!bravo.permanentlyAmbiguous(), "single-UUID group stays unique");
        require(bravo.uniqueHistoricalOwner().orElseThrow().equals(BRAVO_ID), "single owner is historical");
        require(bravo.currentOwners().equals(Set.of(BRAVO_ID)), "single owner is current");

        require(
                migrated.players().get(ALPHA_ID).revision() == 1,
                "migration preserves player record revisions"
        );

        PlayerDataStoreSnapshot repeated = codec.decode(v1);
        require(repeated.equals(migrated), "migration is deterministic");
    }

    private static void testMigrationFailurePreservesOriginal() {
        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        CompoundTag v1 = new CompoundTag();
        v1.putInt("StoreVersion", 1);
        v1.putLong("StoreRevision", 3);
        CompoundTag players = new CompoundTag();
        players.put(ALPHA_ID.toString(), playerRecord(ALPHA_ID, "Bad Name!", 1_000));
        v1.put("Players", players);

        PlayerDataNbtException failure = expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(v1),
                "v1 store with an invalid name is rejected"
        );
        require(
                failure.getMessage().contains(ALPHA_ID.toString()),
                "migration failure identifies the offending player"
        );
        // Original input is untouched: decoding never mutates the source tag.
        require(v1.getInt("StoreVersion") == 1, "original store version preserved");
        require(v1.getCompound("Players").contains(ALPHA_ID.toString()),
                "original players preserved");
    }

    // ------------------------------------------------------------------
    // commit semantics (FR-DATA-003-A §7.5 / §13)
    // ------------------------------------------------------------------

    private static void testInjectedCommitFailurePublishesNothing() {
        FailingCommitStore store = new FailingCommitStore();
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(
                repository,
                new MutableClock(1_000)
        );

        store.setFailCommits(true);
        PlayerDataCommitException failure = expectThrows(
                PlayerDataCommitException.class,
                () -> service.recordLogin(ALPHA_ID, "Alpha"),
                "rejected durable commit is propagated"
        );
        require(failure.failureCode().equals("IO_WRITE"), "failure code is surfaced");
        require(repository.size() == 0, "no player record published on failure");
        require(repository.snapshot().storeRevision() == 0, "store revision unchanged on failure");
        require(repository.findDirectoryEntry("alpha").isEmpty(), "no directory entry published");
        require(store.persisted().isEmpty(), "no snapshot persisted on failure");

        store.setFailCommits(false);
        service.recordLogin(ALPHA_ID, "Alpha");
        require(repository.size() == 1, "mutation succeeds after the store recovers");
        require(
                repository.findDirectoryEntry("alpha").orElseThrow()
                        .currentOwners().equals(Set.of(ALPHA_ID)),
                "directory entry published after recovery"
        );
    }

    // ------------------------------------------------------------------
    // restart persistence (FR-DATA-003-A §12.3)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        service.recordLogin(ALPHA_ID, "Alpha");
        clock.advance(100);
        service.recordLogin(ALPHA_ID, "Bravo");
        clock.advance(100);
        service.recordLogin(BRAVO_ID, "Alpha");

        PlayerDataStoreSnapshot beforeRestart = repository.snapshot();

        PlayerDataRepository restarted = repository(store.restart());
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(restarted);
        require(
                restarted.snapshot().equals(beforeRestart),
                "restart recovers the same players, directory, and revisions"
        );
        require(
                restarted.require(ALPHA_ID).identity().lastKnownGameName().equals("Bravo"),
                "restart recovers the renamed identity"
        );
        require(
                directory.resolveExactGameName("Bravo").kind()
                        == PlayerNameResolutionKind.UNIQUE_CURRENT,
                "restart resolves current alias"
        );
        require(
                directory.resolveExactGameName("Alpha").kind()
                        == PlayerNameResolutionKind.AMBIGUOUS,
                "restart keeps permanent ambiguity"
        );
    }

    // ------------------------------------------------------------------
    // strict codec (FR-DATA-003-A §7.3)
    // ------------------------------------------------------------------

    private static void testStrictCodecRejections() {
        InMemoryStore store = new InMemoryStore();
        PlayerDataRepository repository = repository(store);
        new DefaultPlayerDataService(repository, new MutableClock(1_000))
                .recordLogin(ALPHA_ID, "Alpha");
        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        CompoundTag base = codec.encode(repository.snapshot());

        // Orphan current owner: UUID without a player record.
        CompoundTag orphan = base.copy();
        owners(orphan, "alpha").add(NbtUtils.createUUID(BRAVO_ID));
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(orphan),
                "orphan current owner is rejected"
        );

        // Duplicate current owner inside one entry.
        CompoundTag duplicate = base.copy();
        ListTag duplicateOwners = owners(duplicate, "alpha");
        duplicateOwners.add(duplicateOwners.get(0).copy());
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(duplicate),
                "duplicate current owner is rejected"
        );

        // Player name does not normalize to its entry.
        CompoundTag mismatched = base.copy();
        mismatched.getCompound("Players")
                .getCompound(ALPHA_ID.toString())
                .getCompound("Identity")
                .putString("LastKnownGameName", "Bravo");
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(mismatched),
                "player name not normalizing to the entry is rejected"
        );

        // Player record not covered by any directory entry.
        CompoundTag uncovered = base.copy();
        uncovered.getCompound("Players")
                .put(BRAVO_ID.toString(), playerRecord(BRAVO_ID, "Bravo", 1_000));
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(uncovered),
                "player without a directory entry is rejected"
        );

        // Unknown entry field.
        CompoundTag unknownField = base.copy();
        unknownField.getCompound("Directory")
                .getCompound("Entries")
                .getCompound("alpha")
                .putLong("Balance", 100);
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(unknownField),
                "unknown directory field is rejected"
        );

        // Wrong NBT type for a required entry field.
        CompoundTag wrongType = base.copy();
        wrongType.getCompound("Directory")
                .getCompound("Entries")
                .getCompound("alpha")
                .putString("PermanentlyAmbiguous", "yes");
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(wrongType),
                "wrong NBT type in an entry is rejected"
        );

        // Ambiguous entry carrying a unique historical owner.
        CompoundTag ambiguousWithOwner = base.copy();
        CompoundTag entryTag = ambiguousWithOwner.getCompound("Directory")
                .getCompound("Entries")
                .getCompound("alpha");
        entryTag.putByte("PermanentlyAmbiguous", (byte) 1);
        entryTag.putUUID("UniqueHistoricalOwner", ALPHA_ID);
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(ambiguousWithOwner),
                "ambiguous entry with a unique historical owner is rejected"
        );

        // Non-ambiguous entry missing the unique historical owner.
        CompoundTag nonAmbiguousWithoutOwner = base.copy();
        nonAmbiguousWithoutOwner.getCompound("Directory")
                .getCompound("Entries")
                .getCompound("alpha")
                .remove("UniqueHistoricalOwner");
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(nonAmbiguousWithoutOwner),
                "non-ambiguous entry without a unique historical owner is rejected"
        );

        // Unknown/newer store version.
        CompoundTag newer = base.copy();
        newer.putInt("StoreVersion", 99);
        expectThrows(
                PlayerDataNbtException.class,
                () -> codec.decode(newer),
                "unknown store version is rejected"
        );
    }

    private static void testDeterministicEncoding() {
        PlayerDataNbtCodec codec = new PlayerDataNbtCodec();
        PlayerData alpha = new PlayerData(
                1,
                1,
                new PlayerIdentity(ALPHA_ID, "Alpha", 1_000, 1_000),
                PlayerProfile.empty(1_000)
        );
        PlayerData bravo = new PlayerData(
                1,
                1,
                new PlayerIdentity(BRAVO_ID, "Bravo", 1_000, 1_000),
                PlayerProfile.empty(1_000)
        );
        DirectoryEntry alphaEntry = new DirectoryEntry(
                "alpha", "Alpha", false, Optional.of(ALPHA_ID), Set.of(ALPHA_ID), 1_000, 1_000, 1
        );
        DirectoryEntry bravoEntry = new DirectoryEntry(
                "bravo", "Bravo", false, Optional.of(BRAVO_ID), Set.of(BRAVO_ID), 1_000, 1_000, 1
        );

        PlayerDataStoreSnapshot first = new PlayerDataStoreSnapshot(
                2,
                9,
                Map.of(ALPHA_ID, alpha, BRAVO_ID, bravo),
                Map.of("alpha", alphaEntry, "bravo", bravoEntry),
                MigrationProvenance.NONE
        );
        PlayerDataStoreSnapshot second = new PlayerDataStoreSnapshot(
                2,
                9,
                Map.of(BRAVO_ID, bravo, ALPHA_ID, alpha),
                Map.of("bravo", bravoEntry, "alpha", alphaEntry),
                MigrationProvenance.NONE
        );

        CompoundTag encodedFirst = codec.encode(first);
        CompoundTag encodedSecond = codec.encode(second);
        require(
                encodedFirst.equals(encodedSecond),
                "equivalent snapshots encode identically"
        );
        require(
                codec.decode(encodedFirst).equals(first),
                "deterministic encode/decode round trip"
        );
    }

    // ------------------------------------------------------------------
    // service surface and feedback (FR-DATA-003-A §4.2 / §10)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        Method[] methods = PlayerDirectoryService.class.getDeclaredMethods();
        Set<String> names = Arrays.stream(methods)
                .map(Method::getName)
                .collect(Collectors.toUnmodifiableSet());
        require(
                names.equals(Set.of("resolveExactGameName")),
                "directory service exposes only the exact resolution method: " + names
        );
    }

    private static void testPublicMessagesMerged() {
        String generic = PlayerNameResolution.GENERIC_NON_RESOLUTION_MESSAGE;
        require(
                PlayerNameResolution.unknown().publicMessage().equals(generic),
                "UNKNOWN maps to the generic message"
        );
        require(
                PlayerNameResolution.retired().publicMessage().equals(generic),
                "RETIRED maps to the generic message"
        );
        require(
                PlayerNameResolution.ambiguous().publicMessage().equals(generic),
                "AMBIGUOUS maps to the generic message"
        );
        require(
                !PlayerNameResolution.invalidInput().publicMessage().equals(generic),
                "malformed syntax may be distinguished"
        );
        require(
                PlayerNameResolution.uniqueCurrent(ALPHA_ID).publicMessage().isEmpty(),
                "UNIQUE_CURRENT carries no public feedback string"
        );
    }

    private static void testInvalidInputs() {
        InMemoryStore store = new InMemoryStore();
        PlayerDataRepository repository = repository(store);
        new DefaultPlayerDataService(repository, new MutableClock(1_000))
                .recordLogin(ALPHA_ID, "Alpha");
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        for (String bad : List.of(
                "",
                " ",
                " Player",
                "Player ",
                "Player_Name!",
                "Player*",
                "a b",
                "玩家",
                "ABCDEFGHIJKLMNOPQ" // 17 characters
        )) {
            require(
                    directory.resolveExactGameName(bad).kind()
                            == PlayerNameResolutionKind.INVALID_INPUT,
                    "invalid input is rejected before index lookup: '" + bad + "'"
            );
        }
        require(
                directory.resolveExactGameName(null).kind()
                        == PlayerNameResolutionKind.INVALID_INPUT,
                "null input is invalid"
        );
    }

    private static void testCaseInsensitiveLookup() {
        InMemoryStore store = new InMemoryStore();
        PlayerDataRepository repository = repository(store);
        new DefaultPlayerDataService(repository, new MutableClock(1_000))
                .recordLogin(ALPHA_ID, "Alpha");
        PlayerDirectoryService directory = new DefaultPlayerDirectoryService(repository);

        require(
                directory.resolveExactGameName("ALPHA").kind()
                        == PlayerNameResolutionKind.UNIQUE_CURRENT,
                "uppercase input addresses the same canonical entry"
        );
        require(
                directory.resolveExactGameName("aLPHA").kind()
                        == PlayerNameResolutionKind.UNIQUE_CURRENT,
                "mixed-case input addresses the same canonical entry"
        );
        require(
                directory.resolveExactGameName("Nobody").kind()
                        == PlayerNameResolutionKind.UNKNOWN,
                "unknown alias returns UNKNOWN"
        );
    }

    private static void testStaleProposalRejected() {
        InMemoryStore store = new InMemoryStore();
        MutableClock clock = new MutableClock(1_000);
        PlayerDataRepository repository = repository(store);
        DefaultPlayerDataService service = new DefaultPlayerDataService(repository, clock);

        service.recordLogin(ALPHA_ID, "Alpha");
        PlayerData current = service.require(ALPHA_ID);
        PlayerData staleReplacement = current.nextRevision(
                new PlayerIdentity(ALPHA_ID, "Bravo", 1_000, 1_000),
                current.profile()
        );

        expectThrows(
                StalePlayerDataRevisionException.class,
                () -> repository.replace(staleReplacement, current.revision() - 1),
                "stale proposal is rejected"
        );
        require(
                repository.findDirectoryEntry("alpha").orElseThrow()
                        .currentOwners().equals(Set.of(ALPHA_ID)),
                "stale rejection leaves the directory unchanged"
        );
        require(
                repository.findDirectoryEntry("bravo").isEmpty(),
                "stale rejection publishes no new alias"
        );
        require(
                service.require(ALPHA_ID).identity().lastKnownGameName().equals("Alpha"),
                "stale rejection leaves the identity unchanged"
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ListTag owners(CompoundTag root, String normalizedName) {
        return root.getCompound("Directory")
                .getCompound("Entries")
                .getCompound(normalizedName)
                .getList("CurrentOwners", Tag.TAG_INT_ARRAY);
    }

    private static CompoundTag playerRecord(UUID playerId, String name, long observedAt) {
        CompoundTag record = new CompoundTag();
        record.putInt("RecordVersion", 1);
        record.putLong("Revision", 1);
        CompoundTag identity = new CompoundTag();
        identity.putUUID("PlayerId", playerId);
        identity.putString("LastKnownGameName", name);
        identity.putLong("FirstSeenAt", observedAt);
        identity.putLong("LastSeenAt", observedAt);
        record.put("Identity", identity);
        CompoundTag profile = new CompoundTag();
        profile.putLong("UpdatedAt", observedAt);
        record.put("Profile", profile);
        return record;
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

    private static final class FailingCommitStore implements PlayerDataStore {
        private CompoundTag data = new CompoundTag();
        private boolean failCommits;

        @Override
        public CompoundTag load() {
            return data.copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            data = snapshot.copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (failCommits) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        PlayerDataRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        "IO_WRITE"
                );
            }
            data = snapshot.copy();
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    PlayerDataRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void setFailCommits(boolean enabled) {
            failCommits = enabled;
        }

        private CompoundTag persisted() {
            return data;
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
