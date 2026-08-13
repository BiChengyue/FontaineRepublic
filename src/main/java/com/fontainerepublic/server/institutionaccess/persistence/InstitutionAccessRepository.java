package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
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

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single-writer repository owning the {@code "institution-access"} NBT
 * namespace (FR-INST-002-B §2, store v2).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "institution-access"} key. All mutations run on the logical server
 * owner thread; every mutation builds one complete immutable replacement
 * snapshot, increments {@code StoreRevision} once and each changed record
 * revision once, and publishes the new in-memory state only after the
 * FR-CORE-002 durable gate reports {@code COMMITTED}. A failed write has no
 * side effects: no facility, no zone, no revision change, and no downstream
 * event.</p>
 *
 * <p>On an empty namespace the repository starts with a fresh, empty store
 * (revision 0). A present namespace is decoded strictly and fail-closed:
 * unknown fields, inconsistent ids, shared parcels, or newer versions reject
 * the whole load; a v1 store (the removed terminal model) fails closed with
 * an explicit migration requirement. Exact lookups only — no bulk
 * enumeration API is exposed.</p>
 */
public final class InstitutionAccessRepository {

    /** Reserved module-data key for the institution-access namespace. */
    public static final String MODULE_DATA_KEY = "institution-access";

    private static final int MAX_FACILITY_ID_ATTEMPTS = 8;
    private static final int MAX_ZONE_ID_ATTEMPTS = 8;

    private final InstitutionAccessStore store;
    private final InstitutionAccessNbtCodec codec;
    private final InstitutionAccessLimits limits;
    private final Supplier<UUID> facilityIdSource;
    private final Supplier<UUID> zoneIdSource;
    private final Thread ownerThread;

    private final LinkedHashMap<FacilityId, Facility> facilities = new LinkedHashMap<>();
    private final LinkedHashMap<ZoneId, Zone> zones = new LinkedHashMap<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static InstitutionAccessRepository createProduction(
            InstitutionAccessNbtCodec codec
    ) {
        Objects.requireNonNull(codec, "codec");
        return new InstitutionAccessRepository(
                new DataManagerInstitutionAccessStore(),
                codec,
                InstitutionAccessLimits.DEFAULT,
                UUID::randomUUID,
                UUID::randomUUID
        );
    }

    public InstitutionAccessRepository(
            InstitutionAccessStore store,
            InstitutionAccessNbtCodec codec,
            InstitutionAccessLimits limits,
            Supplier<UUID> facilityIdSource,
            Supplier<UUID> zoneIdSource
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.facilityIdSource = Objects.requireNonNull(facilityIdSource, "facilityIdSource");
        this.zoneIdSource = Objects.requireNonNull(zoneIdSource, "zoneIdSource");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            InstitutionAccessStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups only; no enumeration API)
    // ------------------------------------------------------------------

    public Optional<Facility> findByFacilityId(FacilityId facilityId) {
        requireOwnerThread();
        return Optional.ofNullable(
                facilities.get(Objects.requireNonNull(facilityId, "facilityId"))
        );
    }

    public Facility requireFacility(FacilityId facilityId) {
        return findByFacilityId(facilityId).orElseThrow(
                () -> new InstitutionAccessUnavailableException(
                        InstitutionAccessUnavailableException.CODE_FACILITY_NOT_FOUND,
                        "No facility exists for " + facilityId
                )
        );
    }

    public Optional<Zone> findByZoneId(ZoneId zoneId) {
        requireOwnerThread();
        return Optional.ofNullable(
                zones.get(Objects.requireNonNull(zoneId, "zoneId"))
        );
    }

    public Zone requireZone(ZoneId zoneId) {
        return findByZoneId(zoneId).orElseThrow(
                () -> new InstitutionAccessUnavailableException(
                        InstitutionAccessUnavailableException.CODE_ZONE_NOT_FOUND,
                        "No zone exists for " + zoneId
                )
        );
    }

    /** The facility currently bound to a parcel, if any. */
    public Optional<Facility> findByParcelId(ParcelId parcelId) {
        requireOwnerThread();
        Objects.requireNonNull(parcelId, "parcelId");
        return facilities.values().stream()
                .filter(facility -> facility.parcelId().equals(parcelId))
                .findFirst();
    }

    public int size() {
        requireOwnerThread();
        return facilities.size();
    }

    public int zoneCount() {
        requireOwnerThread();
        return zones.size();
    }

    public int zoneCountOf(FacilityId facilityId) {
        requireOwnerThread();
        Objects.requireNonNull(facilityId, "facilityId");
        return (int) zones.values().stream()
                .filter(zone -> zone.facilityId().equals(facilityId))
                .count();
    }

    public InstitutionAccessStoreSnapshot snapshot() {
        requireOwnerThread();
        return new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                facilities,
                zones
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Registers a facility bound to a FR-LAND parcel: server-assigned
     * immutable id, state ACTIVE, revision 1. One complete replacement
     * snapshot; committed only after the gate. The caller must have verified
     * the parcel exists and is unbound.
     */
    public Facility registerFacility(
            InstitutionType institutionType,
            ParcelId parcelId
    ) {
        requireOwnerThread();
        Objects.requireNonNull(institutionType, "institutionType");
        Objects.requireNonNull(parcelId, "parcelId");
        if (facilities.size() >= limits.maxFacilities()) {
            throw capacity("Facility count would exceed the budget of "
                    + limits.maxFacilities());
        }
        requireStoreRevisionSpace();

        FacilityId facilityId = assignFacilityId();
        Facility facility = new Facility(
                Facility.CURRENT_SCHEMA_VERSION,
                facilityId,
                institutionType,
                parcelId,
                FacilityState.ACTIVE,
                1
        );
        LinkedHashMap<FacilityId, Facility> next = new LinkedHashMap<>(facilities);
        next.put(facilityId, facility);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                next,
                zones
        ));
        return facility;
    }

    /**
     * Suspends a facility: {@code ACTIVE}/{@code RELOCATING} become
     * {@code SUSPENDED} (no-op when already suspended; {@code DISABLED} is
     * rejected). Revision +1 exactly once on change.
     */
    public Facility suspendFacility(FacilityId facilityId) {
        requireOwnerThread();
        Facility current = requireFacility(facilityId);
        if (current.state() == FacilityState.SUSPENDED) {
            return current;
        }
        rejectDisabled(current);
        return replaceAndPublish(current.withState(FacilityState.SUSPENDED));
    }

    /**
     * Activates a facility: {@code SUSPENDED}/{@code RELOCATING} become
     * {@code ACTIVE} (no-op when already active; {@code DISABLED} is
     * rejected). Revision +1 exactly once on change.
     */
    public Facility activateFacility(FacilityId facilityId) {
        requireOwnerThread();
        Facility current = requireFacility(facilityId);
        if (current.state() == FacilityState.ACTIVE) {
            return current;
        }
        rejectDisabled(current);
        return replaceAndPublish(current.withState(FacilityState.ACTIVE));
    }

    /**
     * Relocates a facility onto a new FR-LAND parcel: the facility enters
     * {@code RELOCATING} with the new parcel in one commit (controlled
     * relocation, FR-INST-001-A §6.1). {@code DISABLED} is rejected. The
     * caller must have verified the new parcel exists and is unbound.
     * Revision +1 exactly once.
     */
    public Facility relocateFacility(FacilityId facilityId, ParcelId newParcelId) {
        requireOwnerThread();
        Objects.requireNonNull(newParcelId, "newParcelId");
        Facility current = requireFacility(facilityId);
        rejectDisabled(current);
        Facility relocated = current.relocateTo(newParcelId);
        return replaceAndPublish(relocated);
    }

    /**
     * Disables a facility (final state; idempotent when already disabled).
     * Revision +1 exactly once on change. All anchored contexts are
     * invalidated by the caller on revision change.
     */
    public Facility disableFacility(FacilityId facilityId) {
        requireOwnerThread();
        Facility current = requireFacility(facilityId);
        if (current.state() == FacilityState.DISABLED) {
            return current;
        }
        return replaceAndPublish(current.withState(FacilityState.DISABLED));
    }

    /**
     * Adds a zone to a facility: server-assigned immutable id, state ACTIVE,
     * revision 1, kind/region/capability set applied. One complete
     * replacement snapshot; committed only after the gate. The caller must
     * have verified facility existence/type/region and the capability/kind
     * match.
     */
    public Zone addZone(
            FacilityId facilityId,
            InstitutionType institutionType,
            ZoneKind kind,
            ZoneRegion region,
            Set<CapabilityClass> capabilitySet
    ) {
        requireOwnerThread();
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(institutionType, "institutionType");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(capabilitySet, "capabilitySet");
        if (zones.size() >= limits.maxZones()) {
            throw capacity("Zone count would exceed the budget of "
                    + limits.maxZones());
        }
        if (zoneCountOf(facilityId) >= limits.maxZonesPerFacility()) {
            throw capacity("Zone count of " + facilityId
                    + " would exceed the budget of "
                    + limits.maxZonesPerFacility());
        }
        requireStoreRevisionSpace();

        ZoneId zoneId = assignZoneId();
        Zone zone = new Zone(
                Zone.CURRENT_SCHEMA_VERSION,
                zoneId,
                facilityId,
                institutionType,
                kind,
                region,
                capabilitySet,
                ZoneState.ACTIVE,
                1
        );
        LinkedHashMap<ZoneId, Zone> next = new LinkedHashMap<>(zones);
        next.put(zoneId, zone);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                facilities,
                next
        ));
        return zone;
    }

    /**
     * Removes a zone from the directory. Revision +1 exactly once. Anchored
     * contexts are invalidated by the caller.
     */
    public Zone removeZone(ZoneId zoneId) {
        requireOwnerThread();
        Zone current = requireZone(zoneId);
        LinkedHashMap<ZoneId, Zone> next = new LinkedHashMap<>(zones);
        next.remove(zoneId);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                facilities,
                next
        ));
        return current;
    }

    /**
     * Resizes a zone onto a new region. Revision +1 exactly once on change.
     * The caller must have verified the region stays inside the facility
     * parcel and within the small-size budget.
     */
    public Zone resizeZone(ZoneId zoneId, ZoneRegion newRegion) {
        requireOwnerThread();
        Objects.requireNonNull(newRegion, "newRegion");
        Zone current = requireZone(zoneId);
        rejectDisabledZone(current);
        return replaceZoneAndPublish(current.withRegion(newRegion));
    }

    /**
     * Re-kinds a zone. Revision +1 exactly once on change. The caller must
     * have verified the capability set matches the new kind.
     */
    public Zone setZoneKind(ZoneId zoneId, ZoneKind newKind) {
        requireOwnerThread();
        Objects.requireNonNull(newKind, "newKind");
        Zone current = requireZone(zoneId);
        if (current.kind() == newKind) {
            return current;
        }
        rejectDisabledZone(current);
        return replaceZoneAndPublish(current.withKind(newKind));
    }

    /**
     * Suspends a zone: {@code ACTIVE} becomes {@code SUSPENDED} (no-op when
     * already suspended; {@code DISABLED} is rejected). Revision +1 exactly
     * once on change.
     */
    public Zone suspendZone(ZoneId zoneId) {
        requireOwnerThread();
        Zone current = requireZone(zoneId);
        if (current.state() == ZoneState.SUSPENDED) {
            return current;
        }
        if (current.state() == ZoneState.DISABLED) {
            throw invalidTransition(
                    "Cannot suspend a disabled zone " + zoneId
            );
        }
        return replaceZoneAndPublish(current.withState(ZoneState.SUSPENDED));
    }

    /**
     * Activates a zone: {@code SUSPENDED} becomes {@code ACTIVE} (no-op when
     * already active; {@code DISABLED} is rejected). Revision +1 exactly
     * once on change.
     */
    public Zone activateZone(ZoneId zoneId) {
        requireOwnerThread();
        Zone current = requireZone(zoneId);
        if (current.state() == ZoneState.ACTIVE) {
            return current;
        }
        if (current.state() == ZoneState.DISABLED) {
            throw invalidTransition(
                    "Cannot activate a disabled zone " + zoneId
            );
        }
        return replaceZoneAndPublish(current.withState(ZoneState.ACTIVE));
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private Facility replaceAndPublish(Facility updated) {
        LinkedHashMap<FacilityId, Facility> next = new LinkedHashMap<>(facilities);
        next.put(updated.facilityId(), updated);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                next,
                zones
        ));
        return updated;
    }

    private Zone replaceZoneAndPublish(Zone updated) {
        LinkedHashMap<ZoneId, Zone> next = new LinkedHashMap<>(zones);
        next.put(updated.zoneId(), updated);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                facilities,
                next
        ));
        return updated;
    }

    private void rejectDisabled(Facility facility) {
        if (facility.state() == FacilityState.DISABLED) {
            throw invalidTransition(
                    "Facility " + facility.facilityId()
                            + " is disabled and cannot be changed"
            );
        }
    }

    private void rejectDisabledZone(Zone zone) {
        if (zone.state() == ZoneState.DISABLED) {
            throw invalidTransition(
                    "Zone " + zone.zoneId() + " is disabled and cannot be changed"
            );
        }
    }

    private FacilityId assignFacilityId() {
        for (int attempt = 0; attempt < MAX_FACILITY_ID_ATTEMPTS; attempt++) {
            UUID candidate = facilityIdSource.get();
            if (candidate == null) {
                throw new IllegalStateException("FacilityId source returned null");
            }
            FacilityId facilityId = FacilityId.of(candidate);
            if (!facilities.containsKey(facilityId)) {
                return facilityId;
            }
        }
        throw new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                "Unable to allocate a fresh facility id after "
                        + MAX_FACILITY_ID_ATTEMPTS + " attempts"
        );
    }

    private ZoneId assignZoneId() {
        for (int attempt = 0; attempt < MAX_ZONE_ID_ATTEMPTS; attempt++) {
            UUID candidate = zoneIdSource.get();
            if (candidate == null) {
                throw new IllegalStateException("ZoneId source returned null");
            }
            ZoneId zoneId = ZoneId.of(candidate);
            if (!zones.containsKey(zoneId)) {
                return zoneId;
            }
        }
        throw new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                "Unable to allocate a fresh zone id after "
                        + MAX_ZONE_ID_ATTEMPTS + " attempts"
        );
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw capacity("Institution-access store revision space exhausted");
        }
    }

    private InstitutionAccessUnavailableException capacity(String message) {
        return new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                message
        );
    }

    private InstitutionAccessUnavailableException invalidTransition(String message) {
        return new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_INVALID_STATE_TRANSITION,
                message
        );
    }

    private void commitAndPublish(InstitutionAccessStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw capacity("Institution-access namespace would exceed the byte budget ("
                    + bytes + " > " + limits.maxTotalBytes() + ")");
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_STORE_FAILURE,
                    "Institution-access store rejected a commit: "
                            + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of institution-access failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(InstitutionAccessStoreSnapshot snapshot) {
        facilities.clear();
        facilities.putAll(snapshot.facilities());
        zones.clear();
        zones.putAll(snapshot.zones());
        storeRevision = snapshot.storeRevision();
    }

    private void enforceLoadedCapacity(InstitutionAccessStoreSnapshot snapshot) {
        if (snapshot.facilities().size() > limits.maxFacilities()) {
            throw capacity("Loaded facility count " + snapshot.facilities().size()
                    + " exceeds the budget of " + limits.maxFacilities());
        }
        if (snapshot.zones().size() > limits.maxZones()) {
            throw capacity("Loaded zone count " + snapshot.zones().size()
                    + " exceeds the budget of " + limits.maxZones());
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "InstitutionAccessRepository may only be accessed from "
                            + "its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerInstitutionAccessStore
            implements InstitutionAccessStore {
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
