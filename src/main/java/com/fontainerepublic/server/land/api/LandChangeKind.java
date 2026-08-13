package com.fontainerepublic.server.land.api;

/**
 * The aspect a {@link LandReceipt} reports on.
 */
public enum LandChangeKind {
    /** A parcel was created ({@code LandService#createParcel}). */
    PARCEL_CREATED,

    /** The zone type was replaced ({@code LandService#setZoneType}). */
    ZONE_TYPE,

    /** The access policy was replaced ({@code LandService#setAccess}). */
    ACCESS
}
