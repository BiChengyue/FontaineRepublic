package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.ZoneType;

import java.util.Objects;

/**
 * Authoritative create-parcel request (FR-LAND-001-A §4).
 *
 * <p>The parcel is created republic-owned with the given planning
 * designation and access policy and no usage rights; the server assigns the
 * immutable {@link ParcelId}. Regions come from the caller — the land module
 * has no hard-coded coordinates.</p>
 *
 * @param dimension canonical world dimension key (resource location)
 * @param region    validated inclusive block region
 * @param zoneType  planning designation
 * @param access    access policy (defaults to {@code PUBLIC})
 */
public record CreateParcelRequest(
        String dimension,
        ParcelRegion region,
        ZoneType zoneType,
        LandAccess access
) {

    public CreateParcelRequest {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(zoneType, "zoneType");
        Objects.requireNonNull(access, "access");
    }

    /** Convenience constructor defaulting the access policy to PUBLIC. */
    public CreateParcelRequest(String dimension, ParcelRegion region, ZoneType zoneType) {
        this(dimension, region, zoneType, LandAccess.PUBLIC);
    }
}
