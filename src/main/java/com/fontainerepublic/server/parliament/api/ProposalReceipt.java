package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Proposal;

/**
 * Outcome of an authoritative proposal submission (FR-PAR-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException}).
 * Submission is never an idempotent no-op: every submission creates a new
 * proposal record with a fresh sequence number.</p>
 *
 * @param proposal  the committed proposal record
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record ProposalReceipt(
        Proposal proposal,
        long atMillis
) {
}
