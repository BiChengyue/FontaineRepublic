package com.fontainerepublic.server.landrights.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Server player surface of the my-usage-rights transport (FR-LAND-002-A §5.2).
 *
 * <p>All access is server-authoritative and main-thread serialized: the online
 * player is resolved through the live server and the communicator gate compares
 * the held registry key (main or off hand). Deliberately free of any
 * {@code client/} class so a dedicated server never loads client code.</p>
 */
public interface MyLandRightsServerPlayerAccess {

    /** The online player with the given id, or empty when offline. */
    Optional<ServerPlayer> onlinePlayer(UUID playerId);

    /** Whether the player is currently online (headless-testable gate). */
    boolean isOnline(UUID playerId);

    /** Server-authoritative: the player holds the registered communicator. */
    boolean holdsCommunicator(UUID playerId);
}
