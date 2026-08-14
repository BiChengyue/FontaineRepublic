package com.fontainerepublic.server.mail.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server player surface the mail service depends on (FR-MAIL-001-A §3),
 * mirroring the trade module's {@code ServerTradePlayerAccess}: online-player
 * resolution, the authoritative communicator gate (held or in-inventory),
 * inventory item movement, and chat feedback. Abstracted so the service is
 * headless-testable and the production implementation touches the live
 * server.
 */
public interface ServerMailPlayerAccess {

    /** The online player for a UUID, or empty when offline/unknown. */
    Optional<ServerPlayer> onlinePlayer(UUID playerId);

    /** Server-authoritative: the player holds the communicator in hand. */
    boolean holdsCommunicator(UUID playerId);

    /** Server-authoritative: the player's inventory contains the
     *  communicator somewhere (used to gate the new-mail HUD/chat alert). */
    boolean inventoryContainsCommunicator(UUID playerId);

    /** Copies of the item at a main-inventory index (0..35); empty-safe. */
    ItemStack mainInventoryStack(UUID playerId, int inventoryIndex);

    /** Overwrites a main-inventory index with the given stack. */
    void setMainInventoryStack(UUID playerId, int inventoryIndex, ItemStack stack);

    /** True when the player can hold the whole stack (partial/full). */
    boolean hasRoomFor(UUID playerId, ItemStack stack);

    /** Adds the stack to the player's inventory; returns the leftover
     *  (empty when fully placed). */
    ItemStack addToInventory(UUID playerId, ItemStack stack);

    /** Server chat feedback to the player. */
    void message(UUID playerId, String text);
}
