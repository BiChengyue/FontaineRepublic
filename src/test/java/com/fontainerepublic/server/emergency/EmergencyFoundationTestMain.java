package com.fontainerepublic.server.emergency;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.emergency.api.EmergencyActionRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyReceiptProvider;
import com.fontainerepublic.server.emergency.api.EmergencyRequest;
import com.fontainerepublic.server.emergency.api.EmergencyService;
import com.fontainerepublic.server.emergency.api.ConfigureResult;
import com.fontainerepublic.server.emergency.model.EmergencyActorSource;
import com.fontainerepublic.server.emergency.model.EmergencyActorType;
import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencyConfigPhase;
import com.fontainerepublic.server.emergency.model.EmergencyConfigState;
import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.emergency.model.EmergencyEntityKind;
import com.fontainerepublic.server.emergency.model.EmergencySourceClassification;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;
import com.fontainerepublic.server.emergency.model.EmergencyTokenEntry;
import com.fontainerepublic.server.emergency.persistence.EmergencyLimits;
import com.fontainerepublic.server.emergency.persistence.EmergencyNbtCodec;
import com.fontainerepublic.server.emergency.persistence.EmergencyRepository;
import com.fontainerepublic.server.emergency.persistence.EmergencyStore;
import com.fontainerepublic.server.emergency.persistence.EmergencyStoreSnapshot;
import com.fontainerepublic.server.emergency.persistence.EmergencyUnavailableException;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyActionRegistry;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyService;
import com.fontainerepublic.server.emergency.service.EmergencyAuthorityConfigSource;
import com.fontainerepublic.server.emergency.service.EmergencyConsoleClassifier;
import com.fontainerepublic.server.emergency.service.EmergencyTokenTable;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Dependency-free FR-EMG-001 foundation validation (Level 1-2).
 *
 * <p>Covers the security invariants of the shared emergency authority:
 * actor/source classification, canonical request validation, token-table
 * single-use lifecycle, authority-configuration bootstrap/staging/drift/
 * recovery, injected store failure with no publication, and restart
 * recovery.</p>
 */
public final class EmergencyFoundationTestMain {

    private static final UUID HYDRO_UUID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_UUID =
            UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final long START = 1_000L;

    private EmergencyFoundationTestMain() {
    }

    public static void main(String[] args) {
        testActorSourceInvariants();
        testRequestValidation();
        testClassifierMatrix();
        testTokenTableLifecycle();
        testBootstrapAuthorityConsoleOnly();
        testStageAcceptAndDrift();
        testRecoverAuthorityConsoleOnly();
        testInjectedStoreFailureNoPublish();
        testRestartRecovery();
        System.out.println("[FR-EMG-001] Emergency foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. actor/source invariants
    // ------------------------------------------------------------------

    private static void testActorSourceInvariants() {
        EmergencyActorSource hydro = new EmergencyActorSource(
                EmergencyActorType.HYDRO_ARCHON, HYDRO_UUID,
                EmergencySourceClassification.LOCAL_CONSOLE);
        require(hydro.uuid().isPresent(), "hydro source carries the actor uuid");
        require(hydro.actorType() == EmergencyActorType.HYDRO_ARCHON, "actor type hydro");

        EmergencyActorSource console = new EmergencyActorSource(
                EmergencyActorType.SERVER_CONSOLE, null,
                EmergencySourceClassification.LOCAL_CONSOLE);
        require(console.uuid().isEmpty(), "console source carries no uuid");

        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyActorSource(EmergencyActorType.HYDRO_ARCHON, null,
                        EmergencySourceClassification.LOCAL_CONSOLE),
                "hydro source requires a uuid");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyActorSource(EmergencyActorType.SERVER_CONSOLE,
                        OTHER_UUID, EmergencySourceClassification.LOCAL_CONSOLE),
                "console source cannot carry a uuid");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyActorSource(EmergencyActorType.SERVER_CONSOLE, null,
                        EmergencySourceClassification.RCON),
                "console source requires LOCAL_CONSOLE classification");
    }

    // ------------------------------------------------------------------
    // 2. request validation
    // ------------------------------------------------------------------

    private static void testRequestValidation() {
        EmergencyRequest valid = new EmergencyRequest(
                "economy", "issue", "1", EmergencyTargetType.PLAYER_UUID,
                OTHER_UUID.toString(), EmergencyCategory.DEBUG, "remediation", Map.of());
        require(valid.targetUuid().isPresent(), "player uuid target parses");

        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyRequest("economy", "issue", "1",
                        EmergencyTargetType.PLAYER_UUID, "not-a-uuid",
                        EmergencyCategory.DEBUG, "r", Map.of()),
                "player target must be canonical uuid");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyRequest("economy", "issue", "1",
                        EmergencyTargetType.PLAYER_UUID, OTHER_UUID.toString(),
                        EmergencyCategory.DEBUG, "   ", Map.of()),
                "reason must not be blank");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyRequest("economy", "issue", "1",
                        EmergencyTargetType.PLAYER_UUID, OTHER_UUID.toString(),
                        EmergencyCategory.DEBUG, "r", Map.of("k".repeat(40), "v")),
                "parameter keys are bounded");
    }

    // ------------------------------------------------------------------
    // 3. classifier matrix
    // ------------------------------------------------------------------

    private static void testClassifierMatrix() {
        require(EmergencyConsoleClassifier.classify(true,
                        EmergencyEntityKind.NONE, "Server")
                        == EmergencySourceClassification.LOCAL_CONSOLE,
                "dedicated + entityless + Server is the local console");
        require(EmergencyConsoleClassifier.isLocalConsole(
                        EmergencySourceClassification.LOCAL_CONSOLE),
                "LOCAL_CONSOLE accepted for mutation");
        require(EmergencyConsoleClassifier.classify(true,
                        EmergencyEntityKind.NONE, "Rcon")
                        == EmergencySourceClassification.RCON,
                "RCON is rejected");
        require(EmergencyConsoleClassifier.classify(true,
                        EmergencyEntityKind.NONE, "@")
                        == EmergencySourceClassification.COMMAND_BLOCK,
                "command block rejected");
        require(EmergencyConsoleClassifier.classify(true,
                        EmergencyEntityKind.PLAYER, "p")
                        == EmergencySourceClassification.PLAYER,
                "player rejected");
        require(EmergencyConsoleClassifier.classify(false,
                        EmergencyEntityKind.NONE, "Server")
                        == EmergencySourceClassification.INTEGRATED_HOST,
                "integrated host rejected");
    }

    // ------------------------------------------------------------------
    // 4. token table lifecycle
    // ------------------------------------------------------------------

    private static void testTokenTableLifecycle() {
        EmergencyTokenTable table = new EmergencyTokenTable(7L);
        EmergencyActorSource actor = console();
        EmergencyTokenTable.TokenIssue issue =
                table.issue(actor.bind("economy", "issue", "1",
                                EmergencyTargetType.PLAYER_UUID, OTHER_UUID.toString(),
                                EmergencyCategory.DEBUG, "reason", Map.of("amount", "10"),
                                EmergencyDigests.uuidDigest(OTHER_UUID), true),
                        1L, START);
        require(table.size() == 1, "one token issued");

        EmergencyTokenEntry claimed = table.claim(issue.token(), START + 1);
        require(claimed != null, "token claims once");
        require(table.claim(issue.token(), START + 2) == null,
                "second claim rejected (single use)");
        table.consume(claimed);
        require(table.claim(issue.token(), START + 3) == null,
                "consumed token cannot be claimed");

        EmergencyTokenTable expired = new EmergencyTokenTable(9L);
        EmergencyTokenTable.TokenIssue exp =
                expired.issue(actor.bind("economy", "issue", "1",
                                EmergencyTargetType.PLAYER_UUID, OTHER_UUID.toString(),
                                EmergencyCategory.DEBUG, "r", Map.of(),
                                EmergencyDigests.ZERO_DIGEST, true),
                        2L, START);
        require(expired.claim(exp.token(), START + 100_000L) == null,
                "expired token rejected");

        expired.invalidateAll();
        require(expired.size() == 0, "invalidateAll clears the table");
        require(expired.isEpoch(9L) && !expired.isEpoch(7L), "epoch binding");
    }

    // ------------------------------------------------------------------
    // 5. bootstrap authority (console only)
    // ------------------------------------------------------------------

    private static void testBootstrapAuthorityConsoleOnly() {
        TestHarness h = harness(0L);
        EmergencyService service = h.service();

        ConfigureResult nonConsole = service.bootstrapAuthority(
                OTHER_UUID, "first", hydroSource(OTHER_UUID));
        require(!nonConsole.accepted(), "non-console bootstrap rejected");
        require(h.repository.config().phase() == EmergencyConfigPhase.UNSET,
                "rejected bootstrap leaves UNSET");

        service.bootstrapAuthority(HYDRO_UUID, "first", console());
        require(h.repository.config().phase() == EmergencyConfigPhase.ACTIVE,
                "console bootstrap activates authority");
        require(java.util.Arrays.equals(h.repository.config().activeUuidDigest(),
                        EmergencyDigests.uuidDigest(HYDRO_UUID)),
                "active digest is the bootstrapped uuid");
    }

    // ------------------------------------------------------------------
    // 6. staged change / startup acceptance / drift
    // ------------------------------------------------------------------

    private static void testStageAcceptAndDrift() {
        TestHarness h = harness(0L);
        EmergencyService service = h.service();
        service.bootstrapAuthority(HYDRO_UUID, "first", console());

        service.stageAuthority(OTHER_UUID, "rotate", console());
        require(h.repository.config().phase() == EmergencyConfigPhase.STAGED,
                "controlled change stages the next uuid");

        // matching candidate (digest equals staged) -> accepted
        h.config.configure(OTHER_UUID);
        require(service.acceptStagedAtStartup(), "matching staged value accepted");
        require(h.repository.config().phase() == EmergencyConfigPhase.ACTIVE,
                "accepted staged value activates");

        // drift: configured candidate differs from accepted -> fail closed
        TestHarness h2 = harness(0L);
        h2.service().bootstrapAuthority(HYDRO_UUID, "first", console());
        h2.service().stageAuthority(OTHER_UUID, "rotate", console());
        h2.config.configure(HYDRO_UUID);
        require(!h2.service().acceptStagedAtStartup(),
                "drift fails closed (no authority accepted)");
        require(h2.repository.config().phase() == EmergencyConfigPhase.STAGED,
                "drift leaves the staged state unresolved");
    }

    // ------------------------------------------------------------------
    // 7. recovery (console only)
    // ------------------------------------------------------------------

    private static void testRecoverAuthorityConsoleOnly() {
        TestHarness h = harness(0L);
        EmergencyService service = h.service();
        service.bootstrapAuthority(HYDRO_UUID, "first", console());
        h.config.configure(OTHER_UUID);
        require(!service.acceptStagedAtStartup(), "drift detected");

        ConfigureResult nonConsoleRecovery = service.recoverAuthority(
                OTHER_UUID, "repair", hydroSource(HYDRO_UUID));
        require(!nonConsoleRecovery.accepted(), "recovery requires the real local console");
        service.recoverAuthority(OTHER_UUID, "repair", console());
        require(java.util.Arrays.equals(h.repository.config().activeUuidDigest(),
                        EmergencyDigests.uuidDigest(OTHER_UUID)),
                "console recovery repairs authority");
    }

    // ------------------------------------------------------------------
    // 8. injected store failure -> no publication
    // ------------------------------------------------------------------

    private static void testInjectedStoreFailureNoPublish() {
        TestHarness h = harness(1L);
        h.store.failNext(true);
        ConfigureResult failure = h.service().bootstrapAuthority(
                HYDRO_UUID, "first", console());
        require(!failure.accepted(), "store failure rejects the bootstrap");
        require(h.repository.config().phase() == EmergencyConfigPhase.UNSET,
                "failed bootstrap leaves UNSET (no publication)");
    }

    // ------------------------------------------------------------------
    // 9. restart recovery
    // ------------------------------------------------------------------

    private static void testRestartRecovery() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        EmergencyRepository first = repository(store);
        EmergencyService service = service(first, store, 0L);
        service.bootstrapAuthority(HYDRO_UUID, "first", console());
        require(first.config().phase() == EmergencyConfigPhase.ACTIVE, "bootstrapped");

        EmergencyRepository restarted = repository(store);
        require(restarted.config().phase() == EmergencyConfigPhase.ACTIVE,
                "config survives restart");
        require(java.util.Arrays.equals(restarted.config().activeUuidDigest(),
                        EmergencyDigests.uuidDigest(HYDRO_UUID)),
                "active digest survives restart");
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private static final class MutableClock implements LongSupplier {
        private long value;

        MutableClock(long value) {
            this.value = value;
        }

        @Override
        public long getAsLong() {
            return value;
        }
    }

    private static final class FakeAuthorityConfigSource
            implements EmergencyAuthorityConfigSource {
        private Optional<UUID> candidate = Optional.empty();

        void configure(UUID uuid) {
            candidate = Optional.of(uuid);
        }

        @Override
        public Optional<byte[]> candidateUuidDigest() {
            return candidate.map(EmergencyDigests::uuidDigest);
        }

        @Override
        public Optional<UUID> candidateUuid() {
            return candidate;
        }
    }

    private static final class SavedDataBackedTestStore implements EmergencyStore {
        private CompoundTag raw = new CompoundTag();
        private final AtomicBoolean failNext = new AtomicBoolean(false);
        private int commits;

        @Override
        public CompoundTag load() {
            return raw.copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (failNext.getAndSet(false)) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED, "emergency", 0L, 0L, "INJECTED");
            }
            raw = snapshot.copy();
            commits++;
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED, "emergency", 0L, 0L, "");
        }

        void failNext(boolean value) {
            failNext.set(value);
        }
    }

    private static final class TestHarness {
        final SavedDataBackedTestStore store;
        final EmergencyRepository repository;
        final FakeAuthorityConfigSource config;

        TestHarness(long epoch) {
            store = new SavedDataBackedTestStore();
            repository = repository(store);
            config = new FakeAuthorityConfigSource();
        }

        EmergencyService service() {
            return EmergencyFoundationTestMain.service(repository, store, config, 1L);
        }
    }

    private static EmergencyRepository repository(EmergencyStore store) {
        return new EmergencyRepository(store, new EmergencyNbtCodec(), EmergencyLimits.DEFAULT);
    }

    private static EmergencyService service(
            EmergencyRepository repository,
            SavedDataBackedTestStore store,
            long epoch
    ) {
        return service(repository, store, new FakeAuthorityConfigSource(), epoch);
    }

    private static EmergencyService service(
            EmergencyRepository repository,
            SavedDataBackedTestStore store,
            FakeAuthorityConfigSource config,
            long epoch
    ) {
        EmergencyActionRegistry registry = new DefaultEmergencyActionRegistry();
        long positiveEpoch = Math.max(epoch, 1L);
        EmergencyTokenTable table = new EmergencyTokenTable(positiveEpoch);
        MutableClock clock = new MutableClock(START);
        return new DefaultEmergencyService(
                repository, registry, table, clock, positiveEpoch, config, Map.of());
    }

    private static EmergencyActorSource console() {
        return new EmergencyActorSource(
                EmergencyActorType.SERVER_CONSOLE, null,
                EmergencySourceClassification.LOCAL_CONSOLE);
    }

    private static EmergencyActorSource hydroSource(UUID uuid) {
        return new EmergencyActorSource(
                EmergencyActorType.HYDRO_ARCHON, uuid,
                EmergencySourceClassification.PLAYER);
    }

    private static TestHarness harness(long epoch) {
        return new TestHarness(epoch);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type, Runnable action, String message
    ) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError(message + " (unexpected: " + thrown + ")", thrown);
        }
        throw new AssertionError(message + " (no exception thrown)");
    }
}
