package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Vote;
import com.fontainerepublic.server.parliament.model.VoteChoice;

import java.util.Optional;

/**
 * Outcome of an authoritative vote open or vote cast (FR-PAR-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException}).
 * Opening is not idempotent (a proposal already in VOTING rejects a second
 * open); casting a second vote by the same citizen rejects fail-closed. The
 * applied flag therefore always reports {@code true} — it exists to keep the
 * receipt shape uniform with the other receipts. {@code choice} is present
 * only for a cast (the voter's choice); it is empty for an open.</p>
 *
 * @param vote      the resulting ballot record
 * @param applied   always true (a committed mutation happened)
 * @param atMillis  server-assigned timestamp of the receipt
 * @param choice    the voter's choice for a cast; empty for an open
 */
public record VoteReceipt(
        Vote vote,
        boolean applied,
        long atMillis,
        Optional<VoteChoice> choice
) {
}
