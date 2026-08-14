package com.fontainerepublic.server.mail.service;

import com.fontainerepublic.common.item.CommunicatorAuthenticator;
import com.fontainerepublic.server.communicator.CommunicatorAuthority;
import com.fontainerepublic.server.mail.api.ServerMailPlayerAccess;
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
 * Production server player surface of the mail module (FR-MAIL-001-A §3):
 * resolves players through the live server, enforces the Water Mirror gate
 * (a signed vanilla clock carrier — held for sending, in-inventory for the
 * new-mail alert), touches the real inventories, and sends server chat
 * feedback. Main-inventory indices are 0..35.
 */
public final class MailServerPlayerAccess implements ServerMailPlayerAccess {

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
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return false;
        }
        CommunicatorAuthenticator authenticator = CommunicatorAuthority.authenticator();
        if (authenticator == null) {
            return false;
        }
        return authenticator.authenticate(player.get().getMainHandItem(), playerId)
                || authenticator.authenticate(player.get().getOffhandItem(), playerId);
    }

    @Override
    public boolean inventoryContainsCommunicator(UUID playerId) {
        Optional<ServerPlayer> player = onlinePlayer(playerId);
        if (player.isEmpty()) {
            return false;
        }
        CommunicatorAuthenticator authenticator = CommunicatorAuthority.authenticator();
        if (authenticator == null) {
            return false;
        }
        Inventory inventory = player.get().getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (authenticator.authenticate(inventory.getItem(index), playerId)) {
                return true;
            }
        }
        return false;
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
    public ItemStack addToInventory(UUID playerId, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        Inventory inventory = requirePlayer(playerId).getInventory();
        inventory.add(remaining);
        return remaining;
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
