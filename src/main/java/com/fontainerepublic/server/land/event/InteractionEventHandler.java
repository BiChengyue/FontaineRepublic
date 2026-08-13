package com.fontainerepublic.server.land.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Objects;

/**
 * Thin Forge binding of interaction events to the land policy
 * (FR-LAND-001-A §5).
 *
 * <p>Per the Phase 0 design, interaction is governed only for
 * {@code BlockEntity} targets (containers, machines, institutional blocks);
 * plain decorative blocks are not intercepted. Any denial cancels the event.
 * All logic lives in {@link LandEventPolicy} (Forge-free and testable).</p>
 */
public final class InteractionEventHandler {

    private final LandEventPolicy policy;

    public InteractionEventHandler(LandEventPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) == null) {
            // BlockEntity interaction only (Phase 0 design).
            return;
        }
        if (!policy.allowInteract(
                player.getUUID(),
                dimension(event.getLevel()),
                event.getPos().getX(),
                event.getPos().getY(),
                event.getPos().getZ()
        )) {
            event.setCanceled(true);
        }
    }

    private static String dimension(LevelAccessor level) {
        // Interaction events always carry a concrete Level on the server thread.
        return ((Level) level).dimension().location().toString();
    }
}
