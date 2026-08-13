package com.fontainerepublic.server.institutionaccess.model;

import com.fontainerepublic.server.land.model.LandParcel;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable bounded 3D box of an institution zone (FR-INST-002-B §2).
 *
 * <p>The dimension is a canonical Minecraft resource location and the block
 * coordinates are inclusive block coordinates. The region is either the
 * facility's FR-LAND parcel region or an explicit bounded sub-region supplied
 * by the authoritative registration/resize request or by decoded persisted
 * state — the module never carries any hard-coded coordinate value. A zone
 * region must lie fully inside its facility's parcel region and stay within
 * the small-size budget (default 16×16×8, server-configurable).</p>
 */
public record ZoneRegion(
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    /** Canonical dimension keys are Minecraft resource locations. */
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    public ZoneRegion {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (!DIMENSION_PATTERN.matcher(dimension).matches()) {
            throw new IllegalArgumentException(
                    "Dimension must be a canonical resource location: " + dimension
            );
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "ZoneRegion min must not exceed max on any axis: ["
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

    /** Whether this region lies fully inside the given FR-LAND parcel. */
    public boolean inside(LandParcel parcel) {
        return dimension.equals(parcel.dimension())
                && parcel.region().contains(minX, minY, minZ)
                && parcel.region().contains(maxX, maxY, maxZ);
    }

    /** Inclusive extent on the x axis (number of blocks spanned). */
    public int sizeX() {
        return maxX - minX + 1;
    }

    /** Inclusive extent on the y axis (number of blocks spanned). */
    public int sizeY() {
        return maxY - minY + 1;
    }

    /** Inclusive extent on the z axis (number of blocks spanned). */
    public int sizeZ() {
        return maxZ - minZ + 1;
    }
}
