package com.fontainerepublic.server.land.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.UsageRight;
import com.fontainerepublic.server.land.model.UsageType;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.land.model.ViolationStatus;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.registry.model.OwnerReference;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-writer repository owning the {@code "land"} NBT namespace
 * (FR-LAND-001-A §3.5/§4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "land"} key. All mutations run on the logical server owner thread;
 * every mutation builds one complete immutable replacement snapshot,
 * increments {@code StoreRevision} once and each changed parcel revision once,
 * and publishes the new in-memory state only after the FR-CORE-002 durable
 * gate reports {@code COMMITTED}. A failed write has no side effects: no
 * parcel, usage right, report, or revision change, and no downstream event.</p>
 *
 * <p>On an empty namespace the repository starts with a fresh, empty store
 * (revision 0). A present namespace is decoded strictly and fail-closed:
 * unknown fields, inconsistent holder index, or newer versions reject the
 * whole load. Exact lookups only — no bulk enumeration API is exposed.</p>
 */
public final class LandRepository {

    /** Reserved module-data key for the land namespace. */
    public static final String MODULE_DATA_KEY = "land";

    private static final int MAX_PARCEL_ID_ATTEMPTS = 8;

    private final LandStore store;
    private final LandNbtCodec codec;
    private final LandLimits limits;
    private final ParcelIdSource parcelIdSource;
    private final Thread ownerThread;

    private final LinkedHashMap<ParcelId, LandParcel> parcels = new LinkedHashMap<>();
    private final LinkedHashMap<Long, ViolationReport> reports = new LinkedHashMap<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static LandRepository createProduction(LandNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new LandRepository(
                new DataManagerLandStore(),
                codec,
                LandLimits.DEFAULT,
                UUID::randomUUID
        );
    }

    public LandRepository(
            LandStore store,
            LandNbtCodec codec,
            LandLimits limits,
            ParcelIdSource parcelIdSource
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.parcelIdSource = Objects.requireNonNull(parcelIdSource, "parcelIdSource");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            LandStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups only; no enumeration API)
    // ------------------------------------------------------------------

    public Optional<LandParcel> findByParcelId(ParcelId parcelId) {
        requireOwnerThread();
        return Optional.ofNullable(
                parcels.get(Objects.requireNonNull(parcelId, "parcelId"))
        );
    }

    public LandParcel requireParcel(ParcelId parcelId) {
        return findByParcelId(parcelId).orElseThrow(
                () -> new LandUnavailableException(
                        LandUnavailableException.CODE_PARCEL_NOT_FOUND,
                        "No parcel exists for " + parcelId
                )
        );
    }

    /**
     * Exact coordinate lookup of the parcel containing a block position in a
     * dimension (event-time resolution). Deterministic across overlapping
     * parcels by parcel-id order; returns empty when the position is not part
     * of any registered parcel (events then fail closed).
     */
    public Optional<LandParcel> findParcelAt(String dimension, int x, int y, int z) {
        requireOwnerThread();
        Objects.requireNonNull(dimension, "dimension");
        return parcels.values().stream()
                .filter(parcel -> parcel.dimension().equals(dimension)
                        && parcel.region().contains(x, y, z))
                .sorted((left, right) -> left.parcelId().canonicalKey()
                        .compareTo(right.parcelId().canonicalKey()))
                .findFirst();
    }

    public int size() {
        requireOwnerThread();
        return parcels.size();
    }

    public int reportCount() {
        requireOwnerThread();
        return reports.size();
    }

    public LandStoreSnapshot snapshot() {
        requireOwnerThread();
        return new LandStoreSnapshot(
                LandStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                parcels,
                reports
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Creates a republic-owned parcel: server-assigned immutable id, revision
     * 1, the given planning designation and access policy, no usage rights.
     * One complete replacement snapshot; committed only after the gate.
     */
    public LandParcel createParcel(
            String dimension,
            ParcelRegion region,
            ZoneType zoneType,
            LandAccess access,
            long timestamp
    ) {
        requireOwnerThread();
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(zoneType, "zoneType");
        Objects.requireNonNull(access, "access");
        if (parcels.size() >= limits.maxParcels()) {
            throw capacity("Parcel count would exceed the budget of "
                    + limits.maxParcels());
        }
        requireStoreRevisionSpace();

        ParcelId parcelId = assignParcelId();
        LandParcel parcel = new LandParcel(
                LandParcel.CURRENT_SCHEMA_VERSION,
                parcelId,
                dimension,
                region,
                zoneType,
                com.fontainerepublic.server.land.model.LandOwnership.REPUBLIC,
                access,
                Map.of(),
                1
        );
        LinkedHashMap<ParcelId, LandParcel> next = new LinkedHashMap<>(parcels);
        next.put(parcelId, parcel);
        commitAndPublish(new LandStoreSnapshot(
                LandStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                next,
                reports
        ));
        return parcel;
    }

    /**
     * Replaces the zone type: one complete replacement snapshot, parcel and
     * store revision +1 exactly once. Same-zone requests are idempotent
     * no-ops (nothing committed).
     */
    public LandParcel updateZoneType(ParcelId parcelId, ZoneType zoneType) {
        requireOwnerThread();
        Objects.requireNonNull(zoneType, "zoneType");
        LandParcel current = requireParcel(parcelId);
        if (current.zoneType() == zoneType) {
            return current;
        }
        requireStoreRevisionSpace();
        return replaceAndPublish(current.withZoneType(zoneType));
    }

    /**
     * Replaces the access policy: one complete replacement snapshot, parcel
     * and store revision +1 exactly once. Same-access requests are idempotent
     * no-ops (nothing committed).
     */
    public LandParcel updateAccess(ParcelId parcelId, LandAccess access) {
        requireOwnerThread();
        Objects.requireNonNull(access, "access");
        LandParcel current = requireParcel(parcelId);
        if (current.access() == access) {
            return current;
        }
        requireStoreRevisionSpace();
        return replaceAndPublish(current.withAccess(access));
    }

    /**
     * Grants a usage right to a holder: one complete replacement snapshot,
     * right and parcel and store revision +1 exactly once. A holder that
     * already holds a right must use renewal — a duplicate grant fails closed.
     */
    public LandParcel grantUsage(
            ParcelId parcelId,
            OwnerReference holder,
            long grantedAt,
            long expiresAt
    ) {
        requireOwnerThread();
        Objects.requireNonNull(holder, "holder");
        LandParcel current = requireParcel(parcelId);
        if (current.usageRightOf(holder).isPresent()) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_DUPLICATE_GRANT,
                    "Holder " + holder.key() + " already holds a usage right on "
                            + parcelId + "; use renew"
            );
        }
        if (current.usageRights().size() >= limits.maxUsageRightsPerParcel()) {
            throw capacity("Usage-right count of " + parcelId
                    + " would exceed the budget of "
                    + limits.maxUsageRightsPerParcel());
        }
        requireStoreRevisionSpace();
        UsageRight right = new UsageRight(
                UsageRight.CURRENT_SCHEMA_VERSION,
                holder,
                UsageType.USAGE_GRANT,
                grantedAt,
                expiresAt,
                1
        );
        return replaceAndPublish(current.withUsageRight(holder, right));
    }

    /**
     * Renews a holder's usage right: the expiry is replaced from the renewal
     * time, the original grant time is kept, and the right revision +1 exactly
     * once. A holder without a right fails closed.
     */
    public LandParcel renewUsage(
            ParcelId parcelId,
            OwnerReference holder,
            long expiresAt
    ) {
        requireOwnerThread();
        Objects.requireNonNull(holder, "holder");
        LandParcel current = requireParcel(parcelId);
        UsageRight existing = current.usageRightOf(holder).orElseThrow(
                () -> new LandUnavailableException(
                        LandUnavailableException.CODE_NO_USAGE_RIGHT,
                        "Holder " + holder.key() + " holds no usage right on "
                                + parcelId + "; nothing to renew"
                )
        );
        requireStoreRevisionSpace();
        return replaceAndPublish(
                current.withUsageRight(holder, existing.withRenewal(expiresAt))
        );
    }

    /**
     * Revokes a holder's usage right: one complete replacement snapshot,
     * parcel and store revision +1 exactly once. A holder without a right is
     * an idempotent no-op (nothing committed).
     */
    public LandParcel revokeUsage(ParcelId parcelId, OwnerReference holder) {
        requireOwnerThread();
        Objects.requireNonNull(holder, "holder");
        LandParcel current = requireParcel(parcelId);
        if (current.usageRightOf(holder).isEmpty()) {
            return current;
        }
        requireStoreRevisionSpace();
        return replaceAndPublish(current.withoutUsageRight(holder));
    }

    /**
     * Creates a read-only violation report referencing an existing parcel.
     * Report ids are server-assigned ascending from the current maximum.
     * One complete replacement snapshot; committed only after the gate.
     */
    public ViolationReport createReport(
            ParcelId parcelId,
            OwnerReference reporter,
            String description,
            long reportedAt
    ) {
        requireOwnerThread();
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(description, "description");
        requireParcel(parcelId);
        if (description.isEmpty()) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_REQUEST,
                    "Violation description must not be empty"
            );
        }
        if (description.length() > limits.maxDescriptionChars()) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_REQUEST,
                    "Violation description exceeds the limit of "
                            + limits.maxDescriptionChars() + " chars"
            );
        }
        if (reports.size() >= limits.maxReports()) {
            throw capacity("Report count would exceed the budget of "
                    + limits.maxReports());
        }
        requireStoreRevisionSpace();

        long reportId = nextReportId();
        ViolationReport report = new ViolationReport(
                ViolationReport.CURRENT_SCHEMA_VERSION,
                reportId,
                parcelId,
                reporter,
                description,
                reportedAt,
                ViolationStatus.OPEN
        );
        LinkedHashMap<Long, ViolationReport> next = new LinkedHashMap<>(reports);
        next.put(reportId, report);
        commitAndPublish(new LandStoreSnapshot(
                LandStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                parcels,
                next
        ));
        return report;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private LandParcel replaceAndPublish(LandParcel updated) {
        LinkedHashMap<ParcelId, LandParcel> next = new LinkedHashMap<>(parcels);
        next.put(updated.parcelId(), updated);
        commitAndPublish(new LandStoreSnapshot(
                LandStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                next,
                reports
        ));
        return updated;
    }

    private ParcelId assignParcelId() {
        for (int attempt = 0; attempt < MAX_PARCEL_ID_ATTEMPTS; attempt++) {
            UUID candidate = parcelIdSource.nextUuid();
            if (candidate == null) {
                throw new IllegalStateException("ParcelIdSource returned null");
            }
            ParcelId parcelId = ParcelId.of(candidate);
            if (!parcels.containsKey(parcelId)) {
                return parcelId;
            }
        }
        throw new LandUnavailableException(
                LandUnavailableException.CODE_PARCEL_EXISTS,
                "Unable to allocate a fresh parcel id after "
                        + MAX_PARCEL_ID_ATTEMPTS + " attempts"
        );
    }

    private long nextReportId() {
        long max = 0L;
        for (Long reportId : reports.keySet()) {
            if (reportId > max) {
                max = reportId;
            }
        }
        if (max == Long.MAX_VALUE) {
            throw capacity("Report id space exhausted");
        }
        return max + 1;
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw capacity("Land store revision space exhausted");
        }
    }

    private LandUnavailableException capacity(String message) {
        return new LandUnavailableException(
                LandUnavailableException.CODE_CAPACITY_EXCEEDED,
                message
        );
    }

    private void commitAndPublish(LandStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw capacity("Land namespace would exceed the byte budget (" + bytes
                    + " > " + limits.maxTotalBytes() + ")");
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_STORE_FAILURE,
                    "Land store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of land failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(LandStoreSnapshot snapshot) {
        parcels.clear();
        parcels.putAll(snapshot.parcels());
        reports.clear();
        reports.putAll(snapshot.reports());
        storeRevision = snapshot.storeRevision();
    }

    private void enforceLoadedCapacity(LandStoreSnapshot snapshot) {
        if (snapshot.parcels().size() > limits.maxParcels()) {
            throw capacity("Loaded parcel count " + snapshot.parcels().size()
                    + " exceeds the budget of " + limits.maxParcels());
        }
        if (snapshot.reports().size() > limits.maxReports()) {
            throw capacity("Loaded report count " + snapshot.reports().size()
                    + " exceeds the budget of " + limits.maxReports());
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "LandRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerLandStore implements LandStore {
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
}
