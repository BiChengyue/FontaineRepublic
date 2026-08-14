package com.fontainerepublic.trade.securetrade;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Item offer validation helpers (FR-TRADE-003-A).
 *
 * <p>Adapted from Secure Trade's {@code TradeItemValidator} (MIT, Copyright
 * (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}). See
 * {@code docs/third_party/securetrade/} for the archived license and
 * notices. The nested-container recursion protects against blacklisted items
 * hidden inside shulker-like containers or Forge capability carriers. The
 * blacklist is supplied by the caller (server-authoritative) rather than a
 * platform service loader.</p>
 */
public final class TradeItemValidator {
    private static final int MAX_NESTED_DEPTH = 8;

    private TradeItemValidator() {
    }

    /** True when the stack (or any nested container content) matches the list. */
    public static boolean containsBlacklistedItem(ItemStack stack, List<String> blacklist) {
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }
        return containsBlacklistedItem(stack, blacklist, 0);
    }

    /** True when any slot of the container holds a blacklisted item. */
    public static boolean containsBlacklistedItems(SimpleContainer container, List<String> blacklist) {
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (containsBlacklistedItem(container.getItem(i), blacklist, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBlacklistedItem(ItemStack stack, List<String> blacklist, int depth) {
        if (stack.isEmpty()) {
            return false;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (blacklist.contains(itemId)) {
            return true;
        }
        if (depth >= MAX_NESTED_DEPTH) {
            return true;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
            if (blockEntityTag.contains("Items")) {
                ListTag items = blockEntityTag.getList("Items", 10);
                for (int i = 0; i < items.size(); i++) {
                    if (containsBlacklistedItem(ItemStack.of(items.getCompound(i)), blacklist, depth + 1)) {
                        return true;
                    }
                }
            }
            if (tag.contains("Items")) {
                ListTag items = tag.getList("Items", 10);
                for (int i = 0; i < items.size(); i++) {
                    if (containsBlacklistedItem(ItemStack.of(items.getCompound(i)), blacklist, depth + 1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
