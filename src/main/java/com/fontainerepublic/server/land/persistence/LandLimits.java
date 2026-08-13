package com.fontainerepublic.server.land.persistence;

/**
 * Implementation-design bounds of the land store (FR-LAND-001-A §3.5: bounded
 * parcels, rights per parcel, reports, description length, and bytes; concrete
 * caps are an implementation choice, values below are the design defaults).
 *
 * @param maxParcels             maximum live parcels before creation fails
 *                               closed
 * @param maxUsageRightsPerParcel maximum usage rights per parcel before a
 *                               grant fails closed
 * @param maxReports             maximum violation reports before creation
 *                               fails closed
 * @param maxDescriptionChars    maximum violation description length
 * @param maxTotalBytes          serialized (uncompressed) namespace byte
 *                               budget; commits beyond it fail closed
 */
public record LandLimits(
        int maxParcels,
        int maxUsageRightsPerParcel,
        int maxReports,
        int maxDescriptionChars,
        int maxTotalBytes
) {

    public static final LandLimits DEFAULT =
            new LandLimits(1_000, 64, 1_000, 500, 8 * 1024 * 1024);

    public LandLimits {
        if (maxParcels <= 0) {
            throw new IllegalArgumentException("maxParcels must be positive");
        }
        if (maxUsageRightsPerParcel <= 0) {
            throw new IllegalArgumentException(
                    "maxUsageRightsPerParcel must be positive"
            );
        }
        if (maxReports <= 0) {
            throw new IllegalArgumentException("maxReports must be positive");
        }
        if (maxDescriptionChars <= 0) {
            throw new IllegalArgumentException(
                    "maxDescriptionChars must be positive"
            );
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
