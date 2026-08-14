package com.fontainerepublic.client.landclaim;

import com.fontainerepublic.common.landclaim.LandClaimPacket;
import com.fontainerepublic.common.landclaim.LandInspectPacket;
import com.fontainerepublic.common.network.NetworkBootstrap;

/**
 * Client-side C2S sender of the land-claim ledger (FR-LAND-CLAIM-001-A §4,
 * message ledger IDs 23, 25). Thin transport helpers; the server re-runs every
 * authority rule. This class lives in {@code client/} and is never loaded by a
 * dedicated server.
 */
public final class ClientLandClaimSender {

    private ClientLandClaimSender() {
    }

    /** Asks the server whether the block is claimable (and its parcel, if any). */
    public static void inspect(String dimension, int x, int y, int z) {
        NetworkBootstrap.instance().sendToServer(
                new LandInspectPacket(dimension, x, y, z)
        );
    }

    /** Asks the server to claim (create + grant usage on) the block. */
    public static void claim(String dimension, int x, int y, int z) {
        NetworkBootstrap.instance().sendToServer(
                new LandClaimPacket(dimension, x, y, z)
        );
    }
}
