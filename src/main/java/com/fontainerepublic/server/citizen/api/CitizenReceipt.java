package com.fontainerepublic.server.citizen.api;

import com.fontainerepublic.server.citizen.model.CitizenRecord;

/**
 * Outcome of an authoritative rank/status mutation (FR-CIT-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException}).
 * {@code applied} is {@code false} when the request was an idempotent no-op
 * (same rank/status as the current record) — in that case the record and
 * revisions are unchanged and nothing was committed.</p>
 *
 * @param kind      the mutated aspect (rank or status)
 * @param record    the resulting citizen record
 * @param applied   whether a committed mutation happened (false = idempotent no-op)
 * @param atMillis  server-assigned timestamp of the receipt
 */
public record CitizenReceipt(
        CitizenChangeKind kind,
        CitizenRecord record,
        boolean applied,
        long atMillis
) {
}
