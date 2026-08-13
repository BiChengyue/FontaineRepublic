package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.Proposal;

import java.util.Optional;

/**
 * Outcome of an authoritative water-god guardian action (FR-PAR-002-A §4):
 * guardian review approval / return / recusal / timeout advancement, and the
 * amendment constitutional consent.
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException}).
 * {@code applied} always reports {@code true} — it exists to keep the
 * receipt shape uniform. {@code bill} carries the resulting bill when the
 * proposal has one (legislation bills exist from passage; amendment bills
 * are born at constitutional consent).</p>
 *
 * @param proposal  the committed proposal record
 * @param bill      the committed bill record, when the proposal has one
 * @param applied   always true (a committed mutation happened)
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record GuardianReceipt(
        Proposal proposal,
        Optional<Bill> bill,
        boolean applied,
        long atMillis
) {
}
