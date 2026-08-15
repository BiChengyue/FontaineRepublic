package com.fontainerepublic.server.landrights.service;

import com.fontainerepublic.common.item.FRItems;
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
 * the server-side Water Mirror gate (the communicator item, main or off
 * hand — independent of any {@code client/} class).
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
        return player.get().getMainHandItem().is(FRItems.COMMUNICATOR.get())
                || player.get().getOffhandItem().is(FRItems.COMMUNICATOR.get());
    }
}
