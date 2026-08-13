package com.fontainerepublic.server.audit.persistence;

import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
import com.fontainerepublic.server.audit.model.AuditEntry;
import com.fontainerepublic.server.audit.model.AuditSegment;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "audit"} namespace
 * (FR-AUD-001-A §3.3 / §8).
 *
 * <p>Encoding is ordered (segments by id, entries by entry id, compound keys
 * sorted) so the same snapshot always encodes to an equivalent ordered NBT.
 * The tamper-evidence chain is computed over a hand-rolled canonical byte
 * encoding that never depends on NBT map iteration order, so digests remain
 * stable across JVM restarts even though stored compound key order may vary.</p>
 *
 * <p>Decoding is strict and fail-closed: unsupported versions, unknown fields,
 * wrong types, non-canonical ids, chain breaks, tail mismatch, secret
 * plaintext or a corrupted payload digest all raise
 * {@link AuditNbtException}. No auto-repair is ever attempted.</p>
 */
public final class AuditNbtCodec {
    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String NEXT_ENTRY_ID = "NextEntryId";
    private static final String SEGMENTS = "Segments";
    private static final String TAIL_DIGEST = "TailDigest";

    private static final String SEGMENT_ID = "SegmentId";
    private static final String START_ENTRY_ID = "StartEntryId";
    private static final String END_ENTRY_ID = "EndEntryId";
    private static final String PREV_DIGEST = "PrevDigest";
    private static final String ENTRIES = "Entries";

    private static final String ENTRY_ID = "EntryId";
    private static final String TIMESTAMP = "Timestamp";
    private static final String ACTOR_TYPE = "ActorType";
    private static final String ACTOR_ID = "ActorId";
    private static final String CATEGORY = "Category";
    private static final String MODULE_ID = "ModuleId";
    private static final String ACTION_ID = "ActionId";
    private static final String TARGET_TYPE = "TargetType";
    private static final String TARGET_ID = "TargetId";
    private static final String CLASSIFICATION = "Classification";
    private static final String SUMMARY = "Summary";
    private static final String PAYLOAD_DIGEST = "PayloadDigest";
    private static final String PAYLOAD = "Payload";
    private static final String REVISION = "Revision";

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            NEXT_ENTRY_ID,
            SEGMENTS,
            TAIL_DIGEST
    );
    private static final Set<String> SEGMENT_KEYS = Set.of(
            SEGMENT_ID,
            START_ENTRY_ID,
            END_ENTRY_ID,
            PREV_DIGEST,
            ENTRIES
    );
    private static final Set<String> ENTRY_KEYS = Set.of(
            ENTRY_ID,
            TIMESTAMP,
            ACTOR_TYPE,
            ACTOR_ID,
            CATEGORY,
            MODULE_ID,
            ACTION_ID,
            TARGET_TYPE,
            TARGET_ID,
            CLASSIFICATION,
            SUMMARY,
            PAYLOAD_DIGEST,
            PAYLOAD,
            REVISION
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    public AuditStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return AuditStoreSnapshot.empty();
        }

        requireOnlyKeys(root, STORE_KEYS, "audit");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "audit");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "audit");
        requireType(root, NEXT_ENTRY_ID, Tag.TAG_LONG, "audit");
        requireType(root, SEGMENTS, Tag.TAG_LIST, "audit");
        requireType(root, TAIL_DIGEST, Tag.TAG_BYTE_ARRAY, "audit");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != AuditStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported audit store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }
        long nextEntryId = root.getLong(NEXT_ENTRY_ID);
        if (nextEntryId <= 0) {
            throw invalid("NextEntryId must be positive");
        }

        ListTag segmentsTag = root.getList(SEGMENTS, Tag.TAG_COMPOUND);
        List<AuditSegment> segments = new ArrayList<>();
        for (Tag segmentTag : segmentsTag) {
            segments.add(decodeSegment((CompoundTag) segmentTag));
        }

        validateChain(segments, root.getByteArray(TAIL_DIGEST));
        return new AuditStoreSnapshot(storeVersion, storeRevision, nextEntryId, segments);
    }

    private AuditSegment decodeSegment(CompoundTag tag) {
        requireOnlyKeys(tag, SEGMENT_KEYS, "segment");
        requireType(tag, SEGMENT_ID, Tag.TAG_LONG, "segment");
        requireType(tag, START_ENTRY_ID, Tag.TAG_LONG, "segment");
        requireType(tag, END_ENTRY_ID, Tag.TAG_LONG, "segment");
        requireType(tag, PREV_DIGEST, Tag.TAG_BYTE_ARRAY, "segment");
        requireType(tag, ENTRIES, Tag.TAG_LIST, "segment");

        long segmentId = tag.getLong(SEGMENT_ID);
        long startEntryId = tag.getLong(START_ENTRY_ID);
        long endEntryId = tag.getLong(END_ENTRY_ID);
        String prevDigestHex = hex(tag.getByteArray(PREV_DIGEST));

        ListTag entriesTag = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        List<AuditEntry> entries = new ArrayList<>();
        Map<Long, CompoundTag> payloads = new LinkedHashMap<>();
        for (Tag entryTag : entriesTag) {
            DecodedEntry decoded = decodeEntry((CompoundTag) entryTag);
            entries.add(decoded.entry());
            if (decoded.payload() != null) {
                payloads.put(decoded.entry().entryId(), decoded.payload());
            }
        }

        return new AuditSegment(
                segmentId,
                startEntryId,
                endEntryId,
                prevDigestHex,
                entries,
                payloads
        );
    }

    private DecodedEntry decodeEntry(CompoundTag tag) {
        requireOnlyKeys(tag, ENTRY_KEYS, "entry");
        requireType(tag, ENTRY_ID, Tag.TAG_LONG, "entry");
        requireType(tag, TIMESTAMP, Tag.TAG_LONG, "entry");
        requireType(tag, ACTOR_TYPE, Tag.TAG_STRING, "entry");
        requireType(tag, ACTOR_ID, Tag.TAG_STRING, "entry");
        requireType(tag, CATEGORY, Tag.TAG_STRING, "entry");
        requireType(tag, MODULE_ID, Tag.TAG_STRING, "entry");
        requireType(tag, ACTION_ID, Tag.TAG_STRING, "entry");
        requireType(tag, CLASSIFICATION, Tag.TAG_STRING, "entry");
        requireType(tag, SUMMARY, Tag.TAG_STRING, "entry");
        requireType(tag, REVISION, Tag.TAG_LONG, "entry");
        requireOptionalType(tag, TARGET_TYPE, Tag.TAG_STRING, "entry");
        requireOptionalType(tag, TARGET_ID, Tag.TAG_STRING, "entry");
        requireOptionalType(tag, PAYLOAD_DIGEST, Tag.TAG_BYTE_ARRAY, "entry");
        requireOptionalType(tag, PAYLOAD, Tag.TAG_COMPOUND, "entry");

        long entryId = tag.getLong(ENTRY_ID);
        AuditActorType actorType = enumValue(AuditActorType.class, tag.getString(ACTOR_TYPE), "ActorType");
        AuditCategory category = enumValue(AuditCategory.class, tag.getString(CATEGORY), "Category");
        AuditClassification classification = enumValue(
                AuditClassification.class,
                tag.getString(CLASSIFICATION),
                "Classification"
        );

        CompoundTag payload = tag.contains(PAYLOAD) ? tag.getCompound(PAYLOAD) : null;
        String payloadDigestHex = tag.contains(PAYLOAD_DIGEST)
                ? hex(tag.getByteArray(PAYLOAD_DIGEST))
                : "";

        if (classification == AuditClassification.SECRET_DIGEST_ONLY && payload != null) {
            throw invalid(
                    "Secret entry " + entryId + " must not carry persisted payload plaintext"
            );
        }
        if (payload != null) {
            if (payloadDigestHex.isEmpty()) {
                throw invalid("Entry " + entryId + " carries a payload without a payload digest");
            }
            if (!payloadDigestHex.equals(payloadDigestHex(payload))) {
                throw invalid("Entry " + entryId + " payload does not match its digest");
            }
        }

        AuditEntry entry;
        try {
            entry = new AuditEntry(
                    entryId,
                    tag.getLong(TIMESTAMP),
                    actorType,
                    tag.getString(ACTOR_ID),
                    category,
                    tag.getString(MODULE_ID),
                    tag.getString(ACTION_ID),
                    optionalString(tag, TARGET_TYPE),
                    optionalString(tag, TARGET_ID),
                    classification,
                    tag.getString(SUMMARY),
                    payloadDigestHex.isEmpty()
                            ? Optional.empty()
                            : Optional.of(payloadDigestHex),
                    tag.getLong(REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid entry " + entryId + ": " + failure.getMessage(), failure);
        }
        return new DecodedEntry(entry, payload);
    }

    private void validateChain(List<AuditSegment> segments, byte[] storedTailDigest) {
        for (int index = 0; index < segments.size(); index++) {
            AuditSegment segment = segments.get(index);
            if (index == 0) {
                if (!segment.prevDigestHex().isEmpty()) {
                    throw invalid("Genesis segment must have an empty PrevDigest");
                }
            } else {
                AuditSegment previous = segments.get(index - 1);
                if (!segment.prevDigestHex().equals(segmentDigestHex(previous))) {
                    throw invalid(
                            "Segment " + segment.segmentId() + " breaks the digest chain (prev digest mismatch)"
                    );
                }
                if (segment.segmentId() <= previous.segmentId()) {
                    throw invalid("Segment ids must be strictly increasing");
                }
                if (segment.startEntryId() != previous.endEntryId() + 1) {
                    throw invalid(
                            "Segment " + segment.segmentId() + " does not continue after segment "
                                    + previous.segmentId()
                    );
                }
            }
        }

        if (segments.isEmpty()) {
            if (storedTailDigest.length != 0) {
                throw invalid("Empty store must carry an empty TailDigest");
            }
        } else {
            AuditSegment tail = segments.get(segments.size() - 1);
            if (!hex(storedTailDigest).equals(segmentDigestHex(tail))) {
                throw invalid("Tail digest does not match the last segment (tamper detected)");
            }
        }
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    public CompoundTag encode(AuditStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());
        root.putLong(NEXT_ENTRY_ID, snapshot.nextEntryId());

        ListTag segments = new ListTag();
        for (AuditSegment segment : snapshot.segments()) {
            segments.add(encodeSegment(segment));
        }
        root.put(SEGMENTS, segments);

        byte[] tailDigest = snapshot.segments().isEmpty()
                ? new byte[0]
                : segmentDigestBytes(snapshot.segments().get(snapshot.segments().size() - 1));
        root.putByteArray(TAIL_DIGEST, tailDigest);
        return root;
    }

    private CompoundTag encodeSegment(AuditSegment segment) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(SEGMENT_ID, segment.segmentId());
        tag.putLong(START_ENTRY_ID, segment.startEntryId());
        tag.putLong(END_ENTRY_ID, segment.endEntryId());
        tag.putByteArray(PREV_DIGEST, hexToBytes(segment.prevDigestHex()));

        ListTag entries = new ListTag();
        for (AuditEntry entry : segment.entries()) {
            entries.add(encodeEntry(entry, segment.payloads().get(entry.entryId())));
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    private CompoundTag encodeEntry(AuditEntry entry, CompoundTag payload) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(ENTRY_ID, entry.entryId());
        tag.putLong(TIMESTAMP, entry.timestamp());
        tag.putString(ACTOR_TYPE, entry.actorType().name());
        tag.putString(ACTOR_ID, entry.actorId());
        tag.putString(CATEGORY, entry.category().name());
        tag.putString(MODULE_ID, entry.moduleId());
        tag.putString(ACTION_ID, entry.actionId());
        entry.targetType().ifPresent(value -> tag.putString(TARGET_TYPE, value));
        entry.targetId().ifPresent(value -> tag.putString(TARGET_ID, value));
        tag.putString(CLASSIFICATION, entry.classification().name());
        tag.putString(SUMMARY, entry.summary());
        entry.payloadDigest().ifPresent(
                digest -> tag.putByteArray(PAYLOAD_DIGEST, hexToBytes(digest))
        );
        if (payload != null && entry.classification() != AuditClassification.SECRET_DIGEST_ONLY) {
            tag.put(PAYLOAD, payload);
        }
        tag.putLong(REVISION, entry.revision());
        return tag;
    }

    // ------------------------------------------------------------------
    // canonical digests
    // ------------------------------------------------------------------

    /**
     * SHA-256 digest of one segment's canonical bytes (the tamper-evidence
     * chain element). Public so the repository can chain a new segment.
     */
    public String segmentDigestHex(AuditSegment segment) {
        return hex(segmentDigestBytes(segment));
    }

    /** Canonical SHA-256 digest (lowercase hex) of a payload. */
    public String payloadDigestHex(CompoundTag payload) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            writeCanonicalCompound(out, payload);
            return hex(sha256(buffer.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException("Canonical payload encoding failed", impossible);
        }
    }

    private byte[] segmentDigestBytes(AuditSegment segment) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeLong(segment.segmentId());
            out.writeLong(segment.startEntryId());
            out.writeLong(segment.endEntryId());
            byte[] prevDigest = hexToBytes(segment.prevDigestHex());
            out.writeInt(prevDigest.length);
            out.write(prevDigest);
            out.writeInt(segment.entries().size());
            for (AuditEntry entry : segment.entries()) {
                writeCanonicalEntry(out, entry, segment.payloads().get(entry.entryId()));
            }
            return sha256(buffer.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("Canonical segment encoding failed", impossible);
        }
    }

    private void writeCanonicalEntry(
            DataOutputStream out,
            AuditEntry entry,
            CompoundTag payload
    ) throws IOException {
        out.writeLong(entry.entryId());
        out.writeLong(entry.timestamp());
        writeString(out, entry.actorType().name());
        writeString(out, entry.actorId());
        writeString(out, entry.category().name());
        writeString(out, entry.moduleId());
        writeString(out, entry.actionId());
        writeOptionalString(out, entry.targetType());
        writeOptionalString(out, entry.targetId());
        writeString(out, entry.classification().name());
        writeString(out, entry.summary());
        byte[] digest = entry.payloadDigest().map(AuditNbtCodec::hexToBytes).orElse(new byte[0]);
        out.writeInt(digest.length);
        out.write(digest);
        out.writeLong(entry.revision());
        if (payload != null && entry.classification() != AuditClassification.SECRET_DIGEST_ONLY) {
            out.writeBoolean(true);
            writeCanonicalCompound(out, payload);
        } else {
            out.writeBoolean(false);
        }
    }

    /**
     * Recursive deterministic encoding of a CompoundTag: keys sorted, values
     * encoded by NBT type with canonical scalars. Never depends on map order.
     */
    private void writeCanonicalCompound(DataOutputStream out, CompoundTag tag) throws IOException {
        List<String> keys = tag.getAllKeys().stream().sorted().toList();
        out.writeInt(keys.size());
        for (String key : keys) {
            writeString(out, key);
            writeCanonicalTag(out, tag.get(key));
        }
    }

    private void writeCanonicalTag(DataOutputStream out, Tag tag) throws IOException {
        out.writeByte(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_BYTE -> out.writeByte(((ByteTag) tag).getAsByte());
            case Tag.TAG_SHORT -> out.writeShort(((ShortTag) tag).getAsShort());
            case Tag.TAG_INT -> out.writeInt(((IntTag) tag).getAsInt());
            case Tag.TAG_LONG -> out.writeLong(((LongTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> out.writeInt(Float.floatToIntBits(((FloatTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> out.writeLong(Double.doubleToLongBits(((DoubleTag) tag).getAsDouble()));
            case Tag.TAG_STRING -> writeString(out, ((StringTag) tag).getAsString());
            case Tag.TAG_BYTE_ARRAY -> writeBytes(out, ((ByteArrayTag) tag).getAsByteArray());
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray();
                out.writeInt(values.length);
                for (int value : values) {
                    out.writeInt(value);
                }
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray();
                out.writeInt(values.length);
                for (long value : values) {
                    out.writeLong(value);
                }
            }
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                out.writeByte(list.getElementType());
                out.writeInt(list.size());
                for (int index = 0; index < list.size(); index++) {
                    writeCanonicalTag(out, list.get(index));
                }
            }
            case Tag.TAG_COMPOUND -> writeCanonicalCompound(out, (CompoundTag) tag);
            default -> throw invalid("Unsupported NBT tag type " + tag.getId() + " in audit payload");
        }
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

    private static void writeString(DataOutputStream out, String value) throws IOException {
        out.writeUTF(value);
    }

    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static void writeOptionalString(DataOutputStream out, Optional<String> value)
            throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeString(out, value.get());
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.isEmpty()) {
            return new byte[0];
        }
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid hex digest: " + hex, failure);
        }
    }

    private static Optional<String> optionalString(CompoundTag tag, String key) {
        return tag.contains(key) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw invalid("Unsupported " + field + " value: " + value);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(path + " is missing required field " + key + " or has the wrong type");
        }
    }

    private static void requireOptionalType(CompoundTag tag, String key, int type, String path) {
        if (tag.contains(key) && !tag.contains(key, type)) {
            throw invalid(path + " field " + key + " has the wrong NBT type");
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static AuditNbtException invalid(String message) {
        return new AuditNbtException(message);
    }

    private static AuditNbtException invalid(String message, Throwable cause) {
        return new AuditNbtException(message, cause);
    }

    private record DecodedEntry(AuditEntry entry, CompoundTag payload) {
    }
}
