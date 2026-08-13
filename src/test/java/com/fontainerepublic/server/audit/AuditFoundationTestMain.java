package com.fontainerepublic.server.audit;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditPage;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
import com.fontainerepublic.server.audit.model.AuditEntry;
import com.fontainerepublic.server.audit.model.AuditSegment;
import com.fontainerepublic.server.audit.persistence.AuditLimits;
import com.fontainerepublic.server.audit.persistence.AuditNbtCodec;
import com.fontainerepublic.server.audit.persistence.AuditNbtException;
import com.fontainerepublic.server.audit.persistence.AuditRepository;
import com.fontainerepublic.server.audit.persistence.AuditStore;
import com.fontainerepublic.server.audit.persistence.AuditStoreSnapshot;
import com.fontainerepublic.server.audit.persistence.AuditUnavailableException;
import com.fontainerepublic.server.audit.service.DefaultAuditService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-AUD-001 (append-only audit
 * module). Exercises the FR-AUD-001-A §8 acceptance matrix with an injectable
 * store and SavedData-backed restart simulation.
 */
public final class AuditFoundationTestMain {
    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private AuditFoundationTestMain() {
    }

    public static void main(String[] args) {
        testAppendOnlySurface();
        testEmptyStoreBasics();
        testRecordNormalPath();
        testSegmentRolloverAndDigestChain();
        testTamperDetection();
        testAuthoritativeSuccessAndFailureInjection();
        testClassificationEnforcement();
        testBoundedPaging();
        testRestartPersistence();
        testDeterministicCodec();
        testCapacityFailClosed();
        testDraftBoundsValidation();
        testSaveFailureAtomicity();
        testFremgIsolation();
        System.out.println("[FR-AUD-001] Audit foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: append-only invariant
    // ------------------------------------------------------------------

    private static void testAppendOnlySurface() {
        assertNoMutationMethod(AuditService.class, "AuditService");
        assertNoMutationMethod(AuditRepository.class, "AuditRepository");
        assertNoMutationMethod(AuditNbtCodec.class, "AuditNbtCodec");
        assertNoMutationMethod(AuditStore.class, "AuditStore");
    }

    private static void assertNoMutationMethod(Class<?> type, String typeName) {
        for (Method method : type.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (name.contains("update") || name.contains("delete") || name.contains("remove")
                    || name.contains("clear") || name.contains("modify")
                    || name.contains("export")) {
                throw new AssertionError(
                        typeName + " exposes a forbidden mutation method " + method.getName()
                );
            }
        }
    }

    // ------------------------------------------------------------------
    // acceptance: basic writes and reads
    // ------------------------------------------------------------------

    private static void testEmptyStoreBasics() {
        AuditRepository repository = repository(new SavedDataBackedTestStore(), AuditLimits.DEFAULT);
        require(repository.find(1L).isEmpty(), "empty store has no entries");
        require(repository.page(0L, 10).entries().isEmpty(), "empty store pages empty");
        require(repository.snapshot().storeRevision() == 0L, "empty store revision is 0");
        require(repository.snapshot().nextEntryId() == 1L, "first entry id is 1");
    }

    private static void testRecordNormalPath() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        AuditService service = new DefaultAuditService(repository, clock);

        AuditEntry first = service.record(draft("normal-action"));
        require(first.entryId() == 1L, "first record gets entry id 1");
        require(first.revision() == 1L, "first record has revision 1");
        require(repository.size() == 1, "record is visible");
        require(repository.find(1L).orElseThrow().equals(first), "find returns the committed entry");

        clock.advance(100);
        AuditEntry second = service.record(draft("another-action"));
        require(second.entryId() == 2L, "entry ids are monotonic");
        require(second.revision() == 2L, "revision increments once per commit");
        require(repository.snapshot().storeRevision() == 2L, "store revision advances");
        require(repository.page(0L, 10).entries().size() == 2, "both entries are paged");
    }

    // ------------------------------------------------------------------
    // acceptance: segment rollover + digest chain
    // ------------------------------------------------------------------

    private static void testSegmentRolloverAndDigestChain() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        AuditRepository repository = repository(store, new AuditLimits(3, 8 * 1024 * 1024));
        AuditService service = new DefaultAuditService(repository, clock);

        for (int index = 1; index <= 7; index++) {
            clock.advance(50);
            service.record(draft("rollover-" + index));
        }

        AuditStoreSnapshot snapshot = repository.snapshot();
        require(snapshot.segments().size() == 3, "7 entries with 3-per-segment roll into 3 segments");
        AuditSegment first = snapshot.segments().get(0);
        AuditSegment second = snapshot.segments().get(1);
        AuditSegment third = snapshot.segments().get(2);
        require(first.startEntryId() == 1L && first.endEntryId() == 3L, "segment 1 covers entries 1-3");
        require(second.startEntryId() == 4L && second.endEntryId() == 6L, "segment 2 covers entries 4-6");
        require(third.startEntryId() == 7L && third.endEntryId() == 7L, "segment 3 covers entry 7");
        require(first.prevDigestHex().isEmpty(), "genesis segment has empty prev digest");
        require(!second.prevDigestHex().isEmpty(), "rollover segment chains the previous digest");
        require(second.prevDigestHex().equals(new AuditNbtCodec().segmentDigestHex(first)),
                "segment 2 prev digest equals segment 1 digest");

        // Chain must validate on reload (restart).
        AuditRepository reloaded = repository(store.restart(), new AuditLimits(3, 8 * 1024 * 1024));
        require(reloaded.size() == 7, "reload restores all committed entries");
        require(reloaded.snapshot().storeRevision() == 7L, "reload restores the store revision");
    }

    // ------------------------------------------------------------------
    // acceptance: tamper evidence
    // ------------------------------------------------------------------

    private static void testTamperDetection() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        AuditRepository repository = repository(store, new AuditLimits(3, 8 * 1024 * 1024));
        AuditService service = new DefaultAuditService(repository, clock);
        for (int index = 1; index <= 7; index++) {
            clock.advance(50);
            service.record(draft("tamper-" + index));
        }
        AuditNbtCodec codec = new AuditNbtCodec();
        CompoundTag intact = store.load();

        // Alter an entry inside the first segment: chain breaks.
        CompoundTag deletedMiddle = deepCopy(intact);
        ListTag segments = deletedMiddle.getList("Segments", Tag.TAG_COMPOUND);
        segments.remove(1);
        expectThrows(
                AuditNbtException.class,
                () -> codec.decode(deletedMiddle),
                "removing a middle segment breaks the chain"
        );

        // Reorder two segments: chain/order breaks.
        CompoundTag reordered = deepCopy(intact);
        ListTag reorderedSegments = reordered.getList("Segments", Tag.TAG_COMPOUND);
        CompoundTag firstTag = (CompoundTag) reorderedSegments.get(0).copy();
        CompoundTag secondTag = (CompoundTag) reorderedSegments.get(1).copy();
        reorderedSegments.set(0, secondTag);
        reorderedSegments.set(1, firstTag);
        expectThrows(
                AuditNbtException.class,
                () -> codec.decode(reordered),
                "swapping segments breaks the digest chain"
        );

        // Tamper with the tail segment: TailDigest anchor detects it.
        CompoundTag tailTampered = deepCopy(intact);
        CompoundTag tail = (CompoundTag) tailTampered.getList("Segments", Tag.TAG_COMPOUND)
                .get(tailTampered.getList("Segments", Tag.TAG_COMPOUND).size() - 1);
        ((CompoundTag) tail.getList("Entries", Tag.TAG_COMPOUND).get(0)).putString("Summary", "forged");
        expectThrows(
                AuditNbtException.class,
                () -> codec.decode(tailTampered),
                "modifying the tail segment is detected by the tail digest"
        );

        // Tamper with an entry payload: payload digest mismatch.
        CompoundTag payloadTampered = deepCopy(intact);
        CompoundTag firstSegment = (CompoundTag) payloadTampered.getList("Segments", Tag.TAG_COMPOUND)
                .get(0);
        CompoundTag firstEntry = (CompoundTag) firstSegment.getList("Entries", Tag.TAG_COMPOUND)
                .get(0);
        firstEntry.putString("ActorId", "forged-actor");
        expectThrows(
                AuditNbtException.class,
                () -> codec.decode(payloadTampered),
                "modifying a committed entry is rejected"
        );

        // The intact store still decodes fine.
        require(codec.decode(intact).segments().size() == 3, "intact store remains valid");
    }

    // ------------------------------------------------------------------
    // acceptance: authoritative record + injected failure
    // ------------------------------------------------------------------

    private static void testAuthoritativeSuccessAndFailureInjection() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        AuditService service = new DefaultAuditService(repository, clock);

        AuditReceipt success = service.recordAuthoritative(draft("authoritative"));
        require(success.committed(), "authoritative record commits");
        require(success.entryId() == 1L, "authoritative receipt carries the entry id");
        require(success.storeRevision() == 1L, "authoritative receipt carries the new revision");
        require(repository.size() == 1, "authoritative entry is visible after COMMITTED");
        require(service.getEntry(1L).isPresent(), "authoritative entry is queryable");

        // Injected durable-gate failure: no receipt, no revision change.
        store.setCommitFailureCode("INJECTED_FAILURE");
        clock.advance(100);
        AuditReceipt failed = service.recordAuthoritative(draft("rejected"));
        require(!failed.committed(), "injected gate failure is not committed");
        require(failed.entryId() == 0L, "failed authoritative write assigns no entry id");
        require(failed.failureCode().equals("INJECTED_FAILURE"), "failure code is surfaced");
        require(failed.storeRevision() == repository.snapshot().storeRevision(),
                "failed authoritative write leaves the store revision unchanged");
        require(repository.size() == 1, "failed authoritative write leaves no visible entry");
        require(service.getEntry(2L).isEmpty(), "rejected entry is not visible");

        // Injected store exception: same no-side-effect contract.
        store.setCommitException(new RuntimeException("injected store exception"));
        AuditReceipt exceptionFailure = service.recordAuthoritative(draft("exploded"));
        require(!exceptionFailure.committed(), "store exception is not committed");
        require(exceptionFailure.entryId() == 0L, "store exception assigns no entry id");
        require(repository.size() == 1, "store exception leaves no visible entry");

        // Recovery: the same next entry id is reused after failure.
        store.setCommitFailureCode(null);
        store.setCommitException(null);
        clock.advance(100);
        AuditReceipt recovered = service.recordAuthoritative(draft("recovered"));
        require(recovered.committed(), "authoritative write recovers after failure is lifted");
        require(recovered.entryId() == 2L, "failed attempts do not consume entry ids");
        require(repository.size() == 2, "recovered entry is visible");
        require(repository.snapshot().storeRevision() == 2L, "revision advances only on success");
    }

    // ------------------------------------------------------------------
    // acceptance: classification enforcement
    // ------------------------------------------------------------------

    private static void testClassificationEnforcement() {
        // Unclassified drafts are rejected.
        expectThrows(
                NullPointerException.class,
                () -> new AuditDraft(
                        AuditActorType.SYSTEM,
                        "system",
                        AuditCategory.ADMINISTRATION,
                        "core",
                        "unclassified",
                        Optional.empty(),
                        Optional.empty(),
                        null,
                        "no classification",
                        Optional.empty()
                ),
                "unclassified draft is rejected"
        );

        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        AuditService service = new DefaultAuditService(repository, clock);

        CompoundTag secretPayload = new CompoundTag();
        secretPayload.putString("secret", "classified-clearance");
        AuditDraft secretDraft = new AuditDraft(
                AuditActorType.SYSTEM,
                "system",
                AuditCategory.GOVERNANCE,
                "core",
                "secret-action",
                Optional.empty(),
                Optional.empty(),
                AuditClassification.SECRET_DIGEST_ONLY,
                "safe projection",
                Optional.of(secretPayload)
        );
        service.record(secretDraft);

        CompoundTag publicPayload = new CompoundTag();
        publicPayload.putString("detail", "ordinary detail");
        AuditDraft publicDraft = new AuditDraft(
                AuditActorType.SYSTEM,
                "system",
                AuditCategory.ADMINISTRATION,
                "core",
                "public-action",
                Optional.empty(),
                Optional.empty(),
                AuditClassification.PUBLIC,
                "public projection",
                Optional.of(publicPayload)
        );
        service.record(publicDraft);

        AuditNbtCodec codec = new AuditNbtCodec();
        CompoundTag persisted = store.load();
        ListTag persistedSegments = persisted.getList("Segments", Tag.TAG_COMPOUND);
        CompoundTag secretTag = entryTag(persistedSegments, 1L);
        CompoundTag publicTag = entryTag(persistedSegments, 2L);

        require(!secretTag.contains("Payload"), "secret plaintext is never persisted");
        require(secretTag.contains("PayloadDigest"), "secret payload digest is persisted");
        require(publicTag.contains("Payload"), "public payload is persisted");
        require(publicTag.contains("PayloadDigest"), "public payload digest is persisted");

        AuditEntry secretProjection = service.getEntry(1L).orElseThrow();
        AuditEntry publicProjection = service.getEntry(2L).orElseThrow();
        require(secretProjection.payloadDigest().isPresent(),
                "secret projection carries only the digest");
        require(publicProjection.payloadDigest().isPresent(),
                "public projection carries only the digest");
        require(!containsPayloadPlaintextField(AuditEntry.class),
                "AuditEntry projection exposes no payload plaintext field");
        require(secretProjection.summary().equals("safe projection"),
                "secret projection keeps the safe summary");
    }

    private static CompoundTag entryTag(ListTag segments, long entryId) {
        for (Tag segmentTag : segments) {
            ListTag entries = ((CompoundTag) segmentTag).getList("Entries", Tag.TAG_COMPOUND);
            for (Tag entryTag : entries) {
                CompoundTag entry = (CompoundTag) entryTag;
                if (entry.getLong("EntryId") == entryId) {
                    return entry;
                }
            }
        }
        throw new AssertionError("entry " + entryId + " not found in persisted segments");
    }

    private static boolean containsPayloadPlaintextField(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .anyMatch(component -> component.getName().equals("payload"));
    }

    // ------------------------------------------------------------------
    // acceptance: bounded paging
    // ------------------------------------------------------------------

    private static void testBoundedPaging() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        AuditService service = new DefaultAuditService(repository, clock);
        for (int index = 1; index <= 5; index++) {
            clock.advance(50);
            service.record(draft("page-" + index));
        }

        AuditPage firstPage = service.page(0L, 2);
        require(firstPage.entries().size() == 2, "page respects the limit");
        require(firstPage.entries().get(0).entryId() == 1L, "page starts after the cursor");
        require(firstPage.entries().get(1).entryId() == 2L, "page is ascending");
        require(firstPage.hasMore(), "page reports more when entries remain");

        AuditPage secondPage = service.page(2L, 2);
        require(secondPage.entries().get(0).entryId() == 3L, "cursor resumes at the next entry");
        require(secondPage.entries().get(1).entryId() == 4L, "second page continues ascending");

        AuditPage lastPage = service.page(4L, 2);
        require(lastPage.entries().size() == 1, "final page may be short");
        require(lastPage.entries().get(0).entryId() == 5L, "final page contains the tail");
        require(!lastPage.hasMore(), "final page reports no more");

        require(service.page(5L, 10).entries().isEmpty(), "page past the tail is empty");

        expectThrows(
                IllegalArgumentException.class,
                () -> service.page(0L, 0),
                "zero page limit is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> service.page(0L, -1),
                "negative page limit is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> service.page(0L, AuditRepository.MAX_PAGE_SIZE + 1),
                "page limit above the hard bound is rejected"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock firstClock = new MutableClock(7_000);
        AuditRepository firstRepository = repository(store, AuditLimits.DEFAULT);
        AuditService firstService = new DefaultAuditService(firstRepository, firstClock);
        firstService.recordAuthoritative(draft("persistent-1"));
        firstClock.advance(50);
        firstService.record(draft("persistent-2"));

        AuditRepository restarted = repository(store.restart(), AuditLimits.DEFAULT);
        AuditService restartedService = new DefaultAuditService(
                restarted,
                new MutableClock(8_000)
        );
        require(restarted.size() == 2, "restart restores all committed entries");
        require(restarted.snapshot().storeRevision() == 2L, "restart restores the revision");
        require(restartedService.getEntry(1L).isPresent(), "restart restores authoritative entry");
        require(restartedService.getEntry(2L).isPresent(), "restart restores normal entry");

        AuditEntry continued = restartedService.record(draft("after-restart"));
        require(continued.entryId() == 3L, "restart continues the entry id sequence");
    }

    // ------------------------------------------------------------------
    // acceptance: deterministic codec
    // ------------------------------------------------------------------

    private static void testDeterministicCodec() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        AuditRepository repository = repository(store, new AuditLimits(3, 8 * 1024 * 1024));
        AuditService service = new DefaultAuditService(repository, clock);
        for (int index = 1; index <= 4; index++) {
            clock.advance(50);
            service.record(draft("deterministic-" + index));
        }

        AuditNbtCodec codec = new AuditNbtCodec();
        AuditStoreSnapshot snapshot = repository.snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(
                codec.encodedSize(first) == codec.encodedSize(second),
                "same snapshot encodes to the same serialized size"
        );
        require(
                codec.decode(second).equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot"
        );
        require(
                codec.decode(codec.encode(snapshot)).equals(codec.decode(store.load())),
                "persisted form decodes equivalently to the in-memory snapshot"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: capacity fail closed
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(10_000);
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        AuditService service = new DefaultAuditService(repository, clock);
        for (int index = 1; index <= 5; index++) {
            clock.advance(50);
            service.record(draft("capacity-" + index));
        }
        require(repository.size() == 5, "baseline entries recorded");

        // A tiny budget store reloads existing state but rejects any new write.
        AuditRepository tight = repository(store.restart(), new AuditLimits(512, 256));
        AuditService tightService = new DefaultAuditService(tight, new MutableClock(11_000));
        require(tight.size() == 5, "tight-budget store reloads the committed state");

        AuditUnavailableException failure = expectThrows(
                AuditUnavailableException.class,
                () -> tightService.record(draft("overflow")),
                "write beyond the byte budget fails closed"
        );
        require(
                failure.failureCode().equals(AuditUnavailableException.CODE_CAPACITY_EXCEEDED),
                "capacity failure carries the stable code"
        );
        require(tight.size() == 5, "failed capacity write leaves the store unchanged");
        require(tight.snapshot().storeRevision() == 5L, "failed capacity write leaves revision unchanged");
    }

    // ------------------------------------------------------------------
    // draft validation
    // ------------------------------------------------------------------

    private static void testDraftBoundsValidation() {
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.SYSTEM, "", AuditCategory.SYSTEM, "core", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC, "s", Optional.empty()
                ),
                "blank actor id is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.SYSTEM, "x".repeat(65), AuditCategory.SYSTEM, "core", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC, "s", Optional.empty()
                ),
                "over-long actor id is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.PLAYER, "not-a-uuid", AuditCategory.IDENTITY, "player-data", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC, "s", Optional.empty()
                ),
                "player actor id must be a UUID"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.PLAYER,
                        "12345678-90ab-cdef-1234-567890abcdef".toUpperCase(),
                        AuditCategory.IDENTITY,
                        "player-data", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC, "s", Optional.empty()
                ),
                "player actor id must be canonical"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.SYSTEM, "system", AuditCategory.SYSTEM, "BadModule", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC, "s", Optional.empty()
                ),
                "module id format is enforced"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.SYSTEM, "system", AuditCategory.SYSTEM, "core", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC,
                        "s".repeat(257), Optional.empty()
                ),
                "over-long summary is rejected"
        );

        CompoundTag oversizedPayload = new CompoundTag();
        oversizedPayload.putString("blob", "x".repeat(8_000));
        expectThrows(
                IllegalArgumentException.class,
                () -> new AuditDraft(
                        AuditActorType.SYSTEM, "system", AuditCategory.SYSTEM, "core", "a",
                        Optional.empty(), Optional.empty(), AuditClassification.PUBLIC,
                        "s", Optional.of(oversizedPayload)
                ),
                "over-budget payload is rejected"
        );
    }

    // ------------------------------------------------------------------
    // normal-write atomicity
    // ------------------------------------------------------------------

    private static void testSaveFailureAtomicity() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(12_000);
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        AuditService service = new DefaultAuditService(repository, clock);
        service.record(draft("before-failure"));

        store.setSaveFailureEnabled(true);
        AuditUnavailableException failure = expectThrows(
                AuditUnavailableException.class,
                () -> service.record(draft("uncommitted")),
                "normal write failure is surfaced"
        );
        require(
                failure.failureCode().equals(AuditUnavailableException.CODE_STORE_FAILURE),
                "normal write failure carries the stable code"
        );
        require(repository.size() == 1, "failed normal write leaves the store unchanged");
        require(repository.snapshot().storeRevision() == 1L, "failed normal write leaves revision unchanged");

        store.setSaveFailureEnabled(false);
        service.record(draft("after-recovery"));
        require(repository.size() == 2, "normal write recovers after the failure is lifted");
    }

    // ------------------------------------------------------------------
    // FR-EMG isolation
    // ------------------------------------------------------------------

    private static void testFremgIsolation() {
        require(AuditRepository.MODULE_DATA_KEY.equals("audit"),
                "audit owns exactly the approved 'audit' namespace");
        require(AuditModule.MODULE_ID.value().equals("audit"), "audit module id is 'audit'");

        AuditCategory reference = Enum.valueOf(AuditCategory.class, "EMERGENCY_REFERENCE");
        require(reference == AuditCategory.EMERGENCY_REFERENCE,
                "EMERGENCY_REFERENCE exists for safe cross-references only");

        ModuleDefinition definition = new ModuleDefinition(
                AuditModule.MODULE_ID,
                new ModuleMetadata("Audit", "1.0.0", Optional.empty(), Optional.empty()),
                Set.of(PlayerDataModule.MODULE_ID),
                Set.of(),
                40,
                AuditModule::new
        );
        require(
                definition.requiredDependencies().equals(Set.of(PlayerDataModule.MODULE_ID)),
                "audit depends only on player-data"
        );
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency"),
                    "audit never depends on the FR-EMG namespace");
        }

        // The audit store writes exactly one namespace key and nothing else.
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        AuditRepository repository = repository(store, AuditLimits.DEFAULT);
        new DefaultAuditService(repository, new MutableClock(13_000)).record(draft("isolation"));
        CompoundTag root = store.savedData().save(new CompoundTag());
        require(
                root.getCompound("modules").getAllKeys().equals(Set.of("audit")),
                "audit writes only its own namespace; no FR-EMG data is touched"
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static AuditDraft draft(String actionId) {
        return new AuditDraft(
                AuditActorType.SYSTEM,
                "system",
                AuditCategory.ADMINISTRATION,
                "core",
                actionId,
                Optional.empty(),
                Optional.empty(),
                AuditClassification.PUBLIC,
                "test action " + actionId,
                Optional.empty()
        );
    }

    private static AuditRepository repository(AuditStore store, AuditLimits limits) {
        return new AuditRepository(store, new AuditNbtCodec(), limits);
    }

    private static CompoundTag deepCopy(CompoundTag source) {
        return source.copy();
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

    private static final class SavedDataBackedTestStore implements AuditStore {
        private ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;
        private boolean saveFailureEnabled;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        private ModSavedData savedData() {
            return savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(AuditRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            if (saveFailureEnabled) {
                throw new RuntimeException("injected normal-write failure");
            }
            savedData.putModuleData(AuditRepository.MODULE_DATA_KEY, snapshot.copy());
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        AuditRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(AuditRepository.MODULE_DATA_KEY, snapshot.copy());
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    AuditRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private void setCommitException(RuntimeException exception) {
            this.commitException = exception;
        }

        private void setSaveFailureEnabled(boolean enabled) {
            this.saveFailureEnabled = enabled;
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
