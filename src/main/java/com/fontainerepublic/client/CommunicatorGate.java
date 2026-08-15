package com.fontainerepublic.client;

import com.fontainerepublic.common.item.FRItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * FR client UI gate (FR-ITEM-003).
 *
 * <p>The {@code /frclient} surface only opens while the local player holds the
 * Message Water Mirror - now the independent custom item
 * {@code fontainerepublic:communicator} (FR-ITEM-001-A &sect;2.1) - in the
 * main or off hand. This is a pure UX gate, never an authority check: the
 * client only checks the item type, while the server re-validates every
 * business mutation through its own authoritative services. A malicious
 * client that bypasses the gate only affects its own UI and gains no
 * permission or state-change ability (FR-ITEM-001-A &sect;3).</p>
 */
public final class CommunicatorGate {

    /** Chat feedback shown when the gate rejects an open request. */
    public static final String GATE_MESSAGE =
            "Hold the Message Water Mirror (传讯水镜) to use the FR client interface.";

    private CommunicatorGate() {
    }

    /** True while the player holds the communicator in the main or off hand. */
    public static boolean holdsCommunicator(Player player) {
        return holdsCommunicator(player.getMainHandItem(), player.getOffhandItem());
    }

    /** Pure decision over the two held stacks (dependency-free testable). */
    public static boolean holdsCommunicator(ItemStack mainHand, ItemStack offHand) {
        return isCommunicator(mainHand) || isCommunicator(offHand);
    }

    /** True while the player's inventory (any slot) contains the communicator
     *  (FR-MAIL-001-A &sect;6.5 new-mail reminder gate). */
    public static boolean inventoryContainsCommunicator(Player player) {
        if (player == null) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (isCommunicator(inventory.getItem(index))) {
                return true;
            }
        }
        return false;
    }

    /** True if the stack is the communicator item (empty-safe). */
    public static boolean isCommunicator(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(FRItems.COMMUNICATOR.get());
    }
}