package com.fontainerepublic.client.landrights;

import com.fontainerepublic.common.landrights.MyLandRightsRequestPacket;
import com.fontainerepublic.common.network.NetworkBootstrap;

/**
 * Client-side C2S sender of the my-usage-rights ledger entry (FR-LAND-002-A
 * §5.1, message ledger ID 27). Thin transport helper; the server re-runs every
 * authority rule. The client allocates a fresh positive request id for each
 * request and records it as pending so {@link ClientMyLandRightsCache} accepts
 * only the matching response (stale/lower responses are discarded). This class
 * lives in {@code client/} and is never loaded by a dedicated server.
 */
public final class ClientMyLandRightsSender {

    private ClientMyLandRightsSender() {
    }

    /**
     * Sends a my-usage-rights page request. A first page uses
     * {@code afterParcelId == null} and {@code expectedStoreRevision == 0}; a
     * next page carries the exclusive cursor and the returned store revision.
     */
    public static void requestPage(
            java.util.UUID afterParcelId,
            long expectedStoreRevision,
            int limit
    ) {
        int requestId = ClientMyLandRightsCache.instance().allocateRequestId();
        ClientMyLandRightsCache.instance().markPendingRequest(requestId);
        NetworkBootstrap.instance().sendToServer(new MyLandRightsRequestPacket(
                requestId,
                java.util.Optional.ofNullable(afterParcelId),
                expectedStoreRevision,
                limit
        ));
    }
}
