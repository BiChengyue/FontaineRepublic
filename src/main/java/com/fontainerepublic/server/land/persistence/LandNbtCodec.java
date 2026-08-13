package com.fontainerepublic.server.land.persistence;

import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandOwnership;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.UsageRight;
import com.fontainerepublic.server.land.model.UsageType;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.land.model.ViolationStatus;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "land"} namespace
 * (FR-LAND-001-A §3.5).
 *
 * <p>Encoding writes the parcel, report, and holder-index collections in
 * deterministic lexical key order so the same immutable snapshot always
 * produces an equivalent ordered NBT. The holder index is always derived from
 * the parcels (single source of truth) and never stored redundantly
 * inconsistent with them. Decoding accepts only declared fields with exact NBT
 * types, canonical parcel UUIDs, valid dimensions/regions, the constant
 * {@code REPUBLIC} ownership, valid enums, positive timestamps/revisions, and
 * an exact holder-index/parcels consistency. Unknown newer versions and any
 * inconsistency are rejected; nothing is ever auto-repaired.</p>
 */
public final class LandNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String PARCELS = "Parcels";
    private static final String HOLDER_INDEX = "HolderIndex";
    private static final String REPORTS = "Reports";

    private static final String PARCEL_VERSION = "ParcelVersion";
    private static final String PARCEL_ID = "ParcelId";
    private static final String DIMENSION = "Dimension";
    private static final String REGION = "Region";
    private static final String ZONE_TYPE = "ZoneType";
    private static final String OWNERSHIP = "Ownership";
    private static final String ACCESS = "Access";
    private static final String USAGE_RIGHTS = "UsageRights";
    private static final String PARCEL_REVISION = "ParcelRevision";

    private static final String REGION_MIN_X = "MinX";
    private static final String REGION_MIN_Y = "MinY";
    private static final String REGION_MIN_Z = "MinZ";
    private static final String REGION_MAX_X = "MaxX";
    private static final String REGION_MAX_Y = "MaxY";
    private static final String REGION_MAX_Z = "MaxZ";

    private static final String RIGHT_VERSION = "RightVersion";
    private static final String HOLDER = "Holder";
    private static final String USAGE_TYPE = "UsageType";
    private static final String GRANTED_AT = "GrantedAt";
    private static final String EXPIRES_AT = "ExpiresAt";
    private static final String RIGHT_REVISION = "RightRevision";

    private static final String HOLDER_KIND = "Kind";
    private static final String HOLDER_OWNER_ID = "OwnerId";

    private static final String REPORT_VERSION = "ReportVersion";
    private static final String REPORT_ID = "ReportId";
    private static final String REPORTER = "Reporter";
    private static final String DESCRIPTION = "Description";
    private static final String REPORTED_AT = "ReportedAt";
    private static final String STATUS = "Status";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_PARCELS = 10_000;
    private static final int HARD_MAX_RIGHTS_PER_PARCEL = 1_024;
    private static final int HARD_MAX_REPORTS = 10_000;
    private static final int HARD_MAX_DESCRIPTION_CHARS = 2_000;
    private static final int HARD_MAX_HOLDER_PARCELS = 1_024;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION, STORE_REVISION, PARCELS, HOLDER_INDEX, REPORTS
    );
    private static final Set<String> PARCEL_KEYS = Set.of(
            PARCEL_VERSION, PARCEL_ID, DIMENSION, REGION, ZONE_TYPE, OWNERSHIP,
            ACCESS, USAGE_RIGHTS, PARCEL_REVISION
    );
    private static final Set<String> REGION_KEYS = Set.of(
            REGION_MIN_X, REGION_MIN_Y, REGION_MIN_Z,
            REGION_MAX_X, REGION_MAX_Y, REGION_MAX_Z
    );
    private static final Set<String> RIGHT_KEYS = Set.of(
            RIGHT_VERSION, HOLDER, USAGE_TYPE, GRANTED_AT, EXPIRES_AT, RIGHT_REVISION
    );
    private static final Set<String> HOLDER_KEYS = Set.of(HOLDER_KIND, HOLDER_OWNER_ID);
    private static final Set<String> REPORT_KEYS = Set.of(
            REPORT_VERSION, REPORT_ID, PARCEL_ID, REPORTER, DESCRIPTION,
            REPORTED_AT, STATUS
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present land namespace must be a valid, initialized store.
     */
    public LandStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "land namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "land");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "land");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "land");
        requireType(root, PARCELS, Tag.TAG_COMPOUND, "land");
        requireType(root, HOLDER_INDEX, Tag.TAG_COMPOUND, "land");
        requireType(root, REPORTS, Tag.TAG_COMPOUND, "land");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != LandStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported land store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<ParcelId, LandParcel> parcels = decodeParcels(
                root.getCompound(PARCELS)
        );
        Map<Long, ViolationReport> reports = decodeReports(
                root.getCompound(REPORTS),
                parcels.keySet()
        );
        verifyHolderIndex(root.getCompound(HOLDER_INDEX), parcels);

        return new LandStoreSnapshot(storeVersion, storeRevision, parcels, reports);
    }

    private Map<ParcelId, LandParcel> decodeParcels(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_PARCELS) {
            throw invalid("Parcel count exceeds " + HARD_MAX_PARCELS);
        }
        Map<ParcelId, LandParcel> parcels = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            ParcelId parcelId = parseCanonicalParcelId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Parcels");
            LandParcel parcel = decodeParcel(tag.getCompound(key), parcelId);
            if (parcels.put(parcelId, parcel) != null) {
                throw invalid("Duplicate parcel id: " + parcelId);
            }
        }
        return parcels;
    }

    private LandParcel decodeParcel(CompoundTag tag, ParcelId expectedId) {
        requireOnlyKeys(tag, PARCEL_KEYS, "parcel " + expectedId);
        requireType(tag, PARCEL_VERSION, Tag.TAG_INT, "parcel " + expectedId);
        requireType(tag, PARCEL_ID, Tag.TAG_INT_ARRAY, "parcel " + expectedId);
        requireType(tag, DIMENSION, Tag.TAG_STRING, "parcel " + expectedId);
        requireType(tag, REGION, Tag.TAG_COMPOUND, "parcel " + expectedId);
        requireType(tag, ZONE_TYPE, Tag.TAG_STRING, "parcel " + expectedId);
        requireType(tag, OWNERSHIP, Tag.TAG_STRING, "parcel " + expectedId);
        requireType(tag, ACCESS, Tag.TAG_STRING, "parcel " + expectedId);
        requireType(tag, USAGE_RIGHTS, Tag.TAG_COMPOUND, "parcel " + expectedId);
        requireType(tag, PARCEL_REVISION, Tag.TAG_LONG, "parcel " + expectedId);

        int parcelVersion = tag.getInt(PARCEL_VERSION);
        if (parcelVersion != LandParcel.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported land parcel version for " + expectedId + ": "
                            + parcelVersion
            );
        }
        ParcelId storedId = ParcelId.of(tag.getUUID(PARCEL_ID));
        if (!expectedId.equals(storedId)) {
            throw invalid(
                    "Parcels key " + expectedId + " does not match record parcelId "
                            + storedId
            );
        }
        LandOwnership ownership = enumValue(
                LandOwnership.class,
                tag.getString(OWNERSHIP),
                "Ownership for " + expectedId
        );
        if (ownership != LandOwnership.REPUBLIC) {
            throw invalid(
                    "Parcel ownership must be REPUBLIC for " + expectedId
            );
        }

        try {
            return new LandParcel(
                    parcelVersion,
                    expectedId,
                    tag.getString(DIMENSION),
                    decodeRegion(tag.getCompound(REGION), expectedId),
                    enumValue(ZoneType.class, tag.getString(ZONE_TYPE),
                            "ZoneType for " + expectedId),
                    ownership,
                    enumValue(LandAccess.class, tag.getString(ACCESS),
                            "Access for " + expectedId),
                    decodeUsageRights(tag.getCompound(USAGE_RIGHTS), expectedId),
                    tag.getLong(PARCEL_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid land parcel " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private ParcelRegion decodeRegion(CompoundTag tag, ParcelId parcelId) {
        requireOnlyKeys(tag, REGION_KEYS, "region of " + parcelId);
        requireType(tag, REGION_MIN_X, Tag.TAG_INT, "region of " + parcelId);
        requireType(tag, REGION_MIN_Y, Tag.TAG_INT, "region of " + parcelId);
        requireType(tag, REGION_MIN_Z, Tag.TAG_INT, "region of " + parcelId);
        requireType(tag, REGION_MAX_X, Tag.TAG_INT, "region of " + parcelId);
        requireType(tag, REGION_MAX_Y, Tag.TAG_INT, "region of " + parcelId);
        requireType(tag, REGION_MAX_Z, Tag.TAG_INT, "region of " + parcelId);
        try {
            return new ParcelRegion(
                    tag.getInt(REGION_MIN_X),
                    tag.getInt(REGION_MIN_Y),
                    tag.getInt(REGION_MIN_Z),
                    tag.getInt(REGION_MAX_X),
                    tag.getInt(REGION_MAX_Y),
                    tag.getInt(REGION_MAX_Z)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid region of " + parcelId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<OwnerReference, UsageRight>
    decodeUsageRights(CompoundTag tag, ParcelId parcelId) {
        if (tag.getAllKeys().size() > HARD_MAX_RIGHTS_PER_PARCEL) {
            throw invalid(
                    "Usage-right count of " + parcelId + " exceeds "
                            + HARD_MAX_RIGHTS_PER_PARCEL
            );
        }
        Map<OwnerReference, UsageRight> rights = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            requireType(tag, key, Tag.TAG_COMPOUND, "UsageRights of " + parcelId);
            UsageRight right = decodeUsageRight(tag.getCompound(key), parcelId);
            if (!right.holder().key().equals(key)) {
                throw invalid(
                        "UsageRights key " + key + " does not match holder "
                                + right.holder().key() + " on " + parcelId
                );
            }
            if (rights.put(right.holder(), right) != null) {
                throw invalid(
                        "Duplicate usage-right holder " + right.holder().key()
                                + " on " + parcelId
                );
            }
        }
        return rights;
    }

    private UsageRight decodeUsageRight(CompoundTag tag, ParcelId parcelId) {
        requireOnlyKeys(tag, RIGHT_KEYS, "usage right on " + parcelId);
        requireType(tag, RIGHT_VERSION, Tag.TAG_INT, "usage right on " + parcelId);
        requireType(tag, HOLDER, Tag.TAG_COMPOUND, "usage right on " + parcelId);
        requireType(tag, USAGE_TYPE, Tag.TAG_STRING, "usage right on " + parcelId);
        requireType(tag, GRANTED_AT, Tag.TAG_LONG, "usage right on " + parcelId);
        requireType(tag, EXPIRES_AT, Tag.TAG_LONG, "usage right on " + parcelId);
        requireType(tag, RIGHT_REVISION, Tag.TAG_LONG, "usage right on " + parcelId);

        int rightVersion = tag.getInt(RIGHT_VERSION);
        if (rightVersion != UsageRight.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported usage right version on " + parcelId + ": "
                            + rightVersion
            );
        }
        try {
            return new UsageRight(
                    rightVersion,
                    decodeOwnerReference(tag.getCompound(HOLDER), "usage right on " + parcelId),
                    enumValue(UsageType.class, tag.getString(USAGE_TYPE),
                            "UsageType on " + parcelId),
                    tag.getLong(GRANTED_AT),
                    tag.getLong(EXPIRES_AT),
                    tag.getLong(RIGHT_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid usage right on " + parcelId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private OwnerReference decodeOwnerReference(CompoundTag tag, String path) {
        requireOnlyKeys(tag, HOLDER_KEYS, path);
        requireType(tag, HOLDER_KIND, Tag.TAG_STRING, path);
        requireType(tag, HOLDER_OWNER_ID, Tag.TAG_STRING, path);
        try {
            return new OwnerReference(
                    OwnerReferenceKind.valueOf(tag.getString(HOLDER_KIND)),
                    tag.getString(HOLDER_OWNER_ID)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid owner reference at " + path + ": "
                    + failure.getMessage(), failure);
        }
    }

    private Map<Long, ViolationReport> decodeReports(
            CompoundTag tag,
            Set<ParcelId> knownParcels
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_REPORTS) {
            throw invalid("Report count exceeds " + HARD_MAX_REPORTS);
        }
        Map<Long, ViolationReport> reports = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            long reportId = parseReportKey(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Reports");
            ViolationReport report = decodeReport(tag.getCompound(key), reportId);
            if (report.reportId() != reportId) {
                throw invalid(
                        "Reports key " + key + " does not match report id "
                                + report.reportId()
                );
            }
            if (!knownParcels.contains(report.parcelId())) {
                throw invalid(
                        "Report " + reportId + " references unknown parcel "
                                + report.parcelId()
                );
            }
            if (reports.put(reportId, report) != null) {
                throw invalid("Duplicate report id: " + reportId);
            }
        }
        return reports;
    }

    private ViolationReport decodeReport(CompoundTag tag, long expectedId) {
        requireOnlyKeys(tag, REPORT_KEYS, "report " + expectedId);
        requireType(tag, REPORT_VERSION, Tag.TAG_INT, "report " + expectedId);
        requireType(tag, REPORT_ID, Tag.TAG_LONG, "report " + expectedId);
        requireType(tag, PARCEL_ID, Tag.TAG_INT_ARRAY, "report " + expectedId);
        requireType(tag, REPORTER, Tag.TAG_COMPOUND, "report " + expectedId);
        requireType(tag, DESCRIPTION, Tag.TAG_STRING, "report " + expectedId);
        requireType(tag, REPORTED_AT, Tag.TAG_LONG, "report " + expectedId);
        requireType(tag, STATUS, Tag.TAG_STRING, "report " + expectedId);

        int reportVersion = tag.getInt(REPORT_VERSION);
        if (reportVersion != ViolationReport.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported violation report version for " + expectedId
                            + ": " + reportVersion
            );
        }
        String description = tag.getString(DESCRIPTION);
        if (description.length() > HARD_MAX_DESCRIPTION_CHARS) {
            throw invalid(
                    "Description of report " + expectedId + " exceeds "
                            + HARD_MAX_DESCRIPTION_CHARS + " chars"
            );
        }
        try {
            return new ViolationReport(
                    reportVersion,
                    expectedId,
                    ParcelId.of(tag.getUUID(PARCEL_ID)),
                    decodeOwnerReference(tag.getCompound(REPORTER),
                            "report " + expectedId),
                    description,
                    tag.getLong(REPORTED_AT),
                    enumValue(ViolationStatus.class, tag.getString(STATUS),
                            "Status of report " + expectedId)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid violation report " + expectedId + ": "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    private void verifyHolderIndex(CompoundTag stored, Map<ParcelId, LandParcel> parcels) {
        Map<String, List<String>> derived = deriveHolderIndex(parcels);
        if (stored.getAllKeys().size() != derived.size()) {
            throw invalid(
                    "HolderIndex is inconsistent with parcels (size mismatch)"
            );
        }
        for (String holderKey : stored.getAllKeys().stream().sorted().toList()) {
            List<String> expected = derived.get(holderKey);
            if (expected == null) {
                throw invalid("HolderIndex contains orphan holder " + holderKey);
            }
            requireType(stored, holderKey, Tag.TAG_LIST, "HolderIndex");
            ListTag storedParcels = stored.getList(holderKey, Tag.TAG_STRING);
            if (storedParcels.size() != expected.size()) {
                throw invalid(
                        "HolderIndex for " + holderKey + " is inconsistent with parcels"
                );
            }
            for (int i = 0; i < storedParcels.size(); i++) {
                if (!storedParcels.getString(i).equals(expected.get(i))) {
                    throw invalid(
                            "HolderIndex for " + holderKey
                                    + " is inconsistent with parcels"
                    );
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: parcels, reports, and
     * the derived holder index are written in sorted lexical key order.
     */
    public CompoundTag encode(LandStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag parcelsTag = new CompoundTag();
        TreeMap<String, LandParcel> orderedParcels = new TreeMap<>();
        snapshot.parcels().forEach(
                (parcelId, parcel) -> orderedParcels.put(parcelId.canonicalKey(), parcel)
        );
        orderedParcels.forEach(
                (key, parcel) -> parcelsTag.put(key, encodeParcel(parcel))
        );
        root.put(PARCELS, parcelsTag);

        root.put(HOLDER_INDEX, encodeHolderIndex(snapshot.parcels()));

        CompoundTag reportsTag = new CompoundTag();
        TreeMap<String, ViolationReport> orderedReports = new TreeMap<>();
        snapshot.reports().forEach(
                (reportId, report) -> orderedReports.put(
                        Long.toString(reportId), report
                )
        );
        orderedReports.forEach(
                (key, report) -> reportsTag.put(key, encodeReport(report))
        );
        root.put(REPORTS, reportsTag);
        return root;
    }

    private CompoundTag encodeParcel(LandParcel parcel) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PARCEL_VERSION, parcel.schemaVersion());
        tag.putUUID(PARCEL_ID, parcel.parcelId().value());
        tag.putString(DIMENSION, parcel.dimension());
        tag.put(REGION, encodeRegion(parcel.region()));
        tag.putString(ZONE_TYPE, parcel.zoneType().name());
        tag.putString(OWNERSHIP, parcel.ownership().name());
        tag.putString(ACCESS, parcel.access().name());
        tag.put(USAGE_RIGHTS, encodeUsageRights(parcel));
        tag.putLong(PARCEL_REVISION, parcel.parcelRevision());
        return tag;
    }

    private CompoundTag encodeRegion(ParcelRegion region) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(REGION_MIN_X, region.minX());
        tag.putInt(REGION_MIN_Y, region.minY());
        tag.putInt(REGION_MIN_Z, region.minZ());
        tag.putInt(REGION_MAX_X, region.maxX());
        tag.putInt(REGION_MAX_Y, region.maxY());
        tag.putInt(REGION_MAX_Z, region.maxZ());
        return tag;
    }

    private CompoundTag encodeUsageRights(LandParcel parcel) {
        CompoundTag tag = new CompoundTag();
        TreeMap<String, UsageRight> ordered = new TreeMap<>();
        parcel.usageRights().forEach(
                (holder, right) -> ordered.put(holder.key(), right)
        );
        ordered.forEach((key, right) -> tag.put(key, encodeUsageRight(right)));
        return tag;
    }

    private CompoundTag encodeUsageRight(UsageRight right) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(RIGHT_VERSION, right.schemaVersion());
        tag.put(HOLDER, encodeOwnerReference(right.holder()));
        tag.putString(USAGE_TYPE, right.usageType().name());
        tag.putLong(GRANTED_AT, right.grantedAt());
        tag.putLong(EXPIRES_AT, right.expiresAt());
        tag.putLong(RIGHT_REVISION, right.rightRevision());
        return tag;
    }

    private CompoundTag encodeOwnerReference(OwnerReference reference) {
        CompoundTag tag = new CompoundTag();
        tag.putString(HOLDER_KIND, reference.kind().name());
        tag.putString(HOLDER_OWNER_ID, reference.ownerId());
        return tag;
    }

    private CompoundTag encodeReport(ViolationReport report) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(REPORT_VERSION, report.schemaVersion());
        tag.putLong(REPORT_ID, report.reportId());
        tag.putUUID(PARCEL_ID, report.parcelId().value());
        tag.put(REPORTER, encodeOwnerReference(report.reporter()));
        tag.putString(DESCRIPTION, report.description());
        tag.putLong(REPORTED_AT, report.reportedAt());
        tag.putString(STATUS, report.status().name());
        return tag;
    }

    /**
     * The holder index is always derived from the parcels themselves (single
     * source of truth): holder key -&gt; sorted parcel-id keys.
     */
    private CompoundTag encodeHolderIndex(Map<ParcelId, LandParcel> parcels) {
        Map<String, List<String>> derived = deriveHolderIndex(parcels);
        CompoundTag index = new CompoundTag();
        for (Map.Entry<String, List<String>> entry : derived.entrySet()) {
            ListTag parcelIds = new ListTag();
            for (String parcelKey : entry.getValue()) {
                parcelIds.add(net.minecraft.nbt.StringTag.valueOf(parcelKey));
            }
            index.put(entry.getKey(), parcelIds);
        }
        return index;
    }

    private Map<String, List<String>> deriveHolderIndex(Map<ParcelId, LandParcel> parcels) {
        TreeMap<String, TreeSet<String>> index = new TreeMap<>();
        for (LandParcel parcel : parcels.values()) {
            if (parcel.usageRights().size() > HARD_MAX_RIGHTS_PER_PARCEL) {
                throw invalid(
                        "Usage-right count of " + parcel.parcelId() + " exceeds "
                                + HARD_MAX_RIGHTS_PER_PARCEL
                );
            }
            for (OwnerReference holder : parcel.usageRights().keySet()) {
                TreeSet<String> parcelIds =
                        index.computeIfAbsent(holder.key(), ignored -> new TreeSet<>());
                parcelIds.add(parcel.parcelId().canonicalKey());
                if (parcelIds.size() > HARD_MAX_HOLDER_PARCELS) {
                    throw invalid(
                            "Holder " + holder.key() + " holds more than "
                                    + HARD_MAX_HOLDER_PARCELS + " parcels"
                    );
                }
            }
        }
        Map<String, List<String>> result = new TreeMap<>();
        index.forEach((holderKey, parcelIds) ->
                result.put(holderKey, new ArrayList<>(parcelIds)));
        return result;
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

    private ParcelId parseCanonicalParcelId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Parcels key is not canonical: " + value);
            }
            return ParcelId.of(parsed);
        } catch (IllegalArgumentException failure) {
            if (failure instanceof LandNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid parcel key: " + value, failure);
        }
    }

    private long parseReportKey(String value) {
        try {
            long reportId = Long.parseLong(value);
            if (reportId <= 0 || !Long.toString(reportId).equals(value)) {
                throw invalid("Reports key is not a canonical positive id: " + value);
            }
            return reportId;
        } catch (NumberFormatException failure) {
            throw invalid("Reports key is not a numeric id: " + value, failure);
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

    private static LandNbtException invalid(String message) {
        return new LandNbtException(message);
    }

    private static LandNbtException invalid(String message, Throwable cause) {
        return new LandNbtException(message, cause);
    }
}
