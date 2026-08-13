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
 *
 * <p>The {@code frozen} flag is set/unset only through the on-site official
 * Central-Bank surface (FR-ECO-002-A): a frozen account rejects every
 * balance-changing mutation (deposit, withdraw, transfer) until unfrozen.
 * The flag persists with the account and never changes on its own.</p>
 */
public record EconomyAccount(
        int schemaVersion,
        SubjectId subjectId,
        long balance,
        long accountRevision,
        long createdAt,
        long lastTransactionId,
        boolean frozen
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Structural balance cap (mirrors the store cap, FR-ECO-001-A §5.4). */
    public static final long MAX_BALANCE = Long.MAX_VALUE / 2;

    public EconomyAccount {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported account schema version: " + schemaVersion
            );
        }
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        if (balance < 0 || balance > MAX_BALANCE) {
            throw new IllegalArgumentException(
                    "Balance out of bounds: " + balance
            );
        }
        if (accountRevision <= 0) {
            throw new IllegalArgumentException(
                    "accountRevision must be positive"
            );
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
                newLastTransactionId,
                frozen
        );
    }

    /**
     * Replacement account with the freeze state flipped; revision +1 exactly
     * once. No balance or transaction reference changes (freezing is not a
     * monetary movement).
     */
    public EconomyAccount withFrozen(boolean newFrozen) {
        return new EconomyAccount(
                schemaVersion,
                subjectId,
                balance,
                accountRevision + 1,
                createdAt,
                lastTransactionId,
                newFrozen
        );
    }
}
