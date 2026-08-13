package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Referendum;

/**
 * Outcome of an authoritative referendum action (FR-PAR-002-A §5): open,
 * cast, or close.
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException}).
 * {@code applied} always reports {@code true}. {@code passed} reports the
 * referendum outcome on close (participation 2/3 of the frozen roster and
 * approval 2/3 of the effective votes); it is {@code false} for open/cast.</p>
 *
 * @param referendum  the resulting referendum record
 * @param applied     always true (a committed mutation happened)
 * @param passed      whether the referendum passed its thresholds (close only)
 * @param atMillis    server-assigned timestamp of the receipt
 */
public record ReferendumReceipt(
        Referendum referendum,
        boolean applied,
        boolean passed,
        long atMillis
) {
}
