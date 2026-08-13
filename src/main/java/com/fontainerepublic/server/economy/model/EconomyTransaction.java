package com.fontainerepublic.server.economy.model;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.Objects;

/**
 * Immutable operational transaction record (FR-ECO-001-A §3.2, aligned by
 * FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.4).
 *
 * <p>Participants are the stable {@link SubjectId}s, never game names. The
 * transaction id is assigned by the repository only for a successful commit
 * and is never reused; {@code amount} is always positive. The optional
 * {@code memo} (a.k.a. reason) is a normalized, bounded, display-only
 * projection; {@code null} means absent (distinct from an empty magic value,
 * FR-ECO-001-C §5.1).</p>
 */
public record EconomyTransaction(
        int schemaVersion,
        long transactionId,
        long timestamp,
        SubjectId from,
        SubjectId to,
        long amount,
        TransactionType type,
        String memo
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EconomyTransaction {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported transaction schema version: " + schemaVersion
            );
        }
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Transaction id must be positive");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException(
                    "timestamp must be a positive epoch millisecond"
            );
        }
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        type = Objects.requireNonNull(type, "type");
    }
}
