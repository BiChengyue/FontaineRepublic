package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.Office;

import java.util.Optional;

/**
 * Outcome of an authoritative appointment/dismissal (FR-GOV-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.government.persistence.GovernmentUnavailableException}).
 * {@code applied} is {@code false} when the request was an idempotent no-op
 * (dismissing an already-VACANT position) — in that case the position, office
 * and revisions are unchanged and nothing was committed.</p>
 *
 * @param kind      the mutated aspect (appointed or dismissed)
 * @param position  the resulting position
 * @param office    the resulting office record of the position, if any
 *                  (present on a committed mutation; a no-op carries the
 *                  current record, which may be absent for a never-appointed
 *                  position)
 * @param applied   whether a committed mutation happened (false = idempotent no-op)
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record AppointmentReceipt(
        AppointmentChangeKind kind,
        GovernmentPosition position,
        Optional<Office> office,
        boolean applied,
        long atMillis
) {

    public AppointmentReceipt {
        office = office == null ? Optional.empty() : office;
    }
}
