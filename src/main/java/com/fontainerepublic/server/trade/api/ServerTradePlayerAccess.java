package com.fontainerepublic.server.trade.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side player surface of the trade module (FR-TRADE-001-A §4/§5).
 *
 * <p>All access is authoritative and main-thread serialized: the players are
 * resolved through the live server, the communicator gate compares the held
 * registry keys (main or off hand), inventory operations touch the real
 * player inventories, and chat feedback is server chat. The interface is
 * deliberately dependency-free of {@code client/} classes so a dedicated
 * server never loads client code (the client-side {@code CommunicatorGate}
 * stays a pure UX gate; this server gate is the authority).</p>
 */
public interface ServerTradePlayerAccess {

    /** The online player, when present. */
    Optional<ServerPlayer> onlinePlayer(UUID playerId);

    /** True while the player holds the communicator in the main or off hand
     *  (server-side registry-key gate). */
    boolean holdsCommunicator(UUID playerId);

    /** A copy of the player's main-inventory slot ({@code 0..35}). */
    ItemStack mainInventoryStack(UUID playerId, int inventoryIndex);

    /** Replaces a main-inventory slot with the given stack (authoritative
     *  inventory write). */
    void setMainInventoryStack(UUID playerId, int inventoryIndex, ItemStack stack);

    /** True when the player's main inventory can fully absorb the stack
     *  (an empty slot or a mergeable stack), without mutating anything. */
    boolean hasRoomFor(UUID playerId, ItemStack stack);

    /** Moves the stack into the player's main inventory; returns true only
     *  when the whole stack was absorbed (nothing dropped, nothing lost). */
    boolean addToInventory(UUID playerId, ItemStack stack);

    /** The player's total experience points (server-authoritative counter,
     *  reconstructed from level + progress via {@code ExperiencePointMath}). */
    long totalExperience(UUID playerId);

    /** Rewrites the player's experience to the exact total (level, progress
     *  and total counter); used by the atomic XP-offer exchange. */
    void setTotalExperience(UUID playerId, long totalXp);

    /** Server chat feedback to the player. */
    void message(UUID playerId, String message);
}
