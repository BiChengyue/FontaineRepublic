package com.fontainerepublic.server.audit.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditPage;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.model.AuditClassification;
import com.fontainerepublic.server.audit.model.AuditEntry;
import com.fontainerepublic.server.audit.model.AuditSegment;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Single-writer repository owning the {@code "audit"} NBT namespace
 * (FR-AUD-001-A §3.3 / §4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#putModuleData(String, CompoundTag)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "audit"} key. All writes run on the logical server owner thread;
 * every mutation first builds a candidate snapshot and only publishes it after
 * the underlying store accepted it, so a failed write has no side effects
 * (no visible entry, no revision change).</p>
 *
 * <p>Segments roll over automatically at {@link AuditLimits#maxEntriesPerSegment};
 * the total serialized budget is enforced fail-closed. Entry ids and the store
 * revision are monotonic and are only consumed by successful commits.</p>
 */
public final class AuditRepository {
    /** Reserved module-data key for the audit namespace. */
    public static final String MODULE_DATA_KEY = "audit";

    /** Hard bound for paging; no unbounded enumeration exists. */
    public static final int MAX_PAGE_SIZE = 100;

    private final AuditStore store;
    private final AuditNbtCodec codec;
    private final AuditLimits limits;
    private final Thread ownerThread;

    private long storeRevision;
    private long nextEntryId;
    private final ArrayList<AuditSegment> segments = new ArrayList<>();
    private final NavigableMap<Long, AuditEntry> index = new TreeMap<>();
    private int totalBytes;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static AuditRepository createProduction(AuditNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new AuditRepository(new DataManagerAuditStore(), codec, AuditLimits.DEFAULT);
    }

    public AuditRepository(AuditStore store, AuditNbtCodec codec, AuditLimits limits) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            storeRevision = 0L;
            nextEntryId = 1L;
            totalBytes = 0;
        } else {
            AuditStoreSnapshot snapshot = codec.decode(loaded);
            storeRevision = snapshot.storeRevision();
            nextEntryId = snapshot.nextEntryId();
            segments.addAll(snapshot.segments());
            rebuildIndex();
            totalBytes = codec.encodedSize(loaded);
        }
    }

    // ------------------------------------------------------------------
    // read surface
    // ------------------------------------------------------------------

    public Optional<AuditEntry> find(long entryId) {
        requireOwnerThread();
        return Optional.ofNullable(index.get(entryId));
    }

    public AuditPage page(long afterEntryId, int limit) {
        requireOwnerThread();
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page limit must be within [1, " + MAX_PAGE_SIZE + "]"
            );
        }
        List<AuditEntry> result = new ArrayList<>();
        Long current = index.higherKey(afterEntryId);
        while (current != null && result.size() < limit) {
            result.add(index.get(current));
            current = index.higherKey(current);
        }
        return new AuditPage(result, current != null);
    }

    public int size() {
        requireOwnerThread();
        return index.size();
    }

    public AuditStoreSnapshot snapshot() {
        requireOwnerThread();
        return new AuditStoreSnapshot(
                AuditStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                nextEntryId,
                List.copyOf(segments)
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Normal autosave path: publishes after the store accepted the snapshot.
     */
    public AuditEntry record(AuditDraft draft, long timestamp) {
        requireOwnerThread();
        AuditEntry entry = buildEntry(draft, timestamp);
        CompoundTag payload = payloadToPersist(draft);
        AuditStoreSnapshot candidate = append(entry, payload);
        CompoundTag encoded = codec.encode(candidate);
        enforceTotalBytes(encoded);
        try {
            store.save(encoded);
        } catch (RuntimeException failure) {
            throw new AuditUnavailableException(
                    AuditUnavailableException.CODE_STORE_FAILURE,
                    "Audit store rejected a normal write: " + failure.getMessage(),
                    failure
            );
        }
        publish(candidate, encoded);
        return entry;
    }

    /**
     * Authoritative path through the FR-CORE-002 gate: only a
     * {@code COMMITTED} result publishes the entry and advances the revision.
     * Any failure returns a receipt with no entry id and no revision change.
     */
    public AuditReceipt recordAuthoritative(AuditDraft draft, long timestamp) {
        requireOwnerThread();
        AuditEntry entry = buildEntry(draft, timestamp);
        CompoundTag payload = payloadToPersist(draft);
        AuditStoreSnapshot candidate = append(entry, payload);
        CompoundTag encoded = codec.encode(candidate);
        enforceTotalBytes(encoded);

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            return new AuditReceipt(
                    DurableCommitStatus.FAILED,
                    0L,
                    storeRevision,
                    AuditUnavailableException.CODE_STORE_FAILURE
            );
        }
        if (result.status() == DurableCommitStatus.COMMITTED) {
            publish(candidate, encoded);
            return new AuditReceipt(
                    DurableCommitStatus.COMMITTED,
                    entry.entryId(),
                    storeRevision,
                    ""
            );
        }
        return new AuditReceipt(result.status(), 0L, storeRevision, result.failureCode());
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private AuditEntry buildEntry(AuditDraft draft, long timestamp) {
        if (nextEntryId == Long.MAX_VALUE) {
            throw new AuditUnavailableException(
                    AuditUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Audit entry id space exhausted"
            );
        }
        if (storeRevision == Long.MAX_VALUE) {
            throw new AuditUnavailableException(
                    AuditUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Audit store revision space exhausted"
            );
        }
        String payloadDigest = draft.payload()
                .map(codec::payloadDigestHex)
                .orElse(null);
        return new AuditEntry(
                nextEntryId,
                timestamp,
                draft.actorType(),
                draft.actorId(),
                draft.category(),
                draft.moduleId(),
                draft.actionId(),
                draft.targetType(),
                draft.targetId(),
                draft.classification(),
                draft.summary(),
                payloadDigest == null ? Optional.empty() : Optional.of(payloadDigest),
                storeRevision + 1
        );
    }

    /** Secret entries never persist payload plaintext (digest only). */
    private CompoundTag payloadToPersist(AuditDraft draft) {
        if (draft.classification() == AuditClassification.SECRET_DIGEST_ONLY) {
            return null;
        }
        return draft.payload().orElse(null);
    }

    /**
     * Builds the candidate next snapshot: extends the active tail segment, or
     * rolls over to a new chained segment. Closed segments are never touched.
     */
    private AuditStoreSnapshot append(AuditEntry entry, CompoundTag payload) {
        List<AuditSegment> nextSegments;
        if (segments.isEmpty()
                || segments.get(segments.size() - 1).entries().size()
                >= limits.maxEntriesPerSegment()) {
            AuditSegment active = segments.isEmpty() ? null : segments.get(segments.size() - 1);
            long segmentId = active == null ? 1L : active.segmentId() + 1L;
            String prevDigest = active == null ? "" : codec.segmentDigestHex(active);
            Map<Long, CompoundTag> payloads = payload == null
                    ? Map.of()
                    : Map.of(entry.entryId(), payload);
            AuditSegment fresh = new AuditSegment(
                    segmentId,
                    entry.entryId(),
                    entry.entryId(),
                    prevDigest,
                    List.of(entry),
                    payloads
            );
            nextSegments = new ArrayList<>(segments);
            nextSegments.add(fresh);
        } else {
            AuditSegment active = segments.get(segments.size() - 1);
            List<AuditEntry> entries = new ArrayList<>(active.entries());
            entries.add(entry);
            Map<Long, CompoundTag> payloads = new LinkedHashMap<>(active.payloads());
            if (payload != null) {
                payloads.put(entry.entryId(), payload);
            }
            AuditSegment extended = new AuditSegment(
                    active.segmentId(),
                    active.startEntryId(),
                    entry.entryId(),
                    active.prevDigestHex(),
                    entries,
                    payloads
            );
            nextSegments = new ArrayList<>(segments);
            nextSegments.set(nextSegments.size() - 1, extended);
        }
        return new AuditStoreSnapshot(
                AuditStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1L,
                nextEntryId + 1L,
                nextSegments
        );
    }

    private void enforceTotalBytes(CompoundTag encoded) {
        int encodedBytes = codec.encodedSize(encoded);
        if (encodedBytes > limits.maxTotalBytes()) {
            throw new AuditUnavailableException(
                    AuditUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Audit store would exceed the total byte budget ("
                            + encodedBytes + " > " + limits.maxTotalBytes() + ")"
            );
        }
    }

    /** Swaps in a store-accepted candidate; only reachable on success. */
    private void publish(AuditStoreSnapshot candidate, CompoundTag encoded) {
        segments.clear();
        segments.addAll(candidate.segments());
        storeRevision = candidate.storeRevision();
        nextEntryId = candidate.nextEntryId();
        rebuildIndex();
        totalBytes = codec.encodedSize(encoded);
    }

    private void rebuildIndex() {
        index.clear();
        for (AuditSegment segment : segments) {
            for (AuditEntry entry : segment.entries()) {
                index.put(entry.entryId(), entry);
            }
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "AuditRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerAuditStore implements AuditStore {
        @Override
        public CompoundTag load() {
            return DataManager.getModuleData(MODULE_DATA_KEY).copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            DataManager.putModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            return DataManager.commitModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }
    }
}
