package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.LandParcel;

/**
 * Outcome of an authoritative parcel-level mutation (FR-LAND-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.land.persistence.LandUnavailableException}).
 * {@code applied} is {@code false} when the request was an idempotent no-op
 * (same zone/access as the current parcel) — in that case the parcel and
 * revisions are unchanged and nothing was committed.</p>
 *
 * @param kind     the mutated aspect
 * @param parcel   the resulting parcel
 * @param applied  whether a committed mutation happened (false = no-op)
 * @param atMillis server-assigned timestamp of the receipt
 */
public record LandReceipt(
        LandChangeKind kind,
        LandParcel parcel,
        boolean applied,
        long atMillis
) {
}
