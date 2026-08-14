package com.fontainerepublic.server.landclaim.api;

import java.util.UUID;

/**
 * Server-authoritative communicator land-claim service (FR-LAND-CLAIM-001-A
 * §3.2/§3.3).
 *
 * <p>Both the C2S handlers and the no-client commands call this very same
 * service so the entire fail-closed gate — online player, held communicator,
 * current dimension, loaded target, server block reach, valid coordinates,
 * exact-point coverage and full-region overlap — plus the single atomic
 * {@code LandService.createParcelWithUsage} durable commit, is shared and can
 * never be bypassed by any entry point. {@code dimension} is always a
 * canonical resource key supplied by the caller; the request dimension must
 * equal the acting player's current dimension.</p>
 */
public interface LandClaimService {

    /**
     * Bounded single-position read: whether the checked block is claimable
     * (no parcel covers it) or covered by an existing parcel whose summary is
     * returned. Applies the same online/communicator/dimension/loading/reach
     * gates and never mutates state.
     */
    InspectResult inspect(UUID actor, String dimension, int x, int y, int z);

    /**
     * Authoritative land claim at a block position: re-runs the full fail
     * closed gate, rejects an exact-point-covered position with
     * {@code CLAIM_ALREADY_OWNED} and a full-region overlap with
     * {@code CLAIM_OVERLAP}, then issues a single atomic
     * {@code createParcelWithUsage(..., 0)} durable commit granting the actor
     * a non-expiring usage right on a republic-owned parcel. Always returns a
     * {@link ClaimReceipt} (a stable bounded code, never throws for a
     * fail-open path).
     */
    ClaimReceipt claim(UUID actor, String dimension, int x, int y, int z);
}
