package com.fontainerepublic.client;

import com.fontainerepublic.common.item.FRItemIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * FR client UI gate (FR-ITEM-001-A §2.2).
 *
 * <p>The {@code /frclient} surface only opens while the local player holds
 * the {@code communicator} in the main or off hand. This is a pure UX gate,
 * never an authority check: every action still re-enters the server command
 * surface, which re-validates identity, permissions and state. A malicious
 * client that bypasses the gate only affects its own UI and gains no
 * permission or state-change ability (FR-ITEM-001-A §3).</p>
 *
 * <p>The item identity is compared by registry name so the decision logic is
 * dependency-free and testable in a plain JVM (no live registry required):
 * {@link #isCommunicatorKey} is the pure predicate, the {@link ItemStack}
 * overloads only extract the registry key of the held item.</p>
 */
public final class CommunicatorGate {

    /** Chat feedback shown when the gate rejects an open request. */
    public static final String GATE_MESSAGE =
            "Hold the Message Water Mirror (传讯水镜) to use the FR client interface.";

    private CommunicatorGate() {
    }

    /** True while the player holds the communicator in the main or off hand. */
    public static boolean holdsCommunicator(Player player) {
        return holdsCommunicator(
                player.getMainHandItem(),
                player.getOffhandItem()
        );
    }

    /** Pure decision over the two held stacks (dependency-free testable). */
    public static boolean holdsCommunicator(ItemStack mainHand, ItemStack offHand) {
        return isCommunicator(mainHand) || isCommunicator(offHand);
    }

    /** True while the player's inventory (any slot) contains the communicator
     *  (FR-MAIL-001-A §6.5 new-mail reminder gate). */
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

    /** True if the stack carries the communicator item (empty-safe). */
    public static boolean isCommunicator(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return isCommunicatorKey(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** Pure predicate: does the registry key identify the communicator? */
    public static boolean isCommunicatorKey(ResourceLocation key) {
        return key != null && FRItemIds.ITEM_REGISTRY_NAME.equals(key.toString());
    }
}
