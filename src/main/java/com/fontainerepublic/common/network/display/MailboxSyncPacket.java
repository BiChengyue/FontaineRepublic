package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * S2C mailbox sync (FR-MAIL-001-A §2.4/§6.6, message ledger ID 20).
 *
 * <p>Carries a bounded projection of the receiver's mailbox (direct mail +
 * derived broadcasts) and the exact unread count. Display-only; never
 * authoritative.</p>
 */
public record MailboxSyncPacket(List<Entry> entries, int unreadCount, long at) {

    public static final int MAX_ENTRIES = 64;

    /** One bounded mail entry in the sync projection. */
    public record Entry(
            long mailId,
            boolean broadcast,
            String from,
            String subject,
            String body,
            long sentAt,
            boolean read,
            long moneyAttachment,
            boolean claimed,
            int itemSlotCount
    ) {
        public static final int MAX_FROM = 64;
        public static final int MAX_SUBJECT = 64;
        public static final int MAX_BODY = 2000;

        public Entry {
            if (mailId <= 0) {
                throw new NetworkPayloadException("mailId must be positive");
            }
            from = Objects.requireNonNull(from, "from");
            if (from.length() > MAX_FROM) {
                throw new NetworkPayloadException("from exceeds " + MAX_FROM + " characters");
            }
            subject = Objects.requireNonNull(subject, "subject");
            if (subject.length() > MAX_SUBJECT) {
                throw new NetworkPayloadException("subject exceeds " + MAX_SUBJECT + " characters");
            }
            body = Objects.requireNonNull(body, "body");
            if (body.length() > MAX_BODY) {
                throw new NetworkPayloadException("body exceeds " + MAX_BODY + " characters");
            }
            if (sentAt <= 0) {
                throw new NetworkPayloadException("sentAt must be positive");
            }
            if (moneyAttachment < 0) {
                throw new NetworkPayloadException("moneyAttachment must not be negative");
            }
            if (itemSlotCount < 0) {
                throw new NetworkPayloadException("itemSlotCount must not be negative");
            }
        }
    }

    public MailboxSyncPacket {
        if (entries == null || entries.size() > MAX_ENTRIES) {
            throw new NetworkPayloadException("entries must be at most " + MAX_ENTRIES);
        }
        if (unreadCount < 0) {
            throw new NetworkPayloadException("unreadCount must not be negative");
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp");
        }
        entries = List.copyOf(entries);
    }

    public static void encode(MailboxSyncPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(message.entries().size());
        for (Entry entry : message.entries()) {
            buffer.writeLong(entry.mailId());
            buffer.writeBoolean(entry.broadcast());
            NetworkPayloadLimits.writeUtf(buffer, entry.from(), Entry.MAX_FROM);
            NetworkPayloadLimits.writeUtf(buffer, entry.subject(), Entry.MAX_SUBJECT);
            NetworkPayloadLimits.writeUtf(buffer, entry.body(), Entry.MAX_BODY);
            buffer.writeLong(entry.sentAt());
            buffer.writeBoolean(entry.read());
            buffer.writeLong(entry.moneyAttachment());
            buffer.writeBoolean(entry.claimed());
            buffer.writeInt(entry.itemSlotCount());
        }
        buffer.writeInt(message.unreadCount());
        buffer.writeLong(message.at());
    }

    public static MailboxSyncPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int count = buffer.readVarInt();
        if (count > MAX_ENTRIES) {
            throw new NetworkPayloadException("entry count exceeds " + MAX_ENTRIES);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long mailId = buffer.readLong();
            boolean broadcast = buffer.readBoolean();
            String from = NetworkPayloadLimits.readUtf(buffer, Entry.MAX_FROM);
            String subject = NetworkPayloadLimits.readUtf(buffer, Entry.MAX_SUBJECT);
            String body = NetworkPayloadLimits.readUtf(buffer, Entry.MAX_BODY);
            long sentAt = buffer.readLong();
            boolean read = buffer.readBoolean();
            long moneyAttachment = buffer.readLong();
            boolean claimed = buffer.readBoolean();
            int itemSlotCount = buffer.readInt();
            entries.add(new Entry(
                    mailId, broadcast, from, subject, body, sentAt,
                    read, moneyAttachment, claimed, itemSlotCount
            ));
        }
        int unread = buffer.readInt();
        long at = buffer.readLong();
        return new MailboxSyncPacket(entries, unread, at);
    }
}
