package com.fontainerepublic.server.citizen;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.citizen.api.CitizenChangeKind;
import com.fontainerepublic.server.citizen.api.CitizenReceipt;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.api.SubjectDirectory;
import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.citizen.persistence.CitizenLimits;
import com.fontainerepublic.server.citizen.persistence.CitizenNbtCodec;
import com.fontainerepublic.server.citizen.persistence.CitizenNbtException;
import com.fontainerepublic.server.citizen.persistence.CitizenRepository;
import com.fontainerepublic.server.citizen.persistence.CitizenStore;
import com.fontainerepublic.server.citizen.persistence.CitizenStoreSnapshot;
import com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException;
import com.fontainerepublic.server.citizen.service.DefaultCitizenService;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Dependency-free validation entry point for FR-CIT-001 (citizen module).
 * Exercises the FR-CIT-001-A §7 acceptance matrix with an injectable store
 * and SavedData-backed restart simulation: lazy idempotent provisioning,
 * citizen-without-subject rejection, single-snapshot gated rank/status
 * mutations, rank-never-permission, strict deterministic codec, restart
 * recovery, and no bulk enumeration API.
 */
public final class CitizenFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ALPHA_SUBJECT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_SUBJECT =
            UUID.fromString("10000000-0000-0000-0000-000000000002");

    private CitizenFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testLazyEnsureIdempotentDefaults();
        testEnsurePreconditionFailures();
        testCitizenWithoutSubjectRejected();
        testRankStatusMutationSingleSnapshot();
        testIdempotentNoOp();
        testRankNoTechnicalEffect();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testRestartPersistence();
        testNoEnumerationApi();
        testCapacityFailClosed();
        testStoreFailureAtomicity();
        testSubjectMissingOnMutation();
        testModuleContract();
        System.out.println("[FR-CIT-001] Citizen foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: lazy idempotent provisioning with defaults (§7)
    // ------------------------------------------------------------------

    private static void testLazyEnsureIdempotentDefaults() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        CitizenRepository repository = repository(store);
        CitizenService service = service(repository, clock, presence(ALPHA_ID), directory(ALPHA_ID));

        CitizenRecord first = service.ensureCitizen(ALPHA_ID);
        require(first.status() == CitizenStatus.CITIZEN, "default status is CITIZEN");
        require(first.rank() == CitizenRank.CITIZEN, "default rank is CITIZEN");
        require(first.recordRevision() == 1, "new record starts at revision 1");
        require(first.firstCitizenAt() == 1_000L, "firstCitizenAt is the server clock");
        require(first.subjectId().equals(SubjectId.of(ALPHA_SUBJECT)),
                "subject is bound from the subject registry");
        require(repository.snapshot().storeRevision() == 1L,
                "provisioning commits exactly one store revision");

        CitizenRecord second = service.ensureCitizen(ALPHA_ID);
        require(second.equals(first), "ensure is idempotent");
        require(store.commitCount() == 1, "idempotent ensure commits nothing");
        require(repository.snapshot().storeRevision() == 1L,
                "idempotent ensure does not advance the store revision");
    }

    private static void testEnsurePreconditionFailures() {
        // player-data unavailable
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        CitizenRepository repository = repository(store);
        FakePlayerPresence unavailable = new FakePlayerPresence();
        unavailable.setAvailable(false);
        CitizenService service = service(
                repository, clock, unavailable, directory(ALPHA_ID)
        );
        CitizenUnavailableException failure = expectThrows(
                CitizenUnavailableException.class,
                () -> service.ensureCitizen(ALPHA_ID),
                "ensure fails closed when player-data is unavailable"
        );
        require(failure.failureCode().equals(
                        CitizenUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE),
                "player-data unavailability carries the stable code");

        // player not provisioned
        CitizenService noRecordService = service(
                repository, clock, presence(), directory(ALPHA_ID)
        );
        CitizenUnavailableException notProvisioned = expectThrows(
                CitizenUnavailableException.class,
                () -> noRecordService.ensureCitizen(ALPHA_ID),
                "ensure fails closed without a PlayerData record"
        );
        require(notProvisioned.failureCode().equals(
                        CitizenUnavailableException.CODE_PLAYER_NOT_PROVISIONED),
                "missing player record carries the stable code");

        // subject registry unavailable
        FakeSubjectDirectory offlineDirectory = directory(ALPHA_ID);
        offlineDirectory.setAvailable(false);
        CitizenService offlineSubjectService = service(
                repository, clock, presence(ALPHA_ID), offlineDirectory
        );
        CitizenUnavailableException subjectFailure = expectThrows(
                CitizenUnavailableException.class,
                () -> offlineSubjectService.ensureCitizen(ALPHA_ID),
                "ensure fails closed when the subject registry is unavailable"
        );
        require(subjectFailure.failureCode().equals(
                        CitizenUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE),
                "subject registry unavailability carries the stable code");

        require(repository.size() == 0, "failed preconditions publish no citizen");
        require(repository.snapshot().storeRevision() == 0L,
                "failed preconditions advance no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: citizen without subject rejected (§7)
    // ------------------------------------------------------------------

    private static void testCitizenWithoutSubjectRejected() {
        // Record missing the SubjectId field entirely.
        SavedDataBackedTestStore missingSubject = new SavedDataBackedTestStore();
        missingSubject.putRaw(encodedStoreWithRecord(record(
                false, // includeSubjectId = false
                false // includeUnknownField = false
        )));
        expectThrows(
                CitizenNbtException.class,
                () -> repository(missingSubject),
                "a citizen record without a subject is rejected at load"
        );

        // Record whose SubjectId has the wrong NBT type (not a UUID array).
        SavedDataBackedTestStore wrongType = new SavedDataBackedTestStore();
        CompoundTag wrongRecord = new CompoundTag();
        wrongRecord.putInt("RecordVersion", 1);
        wrongRecord.putUUID("PlayerId", ALPHA_ID);
        wrongRecord.putString("SubjectId", "not-a-uuid");
        wrongRecord.putString("Status", "CITIZEN");
        wrongRecord.putString("Rank", "CITIZEN");
        wrongRecord.putLong("FirstCitizenAt", 1_000L);
        wrongRecord.putLong("RecordRevision", 1L);
        wrongType.putRaw(storeWith("Citizens", wrongRecord));
        expectThrows(
                CitizenNbtException.class,
                () -> repository(wrongType),
                "a citizen record with an invalid subject id is rejected at load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: rank/status mutation — one snapshot, one revision, gated
    // ------------------------------------------------------------------

    private static void testRankStatusMutationSingleSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        CitizenRepository repository = repository(store);
        CitizenService service = service(repository, clock, presence(ALPHA_ID), directory(ALPHA_ID));
        service.ensureCitizen(ALPHA_ID);

        int commitsBeforeRank = store.commitCount();
        CitizenReceipt rankReceipt = service.setRank(ALPHA_ID, CitizenRank.GOD);
        require(rankReceipt.applied(), "rank change is applied");
        require(rankReceipt.kind() == CitizenChangeKind.RANK, "receipt kind is RANK");
        require(rankReceipt.record().rank() == CitizenRank.GOD, "rank was replaced");
        require(rankReceipt.record().status() == CitizenStatus.CITIZEN,
                "rank change leaves status untouched");
        require(rankReceipt.record().recordRevision() == 2, "record revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 2L, "store revision +1 exactly once");
        require(store.commitCount() == commitsBeforeRank + 1,
                "rank change commits exactly one snapshot");
        require(rankReceipt.atMillis() == 3_000L, "receipt carries the server clock");

        int commitsBeforeStatus = store.commitCount();
        CitizenReceipt statusReceipt = service.setStatus(ALPHA_ID, CitizenStatus.SUSPENDED);
        require(statusReceipt.applied(), "status change is applied");
        require(statusReceipt.kind() == CitizenChangeKind.STATUS, "receipt kind is STATUS");
        require(statusReceipt.record().status() == CitizenStatus.SUSPENDED, "status was replaced");
        require(statusReceipt.record().rank() == CitizenRank.GOD,
                "status change leaves rank untouched");
        require(statusReceipt.record().recordRevision() == 3, "record revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 3L, "store revision +1 exactly once");
        require(store.commitCount() == commitsBeforeStatus + 1,
                "status change commits exactly one snapshot");
    }

    private static void testIdempotentNoOp() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        CitizenRepository repository = repository(store);
        CitizenService service = service(repository, clock, presence(ALPHA_ID), directory(ALPHA_ID));
        service.ensureCitizen(ALPHA_ID);
        service.setRank(ALPHA_ID, CitizenRank.GOD);

        int commitsBefore = store.commitCount();
        CitizenReceipt again = service.setRank(ALPHA_ID, CitizenRank.GOD);
        require(!again.applied(), "same-rank request is an idempotent no-op");
        require(again.record().recordRevision() == 2, "no-op does not advance the record revision");
        require(store.commitCount() == commitsBefore, "no-op commits nothing");
        require(repository.snapshot().storeRevision() == 2L,
                "no-op does not advance the store revision");

        CitizenReceipt sameStatus = service.setStatus(ALPHA_ID, CitizenStatus.CITIZEN);
        require(!sameStatus.applied(), "same-status request is an idempotent no-op");
        require(sameStatus.record().recordRevision() == 2,
                "status no-op does not advance the record revision");
    }

    // ------------------------------------------------------------------
    // acceptance: rank never grants technical permission (§7)
    // ------------------------------------------------------------------

    private static void testRankNoTechnicalEffect() throws Exception {
        // The rank enum is exactly the approved political classification.
        require(
                Set.of(CitizenRank.values())
                        .equals(Set.of(CitizenRank.GOD, CitizenRank.COUNCIL, CitizenRank.CITIZEN)),
                "CitizenRank exposes exactly GOD / COUNCIL / CITIZEN"
        );
        require(CitizenRank.GOD != CitizenRank.CITIZEN, "GOD is a distinct political rank");

        // No method or field in the citizen module is permission-related.
        for (Class<?> type : citizenTypes()) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                require(!name.contains("op") && !name.contains("permission")
                                && !name.contains("bypass") && !name.contains("sudo"),
                        "no permission-related method in " + type.getSimpleName() + ": "
                                + method.getName());
            }
        }

        // Source-level check: no rank-to-OP/permission mapping exists in the
        // citizen module production code (comments excluded).
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path citizenDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/citizen"
        );
        require(Files.isDirectory(citizenDirectory),
                "Production citizen source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(citizenDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "isOp", "opLevel", "permissionLevel", "getPermission",
                "bypass", "canBypass", "sudo", "setOp", "Commands.OP"
        )) {
            require(
                    !codeOnly.contains(forbidden),
                    "citizen production code must not contain rank-to-permission mapping: "
                            + forbidden
            );
        }
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§7)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() {
        CitizenNbtCodec codec = new CitizenNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        CitizenRepository repository = repository(store);
        CitizenService service = service(repository, clock, presence(ALPHA_ID), directory(ALPHA_ID));
        service.ensureCitizen(ALPHA_ID);
        service.setRank(ALPHA_ID, CitizenRank.COUNCIL);

        CitizenStoreSnapshot snapshot = repository.snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(java.util.Arrays.equals(nbtBytes(first), nbtBytes(second)),
                "same snapshot encodes to identical ordered bytes");
        require(codec.encodedSize(first) == codec.encodedSize(second),
                "same snapshot encodes to the same serialized size");
        require(codec.decode(second).equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot");
        require(codec.decode(codec.encode(snapshot))
                        .equals(codec.decode(store.load())),
                "persisted form decodes equivalently to the in-memory snapshot");
        require(codec.encodedSize(first) > 0, "encoded snapshot carries real payload bytes");
    }

    private static void testCorruptSnapshotFailClosed() {
        // Unknown store field.
        SavedDataBackedTestStore unknownStoreField = new SavedDataBackedTestStore();
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        root.put("Citizens", new CompoundTag());
        root.putString("Surprise", "x");
        unknownStoreField.putRaw(root);
        expectThrows(
                CitizenNbtException.class,
                () -> repository(unknownStoreField),
                "unknown store field rejects the load"
        );

        // Unknown record field.
        SavedDataBackedTestStore unknownRecordField = new SavedDataBackedTestStore();
        unknownRecordField.putRaw(encodedStoreWithRecord(record(
                true, true // includeSubjectId, includeUnknownField
        )));
        expectThrows(
                CitizenNbtException.class,
                () -> repository(unknownRecordField),
                "unknown record field rejects the load"
        );

        // Newer store version.
        SavedDataBackedTestStore newerStore = new SavedDataBackedTestStore();
        CompoundTag newerRoot = new CompoundTag();
        newerRoot.putInt("StoreVersion", 99);
        newerRoot.putLong("StoreRevision", 0L);
        newerRoot.put("Citizens", new CompoundTag());
        newerStore.putRaw(newerRoot);
        expectThrows(
                CitizenNbtException.class,
                () -> repository(newerStore),
                "a newer store version is rejected fail-closed"
        );

        // Newer record version.
        SavedDataBackedTestStore newerRecord = new SavedDataBackedTestStore();
        CompoundTag newerRecordTag = new CompoundTag();
        newerRecordTag.putInt("RecordVersion", 99);
        newerRecordTag.putUUID("PlayerId", ALPHA_ID);
        newerRecordTag.putUUID("SubjectId", ALPHA_SUBJECT);
        newerRecordTag.putString("Status", "CITIZEN");
        newerRecordTag.putString("Rank", "CITIZEN");
        newerRecordTag.putLong("FirstCitizenAt", 1_000L);
        newerRecordTag.putLong("RecordRevision", 1L);
        newerRecord.putRaw(storeWith("Citizens", newerRecordTag));
        expectThrows(
                CitizenNbtException.class,
                () -> repository(newerRecord),
                "a newer record version is rejected fail-closed"
        );

        // Key does not match the record playerId.
        SavedDataBackedTestStore mismatch = new SavedDataBackedTestStore();
        CompoundTag mismatchRecord = new CompoundTag();
        mismatchRecord.putInt("RecordVersion", 1);
        mismatchRecord.putUUID("PlayerId", BRAVO_ID);
        mismatchRecord.putUUID("SubjectId", ALPHA_SUBJECT);
        mismatchRecord.putString("Status", "CITIZEN");
        mismatchRecord.putString("Rank", "CITIZEN");
        mismatchRecord.putLong("FirstCitizenAt", 1_000L);
        mismatchRecord.putLong("RecordRevision", 1L);
        mismatch.putRaw(storeWith("Citizens", mismatchRecord)); // key is ALPHA_ID
        expectThrows(
                CitizenNbtException.class,
                () -> repository(mismatch),
                "a citizens key not matching the record playerId rejects the load"
        );

        // Non-positive revision.
        SavedDataBackedTestStore zeroRevision = new SavedDataBackedTestStore();
        CompoundTag zeroRevisionTag = new CompoundTag();
        zeroRevisionTag.putInt("RecordVersion", 1);
        zeroRevisionTag.putUUID("PlayerId", ALPHA_ID);
        zeroRevisionTag.putUUID("SubjectId", ALPHA_SUBJECT);
        zeroRevisionTag.putString("Status", "CITIZEN");
        zeroRevisionTag.putString("Rank", "CITIZEN");
        zeroRevisionTag.putLong("FirstCitizenAt", 1_000L);
        zeroRevisionTag.putLong("RecordRevision", 0L);
        zeroRevision.putRaw(storeWith("Citizens", zeroRevisionTag));
        expectThrows(
                CitizenNbtException.class,
                () -> repository(zeroRevision),
                "a non-positive record revision rejects the load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery (§7)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        CitizenRepository firstRepository = repository(store);
        CitizenService firstService = service(
                firstRepository, clock, presence(ALPHA_ID), directory(ALPHA_ID)
        );
        firstService.ensureCitizen(ALPHA_ID);
        firstService.setRank(ALPHA_ID, CitizenRank.GOD);
        firstService.setStatus(ALPHA_ID, CitizenStatus.SUSPENDED);

        CitizenRepository restarted = restartRepository(store);
        CitizenRecord reloaded = restarted.findByPlayer(ALPHA_ID).orElseThrow();
        require(reloaded.equals(firstRepository.requireCitizen(ALPHA_ID)),
                "the same citizen record is recovered after restart");
        require(reloaded.rank() == CitizenRank.GOD, "rank survives restart");
        require(reloaded.status() == CitizenStatus.SUSPENDED, "status survives restart");
        require(reloaded.recordRevision() == 3, "record revision survives restart");
        require(restarted.snapshot().storeRevision() == firstRepository.snapshot().storeRevision(),
                "store revision survives restart");
        require(restarted.size() == 1, "the same citizens are recovered");
    }

    // ------------------------------------------------------------------
    // acceptance: no bulk citizen list API (§7)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        for (Class<?> type : List.of(CitizenService.class, CitizenRepository.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                // FR-MAIL-001-A §6.6: {@code activeCitizens} is the single
                // approved bounded, immutable, purpose-scoped projection of
                // the active-citizen range (for the broadcast recipient
                // range). It returns a defensive immutable List, never a live
                // Map/Stream/array, so the no-enumeration boundary is
                // preserved for every other member. The citizen module still
                // exposes no mutable store, no unbounded enumeration, and no
                // internal collection.
                if (method.getName().equals("activeCitizens")) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                require(!Collection.class.isAssignableFrom(returnType)
                                && !Map.class.isAssignableFrom(returnType)
                                && !returnType.isArray()
                                && !Stream.class.isAssignableFrom(returnType),
                        "no bulk enumeration method in " + type.getSimpleName() + ": "
                                + method.getName());
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                require(!name.contains("findall") && !name.contains("list")
                                && !name.contains("values") && !name.contains("all"),
                        "no bulk enumeration method name in " + type.getSimpleName() + ": "
                                + method.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // acceptance: capacity and store failure fail closed (§7)
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        CitizenLimits tight = new CitizenLimits(1, 8 * 1024 * 1024);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(7_000);
        CitizenRepository repository = repository(store, tight);
        CitizenService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        service.ensureCitizen(ALPHA_ID);

        CitizenUnavailableException failure = expectThrows(
                CitizenUnavailableException.class,
                () -> service.ensureCitizen(BRAVO_ID),
                "citizen capacity is enforced fail-closed"
        );
        require(failure.failureCode().equals(
                        CitizenUnavailableException.CODE_CAPACITY_EXCEEDED),
                "capacity failure carries the stable code");
        require(repository.size() == 1, "failed provisioning publishes nothing");
        require(repository.snapshot().storeRevision() == 1L,
                "failed provisioning advances no revision");
    }

    private static void testStoreFailureAtomicity() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        CitizenRepository repository = repository(store);
        CitizenService service = service(repository, clock, presence(ALPHA_ID), directory(ALPHA_ID));
        store.setCommitFailureCode("INJECTED_FAILURE");

        CitizenUnavailableException failure = expectThrows(
                CitizenUnavailableException.class,
                () -> service.ensureCitizen(ALPHA_ID),
                "a durable gate rejection fails closed"
        );
        require(failure.failureCode().equals(
                        CitizenUnavailableException.CODE_STORE_FAILURE),
                "store failure carries the stable code");
        require(repository.size() == 0, "a failed commit publishes no citizen");
        require(repository.snapshot().storeRevision() == 0L,
                "a failed commit advances no revision");
    }

    private static void testSubjectMissingOnMutation() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        CitizenRepository repository = repository(store);
        FakePlayerPresence presence = presence(ALPHA_ID);
        FakeSubjectDirectory directory = directory(ALPHA_ID);
        CitizenService service = service(repository, clock, presence, directory);
        service.ensureCitizen(ALPHA_ID);

        // The subject registry becomes unavailable / the bound subject is gone:
        // the mutation is rejected fail-closed.
        directory.setAvailable(false);
        CitizenUnavailableException rankFailure = expectThrows(
                CitizenUnavailableException.class,
                () -> service.setRank(ALPHA_ID, CitizenRank.GOD),
                "a rank change on a citizen whose subject is missing is rejected"
        );
        require(rankFailure.failureCode().equals(
                        CitizenUnavailableException.CODE_SUBJECT_MISSING),
                "missing subject carries the stable code");
        CitizenUnavailableException statusFailure = expectThrows(
                CitizenUnavailableException.class,
                () -> service.setStatus(ALPHA_ID, CitizenStatus.REVOKED),
                "a status change on a citizen whose subject is missing is rejected"
        );
        require(statusFailure.failureCode().equals(
                        CitizenUnavailableException.CODE_SUBJECT_MISSING),
                "missing subject carries the stable code");
        require(repository.requireCitizen(ALPHA_ID).rank() == CitizenRank.CITIZEN,
                "rejected mutation leaves the record unchanged");
        require(repository.snapshot().storeRevision() == 1L,
                "rejected mutation advances no revision");

        // Mutating a player with no citizen record is rejected explicitly.
        CitizenUnavailableException noRecord = expectThrows(
                CitizenUnavailableException.class,
                () -> service.setRank(BRAVO_ID, CitizenRank.GOD),
                "rank change without a citizen record is rejected"
        );
        require(noRecord.failureCode().equals(
                        CitizenUnavailableException.CODE_NO_CITIZEN_RECORD),
                "no-record failure carries the stable code");
    }

    // ------------------------------------------------------------------
    // module contract checks
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(CitizenRepository.MODULE_DATA_KEY.equals("citizen"),
                "citizen owns exactly the approved namespace");
        require(CitizenModule.MODULE_ID.value().equals("citizen"),
                "citizen module id is 'citizen'");
        ModuleDefinition definition = new ModuleDefinition(
                CitizenModule.MODULE_ID,
                new ModuleMetadata("Citizen", "1.0.0", Optional.empty(), Optional.empty()),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        NetworkRuntimeModule.MODULE_ID
                ),
                Set.of(),
                50,
                CitizenModule::new
        );
        require(definition.requiredDependencies().equals(
                        Set.of(
                                PlayerDataModule.MODULE_ID,
                                SubjectRegistryModule.MODULE_ID,
                                NetworkRuntimeModule.MODULE_ID
                        )),
                "citizen depends only on player-data, subject-registry, and network");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("audit") && !value.contains("economy")
                            && !value.contains("land"),
                    "citizen never depends on later-phase namespaces");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static CitizenRepository repository(CitizenStore store) {
        return new CitizenRepository(store, new CitizenNbtCodec(), CitizenLimits.DEFAULT);
    }

    private static CitizenRepository repository(CitizenStore store, CitizenLimits limits) {
        return new CitizenRepository(store, new CitizenNbtCodec(), limits);
    }

    private static CitizenRepository restartRepository(SavedDataBackedTestStore store) {
        return repository(store.restart());
    }

    private static CitizenService service(
            CitizenRepository repository,
            LongSupplier clock,
            PlayerPresence presence,
            SubjectDirectory directory
    ) {
        return new DefaultCitizenService(repository, clock, presence, directory);
    }

    private static FakePlayerPresence presence(UUID... players) {
        FakePlayerPresence presence = new FakePlayerPresence();
        for (UUID player : players) {
            presence.add(player);
        }
        return presence;
    }

    private static FakeSubjectDirectory directory(UUID... players) {
        FakeSubjectDirectory directory = new FakeSubjectDirectory();
        for (UUID player : players) {
            directory.add(player);
        }
        return directory;
    }

    private static SubjectRecord subject(UUID playerId) {
        UUID subjectUuid = playerId.equals(ALPHA_ID) ? ALPHA_SUBJECT : BRAVO_SUBJECT;
        return new SubjectRecord(
                SubjectRecord.CURRENT_SCHEMA_VERSION,
                SubjectId.of(subjectUuid),
                RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 42),
                SubjectType.NATURAL_PERSON,
                OwnerReference.forPlayer(playerId),
                SubjectStatus.ACTIVE,
                1L,
                1_000L,
                1_000L
        );
    }

    private static List<Class<?>> citizenTypes() {
        return List.of(
                CitizenModule.class,
                CitizenService.class,
                CitizenReceipt.class,
                CitizenChangeKind.class,
                SubjectDirectory.class,
                CitizenRank.class,
                CitizenStatus.class,
                CitizenRecord.class,
                CitizenNbtCodec.class,
                CitizenNbtException.class,
                CitizenUnavailableException.class,
                CitizenLimits.class,
                CitizenStore.class,
                CitizenStoreSnapshot.class,
                CitizenRepository.class,
                DefaultCitizenService.class
        );
    }

    /** Builds a citizens compound containing exactly one record under the ALPHA key. */
    private static CompoundTag storeWith(String citizensKey, CompoundTag record) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        CompoundTag citizens = new CompoundTag();
        citizens.put(ALPHA_ID.toString(), record);
        root.put(citizensKey, citizens);
        return root;
    }

    /** Builds a record for the ALPHA player, optionally omitting the subject or adding an unknown field. */
    private static CompoundTag record(boolean includeSubjectId, boolean includeUnknownField) {
        CompoundTag record = new CompoundTag();
        record.putInt("RecordVersion", 1);
        record.putUUID("PlayerId", ALPHA_ID);
        if (includeSubjectId) {
            record.putUUID("SubjectId", ALPHA_SUBJECT);
        }
        record.putString("Status", "CITIZEN");
        record.putString("Rank", "CITIZEN");
        record.putLong("FirstCitizenAt", 1_000L);
        record.putLong("RecordRevision", 1L);
        if (includeUnknownField) {
            record.putString("Surprise", "x");
        }
        return record;
    }

    private static CompoundTag encodedStoreWithRecord(CompoundTag record) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        CompoundTag citizens = new CompoundTag();
        citizens.put(ALPHA_ID.toString(), record);
        root.put("Citizens", citizens);
        return root;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read " + path, failure);
        }
    }

    private static byte[] nbtBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(tag, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to serialize NBT", failure);
        }
    }

    /** Removes line and block comments while preserving string literals. */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        boolean inString = false;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (inString) {
                out.append(current);
                if (current == '\\' && index + 1 < source.length()) {
                    out.append(source.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                out.append(current);
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
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

    private static final class SavedDataBackedTestStore implements CitizenStore {
        private final ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;
        private int commitCount;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(CitizenRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            commitCount++;
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        CitizenRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    CitizenRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    CitizenRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(CitizenRepository.MODULE_DATA_KEY, raw);
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private int commitCount() {
            return commitCount;
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

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static final class FakePlayerPresence implements PlayerPresence {
        private final Set<UUID> players = new java.util.HashSet<>();
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

        private void setAvailable(boolean available) {
            this.available = available;
        }
    }

    private static final class FakeSubjectDirectory implements SubjectDirectory {
        private final Map<UUID, SubjectRecord> subjects = new LinkedHashMap<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public SubjectRecord ensureSubject(UUID playerId) {
            if (!available) {
                throw new IllegalStateException("subject registry is not available");
            }
            SubjectRecord record = subjects.get(playerId);
            if (record == null) {
                throw new IllegalStateException("no subject provisioned for " + playerId);
            }
            return record;
        }

        @Override
        public boolean hasSubject(SubjectId subjectId) {
            if (!available) {
                return false;
            }
            for (SubjectRecord record : subjects.values()) {
                if (record.subjectId().equals(subjectId)) {
                    return true;
                }
            }
            return false;
        }

        private void add(UUID playerId) {
            subjects.put(playerId, subject(playerId));
        }

        private void setAvailable(boolean available) {
            this.available = available;
        }
    }
}
