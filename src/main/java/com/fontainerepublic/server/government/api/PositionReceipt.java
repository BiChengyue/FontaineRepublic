package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.GovernmentPosition;

/**
 * Outcome of an authoritative position creation (FR-GOV-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.government.persistence.GovernmentUnavailableException}).</p>
 *
 * @param kind      the mutated aspect (position creation)
 * @param position  the resulting position
 * @param applied   whether a committed mutation happened
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record PositionReceipt(
        PositionChangeKind kind,
        GovernmentPosition position,
        boolean applied,
        long atMillis
) {
}
