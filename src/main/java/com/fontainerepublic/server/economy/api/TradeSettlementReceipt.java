package com.fontainerepublic.server.economy.api;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Receipt of one atomic multi-leg trade settlement (FR-TRADE-001-A §6.1).
 *
 * <p>The settlement runs inside a single replacement economy snapshot: each
 * paying player's offer moves to the counterparty and each paying player's
 * tax ( {@code floor(offer * taxRatePercent / 100)}, only on payers) moves to
 * the treasury. Total supply {@code = sum(accounts) + treasury} is conserved
 * exactly. The receipt's transaction ids are visible only after the
 * FR-CORE-002 durable gate reported {@code COMMITTED}; a failed settlement
 * publishes nothing. {@code transactionIds} is the ordered list of created
 * transactions (one per positive leg: offer legs as {@code TRANSFER}, tax
 * legs as {@code TAX}); it is empty for a pure item-only settlement.</p>
 */
public record TradeSettlementReceipt(
        long timestamp,
        SubjectId a,
        SubjectId b,
        long aOffered,
        long bOffered,
        long aTax,
        long bTax,
        List<Long> transactionIds,
        boolean applied
) {

    public TradeSettlementReceipt {
        if (timestamp <= 0) {
            throw new IllegalArgumentException(
                    "timestamp must be a positive epoch millisecond"
            );
        }
        a = Objects.requireNonNull(a, "a");
        b = Objects.requireNonNull(b, "b");
        if (a.equals(b)) {
            throw new IllegalArgumentException("Settlement parties must differ");
        }
        if (aOffered < 0 || bOffered < 0) {
            throw new IllegalArgumentException("Offers must not be negative");
        }
        if (aTax < 0 || bTax < 0) {
            throw new IllegalArgumentException("Taxes must not be negative");
        }
        Objects.requireNonNull(transactionIds, "transactionIds");
        List<Long> copy = new ArrayList<>(transactionIds.size());
        long previous = 0L;
        for (Long id : transactionIds) {
            long value = Objects.requireNonNull(id, "transaction id");
            if (value <= previous) {
                throw new IllegalArgumentException(
                        "Transaction ids must be strictly ascending"
                );
            }
            copy.add(value);
            previous = value;
        }
        transactionIds = List.copyOf(copy);
    }
}
