package com.fontainerepublic.server.economy.model;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.Objects;

/**
 * Immutable personal Economy account (FR-ECO-001-A §3.1, keyed by
 * {@link SubjectId} per FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.1).
 *
 * <p>A zero-balance account is created lazily and idempotently; the account
 * key is the natural-person {@link SubjectId}, never a game name or UUID.
 * Invariants: non-negative balance, positive revision, immutable
 * {@code createdAt}, and {@code lastTransactionId} referencing the most
 * recent transaction (0 when none).</p>
 */
public record EconomyAccount(
        int schemaVersion,
        SubjectId subjectId,
        long balance,
        long accountRevision,
        long createdAt,
        long lastTransactionId
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EconomyAccount {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported account schema version: " + schemaVersion
            );
        }
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        if (balance < 0) {
            throw new IllegalArgumentException("Balance must not be negative");
        }
        if (accountRevision <= 0) {
            throw new IllegalArgumentException("Account revision must be positive");
        }
        if (createdAt <= 0) {
            throw new IllegalArgumentException(
                    "createdAt must be a positive epoch millisecond"
            );
        }
        if (lastTransactionId < 0) {
            throw new IllegalArgumentException(
                    "lastTransactionId must not be negative"
            );
        }
    }

    /** Replacement account after a debit/credit; revision +1 exactly once. */
    public EconomyAccount withBalance(long newBalance, long newLastTransactionId) {
        return new EconomyAccount(
                schemaVersion,
                subjectId,
                newBalance,
                accountRevision + 1,
                createdAt,
                newLastTransactionId
        );
    }
}
