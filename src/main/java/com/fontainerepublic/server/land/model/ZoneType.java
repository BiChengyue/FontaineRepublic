package com.fontainerepublic.server.land.model;

/**
 * Planning designation of a land parcel (FR-LAND-001-A §3.3).
 *
 * <p>Zone types are descriptive policy inputs for the
 * {@link com.fontainerepublic.server.land.api.PermissionResolver}; they never
 * grant technical permission by themselves and never imply automatic
 * compliance judgment (that is always human).</p>
 */
public enum ZoneType {
    RESIDENTIAL,
    COMMERCIAL,
    PUBLIC,
    GOVERNMENT,
    AGRICULTURAL,
    PROTECTED,
    OTHER
}
