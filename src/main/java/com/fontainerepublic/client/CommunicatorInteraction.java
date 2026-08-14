package com.fontainerepublic.client;

import com.fontainerepublic.client.gui.land.LandLocationScreen;
import com.fontainerepublic.client.gui.money.TransferFormComposer;
import com.fontainerepublic.client.gui.FrMainScreen;
import com.fontainerepublic.client.trade.ClientTradeSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Communicator right-click interactions (FR-ITEM-001-A §2.3).
 *
 * <p>Registered on the Forge event bus from {@link ClientManager#init()},
 * which only runs on the physical client, so a dedicated server never loads
 * this class. Both handlers are pure UI entry points: they open a screen /
 * pre-fill a form and trigger <em>no</em> server-side side effect — the
 * transfer still submits through {@code /fr money pay ...} and every
 * authority rule stays on the server (FR-ITEM-001-A §3).</p>
 *
 * <p>Right-click a player while holding the communicator → a C2S trade request
 * is sent; right-click a block → the parcel-location view opens for that exact
 * position (FR-LAND-CLAIM-001), offering an unowned parcel claim when the
 * server reports it claimable.</p>
 */
public final class CommunicatorInteraction {

    private CommunicatorInteraction() {
    }

    /** Right-click on an entity while holding the communicator. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isLocalClientInteract(event)) {
            return;
        }
        LocalPlayer player = (LocalPlayer) event.getEntity();
        if (!CommunicatorGate.holdsCommunicator(player)) {
            return;
        }
        if (!(event.getTarget() instanceof Player target)) {
            return;
        }
        // FR-TRADE-001-A §5: right-clicking a player sends the C2S trade
        // request (the money-transfer form is still reachable from the FR
        // client main menu). The server re-validates online presence, the
        // communicator gate and session conflicts.
        ClientTradeSender.sendRequest(target.getUUID());
    }

    /** Right-click on a block while holding the communicator. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isLocalClientInteract(event)) {
            return;
        }
        LocalPlayer player = (LocalPlayer) event.getEntity();
        if (!CommunicatorGate.holdsCommunicator(player)) {
            return;
        }
        // FR-LAND-CLAIM-001-A §5: the parcel-location view opens carrying the
        // clicked block position and the player's current dimension; it sends
        // the LandInspectPacket and, when claimable, offers the claim step.
        //
        // FR-LAND-CLAIM-001-FIX-01 (F1): RightClickBlock is cancellable in
        // Forge 1.20.1. Cancelling the logical-client event replaces the
        // ordinary block interaction so a chest/door/button/lever is not also
        // activated while the land screen opens — the land view opens *instead
        // of* the block's normal use, not *in addition to* it. In vanilla this
        // event's cancellation is the target-block-NONE path, which suppresses
        // the downstream ServerboundUseItemOnPacket the server would otherwise
        // re-fire against the block. Only the local-client communicator path is
        // cancelled; the dedicated server never loads this class and
        // server-side block interactions are untouched.
        //
        // FR-LAND-CLAIM-001-FIX-01 (F2): Forge 1.20.1 defaults the event's
        // cancellationResult to InteractionResult.PASS. Although cancelling
        // denies the block/item useOn, PASS lets the client retry later
        // interactions / the other hand on the next click; reporting SUCCESS
        // completes the interaction so the client stops trying after the land
        // screen opens.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        Minecraft.getInstance().setScreen(new LandLocationScreen(
                event.getPos(),
                player.level().dimension().location().toString()
        ));
    }

    /** Right-click on empty air while holding the communicator in the main
     * hand opens the FR client main menu (no command needed).
     *
     * <p>FR-ITEM-002-A §8 / Level-3 offhand-conflict fix: vanilla fires
     * {@code RightClickEmpty} only while the clicked hand's stack is empty.
     * Because the communicator is a non-empty vanilla {@code minecraft:clock}
     * (no vanilla {@code use} action), a pure air right-click with it in the
     * main hand instead fires {@code RightClickItem}, and when a usable item
     * (shield / torch) sits in the offhand the offhand {@code use} would
     * otherwise consume the click and silently suppress the communicator. This
     * handler claims the <em>main-hand</em> communicator's air use first (the
     * hand loop visits MAIN_HAND before OFF_HAND) and reports
     * {@link InteractionResult#SUCCESS} so the unrelated offhand item never
     * gets a chance to act. Entity clicks go through {@link #onEntityInteract}
     * and block clicks through {@link #onRightClickBlock} (which consumes the
     * block activation), so this path only ever sees the air/miss case.</p> */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isLocalClientInteract(event)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        LocalPlayer player = (LocalPlayer) event.getEntity();
        if (!CommunicatorGate.isCommunicator(player.getMainHandItem())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult != null
                && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }
        // Consume the main-hand air use with SUCCESS so the offhand item's use
        // (e.g. raising a shield) is never reached this click.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        minecraft.setScreen(new FrMainScreen());
    }

    /** Right-click on empty air while holding the communicator opens the FR
     * client main menu (no command needed). */
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!isLocalClientInteract(event)) {
            return;
        }
        LocalPlayer player = (LocalPlayer) event.getEntity();
        if (!CommunicatorGate.holdsCommunicator(player)) {
            return;
        }
        Minecraft.getInstance().setScreen(new FrMainScreen());
    }

    /**
     * Pure event filter: only the local client's own interactions open UI
     * (the server side never handles these listeners at all, this is a
     * double guard for clarity).
     */
    private static boolean isLocalClientInteract(PlayerInteractEvent event) {
        return event.getSide().isClient()
                && event.getEntity() instanceof LocalPlayer;
    }

    /**
     * Pure pre-fill helper (dependency-free testable): normalizes the target
     * player name for the transfer form, or returns {@code null} when the
     * name cannot be used as a command target (blank / whitespace).
     */
    public static String prefillTargetName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        try {
            return TransferFormComposer.validateTarget(playerName);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
