package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * S2C trade-session snapshot (FR-TRADE-001-A §3, message ledger ID 15).
 *
 * <p>Carries display data only — never an authority decision. The payload is
 * already per-viewer: {@code own*} describes the receiving player's side and
 * {@code other*} the counterparty's side, so the client never infers a
 * perspective. {@code phase} is the server-side state ordinal
 * ({@code 0=REQUESTED, 1=OPEN, 2=LOCKED, 3=EXECUTING, 4=COMPLETED,
 * 5=CANCELLED}), {@code countdownSeconds} the remaining LOCKED confirmation
 * seconds (0 outside LOCKED). Every bound is enforced at construction and at
 * decode so the wire never carries an out-of-range value.</p>
 */
public record TradeStateSyncPacket(
        long sessionId,
        int phase,
        int countdownSeconds,
        long ownMoney,
        List<ItemStack> ownItems,
        boolean ownAgree,
        long otherMoney,
        List<ItemStack> otherItems,
        boolean otherAgree,
        long at
) {

    /** Server-side phase ordinals mirrored for the client view. */
    public static final int PHASE_REQUESTED = 0;
    public static final int PHASE_OPEN = 1;
    public static final int PHASE_LOCKED = 2;
    public static final int PHASE_EXECUTING = 3;
    public static final int PHASE_COMPLETED = 4;
    public static final int PHASE_CANCELLED = 5;

    /** LOCKED confirmation window in seconds (server tick driven). */
    public static final int LOCKED_SECONDS = 5;

    public TradeStateSyncPacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
        if (phase < PHASE_REQUESTED || phase > PHASE_CANCELLED) {
            throw new NetworkPayloadException("Unknown trade phase ordinal: " + phase);
        }
        if (countdownSeconds < 0 || countdownSeconds > LOCKED_SECONDS) {
            throw new NetworkPayloadException(
                    "Countdown must be within [0, " + LOCKED_SECONDS + "]: "
                            + countdownSeconds
            );
        }
        if (ownMoney < 0 || otherMoney < 0) {
            throw new NetworkPayloadException("Offered amounts must not be negative");
        }
        ownItems = fixedSlotList(ownItems, "ownItems");
        otherItems = fixedSlotList(otherItems, "otherItems");
        if (at <= 0) {
            throw new NetworkPayloadException("Snapshot timestamp must be positive: " + at);
        }
    }

    private static List<ItemStack> fixedSlotList(List<ItemStack> items, String name) {
        Objects.requireNonNull(items, name);
        if (items.size() != TradeOfferItemPacket.SLOT_COUNT) {
            throw new NetworkPayloadException(
                    name + " must hold exactly " + TradeOfferItemPacket.SLOT_COUNT + " slots"
            );
        }
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copy.add(Objects.requireNonNull(stack, name + " element").copy());
        }
        return List.copyOf(copy);
    }

    public static void encode(TradeStateSyncPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
        buffer.writeInt(message.phase());
        buffer.writeInt(message.countdownSeconds());
        buffer.writeLong(message.ownMoney());
        writeSlots(buffer, message.ownItems());
        buffer.writeBoolean(message.ownAgree());
        buffer.writeLong(message.otherMoney());
        writeSlots(buffer, message.otherItems());
        buffer.writeBoolean(message.otherAgree());
        buffer.writeLong(message.at());
    }

    public static TradeStateSyncPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeStateSyncPacket(
                buffer.readLong(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readLong(),
                readSlots(buffer),
                buffer.readBoolean(),
                buffer.readLong(),
                readSlots(buffer),
                buffer.readBoolean(),
                buffer.readLong()
        );
    }

    private static void writeSlots(FriendlyByteBuf buffer, List<ItemStack> items) {
        for (ItemStack stack : items) {
            buffer.writeItemStack(stack, false);
        }
    }

    private static List<ItemStack> readSlots(FriendlyByteBuf buffer) {
        List<ItemStack> items = new ArrayList<>(TradeOfferItemPacket.SLOT_COUNT);
        for (int index = 0; index < TradeOfferItemPacket.SLOT_COUNT; index++) {
            items.add(buffer.readItem());
        }
        return items;
    }
}
