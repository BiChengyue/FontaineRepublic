package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.Case;

/**
 * Outcome of an authoritative case mutation (filing, intake, acceptance,
 * advancement, review) (FR-JUS-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed;
 * {@code applied} is {@code true} for a committed change.</p>
 *
 * @param aCase    the case after the mutation
 * @param applied  always {@code true} for a committed mutation
 * @param atMillis server-assigned timestamp of the receipt
 */
public record CaseReceipt(
        Case aCase,
        boolean applied,
        long atMillis
) {
}
