package com.fontainerepublic.server.landrights.service;

import com.fontainerepublic.common.item.FRItemIds;
import com.fontainerepublic.server.landrights.api.MyLandRightsServerPlayerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production server player surface of the my-usage-rights transport
 * (FR-LAND-002-A §5.2): resolves players through the live server and enforces
 * the server-side communicator gate by registry key (main or off hand —
 * independent of any {@code client/} class).
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
        return isCommunicator(player.get().getMainHandItem())
                || isCommunicator(player.get().getOffhandItem());
    }

    /** Registry-key predicate for the communicator item (main/off hand). */
    private static boolean isCommunicator(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && FRItemIds.ITEM_REGISTRY_NAME.equals(key.toString());
    }
}
