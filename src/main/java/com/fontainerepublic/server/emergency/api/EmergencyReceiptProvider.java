package com.fontainerepublic.server.emergency.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only business receipt provider contract for reconciliation
 * (FR-EMG-001-A §10.4).
 *
 * <p>Each business module that contributes emergency actions registers an
 * immutable receipt-provider identity and a stable runtime resolver. When the
 * current business module is ACTIVE, the resolver returns a read-only
 * provider with watermark / page / verify operations. The provider owns
 * decoding and validation of its receipt schema and returns typed immutable
 * projections — never NBT, repositories, mutable collections, or business
 * models.</p>
 */
public interface EmergencyReceiptProvider {

    /** Immutable provider identity. */
    String providerId();

    /** Provider receipt-schema version. */
    String receiptSchema();

    /**
     * Current watermark: highest receipt sequence and segment digest the
     * provider has durably persisted.
     */
    EmergencyReceiptWatermarkResult watermark();

    /**
     * Bounded page of redacted receipt envelopes strictly after
     * {@code afterSequence}, ascending, respecting classification. Page count,
     * encoded bytes, and sequence range are bounded.
     */
    EmergencyReceiptPage readPage(long afterSequence, int limit);

    /** Bounded verification of one receipt envelope by action id + digest. */
    Optional<Boolean> verify(String actionId, String envelopeDigest);

    // ------------------------------------------------------------------

    /** Immutable watermark result of a receipt provider. */
    record EmergencyReceiptWatermarkResult(
            String providerId,
            String receiptSchema,
            long highestSequence,
            String segmentDigest
    ) {
        public EmergencyReceiptWatermarkResult {
            providerId = Objects.requireNonNull(providerId, "providerId");
            receiptSchema = Objects.requireNonNull(receiptSchema, "receiptSchema");
            segmentDigest = Objects.requireNonNull(segmentDigest, "segmentDigest");
            if (highestSequence < 0) {
                throw new IllegalArgumentException("highestSequence must not be negative");
            }
        }
    }

    /** Bounded page of redacted receipt envelopes. */
    record EmergencyReceiptPage(
            List<EmergencyReceiptEnvelope> envelopes,
            boolean hasMore
    ) {
        public EmergencyReceiptPage {
            envelopes = List.copyOf(envelopes);
        }
    }

    /** One redacted receipt envelope projection (typed immutable). */
    record EmergencyReceiptEnvelope(
            long sequence,
            String actionId,
            String envelopeDigest,
            String summary
    ) {
        public EmergencyReceiptEnvelope {
            actionId = Objects.requireNonNull(actionId, "actionId");
            envelopeDigest = Objects.requireNonNull(envelopeDigest, "envelopeDigest");
            summary = Objects.requireNonNull(summary, "summary");
            if (sequence <= 0) {
                throw new IllegalArgumentException("sequence must be positive");
            }
        }
    }
}
