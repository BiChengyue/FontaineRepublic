package com.fontainerepublic.server.trade.service;

import com.fontainerepublic.common.item.FRItems;
import com.fontainerepublic.server.trade.api.ServerTradePlayerAccess;
import com.fontainerepublic.server.trade.xp.ExperiencePointMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production server player surface of the trade module (FR-TRADE-001-A
 * §4/§5): resolves players through the live server, enforces the server-side
 * Water Mirror gate (main or off hand carries the communicator item —
 * independent of any {@code client/} class), touches the real inventories,
 * and sends server chat feedback.
 * Inventory indices are the 36 main slots ({@code 0..35}); armor and off-hand
 * slots are never offered.
 */
public final class ServerPlayerAccess implements ServerTradePlayerAccess {

    /** Main-inventory slot count (0..35), mirrors
     *  {@code TradeOfferItemPacket.MAX_INVENTORY_INDEX}. */
    private static final int MAIN_INVENTORY_SLOTS = 36;

    @Override
    public Optional<ServerPlayer> onlinePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(playerId));
    }

    @Override
    public boolean holdsCommunicator(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return false;
        }
        return player.get().getMainHandItem().is(FRItems.COMMUNICATOR.get())
                || player.get().getOffhandItem().is(FRItems.COMMUNICATOR.get());
    }

    @Override
    public ItemStack mainInventoryStack(UUID playerId, int inventoryIndex) {
        requireMainIndex(inventoryIndex);
        return requirePlayer(playerId).getInventory().getItem(inventoryIndex).copy();
    }

    @Override
    public void setMainInventoryStack(
            UUID playerId,
            int inventoryIndex,
            ItemStack stack
    ) {
        requireMainIndex(inventoryIndex);
        requirePlayer(playerId).getInventory().setItem(
                inventoryIndex,
                Objects.requireNonNull(stack, "stack").copy()
        );
    }

    @Override
    public boolean hasRoomFor(UUID playerId, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return true;
        }
        Inventory inventory = requirePlayer(playerId).getInventory();
        for (int index = 0; index < MAIN_INVENTORY_SLOTS; index++) {
            ItemStack slot = inventory.getItem(index);
            if (slot.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameTags(slot, stack)
                    && slot.getCount() + stack.getCount() <= slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean addToInventory(UUID playerId, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack remaining = stack.copy();
        Inventory inventory = requirePlayer(playerId).getInventory();
        inventory.add(remaining);
        return remaining.isEmpty();
    }

    @Override
    public long totalExperience(UUID playerId) {
        ServerPlayer player = requirePlayer(playerId);
        return ExperiencePointMath.totalXpFor(
                player.experienceLevel,
                player.experienceProgress
        );
    }

    @Override
    public void setTotalExperience(UUID playerId, long totalXp) {
        ServerPlayer player = requirePlayer(playerId);
        long clamped = Math.max(0L, totalXp);
        int level = ExperiencePointMath.levelForXp(clamped);
        long levelBase = ExperiencePointMath.xpForLevel(level);
        long nextBase = ExperiencePointMath.xpForLevel(level + 1);
        long span = nextBase - levelBase;
        player.experienceLevel = level;
        player.experienceProgress = span <= 0L
                ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (float) (clamped - levelBase) / (float) span));
        player.totalExperience = (int) Math.min(Integer.MAX_VALUE, clamped);
    }

    @Override
    public void message(UUID playerId, String message) {
        Objects.requireNonNull(message, "message");
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        player.ifPresent(serverPlayer -> serverPlayer.displayClientMessage(
                Component.literal(message),
                false
        ));
    }

    private ServerPlayer requirePlayer(UUID playerId) {
        return onlinePlayer(playerId).orElseThrow(
                () -> new IllegalStateException("Player is not online: " + playerId)
        );
    }

    private static void requireMainIndex(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= MAIN_INVENTORY_SLOTS) {
            throw new IllegalArgumentException(
                    "Main-inventory index must be within [0, "
                            + (MAIN_INVENTORY_SLOTS - 1) + "]: " + inventoryIndex
            );
        }
    }
}
