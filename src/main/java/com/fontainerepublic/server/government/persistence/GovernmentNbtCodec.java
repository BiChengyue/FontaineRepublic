package com.fontainerepublic.server.government.persistence;

import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.Ministry;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.MinistryState;
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.PositionState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "government"}
 * namespace (FR-GOV-001-A §3.4).
 *
 * <p>Encoding writes each index in deterministic lexical key order so the
 * same immutable snapshot always produces an equivalent ordered NBT.
 * Decoding accepts only declared fields with exact NBT types, canonical
 * UUIDs, valid bounded strings, valid state enums, positive timestamps and
 * revisions, and constructs the validating {@link GovernmentStoreSnapshot}
 * which enforces id-identity, ministry referential integrity, holder/state
 * and office/position consistency. Unknown newer versions are rejected;
 * nothing is ever auto-repaired.</p>
 */
public final class GovernmentNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String MINISTRIES = "Ministries";
    private static final String POSITIONS = "Positions";
    private static final String OFFICES = "Offices";

    private static final String MINISTRY_VERSION = "MinistryVersion";
    private static final String MINISTRY_ID = "MinistryId";
    private static final String NAME = "Name";
    private static final String MINISTRY_STATE = "State";
    private static final String MINISTRY_REVISION = "MinistryRevision";

    private static final String POSITION_VERSION = "PositionVersion";
    private static final String POSITION_ID = "PositionId";
    private static final String TITLE = "Title";
    private static final String POSITION_STATE = "State";
    private static final String HOLDER_REF = "HolderRef";
    private static final String POSITION_REVISION = "PositionRevision";

    private static final String OFFICE_VERSION = "OfficeVersion";
    private static final String OFFICE_ID = "OfficeId";
    private static final String ASSIGNED_AT = "AssignedAt";
    private static final String REVOKED_AT = "RevokedAt";
    private static final String OFFICE_REVISION = "OfficeRevision";

    private static final String HOLDER_KIND = "HolderKind";
    private static final String HOLDER_OWNER_ID = "HolderOwnerId";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_MINISTRIES = 100_000;
    private static final int HARD_MAX_POSITIONS = 100_000;
    private static final int HARD_MAX_OFFICES = 100_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION, STORE_REVISION, MINISTRIES, POSITIONS, OFFICES
    );
    private static final Set<String> MINISTRY_KEYS = Set.of(
            MINISTRY_VERSION, MINISTRY_ID, NAME, MINISTRY_STATE, MINISTRY_REVISION
    );
    private static final Set<String> POSITION_KEYS = Set.of(
            POSITION_VERSION, POSITION_ID, MINISTRY_ID, TITLE, POSITION_STATE,
            HOLDER_REF, POSITION_REVISION
    );
    private static final Set<String> OFFICE_KEYS = Set.of(
            OFFICE_VERSION, OFFICE_ID, POSITION_ID, HOLDER_REF, ASSIGNED_AT,
            REVOKED_AT, OFFICE_REVISION
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present government namespace must be a valid, initialized
     * store.
     */
    public GovernmentStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "government namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "government");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "government");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "government");
        requireType(root, MINISTRIES, Tag.TAG_COMPOUND, "government");
        requireType(root, POSITIONS, Tag.TAG_COMPOUND, "government");
        requireType(root, OFFICES, Tag.TAG_COMPOUND, "government");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != GovernmentStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported government store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<MinistryId, Ministry> ministries = decodeMinistries(
                root.getCompound(MINISTRIES)
        );
        Map<PositionId, GovernmentPosition> positions = decodePositions(
                root.getCompound(POSITIONS)
        );
        Map<PositionId, Office> offices = decodeOffices(
                root.getCompound(OFFICES)
        );
        return new GovernmentStoreSnapshot(
                storeVersion, storeRevision, ministries, positions, offices
        );
    }

    private Map<MinistryId, Ministry> decodeMinistries(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_MINISTRIES) {
            throw invalid("Ministry count exceeds " + HARD_MAX_MINISTRIES);
        }
        Map<MinistryId, Ministry> ministries = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            MinistryId ministryId = parseMinistryId(key, "Ministries");
            requireType(tag, key, Tag.TAG_COMPOUND, "Ministries");
            Ministry ministry = decodeMinistry(tag.getCompound(key), ministryId);
            if (ministries.put(ministryId, ministry) != null) {
                throw invalid("Duplicate ministry id: " + ministryId);
            }
        }
        return ministries;
    }

    private Ministry decodeMinistry(CompoundTag tag, MinistryId expectedId) {
        requireOnlyKeys(tag, MINISTRY_KEYS, "ministry " + expectedId);
        requireType(tag, MINISTRY_VERSION, Tag.TAG_INT, "ministry " + expectedId);
        requireType(tag, MINISTRY_ID, Tag.TAG_INT_ARRAY, "ministry " + expectedId);
        requireType(tag, NAME, Tag.TAG_STRING, "ministry " + expectedId);
        requireType(tag, MINISTRY_STATE, Tag.TAG_STRING, "ministry " + expectedId);
        requireType(tag, MINISTRY_REVISION, Tag.TAG_LONG, "ministry " + expectedId);

        int schemaVersion = tag.getInt(MINISTRY_VERSION);
        if (schemaVersion != Ministry.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported ministry version for " + expectedId + ": " + schemaVersion
            );
        }
        UUID storedId = tag.getUUID(MINISTRY_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Ministries key " + expectedId
                            + " does not match record ministryId " + storedId
            );
        }
        MinistryState state = enumValue(
                MinistryState.class, tag.getString(MINISTRY_STATE),
                "Ministry state for " + expectedId
        );
        try {
            return new Ministry(
                    schemaVersion,
                    expectedId,
                    tag.getString(NAME),
                    state,
                    tag.getLong(MINISTRY_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid ministry " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<PositionId, GovernmentPosition> decodePositions(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_POSITIONS) {
            throw invalid("Position count exceeds " + HARD_MAX_POSITIONS);
        }
        Map<PositionId, GovernmentPosition> positions = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            PositionId positionId = parsePositionId(key, "Positions");
            requireType(tag, key, Tag.TAG_COMPOUND, "Positions");
            GovernmentPosition position = decodePosition(
                    tag.getCompound(key), positionId
            );
            if (positions.put(positionId, position) != null) {
                throw invalid("Duplicate position id: " + positionId);
            }
        }
        return positions;
    }

    private GovernmentPosition decodePosition(CompoundTag tag, PositionId expectedId) {
        requireOnlyKeys(tag, POSITION_KEYS, "position " + expectedId);
        requireType(tag, POSITION_VERSION, Tag.TAG_INT, "position " + expectedId);
        requireType(tag, POSITION_ID, Tag.TAG_INT_ARRAY, "position " + expectedId);
        requireType(tag, TITLE, Tag.TAG_STRING, "position " + expectedId);
        requireType(tag, POSITION_STATE, Tag.TAG_STRING, "position " + expectedId);
        requireType(tag, POSITION_REVISION, Tag.TAG_LONG, "position " + expectedId);
        requireType(tag, HOLDER_REF, Tag.TAG_COMPOUND, "position " + expectedId);

        int schemaVersion = tag.getInt(POSITION_VERSION);
        if (schemaVersion != GovernmentPosition.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported position version for " + expectedId + ": " + schemaVersion
            );
        }
        UUID storedId = tag.getUUID(POSITION_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Positions key " + expectedId
                            + " does not match record positionId " + storedId
            );
        }
        UUID storedMinistry = tag.getUUID(MINISTRY_ID);
        MinistryId ministryId;
        try {
            ministryId = MinistryId.of(storedMinistry);
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid ministry reference for " + expectedId + ": "
                            + failure.getMessage(),
                    failure
            );
        }
        PositionState state = enumValue(
                PositionState.class, tag.getString(POSITION_STATE),
                "Position state for " + expectedId
        );
        Optional<OwnerReference> holderRef = decodeOptionalHolder(
                tag.getCompound(HOLDER_REF), "position " + expectedId
        );
        try {
            return new GovernmentPosition(
                    schemaVersion,
                    expectedId,
                    ministryId,
                    tag.getString(TITLE),
                    state,
                    holderRef,
                    tag.getLong(POSITION_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid position " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<PositionId, Office> decodeOffices(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_OFFICES) {
            throw invalid("Office count exceeds " + HARD_MAX_OFFICES);
        }
        Map<PositionId, Office> offices = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            PositionId positionId = parsePositionId(key, "Offices");
            requireType(tag, key, Tag.TAG_COMPOUND, "Offices");
            Office office = decodeOffice(tag.getCompound(key), positionId);
            if (offices.put(positionId, office) != null) {
                throw invalid("Duplicate office position key: " + positionId);
            }
        }
        return offices;
    }

    private Office decodeOffice(CompoundTag tag, PositionId expectedPositionId) {
        requireOnlyKeys(tag, OFFICE_KEYS, "office on " + expectedPositionId);
        requireType(tag, OFFICE_VERSION, Tag.TAG_INT, "office on " + expectedPositionId);
        requireType(tag, OFFICE_ID, Tag.TAG_INT_ARRAY, "office on " + expectedPositionId);
        requireType(tag, POSITION_ID, Tag.TAG_INT_ARRAY, "office on " + expectedPositionId);
        requireType(tag, HOLDER_REF, Tag.TAG_COMPOUND, "office on " + expectedPositionId);
        requireType(tag, ASSIGNED_AT, Tag.TAG_LONG, "office on " + expectedPositionId);
        requireType(tag, OFFICE_REVISION, Tag.TAG_LONG, "office on " + expectedPositionId);

        int schemaVersion = tag.getInt(OFFICE_VERSION);
        if (schemaVersion != Office.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported office version on " + expectedPositionId
                            + ": " + schemaVersion
            );
        }
        UUID storedPositionId = tag.getUUID(POSITION_ID);
        if (!expectedPositionId.value().equals(storedPositionId)) {
            throw invalid(
                    "Offices key " + expectedPositionId
                            + " does not match record positionId " + storedPositionId
            );
        }
        UUID officeId = tag.getUUID(OFFICE_ID);
        if (!officeId.toString().equals(officeId.toString().toLowerCase(Locale.ROOT))) {
            throw invalid("OfficeId is not canonical: " + officeId);
        }
        OwnerReference holderRef = decodeRequiredHolder(
                tag.getCompound(HOLDER_REF), "office on " + expectedPositionId
        );
        Optional<Long> revokedAt = tag.contains(REVOKED_AT)
                ? Optional.of(tag.getLong(REVOKED_AT))
                : Optional.empty();
        try {
            return new Office(
                    schemaVersion,
                    officeId,
                    expectedPositionId,
                    holderRef,
                    tag.getLong(ASSIGNED_AT),
                    revokedAt,
                    tag.getLong(OFFICE_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid office on " + expectedPositionId + ": "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    // ------------------------------------------------------------------
    // holder references
    // ------------------------------------------------------------------

    private Optional<OwnerReference> decodeOptionalHolder(
            CompoundTag tag,
            String path
    ) {
        if (tag == null || tag.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(decodeRequiredHolder(tag, path));
    }

    private OwnerReference decodeRequiredHolder(CompoundTag tag, String path) {
        requireOnlyKeys(tag, Set.of(HOLDER_KIND, HOLDER_OWNER_ID), path);
        requireType(tag, HOLDER_KIND, Tag.TAG_STRING, path);
        requireType(tag, HOLDER_OWNER_ID, Tag.TAG_STRING, path);
        try {
            return new OwnerReference(
                    OwnerReferenceKind.valueOf(tag.getString(HOLDER_KIND)),
                    tag.getString(HOLDER_OWNER_ID)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid owner reference at " + path + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: every index is written
     * in sorted lexical key order.
     */
    public CompoundTag encode(GovernmentStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag ministriesTag = new CompoundTag();
        TreeMap<MinistryId, Ministry> orderedMinistries = new TreeMap<>(
                Comparator.comparing(MinistryId::canonicalKey)
        );
        orderedMinistries.putAll(snapshot.ministries());
        orderedMinistries.forEach(
                (id, ministry) -> ministriesTag.put(
                        id.canonicalKey(), encodeMinistry(ministry)
                )
        );
        root.put(MINISTRIES, ministriesTag);

        CompoundTag positionsTag = new CompoundTag();
        TreeMap<PositionId, GovernmentPosition> orderedPositions = new TreeMap<>(
                Comparator.comparing(PositionId::canonicalKey)
        );
        orderedPositions.putAll(snapshot.positions());
        orderedPositions.forEach(
                (id, position) -> positionsTag.put(
                        id.canonicalKey(), encodePosition(position)
                )
        );
        root.put(POSITIONS, positionsTag);

        CompoundTag officesTag = new CompoundTag();
        TreeMap<PositionId, Office> orderedOffices = new TreeMap<>(
                Comparator.comparing(PositionId::canonicalKey)
        );
        orderedOffices.putAll(snapshot.offices());
        orderedOffices.forEach(
                (id, office) -> officesTag.put(
                        id.canonicalKey(), encodeOffice(office)
                )
        );
        root.put(OFFICES, officesTag);
        return root;
    }

    private CompoundTag encodeMinistry(Ministry ministry) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(MINISTRY_VERSION, ministry.schemaVersion());
        tag.putUUID(MINISTRY_ID, ministry.ministryId().value());
        tag.putString(NAME, ministry.name());
        tag.putString(MINISTRY_STATE, ministry.state().name());
        tag.putLong(MINISTRY_REVISION, ministry.ministryRevision());
        return tag;
    }

    private CompoundTag encodePosition(GovernmentPosition position) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(POSITION_VERSION, position.schemaVersion());
        tag.putUUID(POSITION_ID, position.positionId().value());
        tag.putUUID(MINISTRY_ID, position.ministryId().value());
        tag.putString(TITLE, position.title());
        tag.putString(POSITION_STATE, position.state().name());
        tag.put(HOLDER_REF, encodeHolder(
                position.holderRef().orElse(null)
        ));
        tag.putLong(POSITION_REVISION, position.positionRevision());
        return tag;
    }

    private CompoundTag encodeOffice(Office office) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(OFFICE_VERSION, office.schemaVersion());
        tag.putUUID(OFFICE_ID, office.officeId());
        tag.putUUID(POSITION_ID, office.positionId().value());
        tag.put(HOLDER_REF, encodeHolder(office.holderRef()));
        tag.putLong(ASSIGNED_AT, office.assignedAt());
        office.revokedAt().ifPresent(revoked -> tag.putLong(REVOKED_AT, revoked));
        tag.putLong(OFFICE_REVISION, office.officeRevision());
        return tag;
    }

    private CompoundTag encodeHolder(OwnerReference holder) {
        CompoundTag tag = new CompoundTag();
        if (holder != null) {
            tag.putString(HOLDER_KIND, holder.kind().name());
            tag.putString(HOLDER_OWNER_ID, holder.ownerId());
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

    private MinistryId parseMinistryId(String value, String index) {
        UUID parsed = parseCanonicalUuid(value, index);
        try {
            return MinistryId.of(parsed);
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid " + index + " key: " + value, failure);
        }
    }

    private PositionId parsePositionId(String value, String index) {
        UUID parsed = parseCanonicalUuid(value, index);
        try {
            return PositionId.of(parsed);
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid " + index + " key: " + value, failure);
        }
    }

    private UUID parseCanonicalUuid(String value, String index) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid(index + " key is not canonical: " + value);
            }
            return parsed;
        } catch (IllegalArgumentException failure) {
            if (failure instanceof GovernmentNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid " + index + " key: " + value, failure);
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

    private static GovernmentNbtException invalid(String message) {
        return new GovernmentNbtException(message);
    }

    private static GovernmentNbtException invalid(String message, Throwable cause) {
        return new GovernmentNbtException(message, cause);
    }
}
