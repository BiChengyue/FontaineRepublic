package com.fontainerepublic.server.land.model;

/**
 * Immutable ownership of every land parcel (FR-LAND-001-A §2/§3.1).
 *
 * <p>Ownership is permanently {@code REPUBLIC}: the enum has exactly one value
 * and the land module exposes no ownership transfer, sale, lease, or auction
 * API (LandMarket is Beta). {@link LandParcel} enforces this constant at
 * construction and decode.</p>
 */
public enum LandOwnership {
    /** The Republic owns every parcel forever. */
    REPUBLIC
}
