package com.fontainerepublic.server.economy.api;

import com.fontainerepublic.server.registry.model.SubjectId;

/**
 * Receipt of a successful ordinary transfer (FR-ECO-001-C §5.2).
 *
 * <p>The transaction id is returned only after the FR-CORE-002 durable gate
 * reported {@code COMMITTED}; a failed operation never fabricates or reserves
 * a visible transaction id. The receipt is visible to the participants only.
 * {@code memo} is the normalized display-only projection (may be
 * {@code null}).</p>
 */
public record TransferReceipt(
        long transactionId,
        long timestamp,
        SubjectId from,
        SubjectId to,
        long amount,
        String memo,
        boolean applied
) {

    public TransferReceipt {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Transaction id must be positive");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException(
                    "timestamp must be a positive epoch millisecond"
            );
        }
        from = java.util.Objects.requireNonNull(from, "from");
        to = java.util.Objects.requireNonNull(to, "to");
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
