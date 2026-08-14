package com.fontainerepublic.server.trade.model;

import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable state snapshot of one trade session (FR-TRADE-001-A §4, intent
 * model — Human-confirmed 2026-08-14, replacing the earlier escrow model).
 *
 * <p>Every server transition builds a fresh session instance; the session
 * map is the single in-memory authority (sessions are deliberately not
 * persisted — the executed outcome is committed through the economy accounts
 * and the inventories). The session holds only <em>intentions</em>: each
 * side's offered money amount ({@code long}) and four offer slots (source
 * inventory index + item snapshot). No money is pre-escrowed and no item
 * ever leaves a player's inventory before the atomic execution; cancel /
 * disconnect / shutdown simply drops the session because nothing was ever
 * moved. Items are always copied on the way in and on the way out so no
 * stack reference is ever shared with an inventory.</p>
 */
public record TradeSession(
        long sessionId,
        UUID initiator,
        UUID partner,
        TradePhase phase,
        long initiatorMoney,
        long partnerMoney,
        List<TradeOfferSlot> initiatorItems,
        List<TradeOfferSlot> partnerItems,
        boolean initiatorAgree,
        boolean partnerAgree,
        long lockTick,
        long requestTick,
        long openTick
) {

    /** LOCKED confirmation window in server ticks (5 seconds at 20 TPS). */
    public static final long LOCKED_TICKS = 100L;

    public TradeSession {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(phase, "phase");
        if (initiator.equals(partner)) {
            throw new IllegalArgumentException("Trade parties must differ");
        }
        if (initiatorMoney < 0 || partnerMoney < 0) {
            throw new IllegalArgumentException("Offered amounts must not be negative");
        }
        initiatorItems = fixedSlots(initiatorItems, "initiatorItems");
        partnerItems = fixedSlots(partnerItems, "partnerItems");
    }

    private static List<TradeOfferSlot> fixedSlots(
            List<TradeOfferSlot> items,
            String name
    ) {
        Objects.requireNonNull(items, name);
        if (items.size() != TradeOfferItemPacket.SLOT_COUNT) {
            throw new IllegalArgumentException(
                    name + " must hold exactly " + TradeOfferItemPacket.SLOT_COUNT
                            + " slots"
            );
        }
        List<TradeOfferSlot> copy = new ArrayList<>(items.size());
        for (TradeOfferSlot slot : items) {
            copy.add(new TradeOfferSlot(
                    slot.inventoryIndex(),
                    slot.stack()
            ));
        }
        return List.copyOf(copy);
    }

    /** True when the player is one of the two session parties. */
    public boolean isMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerId.equals(initiator) || playerId.equals(partner);
    }

    /** True when the player is the session initiator (requester). */
    public boolean isInitiator(UUID playerId) {
        return playerId.equals(initiator);
    }

    /** Offered money of the given member (0 for the other side). */
    public long moneyOf(UUID playerId) {
        return isInitiator(playerId) ? initiatorMoney : partnerMoney;
    }

    /** Four offer slots of the given member (immutable). */
    public List<TradeOfferSlot> itemsOf(UUID playerId) {
        return isInitiator(playerId) ? initiatorItems : partnerItems;
    }

    /** The other member of the session (counterparty of the given member). */
    public UUID otherOf(UUID playerId) {
        return isInitiator(playerId) ? partner : initiator;
    }

    /** Agreement flag of the given member. */
    public boolean agreeOf(UUID playerId) {
        return isInitiator(playerId) ? initiatorAgree : partnerAgree;
    }

    /** Copy of the offered slots of the given member as plain stacks
     *  (display projection; empty slots become {@link ItemStack#EMPTY}). */
    public List<ItemStack> itemStacksOf(UUID playerId) {
        List<ItemStack> projection = new ArrayList<>(TradeOfferItemPacket.SLOT_COUNT);
        for (TradeOfferSlot slot : itemsOf(playerId)) {
            projection.add(slot.stack().copy());
        }
        return List.copyOf(projection);
    }

    // ------------------------------------------------------------------
    // transition builders (each returns a new immutable session)
    // ------------------------------------------------------------------

    public TradeSession withPhase(TradePhase nextPhase, long requestTick, long openTick, long lockTick) {
        return new TradeSession(
                sessionId, initiator, partner,
                nextPhase,
                initiatorMoney, partnerMoney,
                initiatorItems, partnerItems,
                initiatorAgree, partnerAgree,
                lockTick, requestTick, openTick
        );
    }

    public TradeSession withMoney(UUID member, long newMoney) {
        boolean initiatorSide = isInitiator(member);
        return new TradeSession(
                sessionId, initiator, partner,
                phase,
                initiatorSide ? newMoney : initiatorMoney,
                initiatorSide ? partnerMoney : newMoney,
                initiatorItems, partnerItems,
                initiatorAgree, partnerAgree,
                lockTick, requestTick, openTick
        );
    }

    public TradeSession withItem(UUID member, int slot, TradeOfferSlot offer) {
        boolean initiatorSide = isInitiator(member);
        List<TradeOfferSlot> own = initiatorSide ? initiatorItems : partnerItems;
        List<TradeOfferSlot> other = initiatorSide ? partnerItems : initiatorItems;
        List<TradeOfferSlot> nextOwn = new ArrayList<>(own);
        nextOwn.set(slot, new TradeOfferSlot(
                offer.inventoryIndex(),
                offer.stack()
        ));
        return new TradeSession(
                sessionId, initiator, partner,
                phase,
                initiatorMoney, partnerMoney,
                initiatorSide ? nextOwn : other,
                initiatorSide ? other : nextOwn,
                initiatorAgree, partnerAgree,
                lockTick, requestTick, openTick
        );
    }

    public TradeSession withAgree(UUID member, boolean agree) {
        boolean initiatorSide = isInitiator(member);
        return new TradeSession(
                sessionId, initiator, partner,
                phase,
                initiatorMoney, partnerMoney,
                initiatorItems, partnerItems,
                initiatorSide ? agree : initiatorAgree,
                initiatorSide ? partnerAgree : agree,
                lockTick, requestTick, openTick
        );
    }
}
