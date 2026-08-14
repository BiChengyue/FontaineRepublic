package com.fontainerepublic.client;

import com.fontainerepublic.common.item.FRItemIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * FR client UI gate (FR-ITEM-002-A §7).
 *
 * <p>The {@code /frclient} surface only opens while the local player holds the
 * Message Water Mirror — now a vanilla {@code minecraft:clock} carrying the
 * {@code FontaineRepublicDevice} NBT compound (FR-ITEM-002-A §4.1) — in the
 * main or off hand. This is a pure UX gate, never an authority check: the
 * client only checks the carrier item + device-tag presence, while the server
 * re-authenticates the signature and owner binding before any business
 * mutation. A malicious client that bypasses the gate only affects its own
 * UI and gains no permission or state-change ability
 * (FR-ITEM-002-A §4.3).</p>
 */
public final class CommunicatorGate {

    /** Chat feedback shown when the gate rejects an open request. */
    public static final String GATE_MESSAGE =
            "Hold the Message Water Mirror (传讯水镜) to use the FR client interface.";

    private CommunicatorGate() {
    }

    /** True while the player holds the carrier in the main or off hand. */
    public static boolean holdsCommunicator(Player player) {
        return holdsCommunicator(player.getMainHandItem(), player.getOffhandItem());
    }

    /** Pure decision over the two held stacks (dependency-free testable). */
    public static boolean holdsCommunicator(ItemStack mainHand, ItemStack offHand) {
        return isCommunicator(mainHand) || isCommunicator(offHand);
    }

    /** True while the player's inventory (any slot) contains the carrier
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

    /** True if the stack is a vanilla clock carrying the device compound
     *  (empty-safe). */
    public static boolean isCommunicator(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!isCarrierClock(stack)) {
            return false;
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null
                && tag.contains(FRItemIds.DEVICE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND);
    }

    /** Pure predicate: is the item the vanilla {@code minecraft:clock}? */
    public static boolean isCarrierKey(ResourceLocation key) {
        return key != null && FRItemIds.CARRIER_ITEM_REGISTRY_NAME.equals(key.toString());
    }

    /** Extracts the carrier item id check for an {@link ItemStack}. */
    private static boolean isCarrierClock(ItemStack stack) {
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getKey(stack.getItem());
        return isCarrierKey(key);
    }
}
