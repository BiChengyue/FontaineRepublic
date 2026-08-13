package com.fontainerepublic.server.institutionaccess.event;

import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.service.DefaultInstitutionAccessService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded 1-second presence monitor for active on-site contexts
 * (FR-INST-001-B §4).
 *
 * <p>Every {@code presenceCheckIntervalTicks} server ticks the monitor walks
 * only the players that currently hold at least one active context — it never
 * scans all players. For each active context it re-checks online presence,
 * dimension, expiry, official idle/hard limits, and workflow range; any
 * violation invalidates the player's context immediately. Single-use
 * authorizations are never consumed here — only the final mutation boundary
 * consumes them (FR-INST-001-B §3/§4). Lifecycle events (logout, death,
 * dimension change) are handled by {@link LifecycleEventHandlers}.</p>
 */
public final class PresenceMonitor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DefaultInstitutionAccessService service;
    private final int checkIntervalTicks;
    private long lastCheckTick;

    public PresenceMonitor(
            DefaultInstitutionAccessService service,
            int checkIntervalTicks
    ) {
        this.service = Objects.requireNonNull(service, "service");
        if (checkIntervalTicks <= 0) {
            throw new IllegalArgumentException("checkIntervalTicks must be positive");
        }
        this.checkIntervalTicks = checkIntervalTicks;
    }

    /**
     * Forge server-tick entry point. Only players with active contexts are
     * inspected; a player offline at the tick boundary has every context
     * invalidated (logout is also handled by the lifecycle event).
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long tick = server.getTickCount();
        if (tick - lastCheckTick < checkIntervalTicks) {
            return;
        }
        lastCheckTick = tick;
        try {
            checkPresence(server);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[InstitutionAccess] Presence check failed; contexts kept: {}",
                    failure.getMessage()
            );
        }
    }

    private void checkPresence(MinecraftServer server) {
        Set<UUID> players = service.playersWithActiveContexts();
        if (players.isEmpty()) {
            return;
        }
        for (UUID playerId : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                service.invalidateOnLeave(playerId);
                continue;
            }
            String dimension = player.level().dimension().location().toString();
            int x = (int) Math.floor(player.position().x);
            int y = (int) Math.floor(player.position().y);
            int z = (int) Math.floor(player.position().z);
            for (OnSiteContext context : service.activeContextsOf(playerId)) {
                service.evaluatePresence(
                        context,
                        dimension,
                        x,
                        y,
                        z,
                        System.currentTimeMillis()
                );
            }
        }
    }
}
