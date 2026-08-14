package com.fontainerepublic.client;

import com.fontainerepublic.client.gui.land.LandScreen;
import com.fontainerepublic.client.gui.money.MoneyScreen;
import com.fontainerepublic.client.gui.money.TransferFormComposer;
import com.fontainerepublic.client.gui.FrMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
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
 * <p>Right-click a player while holding the communicator → the transfer form
 * opens with the target pre-filled; right-click a block → the read-only land
 * overview opens (parcel purchase / claim is a later task,
 * FR-LAND-CLAIM-001).</p>
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
        String targetName = prefillTargetName(target.getGameProfile().getName());
        if (targetName == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new MoneyScreen(targetName));
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
        Minecraft.getInstance().setScreen(new LandScreen());
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
