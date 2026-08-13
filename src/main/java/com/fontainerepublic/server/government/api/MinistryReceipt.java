package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.Ministry;

/**
 * Outcome of an authoritative ministry creation (FR-GOV-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.government.persistence.GovernmentUnavailableException}).</p>
 *
 * @param kind      the mutated aspect (ministry creation)
 * @param ministry  the resulting ministry
 * @param applied   whether a committed mutation happened
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record MinistryReceipt(
        MinistryChangeKind kind,
        Ministry ministry,
        boolean applied,
        long atMillis
) {
}
