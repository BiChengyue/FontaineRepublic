package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S item offer (FR-TRADE-001-A §3, message ledger ID 12, intent model).
 *
 * <p>{@code slot} is the session offer slot ({@code 0..3}, bounded) and
 * {@code inventoryIndex} is the source main-inventory index of the sender.
 * {@code inventoryIndex = -1} is the withdraw marker: the current item
 * snapshot of the slot is cleared. A positive inventory index records an
 * intention to offer that source slot, copying the current item snapshot for
 * display and later re-validation; no item ever leaves the sender's inventory
 * before the atomic execution. Every bound is enforced at construction and at
 * decode; the server re-validates the source slot and all authority rules.</p>
 */
public record TradeOfferItemPacket(long sessionId, int slot, int inventoryIndex) {

    /** Number of offer slots per side. */
    public static final int SLOT_COUNT = 4;

    /** Largest valid main-inventory index (0..35, the 36 main slots). */
    public static final int MAX_INVENTORY_INDEX = 35;

    /** Withdraw marker: return the slot's item instead of offering one. */
    public static final int WITHDRAW_MARKER = -1;

    public TradeOfferItemPacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new NetworkPayloadException(
                    "Offer slot must be within [0, " + (SLOT_COUNT - 1) + "]: " + slot
            );
        }
        if (inventoryIndex < WITHDRAW_MARKER || inventoryIndex > MAX_INVENTORY_INDEX) {
            throw new NetworkPayloadException(
                    "Inventory index must be within [-1, " + MAX_INVENTORY_INDEX + "]: "
                            + inventoryIndex
            );
        }
    }

    public static void encode(TradeOfferItemPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
        buffer.writeInt(message.slot());
        buffer.writeInt(message.inventoryIndex());
    }

    public static TradeOfferItemPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeOfferItemPacket(
                buffer.readLong(),
                buffer.readInt(),
                buffer.readInt()
        );
    }
}
