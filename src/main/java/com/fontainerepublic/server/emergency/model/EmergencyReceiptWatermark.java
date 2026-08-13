package com.fontainerepublic.server.emergency.model;

import java.util.Objects;

/**
 * Per-provider receipt-index watermark of the shared emergency derived index
 * (FR-EMG-001-A §10.4).
 *
 * @param providerId        stable receipt-provider identity
 * @param receiptSchema     provider receipt schema version
 * @param highestSequence   highest imported receipt sequence
 * @param segmentDigest     digest of the last verified provider segment
 * @param status            COMPLETE_THROUGH / INCOMPLETE /
 *                          TAMPER_OR_CORRUPTION_DETECTED
 */
public record EmergencyReceiptWatermark(
        String providerId,
        String receiptSchema,
        long highestSequence,
        String segmentDigest,
        EmergencyReconciliationStatus status
) {

    public EmergencyReceiptWatermark {
        providerId = Objects.requireNonNull(providerId, "providerId");
        receiptSchema = Objects.requireNonNull(receiptSchema, "receiptSchema");
        segmentDigest = Objects.requireNonNull(segmentDigest, "segmentDigest");
        if (highestSequence < 0) {
            throw new IllegalArgumentException("highestSequence must not be negative");
        }
        status = Objects.requireNonNull(status, "status");
    }

    /** Fresh empty watermark for a provider that has never been reconciled. */
    public static EmergencyReceiptWatermark empty(String providerId, String receiptSchema) {
        return new EmergencyReceiptWatermark(providerId, receiptSchema, 0L, "", 
                EmergencyReconciliationStatus.INCOMPLETE);
    }
}
