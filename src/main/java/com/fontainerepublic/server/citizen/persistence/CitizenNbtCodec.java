package com.fontainerepublic.server.citizen.persistence;

import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.registry.model.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "citizen"}
 * namespace (FR-CIT-001-A §3.3).
 *
 * <p>Encoding writes the citizens index in deterministic lexical key order so
 * the same immutable snapshot always produces an equivalent ordered NBT.
 * Decoding accepts only declared fields with exact NBT types, canonical player
 * UUIDs, a mandatory valid subject id per record, valid status/rank, positive
 * timestamps and revisions, and constructs the validating
 * {@link CitizenStoreSnapshot} which enforces the key/record identity
 * invariant. Unknown newer versions are rejected; nothing is ever
 * auto-repaired.</p>
 */
public final class CitizenNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String CITIZENS = "Citizens";

    private static final String RECORD_VERSION = "RecordVersion";
    private static final String PLAYER_ID = "PlayerId";
    private static final String SUBJECT_ID = "SubjectId";
    private static final String STATUS = "Status";
    private static final String RANK = "Rank";
    private static final String FIRST_CITIZEN_AT = "FirstCitizenAt";
    private static final String RECORD_REVISION = "RecordRevision";

    /** Hard structural cap, independent of the configurable budget. */
    private static final int HARD_MAX_CITIZENS = 100_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            CITIZENS
    );
    private static final Set<String> RECORD_KEYS = Set.of(
            RECORD_VERSION,
            PLAYER_ID,
            SUBJECT_ID,
            STATUS,
            RANK,
            FIRST_CITIZEN_AT,
            RECORD_REVISION
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present citizen namespace must be a valid, initialized
     * store.
     */
    public CitizenStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "citizen namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "citizen");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "citizen");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "citizen");
        requireType(root, CITIZENS, Tag.TAG_COMPOUND, "citizen");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != CitizenStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported citizen store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<UUID, CitizenRecord> citizens = decodeCitizens(
                root.getCompound(CITIZENS)
        );
        return new CitizenStoreSnapshot(storeVersion, storeRevision, citizens);
    }

    private Map<UUID, CitizenRecord> decodeCitizens(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_CITIZENS) {
            throw invalid("Citizen count exceeds " + HARD_MAX_CITIZENS);
        }
        Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            UUID playerId = parseCanonicalPlayerId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Citizens");
            CitizenRecord record = decodeRecord(tag.getCompound(key), playerId);
            if (citizens.put(playerId, record) != null) {
                throw invalid("Duplicate citizen player id: " + playerId);
            }
        }
        return citizens;
    }

    private CitizenRecord decodeRecord(CompoundTag tag, UUID expectedPlayerId) {
        requireOnlyKeys(tag, RECORD_KEYS, "citizen " + expectedPlayerId);
        requireType(tag, RECORD_VERSION, Tag.TAG_INT, "citizen " + expectedPlayerId);
        requireType(tag, PLAYER_ID, Tag.TAG_INT_ARRAY, "citizen " + expectedPlayerId);
        requireType(tag, SUBJECT_ID, Tag.TAG_INT_ARRAY, "citizen " + expectedPlayerId);
        requireType(tag, STATUS, Tag.TAG_STRING, "citizen " + expectedPlayerId);
        requireType(tag, RANK, Tag.TAG_STRING, "citizen " + expectedPlayerId);
        requireType(tag, FIRST_CITIZEN_AT, Tag.TAG_LONG, "citizen " + expectedPlayerId);
        requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "citizen " + expectedPlayerId);

        int recordVersion = tag.getInt(RECORD_VERSION);
        if (recordVersion != CitizenRecord.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported citizen record version for " + expectedPlayerId
                            + ": " + recordVersion
            );
        }

        UUID storedPlayerId = tag.getUUID(PLAYER_ID);
        if (!expectedPlayerId.equals(storedPlayerId)) {
            throw invalid(
                    "Citizens key " + expectedPlayerId
                            + " does not match record playerId " + storedPlayerId
            );
        }

        // A citizen record must carry a valid subject (FR-CIT-001-A §4.1);
        // a missing or non-canonical subject id rejects the load.
        SubjectId subjectId;
        try {
            subjectId = SubjectId.of(tag.getUUID(SUBJECT_ID));
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid SubjectId for " + expectedPlayerId + ": "
                            + failure.getMessage(),
                    failure
            );
        }

        CitizenStatus status = enumValue(
                CitizenStatus.class,
                tag.getString(STATUS),
                "Status for " + expectedPlayerId
        );
        CitizenRank rank = enumValue(
                CitizenRank.class,
                tag.getString(RANK),
                "Rank for " + expectedPlayerId
        );

        try {
            return new CitizenRecord(
                    recordVersion,
                    expectedPlayerId,
                    subjectId,
                    status,
                    rank,
                    tag.getLong(FIRST_CITIZEN_AT),
                    tag.getLong(RECORD_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid citizen record " + expectedPlayerId + ": "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: the citizens index is
     * written in sorted lexical key order.
     */
    public CompoundTag encode(CitizenStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag citizensTag = new CompoundTag();
        TreeMap<String, CitizenRecord> ordered = new TreeMap<>();
        snapshot.citizens().forEach(
                (playerId, record) -> ordered.put(playerId.toString(), record)
        );
        ordered.forEach(
                (key, record) -> citizensTag.put(key, encodeRecord(record))
        );
        root.put(CITIZENS, citizensTag);
        return root;
    }

    private CompoundTag encodeRecord(CitizenRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(RECORD_VERSION, record.schemaVersion());
        tag.putUUID(PLAYER_ID, record.playerId());
        tag.putUUID(SUBJECT_ID, record.subjectId().value());
        tag.putString(STATUS, record.status().name());
        tag.putString(RANK, record.rank().name());
        tag.putLong(FIRST_CITIZEN_AT, record.firstCitizenAt());
        tag.putLong(RECORD_REVISION, record.recordRevision());
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

    private UUID parseCanonicalPlayerId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Citizens key is not canonical: " + value);
            }
            return parsed;
        } catch (IllegalArgumentException failure) {
            if (failure instanceof CitizenNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid citizen key: " + value, failure);
        }
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
            throw invalid(
                    path + " is missing required field " + key + " or has the wrong type"
            );
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static CitizenNbtException invalid(String message) {
        return new CitizenNbtException(message);
    }

    private static CitizenNbtException invalid(String message, Throwable cause) {
        return new CitizenNbtException(message, cause);
    }
}
