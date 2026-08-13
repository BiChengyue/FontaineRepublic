package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.CaseState;
import com.fontainerepublic.server.justice.model.Verdict;

/**
 * Outcome of an authoritative verdict issue (FR-JUS-001-A §3.3/§4).
 *
 * <p>A receipt is returned only after the mutation was durably committed;
 * {@code applied} is always {@code true} for a committed issue and
 * {@code verdict} always carries the immutable binding record. The case
 * enters {@link CaseState#VERDICTED} for a guilty/not-guilty verdict or
 * {@link CaseState#REJECTED} for a dismissal.</p>
 *
 * @param verdict      the immutable verdict record
 * @param outcomeState the case state after the issue (VERDICTED or REJECTED)
 * @param applied      always {@code true} for a committed issue
 * @param atMillis     server-assigned timestamp of the receipt
 */
public record VerdictReceipt(
        Verdict verdict,
        CaseState outcomeState,
        boolean applied,
        long atMillis
) {
}
