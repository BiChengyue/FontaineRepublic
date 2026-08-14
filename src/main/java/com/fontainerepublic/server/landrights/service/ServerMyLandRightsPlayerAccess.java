package com.fontainerepublic.server.landrights.service;

import com.fontainerepublic.common.item.CommunicatorAuthenticator;
import com.fontainerepublic.server.communicator.CommunicatorAuthority;
import com.fontainerepublic.server.landrights.api.MyLandRightsServerPlayerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production server player surface of the my-usage-rights transport
 * (FR-LAND-002-A §5.2): resolves players through the live server and enforces
 * the server-side Water Mirror gate (a signed vanilla clock carrier, main or
 * off hand — independent of any {@code client/} class).
 */
public final class ServerMyLandRightsPlayerAccess
        implements MyLandRightsServerPlayerAccess {

    @Override
    public Optional<ServerPlayer> onlinePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(playerId));
    }

    @Override
    public boolean isOnline(UUID playerId) {
        return onlinePlayer(playerId).isPresent();
    }

    @Override
    public boolean holdsCommunicator(UUID playerId) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return false;
        }
        CommunicatorAuthenticator authenticator = CommunicatorAuthority.authenticator();
        if (authenticator == null) {
            return false;
        }
        return authenticator.authenticate(player.get().getMainHandItem(), playerId)
                || authenticator.authenticate(player.get().getOffhandItem(), playerId);
    }
}
