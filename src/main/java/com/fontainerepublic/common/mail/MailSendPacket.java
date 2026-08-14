package com.fontainerepublic.common.mail;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * C2S mail send (FR-MAIL-001-A §3/§6.2, message ledger ID 16).
 *
 * <p>Carries the textual recipient, subject, body, an optional money
 * attachment (intention; never pre-escrowed) and up to
 * {@value #MAX_ITEM_SLOTS} main-inventory source-slot indices. The sender is
 * taken from the connection context; the server re-resolves the recipient and
 * enforces the communicator gate, fee and bounds — fail closed.</p>
 */
public record MailSendPacket(
        String to,
        String subject,
        String body,
        long moneyAttachment,
        List<Integer> itemSlotIndices
) {

    public static final int MAX_TO = 128;
    public static final int MAX_SUBJECT = 64;
    public static final int MAX_BODY = 2000;
    public static final int MAX_ITEM_SLOTS = 4;
    public static final long MAX_MONEY = 1_000_000_000L;

    public MailSendPacket {
        to = Objects.requireNonNull(to, "to");
        if (to.length() > MAX_TO) {
            throw new NetworkPayloadException("to exceeds " + MAX_TO + " characters");
        }
        subject = Objects.requireNonNull(subject, "subject");
        if (subject.length() > MAX_SUBJECT) {
            throw new NetworkPayloadException("subject exceeds " + MAX_SUBJECT + " characters");
        }
        body = Objects.requireNonNull(body, "body");
        if (body.length() > MAX_BODY) {
            throw new NetworkPayloadException("body exceeds " + MAX_BODY + " characters");
        }
        if (moneyAttachment < 0 || moneyAttachment > MAX_MONEY) {
            throw new NetworkPayloadException("moneyAttachment out of range");
        }
        if (itemSlotIndices == null || itemSlotIndices.size() > MAX_ITEM_SLOTS) {
            throw new NetworkPayloadException("itemSlotIndices exceeds " + MAX_ITEM_SLOTS);
        }
        List<Integer> copy = new ArrayList<>(itemSlotIndices.size());
        for (Integer index : itemSlotIndices) {
            if (index == null || index < 0 || index > 35) {
                throw new NetworkPayloadException("item slot index out of range");
            }
            copy.add(index);
        }
        itemSlotIndices = List.copyOf(copy);
    }

    public static void encode(MailSendPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.to(), MAX_TO);
        NetworkPayloadLimits.writeUtf(buffer, message.subject(), MAX_SUBJECT);
        NetworkPayloadLimits.writeUtf(buffer, message.body(), MAX_BODY);
        buffer.writeLong(message.moneyAttachment());
        buffer.writeVarInt(message.itemSlotIndices().size());
        for (int index : message.itemSlotIndices()) {
            buffer.writeInt(index);
        }
    }

    public static MailSendPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        String to = NetworkPayloadLimits.readUtf(buffer, MAX_TO);
        String subject = NetworkPayloadLimits.readUtf(buffer, MAX_SUBJECT);
        String body = NetworkPayloadLimits.readUtf(buffer, MAX_BODY);
        long money = buffer.readLong();
        int slotCount = buffer.readVarInt();
        if (slotCount > MAX_ITEM_SLOTS) {
            throw new NetworkPayloadException("itemSlotIndices exceeds " + MAX_ITEM_SLOTS);
        }
        List<Integer> slots = new ArrayList<>(slotCount);
        for (int index = 0; index < slotCount; index++) {
            slots.add(buffer.readInt());
        }
        return new MailSendPacket(to, subject, body, money, slots);
    }
}
