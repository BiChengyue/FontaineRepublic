package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.Proposal;

import java.util.Optional;

/**
 * Outcome of an authoritative court-review advancement (FR-PAR-002-A §3/§6):
 * entering the supreme-court constitutionality review, the review result, and
 * the deadline extension.
 *
 * <p>This module only records the court-review stage and its deadlines — the
 * adjudication itself belongs to a later justice revision and is never
 * implemented here. A receipt is returned only after the mutation was
 * durably committed (a rejected mutation throws
 * {@link com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException}).
 * {@code applied} always reports {@code true}. {@code bill} carries the
 * resulting bill when the proposal has one.</p>
 *
 * @param proposal  the committed proposal record
 * @param bill      the committed bill record, when the proposal has one
 * @param applied   always true (a committed mutation happened)
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record CourtReceipt(
        Proposal proposal,
        Optional<Bill> bill,
        boolean applied,
        long atMillis
) {
}
