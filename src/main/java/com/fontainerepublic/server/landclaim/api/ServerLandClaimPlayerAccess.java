package com.fontainerepublic.server.landclaim.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Server player surface of the land-claim module (FR-LAND-CLAIM-001-A §3.3).
 *
 * <p>All access is server-authoritative and main-thread serialized: players
 * are resolved through the live server, the communicator gate compares the
 * held registry keys (main or off hand), the current dimension is read from
 * the player's world, and target loading / block-interaction reach are
 * computed server-side. The interface is deliberately free of any
 * {@code client/} class so a dedicated server never loads client code.</p>
 */
public interface ServerLandClaimPlayerAccess {

    /** The online player with the given UUID, or empty when offline. */
    Optional<ServerPlayer> onlinePlayer(UUID playerId);

    /** Whether the player is currently online (headless-testable gate). */
    boolean isOnline(UUID playerId);

    /** Server-authoritative: the player holds the communicator in hand. */
    boolean holdsCommunicator(UUID playerId);

    /** Canonical resource key of the player's current dimension. */
    String currentDimension(UUID playerId);

    /** Whether the block position is loaded in the player's current world. */
    boolean isBlockLoaded(UUID playerId, int x, int y, int z);

    /** Whether the block position is within the server-side block interaction
     *  distance of the player's position. */
    boolean withinBlockReach(UUID playerId, int x, int y, int z);

    /** Inclusive minimum Y of the player's current world (build floor). */
    int worldMinY(UUID playerId);

    /** Inclusive maximum Y of the player's current world (build ceiling). */
    int worldMaxY(UUID playerId);

    /** Server chat feedback to the player. */
    void message(UUID playerId, String text);
}
