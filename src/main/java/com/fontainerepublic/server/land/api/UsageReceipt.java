package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.registry.model.OwnerReference;

/**
 * Outcome of an authoritative usage-right mutation (FR-LAND-001-A §4).
 *
 * <p>A receipt is returned only after the mutation was durably committed (a
 * rejected mutation throws
 * {@link com.fontainerepublic.server.land.persistence.LandUnavailableException}).
 * {@code applied} is {@code false} when the request was an idempotent no-op
 * (revoking a right the holder does not hold) — in that case the parcel and
 * revisions are unchanged and nothing was committed.</p>
 *
 * @param kind    the usage mutation
 * @param parcel  the resulting parcel
 * @param holder  the affected holder
 * @param applied whether a committed mutation happened (false = no-op)
 * @param atMillis server-assigned timestamp of the receipt
 */
public record UsageReceipt(
        UsageChangeKind kind,
        LandParcel parcel,
        OwnerReference holder,
        boolean applied,
        long atMillis
) {
}
