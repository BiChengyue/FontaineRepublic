package com.fontainerepublic.server.trade.model;

import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * One intention offer slot of a trade session (FR-TRADE-001-A §4, intent
 * model).
 *
 * <p>An offer is an <em>intention</em>, never a held item: the offered stack
 * stays in the sender's inventory until the atomic execution. The slot
 * records the source main-inventory index ({@code 0..35}) together with a
 * copy of the item snapshot at offer time; execution re-validates that the
 * source slot still holds exactly this snapshot (dupe guard) before the item
 * moves. An empty slot is the shared {@link #EMPTY} instance
 * ({@code inventoryIndex = -1}, {@link ItemStack#EMPTY}).</p>
 */
public record TradeOfferSlot(int inventoryIndex, ItemStack stack) {

    /** Shared empty-slot instance (no source index, empty stack). */
    public static final TradeOfferSlot EMPTY = new TradeOfferSlot(
            TradeOfferItemPacket.WITHDRAW_MARKER,
            ItemStack.EMPTY
    );

    public TradeOfferSlot {
        if (inventoryIndex < TradeOfferItemPacket.WITHDRAW_MARKER
                || inventoryIndex > TradeOfferItemPacket.MAX_INVENTORY_INDEX) {
            throw new IllegalArgumentException(
                    "Offer source index must be within [-1, "
                            + TradeOfferItemPacket.MAX_INVENTORY_INDEX + "]: "
                            + inventoryIndex
            );
        }
        stack = Objects.requireNonNull(stack, "stack").copy();
        if (inventoryIndex == TradeOfferItemPacket.WITHDRAW_MARKER
                && !stack.isEmpty()) {
            throw new IllegalArgumentException(
                    "An empty slot must carry the empty stack"
            );
        }
        if (inventoryIndex >= 0 && stack.isEmpty()) {
            throw new IllegalArgumentException(
                    "A filled offer slot must carry a non-empty stack"
            );
        }
    }

    /** True for the empty sentinel. */
    public boolean isEmpty() {
        return this == EMPTY
                || (inventoryIndex == TradeOfferItemPacket.WITHDRAW_MARKER
                && stack.isEmpty());
    }
}
