package com.fontainerepublic.server.institutionaccess.model;

import java.util.Objects;

/**
 * Immutable, value-style authoritative institution facility (FR-INST-002-A
 * §3).
 *
 * <p>{@code facilityId} and {@code parcelId} are server-assigned and
 * immutable; the facility region is never copied — it is the FR-LAND parcel
 * referenced by {@code parcelId} and is resolved through the land service at
 * runtime (no duplicated spatial data, no hard-coded coordinates). Every
 * committed mutation replaces the facility and increments
 * {@code facilityRevision} exactly once; contexts issued before a revision
 * change are invalidated by revision binding (FR-INST-001-A §7.3).</p>
 */
public record Facility(
        int schemaVersion,
        FacilityId facilityId,
        InstitutionType institutionType,
        com.fontainerepublic.server.land.model.ParcelId parcelId,
        FacilityState state,
        long facilityRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Facility {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported facility schema version: " + schemaVersion
            );
        }
        facilityId = Objects.requireNonNull(facilityId, "facilityId");
        institutionType = Objects.requireNonNull(institutionType, "institutionType");
        parcelId = Objects.requireNonNull(parcelId, "parcelId");
        state = Objects.requireNonNull(state, "state");
        if (facilityRevision <= 0) {
            throw new IllegalArgumentException("facilityRevision must be positive");
        }
    }

    /** Replacement facility with a new state: revision incremented once. */
    public Facility withState(FacilityState newState) {
        Objects.requireNonNull(newState, "newState");
        return new Facility(
                schemaVersion,
                facilityId,
                institutionType,
                parcelId,
                newState,
                facilityRevision + 1
        );
    }

    /**
     * Replacement facility bound to a new parcel during controlled
     * relocation: revision incremented once.
     */
    public Facility withParcel(com.fontainerepublic.server.land.model.ParcelId newParcelId) {
        Objects.requireNonNull(newParcelId, "newParcelId");
        return new Facility(
                schemaVersion,
                facilityId,
                institutionType,
                newParcelId,
                state,
                facilityRevision + 1
        );
    }

    /**
     * Atomic controlled relocation: the facility enters
     * {@code RELOCATING} bound to the new parcel in one replacement and the
     * revision increments exactly once (FR-INST-001-A §6.1).
     */
    public Facility relocateTo(com.fontainerepublic.server.land.model.ParcelId newParcelId) {
        Objects.requireNonNull(newParcelId, "newParcelId");
        return new Facility(
                schemaVersion,
                facilityId,
                institutionType,
                newParcelId,
                FacilityState.RELOCATING,
                facilityRevision + 1
        );
    }
}
