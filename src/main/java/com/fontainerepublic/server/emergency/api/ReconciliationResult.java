package com.fontainerepublic.server.emergency.api;

import java.util.Objects;

/**
 * Outcome of one receipt-provider reconciliation (FR-EMG-001-A §10.4).
 *
 * @param providerId        provider identity
 * @param complete          whether the index is COMPLETE_THROUGH
 * @param highestSequence   highest imported receipt sequence
 * @param segmentDigest     digest of the last verified provider segment
 * @param failureCode       bounded failure code when incomplete ("" if none)
 */
public record ReconciliationResult(
        String providerId,
        boolean complete,
        long highestSequence,
        String segmentDigest,
        String failureCode
) {

    public ReconciliationResult {
        providerId = Objects.requireNonNull(providerId, "providerId");
        segmentDigest = Objects.requireNonNull(segmentDigest, "segmentDigest");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        if (highestSequence < 0) {
            throw new IllegalArgumentException("highestSequence must not be negative");
        }
    }
}
