package com.fontainerepublic.server.institutionaccess.event;

import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Objects;

/**
 * Lifecycle event handlers that immediately invalidate a player's on-site
 * contexts (FR-INST-001-A §7.3, FR-INST-001-B §4): logout, death, and
 * dimension change. Moving beyond range is handled by the bounded
 * {@link PresenceMonitor}; server stop clears the whole runtime registry.
 */
public final class LifecycleEventHandlers {

    private final InstitutionAccessService service;

    public LifecycleEventHandlers(InstitutionAccessService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            service.invalidateOnLeave(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            service.invalidateOnLeave(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        service.invalidateOnLeave(event.getEntity().getUUID());
    }
}
