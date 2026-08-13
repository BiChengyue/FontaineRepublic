package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Bill;

import java.util.Optional;

/**
 * Outcome of an authoritative ballot close / state advance (FR-PAR-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException}).
 * Closing an already-closed ballot is an idempotent no-op:
 * {@code applied == false} means the ballot was already closed and nothing
 * was committed; {@code bill} carries the existing bill (or is empty for a
 * rejected ballot) in that case.</p>
 *
 * @param bill      the resulting bill, empty when the ballot was rejected
 * @param passed    whether the ballot reached its frozen-roster threshold
 * @param applied   whether a committed mutation happened (false = idempotent no-op)
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record BillReceipt(
        Optional<Bill> bill,
        boolean passed,
        boolean applied,
        long atMillis
) {
}
