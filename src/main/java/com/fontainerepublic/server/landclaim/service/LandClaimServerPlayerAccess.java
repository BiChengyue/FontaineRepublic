package com.fontainerepublic.server.landclaim.service;

import com.fontainerepublic.common.item.CommunicatorAuthenticator;
import com.fontainerepublic.server.communicator.CommunicatorAuthority;
import com.fontainerepublic.server.landclaim.api.ServerLandClaimPlayerAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production server player surface of the land-claim module
 * (FR-LAND-CLAIM-001-A §3.3): resolves players through the live server,
 * enforces the server-side communicator gate by registry key (main or off
 * hand — independent of any {@code client/} class), reads the player's current
 * dimension, and computes block loading and the server-side block interaction
 * distance from the player's position.
 */
public final class LandClaimServerPlayerAccess implements ServerLandClaimPlayerAccess {

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

    @Override
    public String currentDimension(UUID playerId) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return "";
        }
        ResourceKey<Level> key = player.get().level().dimension();
        return key.location().toString();
    }

    @Override
    public boolean isBlockLoaded(UUID playerId, int x, int y, int z) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return false;
        }
        if (!(player.get().level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return serverLevel.hasChunkAt(net.minecraft.core.BlockPos.containing(x, y, z));
    }

    @Override
    public boolean withinBlockReach(UUID playerId, int x, int y, int z) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return false;
        }
        Vec3 eye = player.get().getEyePosition();
        double reach = player.get().getBlockReach();
        Vec3 target = new Vec3(
                x + 0.5D,
                y + 0.5D,
                z + 0.5D
        );
        return eye.distanceToSqr(target) <= reach * reach;
    }

    // Fallback world build-height bounds used only when a live player level is
    // unavailable; production paths always read the actual level. The values
    // mirror the vanilla overworld's default build range [ -64, 319 ].
    private static final int FALLBACK_MIN_Y = -64;
    private static final int FALLBACK_MAX_Y = 319;

    @Override
    public int worldMinY(UUID playerId) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty() || !(player.get().level() instanceof ServerLevel serverLevel)) {
            return FALLBACK_MIN_Y;
        }
        return serverLevel.getMinBuildHeight();
    }

    @Override
    public int worldMaxY(UUID playerId) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty() || !(player.get().level() instanceof ServerLevel serverLevel)) {
            return FALLBACK_MAX_Y;
        }
        // Inclusive top of the build range: getMaxBuildHeight() is exclusive.
        return serverLevel.getMaxBuildHeight() - 1;
    }

    @Override
    public void message(UUID playerId, String text) {
        Objects.requireNonNull(text, "text");
        onlinePlayer(playerId).ifPresent(player -> player.displayClientMessage(
                Component.literal(text),
                false
        ));
    }
}
