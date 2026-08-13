package com.fontainerepublic.server.registry;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.BootstrapAttemptRecord;
import com.fontainerepublic.server.registry.model.BootstrapAttemptResult;
import com.fontainerepublic.server.registry.model.BootstrapDigests;
import com.fontainerepublic.server.registry.model.BootstrapEntityKind;
import com.fontainerepublic.server.registry.model.BootstrapPhase;
import com.fontainerepublic.server.registry.model.BootstrapSourceClassification;
import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.ReservationKind;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import com.fontainerepublic.server.registry.persistence.RegistrySerialGenerator;
import com.fontainerepublic.server.registry.persistence.SubjectIdSource;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryLimits;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryNbtCodec;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryNbtException;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryRepository;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryStore;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryStoreSnapshot;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryUnavailableException;
import com.fontainerepublic.server.registry.service.BootstrapConsoleClassifier;
import com.fontainerepublic.server.registry.service.DefaultSubjectBootstrapService;
import com.fontainerepublic.server.registry.service.SubjectBootstrapService;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-ID-BOOTSTRAP-001 (original
 * person bootstrap). Exercises the FR-ID-BOOTSTRAP-001-A §7 acceptance matrix:
 * console classification, first successful bind, rejected input/source,
 * incomplete retry, persistence failure without publish, replay idempotency,
 * restart reconciliation, tamper detection, immutability, and deterministic
 * encode — with an injectable store and SavedData-backed restart simulation.
 */
public final class BootstrapFoundationTestMain {

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String REASON = "Human-authorized original person binding";

    private BootstrapFoundationTestMain() {
    }

    public static void main(String[] args) {
        testClassificationMatrix();
        testFirstSuccessfulBind();
        testRejectedInputNoBinding();
        testRejectedSourceNoBinding();
        testPlayerDataMissingIncompleteAndIdempotentRetry();
        testPersistenceFailureNoPublishAndReplay();
        testRestartReconciliationConsistent();
        testTamperDetectionFailClosed();
        testImmutabilityDifferentKeyRejected();
        testIdempotentNoopSameKey();
        testDeterministicEncodeBound();
        testReasonBoundary();
        testServiceContractNoEnumeration();
        System.out.println("[FR-ID-BOOTSTRAP-001] Original person bootstrap validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: console source classification matrix (§7)
    // ------------------------------------------------------------------

    private static void testClassificationMatrix() {
        requireClassified(
                true, BootstrapEntityKind.NONE, "Server",
                BootstrapSourceClassification.LOCAL_CONSOLE,
                "real dedicated console passes"
        );
        requireClassified(
                true, BootstrapEntityKind.NONE, "Rcon",
                BootstrapSourceClassification.RCON,
                "RCON is rejected"
        );
        requireClassified(
                true, BootstrapEntityKind.NONE, "@",
                BootstrapSourceClassification.COMMAND_BLOCK,
                "default command-block name is rejected"
        );
        requireClassified(
                true, BootstrapEntityKind.PLAYER, "PlayerName",
                BootstrapSourceClassification.PLAYER,
                "player (including OP) is rejected"
        );
        requireClassified(
                true, BootstrapEntityKind.MINECART_COMMAND_BLOCK, "mc",
                BootstrapSourceClassification.MINECART_COMMAND_BLOCK,
                "minecart command block is rejected"
        );
        requireClassified(
                false, BootstrapEntityKind.NONE, "Server",
                BootstrapSourceClassification.INTEGRATED_HOST,
                "integrated host is rejected"
        );
        requireClassified(
                true, BootstrapEntityKind.NONE, "function_name",
                BootstrapSourceClassification.FUNCTION_OR_OTHER,
                "entity-less function source is rejected"
        );
        requireClassified(
                true, BootstrapEntityKind.OTHER, "anything",
                BootstrapSourceClassification.FUNCTION_OR_OTHER,
                "unknown entity is rejected"
        );
        require(
                BootstrapConsoleClassifier.isLocalConsole(
                        BootstrapSourceClassification.LOCAL_CONSOLE
                ),
                "LOCAL_CONSOLE is accepted for mutation"
        );
        require(
                !BootstrapConsoleClassifier.isLocalConsole(
                        BootstrapSourceClassification.RCON
                ),
                "RCON is never accepted for mutation"
        );
    }

    private static void requireClassified(
            boolean dedicated,
            BootstrapEntityKind entityKind,
            String name,
            BootstrapSourceClassification expected,
            String message
    ) {
        require(
                BootstrapConsoleClassifier.classify(dedicated, entityKind, name) == expected,
                message
        );
    }

    // ------------------------------------------------------------------
    // acceptance: first successful bind (§7: subject + indexes + BOUND +
    // trail terminal success in one snapshot)
    // ------------------------------------------------------------------

    private static void testFirstSuccessfulBind() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(20_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));

        BootstrapAttemptResult result = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(result == BootstrapAttemptResult.SUCCESS, "first bind succeeds");

        SubjectRegistryStoreSnapshot snapshot = repository.snapshot();
        require(
                snapshot.bootstrapState().phase() == BootstrapPhase.BOUND,
                "bootstrap phase is BOUND after success"
        );
        require(
                snapshot.bootstrapState().originalPersonalBindingApplied(),
                "BootstrapState confirms the applied binding"
        );
        require(
                Arrays.equals(
                        snapshot.bootstrapState().boundUuidDigest(),
                        BootstrapDigests.uuidDigest(ALPHA_ID)
                ),
                "bound UUID digest matches the target"
        );
        require(
                snapshot.bootstrapState().boundAt() > 0,
                "boundAt is a positive server-assigned timestamp"
        );

        // Personal subject materialized with the fixed number, type 10, ACTIVE.
        Optional<SubjectRecord> personal =
                repository.findByNumber(RegistryNumber.FIXED_PERSONAL);
        require(personal.isPresent(), "original personal subject is materialized");
        SubjectRecord record = personal.orElseThrow();
        require(
                record.subjectType() == SubjectType.NATURAL_PERSON,
                "original personal subject is a natural person"
        );
        require(record.status() == SubjectStatus.ACTIVE, "original personal subject is ACTIVE");
        require(record.revision() == 1, "original personal subject starts at revision 1");
        require(
                record.ownerReference().equals(OwnerReference.forPlayer(ALPHA_ID)),
                "original personal subject is bound to the designated UUID"
        );
        require(
                repository.findSubjectIdByNumber(RegistryNumber.FIXED_PERSONAL)
                        .equals(Optional.of(record.subjectId())),
                "number index maps 10-000001-61 to the personal subject"
        );
        require(
                snapshot.reservations().get(RegistryNumber.FIXED_PERSONAL)
                        .boundSubjectId().equals(Optional.of(record.subjectId())),
                "fixed personal reservation is bound to the subject"
        );

        // Trail: PENDING then SUCCESS, chained, head matches BootstrapState.
        Map<String, BootstrapAttemptRecord> attempts = repository.bootstrapAttempts();
        require(attempts.size() == 2, "one PENDING and one SUCCESS record");
        List<BootstrapAttemptRecord> chain = chain(attempts);
        require(
                chain.get(0).resultCode() == BootstrapAttemptResult.PENDING,
                "first trail record is PENDING"
        );
        require(
                chain.get(1).resultCode() == BootstrapAttemptResult.SUCCESS,
                "second trail record is SUCCESS"
        );
        require(
                Arrays.equals(
                        chain.get(1).prevDigest(),
                        chain.get(0).selfDigest()
                ),
                "SUCCESS record chains to the PENDING record"
        );
        require(
                Arrays.equals(
                        snapshot.bootstrapState().trailHeadDigest(),
                        chain.get(1).selfDigest()
                ),
                "trail head digest agrees with the last record"
        );
        require(
                Arrays.equals(
                        chain.get(1).uuidDigest(),
                        BootstrapDigests.uuidDigest(ALPHA_ID)
                ),
                "SUCCESS record targets the bound UUID digest"
        );
        require(
                snapshot.storeRevision() == 3L,
                "bind advances the store exactly: init(1) + pending(2) + success(3)"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: rejected input / rejected source (§7)
    // ------------------------------------------------------------------

    private static void testRejectedInputNoBinding() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(30_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));

        BootstrapAttemptResult blank = service.bootstrapOriginalPerson(
                ALPHA_ID, "   ", BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(blank == BootstrapAttemptResult.REJECTED_INPUT, "blank reason is rejected input");
        require(
                repository.bootstrapState().phase() == BootstrapPhase.UNBOUND,
                "rejected input leaves the registry unbound"
        );
        require(
                repository.findByNumber(RegistryNumber.FIXED_PERSONAL).isEmpty(),
                "rejected input materializes no subject"
        );
        require(
                repository.bootstrapAttempts().size() == 2,
                "rejected input is durably audited (PENDING + REJECTED_INPUT)"
        );

        // Overlong reason.
        String longReason = "r".repeat(DefaultSubjectBootstrapService.MAX_REASON_LENGTH + 1);
        BootstrapAttemptResult overlong = service.bootstrapOriginalPerson(
                ALPHA_ID, longReason, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(
                overlong == BootstrapAttemptResult.REJECTED_INPUT,
                "overlong reason is rejected input"
        );
    }

    private static void testRejectedSourceNoBinding() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(40_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));

        BootstrapAttemptResult result = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.RCON
        );
        require(result == BootstrapAttemptResult.REJECTED_SOURCE, "RCON source is rejected");
        require(
                repository.bootstrapState().phase() == BootstrapPhase.UNBOUND,
                "rejected source leaves the registry unbound"
        );
        require(
                repository.findByNumber(RegistryNumber.FIXED_PERSONAL).isEmpty(),
                "rejected source materializes no subject"
        );
        require(
                repository.bootstrapAttempts().size() == 2,
                "rejected source is durably audited (PENDING + REJECTED_SOURCE)"
        );

        BootstrapAttemptResult function = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.FUNCTION_OR_OTHER
        );
        require(
                function == BootstrapAttemptResult.REJECTED_SOURCE,
                "function source is rejected"
        );
        require(
                repository.bootstrapState().phase() == BootstrapPhase.UNBOUND,
                "function rejection leaves the registry unbound"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: PlayerData absent -> INCOMPLETE; retry idempotent (§7)
    // ------------------------------------------------------------------

    private static void testPlayerDataMissingIncompleteAndIdempotentRetry() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(50_000);
        SubjectRegistryRepository repository = repository(store, clock);
        FakePresence presence = presence(); // no players provisioned yet
        SubjectBootstrapService service =
                bootstrapService(repository, clock, presence);

        BootstrapAttemptResult incomplete = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(
                incomplete == BootstrapAttemptResult.PLAYER_NOT_PROVISIONED,
                "missing PlayerData yields INCOMPLETE"
        );
        require(
                repository.bootstrapState().phase() == BootstrapPhase.UNBOUND,
                "incomplete attempt leaves the registry unbound"
        );
        require(
                repository.findByNumber(RegistryNumber.FIXED_PERSONAL).isEmpty(),
                "incomplete attempt materializes no subject"
        );
        require(
                repository.bootstrapAttempts().size() == 2,
                "incomplete attempt is durably audited (PENDING + INCOMPLETE)"
        );

        // Same-key retry after the player record appears succeeds once.
        presence.add(ALPHA_ID);
        clock.advance(1_000);
        BootstrapAttemptResult retry = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(retry == BootstrapAttemptResult.SUCCESS, "same-key retry succeeds");
        require(
                repository.bootstrapState().phase() == BootstrapPhase.BOUND,
                "retry binds the original person"
        );
        require(
                repository.bootstrapAttempts().size() == 4,
                "retry appends its own PENDING + SUCCESS (append-only trail)"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: persistence failure -> no publish; replay no rebind (§7)
    // ------------------------------------------------------------------

    private static void testPersistenceFailureNoPublishAndReplay() {
        // Commit sequence: init(1) + pending(2) succeed; the binding snapshot
        // commit (3) fails.
        FailAfterCommitsStore store = new FailAfterCommitsStore(2);
        MutableClock clock = new MutableClock(60_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));

        BootstrapAttemptResult failure = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(
                failure == BootstrapAttemptResult.PERSISTENCE_FAILURE,
                "unacknowledged durable commit yields PERSISTENCE_FAILURE"
        );
        require(
                repository.bootstrapState().phase() == BootstrapPhase.UNBOUND,
                "no binding is published on persistence failure"
        );
        require(
                repository.findByNumber(RegistryNumber.FIXED_PERSONAL).isEmpty(),
                "no subject is published on persistence failure"
        );
        require(
                repository.bootstrapAttempts().size() == 1,
                "only the PENDING record survived the failed commit"
        );

        // Recovery: restore the store, replay with the same key -> one bind.
        store.setCommitLimit(Integer.MAX_VALUE);
        clock.advance(1_000);
        BootstrapAttemptResult replay = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(replay == BootstrapAttemptResult.SUCCESS, "replay after recovery binds once");
        require(
                repository.bootstrapState().phase() == BootstrapPhase.BOUND,
                "replay commits the binding"
        );
        require(
                repository.bootstrapAttempts().size() == 3,
                "replay trail: PENDING(1) + retry PENDING + SUCCESS"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart reconciliation (consistent / tampered) (§7)
    // ------------------------------------------------------------------

    private static void testRestartReconciliationConsistent() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(70_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));
        require(
                service.bootstrapOriginalPerson(
                        ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
                ) == BootstrapAttemptResult.SUCCESS,
                "bind before restart"
        );
        SubjectRegistryStoreSnapshot before = repository.snapshot();

        // Restart: fresh repository over the same store must reconcile and
        // keep the binding.
        SubjectRegistryRepository restarted = restartRepository(store, clock);
        SubjectRegistryStoreSnapshot after = restarted.snapshot();
        require(
                after.bootstrapState().phase() == BootstrapPhase.BOUND,
                "restart reconciliation keeps BOUND"
        );
        require(
                after.bootstrapState().equals(before.bootstrapState()),
                "restart preserves the bootstrap state"
        );
        require(
                after.bootstrapAttempts().equals(before.bootstrapAttempts()),
                "restart preserves the attempt trail"
        );
        require(
                restarted.findByNumber(RegistryNumber.FIXED_PERSONAL).isPresent(),
                "restart keeps the bound personal subject"
        );
    }

    private static void testTamperDetectionFailClosed() {
        // Tamper 1: BOUND state but the SUCCESS record is removed from the trail.
        CompoundTag noSuccess = encodedBound();
        CompoundTag attempts = noSuccess.getCompound("BootstrapAttempts");
        attempts.remove(attempts.getAllKeys().stream()
                .filter(key -> {
                    CompoundTag tag = attempts.getCompound(key);
                    return tag.getString("ResultCode").equals("SUCCESS");
                })
                .findFirst()
                .orElseThrow());
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(noSuccess),
                "BOUND without a SUCCESS trail record is rejected"
        );

        // Tamper 2: trail head digest does not match the last record.
        CompoundTag badHead = encodedBound();
        byte[] flipped = new byte[BootstrapDigests.DIGEST_LENGTH];
        Arrays.fill(flipped, (byte) 0x5A);
        badHead.getCompound("BootstrapState").putByteArray("TrailHeadDigest", flipped);
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(badHead),
                "mismatched trail head digest is rejected"
        );

        // Tamper 3: SUCCESS target digest disagrees with boundUuidDigest.
        CompoundTag wrongTarget = encodedBound();
        String successKey = wrongTarget.getCompound("BootstrapAttempts").getAllKeys()
                .stream()
                .filter(key -> wrongTarget.getCompound("BootstrapAttempts")
                        .getCompound(key)
                        .getString("ResultCode")
                        .equals("SUCCESS"))
                .findFirst()
                .orElseThrow();
        byte[] otherDigest = BootstrapDigests.uuidDigest(BRAVO_ID);
        wrongTarget.getCompound("BootstrapAttempts").getCompound(successKey)
                .putByteArray("UuidDigest", otherDigest);
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(wrongTarget),
                "SUCCESS target mismatch is rejected"
        );

        // Tamper 4: BOUND claim while phase UNBOUND (OriginalPersonalBindingApplied
        // flipped off) is an inconsistent BootstrapState.
        CompoundTag flippedState = encodedBound();
        flippedState.getCompound("BootstrapState").putBoolean(
                "OriginalPersonalBindingApplied", false
        );
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(flippedState),
                "inconsistent BOUND flag is rejected"
        );

        // Tamper 5: reordered chain (SUCCESS prevDigest zeroed) forks/rejects.
        CompoundTag reordered = encodedBound();
        String successKey2 = reordered.getCompound("BootstrapAttempts").getAllKeys()
                .stream()
                .filter(key -> reordered.getCompound("BootstrapAttempts")
                        .getCompound(key)
                        .getString("ResultCode")
                        .equals("SUCCESS"))
                .findFirst()
                .orElseThrow();
        reordered.getCompound("BootstrapAttempts").getCompound(successKey2)
                .putByteArray("PrevDigest", BootstrapDigests.ZERO_DIGEST);
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(reordered),
                "forked trail (two roots) is rejected"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: immutability (§7: any later rebind/transfer rejected)
    // ------------------------------------------------------------------

    private static void testImmutabilityDifferentKeyRejected() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(80_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID, BRAVO_ID));

        require(
                service.bootstrapOriginalPerson(
                        ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
                ) == BootstrapAttemptResult.SUCCESS,
                "bind alpha"
        );
        int attemptsBefore = repository.bootstrapAttempts().size();

        BootstrapAttemptResult rebind = service.bootstrapOriginalPerson(
                BRAVO_ID, "transfer attempt", BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(
                rebind == BootstrapAttemptResult.REJECTED,
                "rebinding a different UUID is rejected"
        );
        require(
                repository.bootstrapState().phase() == BootstrapPhase.BOUND,
                "binding stays BOUND after rejected rebind"
        );
        require(
                Arrays.equals(
                        repository.bootstrapState().boundUuidDigest(),
                        BootstrapDigests.uuidDigest(ALPHA_ID)
                ),
                "bound UUID is unchanged after rejected rebind"
        );
        require(
                repository.findByNumber(RegistryNumber.FIXED_PERSONAL)
                        .orElseThrow()
                        .ownerReference()
                        .equals(OwnerReference.forPlayer(ALPHA_ID)),
                "no second subject is created for the rejected target"
        );
        require(
                repository.bootstrapAttempts().size() == attemptsBefore + 2,
                "rejected rebind is durably audited (PENDING + REJECTED)"
        );
    }

    private static void testIdempotentNoopSameKey() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(90_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));

        require(
                service.bootstrapOriginalPerson(
                        ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
                ) == BootstrapAttemptResult.SUCCESS,
                "bind once"
        );
        BootstrapAttemptResult replay = service.bootstrapOriginalPerson(
                ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(
                replay == BootstrapAttemptResult.IDEMPOTENT_NOOP,
                "same-key replay is an idempotent no-op"
        );
        require(
                repository.bootstrapState().phase() == BootstrapPhase.BOUND,
                "no-op keeps the binding"
        );
        require(
                repository.size() == 2,
                "no second personal subject is created"
        );
        require(
                repository.bootstrapAttempts().size() == 4,
                "no-op replay is durably audited (PENDING + IDEMPOTENT_NOOP)"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: deterministic encode (§7)
    // ------------------------------------------------------------------

    private static void testDeterministicEncodeBound() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(100_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));
        require(
                service.bootstrapOriginalPerson(
                        ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
                ) == BootstrapAttemptResult.SUCCESS,
                "bind for codec check"
        );

        SubjectRegistryNbtCodec codec = new SubjectRegistryNbtCodec();
        SubjectRegistryStoreSnapshot snapshot = repository.snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(
                codec.encodedSize(first) == codec.encodedSize(second),
                "bound snapshot encodes deterministically"
        );
        require(
                codec.decode(second).equals(snapshot),
                "decode(encode(bound snapshot)) equals the snapshot"
        );
        require(
                codec.decode(codec.encode(snapshot)).equals(codec.decode(store.load())),
                "persisted bound form decodes equivalently"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: reason boundary
    // ------------------------------------------------------------------

    private static void testReasonBoundary() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(110_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));

        // Exactly the maximum length is accepted.
        String maxReason = "r".repeat(DefaultSubjectBootstrapService.MAX_REASON_LENGTH);
        BootstrapAttemptResult accepted = service.bootstrapOriginalPerson(
                ALPHA_ID, maxReason, BootstrapSourceClassification.LOCAL_CONSOLE
        );
        require(accepted == BootstrapAttemptResult.SUCCESS, "max-length reason is accepted");

        // Beyond the maximum is rejected on a fresh registry.
        SavedDataBackedTestStore store2 = new SavedDataBackedTestStore();
        MutableClock clock2 = new MutableClock(111_000);
        SubjectRegistryRepository repository2 = repository(store2, clock2);
        SubjectBootstrapService service2 = bootstrapService(repository2, clock2, presence(ALPHA_ID));
        String tooLong = "r".repeat(DefaultSubjectBootstrapService.MAX_REASON_LENGTH + 1);
        require(
                service2.bootstrapOriginalPerson(
                        ALPHA_ID, tooLong, BootstrapSourceClassification.LOCAL_CONSOLE
                ) == BootstrapAttemptResult.REJECTED_INPUT,
                "over-max reason is rejected"
        );
    }

    // ------------------------------------------------------------------
    // service contract: no enumeration API on the bootstrap surface
    // ------------------------------------------------------------------

    private static void testServiceContractNoEnumeration() {
        require(
                SubjectRegistryRepository.MODULE_DATA_KEY.equals("subject-registry"),
                "bootstrap trail lives in the subject-registry namespace"
        );
        for (java.lang.reflect.Method method
                : SubjectBootstrapService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (name.contains("list") || name.contains("all") || name.contains("prefix")
                    || name.contains("range") || name.contains("enumerate")
                    || name.contains("export") || name.contains("iterate")) {
                throw new AssertionError(
                        "SubjectBootstrapService exposes a forbidden enumeration method "
                                + method.getName()
                );
            }
        }
        require(
                BootstrapAttemptResult.values().length >= 6,
                "terminal result codes cover rejection/incomplete/noop/failure/success"
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static SubjectRegistryRepository repository(
            SubjectRegistryStore store,
            LongSupplier clock
    ) {
        return new SubjectRegistryRepository(
                store,
                new SubjectRegistryNbtCodec(),
                SubjectRegistryLimits.DEFAULT,
                new IncrementingSerialGenerator(42),
                UUID::randomUUID,
                clock
        );
    }

    private static SubjectRegistryRepository restartRepository(
            SavedDataBackedTestStore store,
            LongSupplier clock
    ) {
        return repository(store.restart(), clock);
    }

    private static SubjectBootstrapService bootstrapService(
            SubjectRegistryRepository repository,
            LongSupplier clock,
            PlayerPresence presence
    ) {
        return new DefaultSubjectBootstrapService(repository, clock, presence);
    }

    private static FakePresence presence(UUID... players) {
        FakePresence presence = new FakePresence();
        for (UUID player : players) {
            presence.add(player);
        }
        return presence;
    }

    /** Orders the trail by the digest chain (root first). */
    private static List<BootstrapAttemptRecord> chain(
            Map<String, BootstrapAttemptRecord> attempts
    ) {
        List<BootstrapAttemptRecord> ordered = new ArrayList<>(attempts.size());
        BootstrapAttemptRecord current = null;
        for (BootstrapAttemptRecord attempt : attempts.values()) {
            if (Arrays.equals(attempt.prevDigest(), BootstrapDigests.ZERO_DIGEST)) {
                current = attempt;
                break;
            }
        }
        require(current != null, "trail has a root");
        while (current != null) {
            ordered.add(current);
            BootstrapAttemptRecord next = null;
            for (BootstrapAttemptRecord candidate : attempts.values()) {
                if (Arrays.equals(candidate.prevDigest(), current.selfDigest())) {
                    next = candidate;
                    break;
                }
            }
            current = next;
        }
        return ordered;
    }

    /** Encodes a successfully bound snapshot for tamper tests. */
    private static CompoundTag encodedBound() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(120_000);
        SubjectRegistryRepository repository = repository(store, clock);
        SubjectBootstrapService service = bootstrapService(repository, clock, presence(ALPHA_ID));
        require(
                service.bootstrapOriginalPerson(
                        ALPHA_ID, REASON, BootstrapSourceClassification.LOCAL_CONSOLE
                ) == BootstrapAttemptResult.SUCCESS,
                "bound snapshot prepared"
        );
        return store.load();
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

    // ------------------------------------------------------------------
    // test stores
    // ------------------------------------------------------------------

    private static final class SavedDataBackedTestStore implements SubjectRegistryStore {
        private ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(
                    SubjectRegistryRepository.MODULE_DATA_KEY
            ).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        SubjectRegistryRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    SubjectRegistryRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    SubjectRegistryRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(SubjectRegistryRepository.MODULE_DATA_KEY, raw);
        }

        private SavedDataBackedTestStore restart() {
            CompoundTag root = savedData.save(new CompoundTag());
            return new SavedDataBackedTestStore(ModSavedData.load(root));
        }
    }

    /** Fails all commits after the first {@code limit} succeed. */
    private static final class FailAfterCommitsStore implements SubjectRegistryStore {
        private final SavedDataBackedTestStore delegate = new SavedDataBackedTestStore();
        private int commits;
        private int commitLimit;

        private FailAfterCommitsStore(int commitLimit) {
            this.commitLimit = commitLimit;
        }

        private void setCommitLimit(int commitLimit) {
            this.commitLimit = commitLimit;
        }

        @Override
        public CompoundTag load() {
            return delegate.load();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (commits >= commitLimit) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        SubjectRegistryRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        "INJECTED_FAILURE"
                );
            }
            commits++;
            return delegate.commit(snapshot);
        }
    }

    private static final class IncrementingSerialGenerator implements RegistrySerialGenerator {
        private int next;

        private IncrementingSerialGenerator(int start) {
            this.next = start;
        }

        @Override
        public int nextSerial() {
            return next++;
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

    private static final class FakePresence implements PlayerPresence {
        private final Set<UUID> players = new HashSet<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            return players.contains(playerId);
        }

        private void add(UUID playerId) {
            players.add(playerId);
        }
    }
}
