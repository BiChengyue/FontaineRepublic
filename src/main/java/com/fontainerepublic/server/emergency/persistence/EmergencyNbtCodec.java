package com.fontainerepublic.server.emergency.persistence;

import com.fontainerepublic.server.emergency.model.EmergencyActorType;
import com.fontainerepublic.server.emergency.model.EmergencyAttemptResult;
import com.fontainerepublic.server.emergency.model.EmergencyConfigPhase;
import com.fontainerepublic.server.emergency.model.EmergencyConfigResult;
import com.fontainerepublic.server.emergency.model.EmergencyConfigState;
import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.emergency.model.EmergencyJournalKind;
import com.fontainerepublic.server.emergency.model.EmergencyJournalRecord;
import com.fontainerepublic.server.emergency.model.EmergencyReceiptWatermark;
import com.fontainerepublic.server.emergency.model.EmergencyReconciliationStatus;
import com.fontainerepublic.server.emergency.model.EmergencySourceClassification;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "emergency"}
 * namespace (FR-EMG-001-A §10 / implementation task §3.2).
 *
 * <p>Encoding is ordered (records by id, receipt watermarks by provider id,
 * compound keys sorted) so the same snapshot always encodes to an equivalent
 * ordered NBT. The tamper-evidence chain is validated on decode: every record
 * must match its derived self digest and link to the previous record; the
 * configuration journal head must match the last record. Decoding is strict
 * and fail-closed: unsupported versions, unknown fields, wrong types,
 * non-canonical values, chain breaks, or tail mismatch all raise
 * {@link EmergencyNbtException}. No auto-repair is ever attempted.</p>
 */
public final class EmergencyNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String NEXT_RECORD_ID = "NextRecordId";
    private static final String CONFIG = "Config";
    private static final String RECORDS = "Records";
    private static final String RECEIPT_INDEX = "ReceiptIndex";

    private static final String CONFIG_PHASE = "Phase";
    private static final String CONFIG_ACTIVE_UUID_DIGEST = "ActiveUuidDigest";
    private static final String CONFIG_ACTIVE_AT = "ActiveAt";
    private static final String CONFIG_ACTIVE_REVISION = "ActiveRevision";
    private static final String CONFIG_STAGED_UUID_DIGEST = "StagedUuidDigest";
    private static final String CONFIG_STAGED_AT = "StagedAt";
    private static final String CONFIG_STAGED_REVISION = "StagedRevision";
    private static final String CONFIG_REVISION = "ConfigRevision";
    private static final String CONFIG_JOURNAL_HEAD = "JournalHeadDigest";

    private static final String RECORD_ID = "RecordId";
    private static final String RECORD_AT = "At";
    private static final String RECORD_ACTOR_TYPE = "ActorType";
    private static final String RECORD_ACTOR_UUID_DIGEST = "ActorUuidDigest";
    private static final String RECORD_SOURCE = "Source";
    private static final String RECORD_KIND = "Kind";
    private static final String RECORD_ATTEMPT_ID = "AttemptId";
    private static final String RECORD_RESULT = "Result";
    private static final String RECORD_REQUEST_DIGEST = "RequestDigest";
    private static final String RECORD_CONFIG_REVISION = "ConfigRevision";
    private static final String RECORD_PREV_DIGEST = "PrevDigest";
    private static final String RECORD_SELF_DIGEST = "SelfDigest";

    private static final String WATERMARK_PROVIDER_ID = "ProviderId";
    private static final String WATERMARK_RECEIPT_SCHEMA = "ReceiptSchema";
    private static final String WATERMARK_HIGHEST_SEQUENCE = "HighestSequence";
    private static final String WATERMARK_SEGMENT_DIGEST = "SegmentDigest";
    private static final String WATERMARK_STATUS = "Status";

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION, STORE_REVISION, NEXT_RECORD_ID, CONFIG, RECORDS, RECEIPT_INDEX
    );
    private static final Set<String> CONFIG_KEYS = Set.of(
            CONFIG_PHASE, CONFIG_ACTIVE_UUID_DIGEST, CONFIG_ACTIVE_AT,
            CONFIG_ACTIVE_REVISION, CONFIG_STAGED_UUID_DIGEST, CONFIG_STAGED_AT,
            CONFIG_STAGED_REVISION, CONFIG_REVISION, CONFIG_JOURNAL_HEAD
    );
    private static final Set<String> RECORD_KEYS = Set.of(
            RECORD_ID, RECORD_AT, RECORD_ACTOR_TYPE, RECORD_ACTOR_UUID_DIGEST,
            RECORD_SOURCE, RECORD_KIND, RECORD_ATTEMPT_ID, RECORD_RESULT,
            RECORD_REQUEST_DIGEST, RECORD_CONFIG_REVISION, RECORD_PREV_DIGEST,
            RECORD_SELF_DIGEST
    );
    private static final Set<String> WATERMARK_KEYS = Set.of(
            WATERMARK_PROVIDER_ID, WATERMARK_RECEIPT_SCHEMA,
            WATERMARK_HIGHEST_SEQUENCE, WATERMARK_SEGMENT_DIGEST, WATERMARK_STATUS
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    public EmergencyStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return EmergencyStoreSnapshot.empty();
        }
        requireOnlyKeys(root, STORE_KEYS, "emergency");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "emergency");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "emergency");
        requireType(root, NEXT_RECORD_ID, Tag.TAG_LONG, "emergency");
        requireType(root, CONFIG, Tag.TAG_COMPOUND, "emergency");
        requireType(root, RECORDS, Tag.TAG_LIST, "emergency");
        requireType(root, RECEIPT_INDEX, Tag.TAG_COMPOUND, "emergency");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != EmergencyStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported emergency store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }
        long nextRecordId = root.getLong(NEXT_RECORD_ID);
        if (nextRecordId <= 0) {
            throw invalid("NextRecordId must be positive");
        }

        EmergencyConfigState config = decodeConfig(root.getCompound(CONFIG));

        ListTag recordsTag = root.getList(RECORDS, Tag.TAG_COMPOUND);
        List<EmergencyJournalRecord> records = new ArrayList<>();
        for (Tag recordTag : recordsTag) {
            records.add(decodeRecord((CompoundTag) recordTag));
        }
        validateChain(records, config);

        Map<String, EmergencyReceiptWatermark> watermarks =
                decodeWatermarks(root.getCompound(RECEIPT_INDEX));

        return new EmergencyStoreSnapshot(
                storeVersion, storeRevision, nextRecordId, config, records, watermarks
        );
    }

    private EmergencyConfigState decodeConfig(CompoundTag tag) {
        requireOnlyKeys(tag, CONFIG_KEYS, "config");
        requireType(tag, CONFIG_PHASE, Tag.TAG_STRING, "config");
        requireType(tag, CONFIG_REVISION, Tag.TAG_LONG, "config");
        requireOptionalByteArray(tag, CONFIG_ACTIVE_UUID_DIGEST, "config");
        requireOptionalType(tag, CONFIG_ACTIVE_AT, Tag.TAG_LONG, "config");
        requireOptionalType(tag, CONFIG_ACTIVE_REVISION, Tag.TAG_LONG, "config");
        requireOptionalByteArray(tag, CONFIG_STAGED_UUID_DIGEST, "config");
        requireOptionalType(tag, CONFIG_STAGED_AT, Tag.TAG_LONG, "config");
        requireOptionalType(tag, CONFIG_STAGED_REVISION, Tag.TAG_LONG, "config");
        requireOptionalByteArray(tag, CONFIG_JOURNAL_HEAD, "config");

        EmergencyConfigPhase phase = enumValue(
                EmergencyConfigPhase.class, tag.getString(CONFIG_PHASE), "Phase"
        );
        long configRevision = tag.getLong(CONFIG_REVISION);
        byte[] activeDigest = optionalByteArray(tag, CONFIG_ACTIVE_UUID_DIGEST);
        byte[] stagedDigest = optionalByteArray(tag, CONFIG_STAGED_UUID_DIGEST);
        byte[] journalHead = optionalByteArray(tag, CONFIG_JOURNAL_HEAD);
        long activeAt = optionalLong(tag, CONFIG_ACTIVE_AT);
        long activeRevision = optionalLong(tag, CONFIG_ACTIVE_REVISION);
        long stagedAt = optionalLong(tag, CONFIG_STAGED_AT);
        long stagedRevision = optionalLong(tag, CONFIG_STAGED_REVISION);
        try {
            return new EmergencyConfigState(
                    phase, activeDigest, activeAt, activeRevision,
                    stagedDigest, stagedAt, stagedRevision, configRevision, journalHead
            );
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid config state: " + failure.getMessage(), failure);
        }
    }

    private EmergencyJournalRecord decodeRecord(CompoundTag tag) {
        requireOnlyKeys(tag, RECORD_KEYS, "record");
        requireType(tag, RECORD_ID, Tag.TAG_LONG, "record");
        requireType(tag, RECORD_AT, Tag.TAG_LONG, "record");
        requireType(tag, RECORD_ACTOR_TYPE, Tag.TAG_STRING, "record");
        requireType(tag, RECORD_SOURCE, Tag.TAG_STRING, "record");
        requireType(tag, RECORD_KIND, Tag.TAG_STRING, "record");
        requireOptionalType(tag, RECORD_ATTEMPT_ID, Tag.TAG_LONG, "record");
        requireType(tag, RECORD_RESULT, Tag.TAG_STRING, "record");
        requireType(tag, RECORD_PREV_DIGEST, Tag.TAG_BYTE_ARRAY, "record");
        requireType(tag, RECORD_SELF_DIGEST, Tag.TAG_BYTE_ARRAY, "record");
        requireOptionalByteArray(tag, RECORD_ACTOR_UUID_DIGEST, "record");
        requireOptionalByteArray(tag, RECORD_REQUEST_DIGEST, "record");
        requireOptionalType(tag, RECORD_CONFIG_REVISION, Tag.TAG_LONG, "record");

        long recordId = tag.getLong(RECORD_ID);
        long at = tag.getLong(RECORD_AT);
        EmergencyActorType actorType = enumValue(
                EmergencyActorType.class, tag.getString(RECORD_ACTOR_TYPE), "ActorType"
        );
        EmergencySourceClassification source = enumValue(
                EmergencySourceClassification.class, tag.getString(RECORD_SOURCE), "Source"
        );
        EmergencyJournalKind kind = enumValue(
                EmergencyJournalKind.class, tag.getString(RECORD_KIND), "Kind"
        );
        long attemptId = optionalLong(tag, RECORD_ATTEMPT_ID);
        String resultName = tag.getString(RECORD_RESULT);
        byte[] actorUuidDigest = optionalByteArray(tag, RECORD_ACTOR_UUID_DIGEST);
        byte[] requestDigest = optionalByteArray(tag, RECORD_REQUEST_DIGEST);
        long configRevision = optionalLong(tag, RECORD_CONFIG_REVISION);
        byte[] prevDigest = tag.getByteArray(RECORD_PREV_DIGEST);
        byte[] selfDigest = tag.getByteArray(RECORD_SELF_DIGEST);

        EmergencyAttemptResult attemptResult = null;
        EmergencyConfigResult configResult = null;
        if (kind == EmergencyJournalKind.ATTEMPT) {
            attemptResult = enumValue(
                    EmergencyAttemptResult.class, resultName, "Result"
            );
        } else {
            configResult = enumValue(
                    EmergencyConfigResult.class, resultName, "Result"
            );
        }
        try {
            return new EmergencyJournalRecord(
                    recordId, at, actorType, actorUuidDigest, source, kind,
                    attemptId, attemptResult, configResult, requestDigest,
                    configRevision, prevDigest, selfDigest
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid record " + recordId + ": " + failure.getMessage(), failure
            );
        }
    }

    private void validateChain(List<EmergencyJournalRecord> records,
                               EmergencyConfigState config) {
        for (int index = 0; index < records.size(); index++) {
            EmergencyJournalRecord record = records.get(index);
            if (index == 0) {
                if (!Arrays.equals(record.prevDigest(), EmergencyDigests.ZERO_DIGEST)) {
                    throw invalid(
                            "Genesis journal record must carry the zero prev digest"
                    );
                }
            } else {
                EmergencyJournalRecord previous = records.get(index - 1);
                if (!Arrays.equals(record.prevDigest(), previous.selfDigest())) {
                    throw invalid(
                            "Record " + record.recordId()
                                    + " breaks the digest chain (prev digest mismatch)"
                    );
                }
                if (record.recordId() <= previous.recordId()) {
                    throw invalid("Record ids must be strictly increasing");
                }
            }
        }
        if (!records.isEmpty()) {
            EmergencyJournalRecord tail = records.get(records.size() - 1);
            if (config.journalHeadDigest() == null
                    || !Arrays.equals(config.journalHeadDigest(), tail.selfDigest())) {
                throw invalid(
                        "Config journal-head digest does not match the last record "
                                + "(tamper detected)"
                );
            }
        } else if (config.journalHeadDigest() != null) {
            throw invalid(
                    "Empty journal cannot carry a journal-head digest"
            );
        }
    }

    private Map<String, EmergencyReceiptWatermark> decodeWatermarks(CompoundTag index) {
        Map<String, EmergencyReceiptWatermark> result = new LinkedHashMap<>();
        for (String key : index.getAllKeys()) {
            CompoundTag entry = index.getCompound(key);
            requireOnlyKeys(entry, WATERMARK_KEYS, "watermark");
            requireType(entry, WATERMARK_PROVIDER_ID, Tag.TAG_STRING, "watermark");
            requireType(entry, WATERMARK_RECEIPT_SCHEMA, Tag.TAG_STRING, "watermark");
            requireType(entry, WATERMARK_HIGHEST_SEQUENCE, Tag.TAG_LONG, "watermark");
            requireType(entry, WATERMARK_SEGMENT_DIGEST, Tag.TAG_STRING, "watermark");
            requireType(entry, WATERMARK_STATUS, Tag.TAG_STRING, "watermark");
            String providerId = entry.getString(WATERMARK_PROVIDER_ID);
            if (!key.equals(providerId)) {
                throw invalid(
                        "Receipt index key " + key + " does not match provider id "
                                + providerId
                );
            }
            try {
                result.put(providerId, new EmergencyReceiptWatermark(
                        providerId,
                        entry.getString(WATERMARK_RECEIPT_SCHEMA),
                        entry.getLong(WATERMARK_HIGHEST_SEQUENCE),
                        entry.getString(WATERMARK_SEGMENT_DIGEST),
                        enumValue(EmergencyReconciliationStatus.class,
                                entry.getString(WATERMARK_STATUS), "Status")
                ));
            } catch (IllegalArgumentException failure) {
                throw invalid(
                        "Invalid receipt watermark for " + providerId + ": "
                                + failure.getMessage(), failure
                );
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    public CompoundTag encode(EmergencyStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());
        root.putLong(NEXT_RECORD_ID, snapshot.nextRecordId());
        root.put(CONFIG, encodeConfig(snapshot.config()));

        ListTag records = new ListTag();
        for (EmergencyJournalRecord record : snapshot.records()) {
            records.add(encodeRecord(record));
        }
        root.put(RECORDS, records);
        root.put(RECEIPT_INDEX, encodeWatermarks(snapshot.receiptWatermarks()));
        return root;
    }

    private CompoundTag encodeConfig(EmergencyConfigState config) {
        CompoundTag tag = new CompoundTag();
        tag.putString(CONFIG_PHASE, config.phase().name());
        putOptionalByteArray(tag, CONFIG_ACTIVE_UUID_DIGEST, config.activeUuidDigest());
        if (config.activeAt() > 0) {
            tag.putLong(CONFIG_ACTIVE_AT, config.activeAt());
        }
        if (config.activeRevision() > 0) {
            tag.putLong(CONFIG_ACTIVE_REVISION, config.activeRevision());
        }
        putOptionalByteArray(tag, CONFIG_STAGED_UUID_DIGEST, config.stagedUuidDigest());
        if (config.stagedAt() > 0) {
            tag.putLong(CONFIG_STAGED_AT, config.stagedAt());
        }
        if (config.stagedRevision() > 0) {
            tag.putLong(CONFIG_STAGED_REVISION, config.stagedRevision());
        }
        tag.putLong(CONFIG_REVISION, config.configRevision());
        putOptionalByteArray(tag, CONFIG_JOURNAL_HEAD, config.journalHeadDigest());
        return tag;
    }

    private CompoundTag encodeRecord(EmergencyJournalRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(RECORD_ID, record.recordId());
        tag.putLong(RECORD_AT, record.at());
        tag.putString(RECORD_ACTOR_TYPE, record.actorType().name());
        putOptionalByteArray(tag, RECORD_ACTOR_UUID_DIGEST, record.actorUuidDigest());
        tag.putString(RECORD_SOURCE, record.source().name());
        tag.putString(RECORD_KIND, record.kind().name());
        if (record.attemptId() > 0) {
            tag.putLong(RECORD_ATTEMPT_ID, record.attemptId());
        }
        tag.putString(RECORD_RESULT, EmergencyJournalRecord.resultName(
                record.attemptResult(), record.configResult()
        ));
        putOptionalByteArray(tag, RECORD_REQUEST_DIGEST, record.requestDigest());
        if (record.configRevision() > 0) {
            tag.putLong(RECORD_CONFIG_REVISION, record.configRevision());
        }
        tag.putByteArray(RECORD_PREV_DIGEST, record.prevDigest());
        tag.putByteArray(RECORD_SELF_DIGEST, record.selfDigest());
        return tag;
    }

    private CompoundTag encodeWatermarks(Map<String, EmergencyReceiptWatermark> index) {
        CompoundTag tag = new CompoundTag();
        TreeMap<String, EmergencyReceiptWatermark> ordered =
                new TreeMap<>(index);
        for (EmergencyReceiptWatermark watermark : ordered.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(WATERMARK_PROVIDER_ID, watermark.providerId());
            entry.putString(WATERMARK_RECEIPT_SCHEMA, watermark.receiptSchema());
            entry.putLong(WATERMARK_HIGHEST_SEQUENCE, watermark.highestSequence());
            entry.putString(WATERMARK_SEGMENT_DIGEST, watermark.segmentDigest());
            entry.putString(WATERMARK_STATUS, watermark.status().name());
            tag.put(watermark.providerId(), entry);
        }
        return tag;
    }

    // ------------------------------------------------------------------
    // size
    // ------------------------------------------------------------------

    /** Serialized (uncompressed) size of an encoded namespace snapshot. */
    public int encodedSize(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void putOptionalByteArray(CompoundTag tag, String key, byte[] value) {
        if (value != null) {
            tag.putByteArray(key, value);
        }
    }

    private static byte[] optionalByteArray(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getByteArray(key) : null;
    }

    private static long optionalLong(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getLong(key) : 0L;
    }

    private static void requireOptionalByteArray(CompoundTag tag, String key, String path) {
        if (tag.contains(key) && !tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw invalid(path + " field " + key + " has the wrong NBT type");
        }
    }

    private static void requireOptionalType(CompoundTag tag, String key, int type, String path) {
        if (tag.contains(key) && !tag.contains(key, type)) {
            throw invalid(path + " field " + key + " has the wrong NBT type");
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(path + " is missing required field " + key + " or has the wrong type");
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw invalid("Unsupported " + field + " value: " + value);
        }
    }

    private static EmergencyNbtException invalid(String message) {
        return new EmergencyNbtException(message);
    }

    private static EmergencyNbtException invalid(String message, Throwable cause) {
        return new EmergencyNbtException(message, cause);
    }

    private static final HexFormat HEX = HexFormat.of();
    // keep reference used for future canonical digests of watermarks
    static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    private static byte[] sha256(byte[] input) {
        Objects.requireNonNull(input, "input");
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
