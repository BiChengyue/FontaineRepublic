package com.fontainerepublic.server.emergency.persistence;

/**
 * Implementation-design bounds of the shared emergency namespace
 * (FR-EMG-001-A §10.2: bounded append-only segments; implementation task §3.2).
 *
 * @param maxRecordsPerSegment journal records per segment before a new
 *                             chained segment rolls over
 * @param maxTotalBytes        serialized (uncompressed) namespace byte budget;
 *                             writes beyond it fail closed with
 *                             {@link EmergencyUnavailableException#CODE_CAPACITY_EXCEEDED}
 * @param maxAttempts          hard cap on journal records; beyond it emergency
 *                             mutations fail closed until a reviewed archival
 *                             handoff exists
 */
public record EmergencyLimits(
        int maxRecordsPerSegment,
        int maxTotalBytes,
        int maxAttempts
) {
    public static final EmergencyLimits DEFAULT =
            new EmergencyLimits(256, 4 * 1024 * 1024, 100_000);

    public EmergencyLimits {
        if (maxRecordsPerSegment <= 0) {
            throw new IllegalArgumentException("maxRecordsPerSegment must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }
}
