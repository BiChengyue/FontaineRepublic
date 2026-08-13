package com.fontainerepublic.server.land.model;

/**
 * Land access policy of a parcel (FR-LAND-001-A §3.3).
 *
 * <p>Access is a descriptive policy input resolved against the holder/role at
 * event time by the
 * {@link com.fontainerepublic.server.land.api.PermissionResolver}; it never
 * grants technical permission by itself and is config-driven (no rank/OP
 * bypass).</p>
 */
public enum LandAccess {
    /** Open to qualified players per configuration. */
    PUBLIC,

    /** Restricted: requires a valid usage right per configuration. */
    RESTRICTED,

    /** Private: requires a valid usage right per configuration. */
    PRIVATE
}
