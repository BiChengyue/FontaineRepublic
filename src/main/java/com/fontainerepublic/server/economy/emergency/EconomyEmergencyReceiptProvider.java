package com.fontainerepublic.server.economy.emergency;

import com.fontainerepublic.server.economy.model.EconomyEmergencyReceipt;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.emergency.api.EmergencyReceiptProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only Economy receipt provider for FR-EMG reconciliation
 * (FR-ECO-001-C 搂13, FR-EMG-001-A 搂10.4).
 *
 * <p>Exposes typed immutable redacted projections only 鈥?never NBT, raw
 * repositories, or business models. Amount, balance, and supply fields are
 * {@code SECRET_DIGEST_ONLY} and are therefore omitted from the projection
 * summary; the envelope digest links each receipt to the shared attempt
 * journal's request digest.</p>
 */
public final class EconomyEmergencyReceiptProvider implements EmergencyReceiptProvider {

    /** Immutable provider identity (same as the action provider). */
    public static final String PROVIDER_ID = "economy/emergency";

    /** Receipt-schema version. */
    public static final String RECEIPT_SCHEMA = "1";

    private final EconomyRepository repository;

    public EconomyEmergencyReceiptProvider(EconomyRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String receiptSchema() {
        return RECEIPT_SCHEMA;
    }

    @Override
    public EmergencyReceiptWatermarkResult watermark() {
        long highest = repository.highestReceiptSequence();
        String segmentDigest = EmergencyReceiptDigests.ZERO_HEX;
        if (highest > 0L) {
            List<EconomyEmergencyReceipt> tail =
                    repository.receiptsAfter(highest - 1L, 1);
            if (!tail.isEmpty()) {
                segmentDigest = tail.get(tail.size() - 1).selfDigest();
            }
        }
        return new EmergencyReceiptWatermarkResult(
                PROVIDER_ID,
                RECEIPT_SCHEMA,
                highest,
                segmentDigest
        );
    }

    @Override
    public EmergencyReceiptPage readPage(long afterSequence, int limit) {
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page limit must be within 1.." + MAX_PAGE_SIZE
            );
        }
        List<EconomyEmergencyReceipt> receipts =
                repository.receiptsAfter(afterSequence, limit);
        boolean hasMore = receipts.size() == limit
                && !repository.receiptsAfter(
                receipts.get(receipts.size() - 1).sequence(), 1
        ).isEmpty();
        return new EmergencyReceiptPage(
                receipts.stream()
                        .map(receipt -> new EmergencyReceiptEnvelope(
                                receipt.sequence(),
                                receipt.actionId(),
                                receipt.envelopeDigest(),
                                safeSummary(receipt)
                        ))
                        .toList(),
                hasMore
        );
    }

    @Override
    public Optional<Boolean> verify(String actionId, String envelopeDigest) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(envelopeDigest, "envelopeDigest");
        return repository.findReceipt(actionId, envelopeDigest)
                .map(receipt -> {
                    String expectedSelf = EmergencyReceiptDigests.selfDigestOf(receipt);
                    if (!expectedSelf.equals(receipt.selfDigest())) {
                        return false;
                    }
                    long sequence = receipt.sequence();
                    if (sequence == 1L) {
                        return EmergencyReceiptDigests.ZERO_HEX
                                .equals(receipt.prevDigest());
                    }
                    List<EconomyEmergencyReceipt> previous =
                            repository.receiptsAfter(sequence - 2L, 1);
                    return !previous.isEmpty()
                            && previous.get(previous.size() - 1).selfDigest()
                            .equals(receipt.prevDigest());
                });
    }

    private static String safeSummary(EconomyEmergencyReceipt receipt) {
        // Amount/balance/supply are SECRET_DIGEST_ONLY; only safe fields
        // (action, category, attempt link) appear in projections.
        return receipt.actionId()
                + " category=" + receipt.category()
                + " attempt=" + receipt.attemptId()
                + " transaction=" + receipt.transactionId();
    }

    /** Bounded page size for reconciliation reads. */
    public static final int MAX_PAGE_SIZE = 500;

    private static final class EmergencyReceiptDigests {
        static final String ZERO_HEX =
                "0000000000000000000000000000000000000000000000000000000000000000";

        private EmergencyReceiptDigests() {
        }

        static String selfDigestOf(EconomyEmergencyReceipt receipt) {
            return EconomyEmergencyReceipt.selfDigest(
                    receipt.schemaVersion(),
                    receipt.sequence(),
                    receipt.attemptId(),
                    receipt.actionId(),
                    receipt.actionVersion(),
                    receipt.providerIdentity(),
                    receipt.transactionId(),
                    receipt.target(),
                    receipt.amount(),
                    receipt.category(),
                    receipt.reason(),
                    receipt.balanceBefore(),
                    receipt.balanceAfter(),
                    receipt.supplyBefore(),
                    receipt.supplyAfter(),
                    receipt.accountRevisionBefore(),
                    receipt.accountRevisionAfter(),
                    receipt.storeRevisionBefore(),
                    receipt.storeRevisionAfter(),
                    receipt.at(),
                    receipt.envelopeDigest(),
                    receipt.prevDigest()
            );
        }
    }
}
