package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.Evidence;

/**
 * Outcome of an authoritative evidence mutation (submission or admission
 * ruling) (FR-JUS-001-A §3.2/§4).
 *
 * <p>A receipt is returned only after the mutation was durably committed;
 * {@code applied} is {@code true} for a committed change.</p>
 *
 * @param evidence the evidence after the mutation
 * @param applied  always {@code true} for a committed mutation
 * @param atMillis server-assigned timestamp of the receipt
 */
public record EvidenceReceipt(
        Evidence evidence,
        boolean applied,
        long atMillis
) {
}
