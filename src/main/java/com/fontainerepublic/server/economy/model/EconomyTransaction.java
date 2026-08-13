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
 *
 * <p>Participant cardinality follows the transaction type (FR-ECO-001-A
 * §3.2/§3.3): a {@code TRANSFER} has both participants, an official
 * {@code DEPOSIT} has only {@code to} (the system/treasury is the source,
 * {@code from} is {@code null}), and an official {@code WITHDRAWAL} has only
 * {@code from} (the treasury is the sink, {@code to} is {@code null}).
 * Exactly one side may be {@code null} on a system transaction; both sides
 * are never {@code null}.</p>
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
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        type = Objects.requireNonNull(type, "type");
        switch (type) {
            case TRANSFER -> {
                requireParticipant(from, "from");
                requireParticipant(to, "to");
            }
            case DEPOSIT, ISSUE -> {
                requireParticipant(to, "to");
                if (from != null) {
                    throw new IllegalArgumentException(
                            "A " + type + " has no source participant (system to account)"
                    );
                }
            }
            case WITHDRAWAL, RECLAIM -> {
                requireParticipant(from, "from");
                if (to != null) {
                    throw new IllegalArgumentException(
                            "A " + type + " has no destination participant (account to system)"
                    );
                }
            }
        }
    }

    private static void requireParticipant(SubjectId participant, String field) {
        if (participant == null) {
            throw new IllegalArgumentException(
                    "A " + field + " participant is required for this transaction type"
            );
        }
    }
}
