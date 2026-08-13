package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.FacilityState;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.institutionaccess.model.ZoneState;
import com.fontainerepublic.server.land.model.ParcelId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the
 * {@code institution-access} namespace (FR-INST-002-B §2, store v2).
 *
 * <p>Encoding writes the facility and zone collections in deterministic
 * lexical key order so the same immutable snapshot always produces an
 * equivalent ordered NBT. Decoding accepts only declared fields with exact
 * NBT types, canonical ids, valid enums/dimensions/regions, a non-empty
 * bounded capability set, and consistent key/record ids. Unknown newer
 * versions and any inconsistency are rejected; a v1 store (the removed
 * terminal model) fails closed with an explicit migration requirement —
 * nothing is ever auto-repaired or silently dropped.</p>
 */
public final class InstitutionAccessNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String FACILITIES = "Facilities";
    private static final String ZONES = "Zones";
    /** Legacy v1 collection key of the removed terminal model. */
    private static final String LEGACY_TERMINALS = "Terminals";

    private static final String FACILITY_VERSION = "FacilityVersion";
    private static final String FACILITY_ID = "FacilityId";
    private static final String INSTITUTION_TYPE = "InstitutionType";
    private static final String PARCEL_ID = "ParcelId";
    private static final String STATE = "State";
    private static final String FACILITY_REVISION = "FacilityRevision";

    private static final String ZONE_VERSION = "ZoneVersion";
    private static final String ZONE_ID = "ZoneId";
    private static final String KIND = "Kind";
    private static final String REGION = "Region";
    private static final String CAPABILITIES = "Capabilities";
    private static final String ZONE_REVISION = "ZoneRevision";

    private static final String DIMENSION = "Dimension";
    private static final String MIN_X = "MinX";
    private static final String MIN_Y = "MinY";
    private static final String MIN_Z = "MinZ";
    private static final String MAX_X = "MaxX";
    private static final String MAX_Y = "MaxY";
    private static final String MAX_Z = "MaxZ";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_FACILITIES = 1_000;
    private static final int HARD_MAX_ZONES = 10_000;
    private static final int HARD_MAX_CAPABILITIES = 8;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION, STORE_REVISION, FACILITIES, ZONES
    );
    private static final Set<String> FACILITY_KEYS = Set.of(
            FACILITY_VERSION, FACILITY_ID, INSTITUTION_TYPE, PARCEL_ID,
            STATE, FACILITY_REVISION
    );
    private static final Set<String> ZONE_KEYS = Set.of(
            ZONE_VERSION, ZONE_ID, FACILITY_ID, INSTITUTION_TYPE,
            KIND, REGION, CAPABILITIES, STATE, ZONE_REVISION
    );
    private static final Set<String> REGION_KEYS = Set.of(
            DIMENSION, MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present institution-access namespace must be a valid,
     * initialized store.
     */
    public InstitutionAccessStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "institution-access namespace is empty; it must be a valid initialized store"
            );
        }
        if (root.contains(STORE_VERSION, Tag.TAG_INT)
                && root.getInt(STORE_VERSION) == 1) {
            throw invalid(
                    "institution-access v1 store (terminal model) detected; "
                            + "an explicit migration is required before this "
                            + "v2 zone-based store can load it"
            );
        }
        if (root.contains(LEGACY_TERMINALS)) {
            throw invalid(
                    "institution-access store contains legacy v1 field '"
                            + LEGACY_TERMINALS + "'; an explicit migration is "
                            + "required before the v2 zone-based store can load it"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "institution-access");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "institution-access");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "institution-access");
        requireType(root, FACILITIES, Tag.TAG_COMPOUND, "institution-access");
        requireType(root, ZONES, Tag.TAG_COMPOUND, "institution-access");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported institution-access store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<FacilityId, Facility> facilities = decodeFacilities(
                root.getCompound(FACILITIES)
        );
        Map<ZoneId, Zone> zones = decodeZones(
                root.getCompound(ZONES),
                facilities.keySet()
        );

        return new InstitutionAccessStoreSnapshot(
                storeVersion,
                storeRevision,
                facilities,
                zones
        );
    }

    private Map<FacilityId, Facility> decodeFacilities(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_FACILITIES) {
            throw invalid("Facility count exceeds " + HARD_MAX_FACILITIES);
        }
        Map<FacilityId, Facility> facilities = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            FacilityId facilityId = parseCanonicalFacilityId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Facilities");
            Facility facility = decodeFacility(tag.getCompound(key), facilityId);
            if (facilities.put(facilityId, facility) != null) {
                throw invalid("Duplicate facility id: " + facilityId);
            }
        }
        return facilities;
    }

    private Facility decodeFacility(CompoundTag tag, FacilityId expectedId) {
        requireOnlyKeys(tag, FACILITY_KEYS, "facility " + expectedId);
        requireType(tag, FACILITY_VERSION, Tag.TAG_INT, "facility " + expectedId);
        requireType(tag, FACILITY_ID, Tag.TAG_INT_ARRAY, "facility " + expectedId);
        requireType(tag, INSTITUTION_TYPE, Tag.TAG_STRING, "facility " + expectedId);
        requireType(tag, PARCEL_ID, Tag.TAG_INT_ARRAY, "facility " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "facility " + expectedId);
        requireType(tag, FACILITY_REVISION, Tag.TAG_LONG, "facility " + expectedId);

        int facilityVersion = tag.getInt(FACILITY_VERSION);
        if (facilityVersion != Facility.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported facility version for " + expectedId + ": "
                            + facilityVersion
            );
        }
        FacilityId storedId = FacilityId.of(tag.getUUID(FACILITY_ID));
        if (!expectedId.equals(storedId)) {
            throw invalid(
                    "Facilities key " + expectedId + " does not match record facilityId "
                            + storedId
            );
        }
        try {
            return new Facility(
                    facilityVersion,
                    expectedId,
                    enumValue(InstitutionType.class, tag.getString(INSTITUTION_TYPE),
                            "InstitutionType for " + expectedId),
                    ParcelId.of(tag.getUUID(PARCEL_ID)),
                    enumValue(FacilityState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getLong(FACILITY_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid facility " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<ZoneId, Zone> decodeZones(
            CompoundTag tag,
            Set<FacilityId> knownFacilities
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_ZONES) {
            throw invalid("Zone count exceeds " + HARD_MAX_ZONES);
        }
        Map<ZoneId, Zone> zones = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            ZoneId zoneId = parseCanonicalZoneId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Zones");
            Zone zone = decodeZone(tag.getCompound(key), zoneId);
            if (!knownFacilities.contains(zone.facilityId())) {
                throw invalid(
                        "Zone " + zoneId + " references unknown facility "
                                + zone.facilityId()
                );
            }
            if (zones.put(zoneId, zone) != null) {
                throw invalid("Duplicate zone id: " + zoneId);
            }
        }
        return zones;
    }

    private Zone decodeZone(CompoundTag tag, ZoneId expectedId) {
        requireOnlyKeys(tag, ZONE_KEYS, "zone " + expectedId);
        requireType(tag, ZONE_VERSION, Tag.TAG_INT, "zone " + expectedId);
        requireType(tag, ZONE_ID, Tag.TAG_INT_ARRAY, "zone " + expectedId);
        requireType(tag, FACILITY_ID, Tag.TAG_INT_ARRAY, "zone " + expectedId);
        requireType(tag, INSTITUTION_TYPE, Tag.TAG_STRING, "zone " + expectedId);
        requireType(tag, KIND, Tag.TAG_STRING, "zone " + expectedId);
        requireType(tag, REGION, Tag.TAG_COMPOUND, "zone " + expectedId);
        requireType(tag, CAPABILITIES, Tag.TAG_LIST, "zone " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "zone " + expectedId);
        requireType(tag, ZONE_REVISION, Tag.TAG_LONG, "zone " + expectedId);

        int zoneVersion = tag.getInt(ZONE_VERSION);
        if (zoneVersion != Zone.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported zone version for " + expectedId + ": "
                            + zoneVersion
            );
        }
        ZoneId storedId = ZoneId.of(tag.getUUID(ZONE_ID));
        if (!expectedId.equals(storedId)) {
            throw invalid(
                    "Zones key " + expectedId + " does not match record zoneId "
                            + storedId
            );
        }
        try {
            return new Zone(
                    zoneVersion,
                    expectedId,
                    FacilityId.of(tag.getUUID(FACILITY_ID)),
                    enumValue(InstitutionType.class, tag.getString(INSTITUTION_TYPE),
                            "InstitutionType for " + expectedId),
                    enumValue(ZoneKind.class, tag.getString(KIND),
                            "Kind for " + expectedId),
                    decodeRegion(tag.getCompound(REGION), expectedId),
                    decodeCapabilities(tag.getList(CAPABILITIES, Tag.TAG_STRING),
                            expectedId),
                    enumValue(ZoneState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getLong(ZONE_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid zone " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private ZoneRegion decodeRegion(CompoundTag tag, ZoneId zoneId) {
        requireOnlyKeys(tag, REGION_KEYS, "region of " + zoneId);
        requireType(tag, DIMENSION, Tag.TAG_STRING, "region of " + zoneId);
        requireType(tag, MIN_X, Tag.TAG_INT, "region of " + zoneId);
        requireType(tag, MIN_Y, Tag.TAG_INT, "region of " + zoneId);
        requireType(tag, MIN_Z, Tag.TAG_INT, "region of " + zoneId);
        requireType(tag, MAX_X, Tag.TAG_INT, "region of " + zoneId);
        requireType(tag, MAX_Y, Tag.TAG_INT, "region of " + zoneId);
        requireType(tag, MAX_Z, Tag.TAG_INT, "region of " + zoneId);
        try {
            return new ZoneRegion(
                    tag.getString(DIMENSION),
                    tag.getInt(MIN_X),
                    tag.getInt(MIN_Y),
                    tag.getInt(MIN_Z),
                    tag.getInt(MAX_X),
                    tag.getInt(MAX_Y),
                    tag.getInt(MAX_Z)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid region of " + zoneId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Set<CapabilityClass> decodeCapabilities(ListTag tag, ZoneId zoneId) {
        if (tag.size() > HARD_MAX_CAPABILITIES) {
            throw invalid(
                    "Capability count of " + zoneId + " exceeds "
                            + HARD_MAX_CAPABILITIES
            );
        }
        Set<CapabilityClass> capabilities = new TreeSet<>();
        for (int i = 0; i < tag.size(); i++) {
            capabilities.add(enumValue(
                    CapabilityClass.class,
                    tag.getString(i),
                    "Capabilities of " + zoneId
            ));
        }
        return capabilities;
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: facilities and zones
     * are written in sorted lexical key order, capabilities in sorted enum
     * order.
     */
    public CompoundTag encode(InstitutionAccessStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag facilitiesTag = new CompoundTag();
        TreeMap<String, Facility> orderedFacilities = new TreeMap<>();
        snapshot.facilities().forEach(
                (facilityId, facility) -> orderedFacilities.put(
                        facilityId.canonicalKey(), facility
                )
        );
        orderedFacilities.forEach(
                (key, facility) -> facilitiesTag.put(key, encodeFacility(facility))
        );
        root.put(FACILITIES, facilitiesTag);

        CompoundTag zonesTag = new CompoundTag();
        TreeMap<String, Zone> orderedZones = new TreeMap<>();
        snapshot.zones().forEach(
                (zoneId, zone) -> orderedZones.put(
                        zoneId.canonicalKey(), zone
                )
        );
        orderedZones.forEach(
                (key, zone) -> zonesTag.put(key, encodeZone(zone))
        );
        root.put(ZONES, zonesTag);
        return root;
    }

    private CompoundTag encodeFacility(Facility facility) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FACILITY_VERSION, facility.schemaVersion());
        tag.putUUID(FACILITY_ID, facility.facilityId().value());
        tag.putString(INSTITUTION_TYPE, facility.institutionType().name());
        tag.putUUID(PARCEL_ID, facility.parcelId().value());
        tag.putString(STATE, facility.state().name());
        tag.putLong(FACILITY_REVISION, facility.facilityRevision());
        return tag;
    }

    private CompoundTag encodeZone(Zone zone) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ZONE_VERSION, zone.schemaVersion());
        tag.putUUID(ZONE_ID, zone.zoneId().value());
        tag.putUUID(FACILITY_ID, zone.facilityId().value());
        tag.putString(INSTITUTION_TYPE, zone.institutionType().name());
        tag.putString(KIND, zone.kind().name());
        tag.put(REGION, encodeRegion(zone.region()));
        tag.put(CAPABILITIES, encodeCapabilities(zone.orderedCapabilities()));
        tag.putString(STATE, zone.state().name());
        tag.putLong(ZONE_REVISION, zone.zoneRevision());
        return tag;
    }

    private CompoundTag encodeRegion(ZoneRegion region) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION, region.dimension());
        tag.putInt(MIN_X, region.minX());
        tag.putInt(MIN_Y, region.minY());
        tag.putInt(MIN_Z, region.minZ());
        tag.putInt(MAX_X, region.maxX());
        tag.putInt(MAX_Y, region.maxY());
        tag.putInt(MAX_Z, region.maxZ());
        return tag;
    }

    private ListTag encodeCapabilities(Set<CapabilityClass> capabilities) {
        ListTag tag = new ListTag();
        for (CapabilityClass capability : capabilities) {
            tag.add(StringTag.valueOf(capability.name()));
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

    private FacilityId parseCanonicalFacilityId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Facilities key is not canonical: " + value);
            }
            return FacilityId.of(parsed);
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid facility key: " + value, failure);
        }
    }

    private ZoneId parseCanonicalZoneId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Zones key is not canonical: " + value);
            }
            return ZoneId.of(parsed);
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid zone key: " + value, failure);
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

    private static InstitutionAccessNbtException invalid(String message) {
        return new InstitutionAccessNbtException(message);
    }

    private static InstitutionAccessNbtException invalid(String message, Throwable cause) {
        return new InstitutionAccessNbtException(message, cause);
    }
}
