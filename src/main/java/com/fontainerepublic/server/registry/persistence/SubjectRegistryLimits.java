package com.fontainerepublic.server.registry.persistence;

/**
 * Implementation-design bounds of the subject registry (FR-ID-001-A §6.3:
 * concrete caps are an implementation choice; values below are the design
 * defaults).
 *
 * @param maxSubjects           maximum live subject records before allocation
 *                              fails closed
 * @param maxTotalBytes         serialized (uncompressed) namespace byte budget;
 *                              commits beyond it fail closed
 * @param maxAllocationAttempts bounded retry attempts for serial/SubjectId
 *                              allocation before explicit exhaustion
 */
public record SubjectRegistryLimits(
        int maxSubjects,
        int maxTotalBytes,
        int maxAllocationAttempts
) {

    public static final SubjectRegistryLimits DEFAULT =
            new SubjectRegistryLimits(10_000, 8 * 1024 * 1024, 64);

    public SubjectRegistryLimits {
        if (maxSubjects <= 0) {
            throw new IllegalArgumentException("maxSubjects must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
        if (maxAllocationAttempts <= 0) {
            throw new IllegalArgumentException("maxAllocationAttempts must be positive");
        }
    }
}
