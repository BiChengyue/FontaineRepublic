package com.fontainerepublic.server.economy.model;

import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * One authoritative Economy emergency success receipt (FR-ECO-001-C §12/§13).
 *
 * <p>The receipt is committed in the same replacement snapshot as the
 * balances, supply, transaction, and pending notification of a successful
 * emergency {@code ISSUE}/{@code RECLAIM}; it is permanent evidence and is
 * never removed by ordinary operational-history pruning (FR-ECO-001-C §5.3).
 * It carries the shared FR-EMG attempt id, the Economy transaction id, the
 * action/version/provider identity, the target, the amount, the category, the
 * private reason, balance/supply/revision before and after, the server
 * timestamp, the canonical envelope digest, and a receipt-chain digest link
 * ({@code prevDigest -> selfDigest}) so deletion, reordering, or replacement
 * is detectable during reconciliation (FR-EMG-001-A §10.2).</p>
 *
 * <p>{@code sequence} is the monotonic receipt watermark of the Economy
 * receipt provider; it is never reused and never pruned.</p>
 */
public record EconomyEmergencyReceipt(
        int schemaVersion,
        long sequence,
        long attemptId,
        String actionId,
        String actionVersion,
        String providerIdentity,
        long transactionId,
        SubjectId target,
        long amount,
        String category,
        String reason,
        long balanceBefore,
        long balanceAfter,
        long supplyBefore,
        long supplyAfter,
        long accountRevisionBefore,
        long accountRevisionAfter,
        long storeRevisionBefore,
        long storeRevisionAfter,
        long at,
        String envelopeDigest,
        String prevDigest,
        String selfDigest
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EconomyEmergencyReceipt {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported emergency receipt schema version: " + schemaVersion
            );
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("Receipt sequence must be positive");
        }
        if (attemptId <= 0) {
            throw new IllegalArgumentException("Attempt id must be positive");
        }
        actionId = Objects.requireNonNull(actionId, "actionId");
        actionVersion = Objects.requireNonNull(actionVersion, "actionVersion");
        providerIdentity = Objects.requireNonNull(providerIdentity, "providerIdentity");
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Transaction id must be positive");
        }
        target = Objects.requireNonNull(target, "target");
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        category = Objects.requireNonNull(category, "category");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
        }
        if (balanceBefore < 0 || balanceAfter < 0
                || supplyBefore < 0 || supplyAfter < 0) {
            throw new IllegalArgumentException(
                    "Balance and supply projections must not be negative"
            );
        }
        if (accountRevisionBefore < 0 || accountRevisionAfter <= 0) {
            throw new IllegalArgumentException(
                    "Account revision before may be 0 only when the account "
                            + "did not exist (lazy creation); the after "
                            + "revision must be positive"
            );
        }
        if (storeRevisionBefore < 0 || storeRevisionAfter < 0) {
            throw new IllegalArgumentException(
                    "Store revisions must not be negative"
            );
        }
        if (at <= 0) {
            throw new IllegalArgumentException(
                    "at must be a positive epoch millisecond"
            );
        }
        envelopeDigest = Objects.requireNonNull(envelopeDigest, "envelopeDigest");
        prevDigest = Objects.requireNonNull(prevDigest, "prevDigest");
        selfDigest = Objects.requireNonNull(selfDigest, "selfDigest");
    }

    /**
     * Deterministic SHA-256 hex self-digest over the canonical fixed-order
     * encoding of every receipt field except {@code selfDigest}. The
     * {@code prevDigest} of the next receipt links to this value, so deletion,
     * reordering, or replacement is detectable during reconciliation
     * (FR-EMG-001-A 搂10.2).
     */
    public static String selfDigest(
            int schemaVersion,
            long sequence,
            long attemptId,
            String actionId,
            String actionVersion,
            String providerIdentity,
            long transactionId,
            SubjectId target,
            long amount,
            String category,
            String reason,
            long balanceBefore,
            long balanceAfter,
            long supplyBefore,
            long supplyAfter,
            long accountRevisionBefore,
            long accountRevisionAfter,
            long storeRevisionBefore,
            long storeRevisionAfter,
            long at,
            String envelopeDigest,
            String prevDigest
    ) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(schemaVersion).append('|')
                .append(sequence).append('|')
                .append(attemptId).append('|')
                .append(actionId).append('|')
                .append(actionVersion).append('|')
                .append(providerIdentity).append('|')
                .append(transactionId).append('|')
                .append(target).append('|')
                .append(amount).append('|')
                .append(category).append('|')
                .append(reason).append('|')
                .append(balanceBefore).append('|')
                .append(balanceAfter).append('|')
                .append(supplyBefore).append('|')
                .append(supplyAfter).append('|')
                .append(accountRevisionBefore).append('|')
                .append(accountRevisionAfter).append('|')
                .append(storeRevisionBefore).append('|')
                .append(storeRevisionAfter).append('|')
                .append(at).append('|')
                .append(envelopeDigest).append('|')
                .append(prevDigest);
        return EmergencyDigests.toHex(EmergencyDigests.sha256(
                canonical.toString().getBytes(StandardCharsets.UTF_8)
        ));
    }
}
