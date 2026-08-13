package com.fontainerepublic.server.land.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Objects;

/**
 * Thin Forge binding of build events to the land policy (FR-LAND-001-A §5).
 *
 * <p>Placement and break events are resolved at event time; any denial
 * cancels the event so the server never accepts unpermitted block changes.
 * All logic lives in {@link LandEventPolicy} (Forge-free and testable).</p>
 */
public final class BuildEventHandler {

    private final LandEventPolicy policy;

    public BuildEventHandler(LandEventPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!policy.allowBuild(
                player.getUUID(),
                dimension(event.getLevel()),
                event.getPos().getX(),
                event.getPos().getY(),
                event.getPos().getZ()
        )) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!policy.allowBreak(
                serverPlayer.getUUID(),
                dimension(event.getLevel()),
                event.getPos().getX(),
                event.getPos().getY(),
                event.getPos().getZ()
        )) {
            event.setCanceled(true);
        }
    }

    private static String dimension(LevelAccessor level) {
        // Block events always carry a concrete Level on the server thread.
        return ((Level) level).dimension().location().toString();
    }
}
