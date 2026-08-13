package com.fontainerepublic.server.government.persistence;

/**
 * Implementation-design bounds of the government store (FR-GOV-001-A §3.4:
 * bounded record counts and bytes; concrete caps are an implementation
 * choice, values below are the design defaults).
 *
 * @param maxMinistries maximum live ministry records before creation fails
 *                      closed
 * @param maxPositions  maximum live position records before creation fails
 *                      closed
 * @param maxOffices    maximum live office records before appointment fails
 *                      closed
 * @param maxTotalBytes serialized (uncompressed) namespace byte budget;
 *                      commits beyond it fail closed
 */
public record GovernmentLimits(
        int maxMinistries,
        int maxPositions,
        int maxOffices,
        int maxTotalBytes
) {

    public static final GovernmentLimits DEFAULT =
            new GovernmentLimits(1_000, 10_000, 10_000, 8 * 1024 * 1024);

    public GovernmentLimits {
        if (maxMinistries <= 0) {
            throw new IllegalArgumentException("maxMinistries must be positive");
        }
        if (maxPositions <= 0) {
            throw new IllegalArgumentException("maxPositions must be positive");
        }
        if (maxOffices <= 0) {
            throw new IllegalArgumentException("maxOffices must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
