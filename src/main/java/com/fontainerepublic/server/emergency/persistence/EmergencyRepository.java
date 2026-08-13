package com.fontainerepublic.server.emergency.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.emergency.model.EmergencyConfigState;
import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.emergency.model.EmergencyJournalRecord;
import com.fontainerepublic.server.emergency.model.EmergencyReceiptWatermark;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-writer repository owning the {@code "emergency"} NBT namespace
 * (FR-EMG-001-A §10 / implementation task §3.2).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "emergency"} key. All writes run on the logical server owner thread;
 * every mutation first builds a candidate snapshot and only publishes it after
 * the underlying store acknowledged durability, so a failed write has no side
 * effects (no visible record, no revision change, no authority change).</p>
 *
 * <p>The journal is append-only: records are never removed or rewritten.
 * Closed segments roll over at {@link EmergencyLimits#maxRecordsPerSegment};
 * the total serialized budget and the attempt hard cap are enforced
 * fail-closed.</p>
 */
public final class EmergencyRepository {
    /** Reserved module-data key for the emergency namespace. */
    public static final String MODULE_DATA_KEY = "emergency";

    private final EmergencyStore store;
    private final EmergencyNbtCodec codec;
    private final EmergencyLimits limits;
    private final Thread ownerThread;

    private long storeRevision;
    private long nextRecordId;
    private EmergencyConfigState config;
    private final ArrayList<EmergencyJournalRecord> records = new ArrayList<>();
    private final LinkedHashMap<String, EmergencyReceiptWatermark> watermarks =
            new LinkedHashMap<>();
    private int totalBytes;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static EmergencyRepository createProduction(EmergencyNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new EmergencyRepository(
                new DataManagerEmergencyStore(), codec, EmergencyLimits.DEFAULT
        );
    }

    public EmergencyRepository(
            EmergencyStore store,
            EmergencyNbtCodec codec,
            EmergencyLimits limits
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            EmergencyStoreSnapshot fresh = EmergencyStoreSnapshot.empty();
            storeRevision = fresh.storeRevision();
            nextRecordId = fresh.nextRecordId();
            config = fresh.config();
            totalBytes = 0;
        } else {
            EmergencyStoreSnapshot snapshot = codec.decode(loaded);
            storeRevision = snapshot.storeRevision();
            nextRecordId = snapshot.nextRecordId();
            config = snapshot.config();
            records.addAll(snapshot.records());
            watermarks.putAll(snapshot.receiptWatermarks());
            totalBytes = codec.encodedSize(loaded);
        }
    }

    // ------------------------------------------------------------------
    // read surface
    // ------------------------------------------------------------------

    public EmergencyConfigState config() {
        requireOwnerThread();
        return config;
    }

    public List<EmergencyJournalRecord> records() {
        requireOwnerThread();
        return Collections.unmodifiableList(records);
    }

    public Optional<EmergencyJournalRecord> findRecord(long recordId) {
        requireOwnerThread();
        return records.stream().filter(record -> record.recordId() == recordId)
                .findFirst();
    }

    public int recordCount() {
        requireOwnerThread();
        return records.size();
    }

    /** Next journal record id to allocate (attempt or config event). */
    public long nextRecordId() {
        requireOwnerThread();
        return nextRecordId;
    }

    public EmergencyStoreSnapshot snapshot() {
        requireOwnerThread();
        return new EmergencyStoreSnapshot(
                EmergencyStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                nextRecordId,
                config,
                records,
                Map.copyOf(watermarks)
        );
    }

    public Optional<EmergencyReceiptWatermark> watermark(String providerId) {
        requireOwnerThread();
        return Optional.ofNullable(watermarks.get(providerId));
    }

    // ------------------------------------------------------------------
    // write surface (all through the FR-CORE-002 durable gate)
    // ------------------------------------------------------------------

    /**
     * Appends one journal record and updates the config journal head in one
     * committed snapshot. Returns {@code true} only after durability.
     */
    public boolean appendRecord(EmergencyJournalRecord record) {
        requireOwnerThread();
        EmergencyStoreSnapshot candidate = append(record);
        return commit(candidate, "journal append");
    }

    /**
     * Commits a full configuration change (authority acceptance, staging, or
     * recovery) together with its journal record in one snapshot.
     */
    public boolean commitConfig(
            EmergencyConfigState nextConfig,
            EmergencyJournalRecord record
    ) {
        requireOwnerThread();
        EmergencyStoreSnapshot candidate = withConfigAndRecord(nextConfig, record);
        return commit(candidate, "config change");
    }

    /** Records a receipt watermark update (derived index; durable). */
    public boolean updateWatermark(EmergencyReceiptWatermark watermark) {
        requireOwnerThread();
        Objects.requireNonNull(watermark, "watermark");
        EmergencyStoreSnapshot candidate = snapshot();
        LinkedHashMap<String, EmergencyReceiptWatermark> next =
                new LinkedHashMap<>(candidate.receiptWatermarks());
        next.put(watermark.providerId(), watermark);
        EmergencyStoreSnapshot nextSnapshot = new EmergencyStoreSnapshot(
                candidate.storeVersion(),
                candidate.storeRevision(),
                candidate.nextRecordId(),
                candidate.config(),
                candidate.records(),
                next
        );
        return commit(nextSnapshot, "receipt watermark");
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private EmergencyStoreSnapshot append(EmergencyJournalRecord record) {
        if (records.size() >= limits.maxAttempts()) {
            throw new EmergencyUnavailableException(
                    EmergencyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Emergency journal record cap reached ("
                            + limits.maxAttempts() + "); no further records accepted"
            );
        }
        if (nextRecordId == Long.MAX_VALUE || storeRevision == Long.MAX_VALUE) {
            throw new EmergencyUnavailableException(
                    EmergencyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Emergency namespace id space exhausted"
            );
        }
        if (record.recordId() != nextRecordId) {
            throw new IllegalArgumentException(
                    "Record id must equal the next record id (expected "
                            + nextRecordId + ", got " + record.recordId() + ")"
            );
        }
        ArrayList<EmergencyJournalRecord> nextRecords = new ArrayList<>(records);
        nextRecords.add(record);
        EmergencyConfigState nextConfig = config.withJournalHead(record.selfDigest());
        return new EmergencyStoreSnapshot(
                EmergencyStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1L,
                nextRecordId + 1L,
                nextConfig,
                nextRecords,
                Map.copyOf(watermarks)
        );
    }

    private EmergencyStoreSnapshot withConfigAndRecord(
            EmergencyConfigState nextConfig,
            EmergencyJournalRecord record
    ) {
        if (nextRecordId == Long.MAX_VALUE || storeRevision == Long.MAX_VALUE) {
            throw new EmergencyUnavailableException(
                    EmergencyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Emergency namespace id space exhausted"
            );
        }
        if (record.recordId() != nextRecordId) {
            throw new IllegalArgumentException(
                    "Record id must equal the next record id (expected "
                            + nextRecordId + ", got " + record.recordId() + ")"
            );
        }
        EmergencyConfigState configWithHead =
                nextConfig.withJournalHead(record.selfDigest());
        ArrayList<EmergencyJournalRecord> nextRecords = new ArrayList<>(records);
        nextRecords.add(record);
        return new EmergencyStoreSnapshot(
                EmergencyStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1L,
                nextRecordId + 1L,
                configWithHead,
                nextRecords,
                Map.copyOf(watermarks)
        );
    }

    private boolean commit(EmergencyStoreSnapshot candidate, String purpose) {
        CompoundTag encoded = codec.encode(candidate);
        enforceTotalBytes(encoded);
        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new EmergencyUnavailableException(
                    EmergencyUnavailableException.CODE_STORE_FAILURE,
                    "Emergency store rejected a " + purpose + ": "
                            + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new EmergencyUnavailableException(
                    EmergencyUnavailableException.CODE_STORE_FAILURE,
                    "Emergency " + purpose + " was not durably committed ("
                            + result.status() + ":" + result.failureCode() + ")"
            );
        }
        publish(candidate, encoded);
        return true;
    }

    private void enforceTotalBytes(CompoundTag encoded) {
        int encodedBytes = codec.encodedSize(encoded);
        if (encodedBytes > limits.maxTotalBytes()) {
            throw new EmergencyUnavailableException(
                    EmergencyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Emergency namespace would exceed the total byte budget ("
                            + encodedBytes + " > " + limits.maxTotalBytes() + ")"
            );
        }
    }

    /** Swaps in a durably accepted candidate; only reachable on success. */
    private void publish(EmergencyStoreSnapshot candidate, CompoundTag encoded) {
        records.clear();
        records.addAll(candidate.records());
        config = candidate.config();
        watermarks.clear();
        watermarks.putAll(candidate.receiptWatermarks());
        storeRevision = candidate.storeRevision();
        nextRecordId = candidate.nextRecordId();
        totalBytes = codec.encodedSize(encoded);
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "EmergencyRepository may only be accessed from its owning "
                            + "server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerEmergencyStore implements EmergencyStore {
        @Override
        public CompoundTag load() {
            return DataManager.getModuleData(MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            return DataManager.commitModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }
    }

    // helper used by the service to build journal records with correct chain
    public byte[] tailDigest() {
        requireOwnerThread();
        if (records.isEmpty()) {
            return EmergencyDigests.ZERO_DIGEST.clone();
        }
        return records.get(records.size() - 1).selfDigest().clone();
    }
}
