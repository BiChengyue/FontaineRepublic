package com.fontainerepublic.server.landrights.api;

import com.fontainerepublic.server.land.model.ParcelId;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative communicator my-usage-rights service (FR-LAND-002-A
 * §5.2): revalidates the online held-communicator gate and the self-only query,
 * then maps the outcome to a closed {@link MyLandRightsResponse}.
 *
 * <p>Both the C2S handler and any future entry point call this very same
 * service so the entire fail-closed gate — live online player, held registered
 * communicator, valid limit/cursor/revision and authoritative subject
 * resolution inside {@code LandService.myUsageRights} — is shared and can never
 * be bypassed. The actor identity is a UUID obtained only from the server
 * connection; there is no target/holder parameter.</p>
 */
public interface MyLandRightsService {

    /**
     * Handles one self-only my-usage-rights page request for the given online
     * authenticated {@code playerId}. Return value is always a closed
     * {@link MyLandRightsResponse}; never throws for a gate/authority failure.
     */
    MyLandRightsResponse request(
            UUID playerId,
            int requestId,
            Optional<ParcelId> afterParcelId,
            long expectedStoreRevision,
            int limit);
}
