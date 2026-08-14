package com.fontainerepublic.server.land.model;

import java.util.Objects;

/**
 * Immutable axis-aligned block region of a land parcel (FR-LAND-001-A §3.1).
 *
 * <p>Coordinates are inclusive block coordinates supplied by the caller; the
 * region is validated (min &le; max on every axis) but never carries any
 * hard-coded value. The land module has no built-in coordinates — every region
 * originates from an authoritative request or from decoded persisted state.</p>
 */
public record ParcelRegion(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public ParcelRegion {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "ParcelRegion min must not exceed max on any axis: ["
                            + minX + "," + minY + "," + minZ + "]..["
                            + maxX + "," + maxY + "," + maxZ + "]"
            );
        }
    }

    /** Whether the given block coordinate is inside this inclusive region. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Whether this inclusive region shares any block volume with {@code other}
     * (axis-aligned overlap on every axis).
     */
    public boolean intersects(ParcelRegion other) {
        Objects.requireNonNull(other, "other");
        return this.maxX >= other.minX && this.minX <= other.maxX
                && this.maxY >= other.minY && this.minY <= other.maxY
                && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
    }
}
