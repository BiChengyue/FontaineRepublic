package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.Objects;

/**
 * Immutable authoritative request to register an institution facility
 * (FR-INST-002-A §3).
 *
 * @param institutionType the serving institution
 * @param parcelId        the FR-LAND parcel whose region is the facility
 *                        (consumed read-only through the land service; never
 *                        copied, never hard-coded)
 */
public record FacilityRegistrationRequest(
        InstitutionType institutionType,
        ParcelId parcelId
) {

    public FacilityRegistrationRequest {
        institutionType = Objects.requireNonNull(institutionType, "institutionType");
        parcelId = Objects.requireNonNull(parcelId, "parcelId");
    }
}
