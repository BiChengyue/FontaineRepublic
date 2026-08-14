package com.fontainerepublic.server.mail.persistence;

import com.fontainerepublic.server.mail.model.MailboxEntry;
import com.fontainerepublic.server.mail.model.MailboxState;
import com.fontainerepublic.server.mail.model.StoredMail;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "mail"} namespace
 * (FR-MAIL-001-A §2.1). Encoding writes every index in lexical key order;
 * decoding accepts only declared fields with exact NBT types and constructs
 * the validating {@link MailStoreSnapshot}. Item attachment slots are
 * serialized with the Forge {@link ItemStack#save(CompoundTag)} format, so
 * messages carrying items are only live on a running server (the headless
 * foundation test exercises the item-free paths). Unknown newer versions are
 * rejected; nothing is ever auto-repaired.
 */
public final class MailNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String NEXT_MAIL_ID = "NextMailId";
    private static final String MESSAGES = "Messages";
    private static final String MAILBOXES = "Mailboxes";

    private static final String MAIL_ID = "MailId";
    private static final String BROADCAST = "Broadcast";
    private static final String FROM_KEY = "FromKey";
    private static final String TO_KEY = "ToKey";
    private static final String SUBJECT = "Subject";
    private static final String BODY = "Body";
    private static final String SENT_AT = "SentAt";
    private static final String MONEY_ATTACHMENT = "MoneyAttachment";
    private static final String ITEM_SLOTS = "ItemSlots";

    private static final String BOX_INBOX = "Inbox";
    private static final String BOX_BROADCAST_READ = "BroadcastRead";
    private static final String ENTRY_READ = "Read";
    private static final String ENTRY_MONEY_DELIVERED = "MoneyDelivered";

    private static final int HARD_MAX_MESSAGES = 200_000;
    private static final int HARD_MAX_MAILBOXES = 20_000;
    private static final int HARD_MAX_INBOX = 100;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            NEXT_MAIL_ID,
            MESSAGES,
            MAILBOXES
    );
    private static final Set<String> MESSAGE_KEYS = Set.of(
            MAIL_ID,
            BROADCAST,
            FROM_KEY,
            TO_KEY,
            SUBJECT,
            BODY,
            SENT_AT,
            MONEY_ATTACHMENT,
            ITEM_SLOTS
    );
    private static final Set<String> BOX_KEYS = Set.of(
            BOX_INBOX,
            BOX_BROADCAST_READ
    );
    private static final Set<String> ENTRY_KEYS = Set.of(
            MAIL_ID,
            ENTRY_READ,
            ENTRY_MONEY_DELIVERED
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    public MailStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "mail namespace is empty; it must be a valid initialized store"
            );
        }
        requireOnlyKeys(root, STORE_KEYS, "mail");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "mail");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "mail");
        requireType(root, NEXT_MAIL_ID, Tag.TAG_LONG, "mail");
        requireType(root, MESSAGES, Tag.TAG_COMPOUND, "mail");
        requireType(root, MAILBOXES, Tag.TAG_COMPOUND, "mail");

        int storeVersion = root.getInt(STORE_VERSION);
        long storeRevision = root.getLong(STORE_REVISION);
        long nextMailId = root.getLong(NEXT_MAIL_ID);

        Map<Long, StoredMail> messages = decodeMessages(
                root.getCompound(MESSAGES), nextMailId
        );
        Map<String, MailboxState> mailboxes = decodeMailboxes(
                root.getCompound(MAILBOXES)
        );
        return new MailStoreSnapshot(
                storeVersion,
                storeRevision,
                nextMailId,
                messages,
                mailboxes
        );
    }

    private Map<Long, StoredMail> decodeMessages(CompoundTag tag, long nextMailId) {
        if (tag.getAllKeys().size() > HARD_MAX_MESSAGES) {
            throw invalid("Message count exceeds " + HARD_MAX_MESSAGES);
        }
        Map<Long, StoredMail> messages = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            long id = parseMailId(key, "Messages");
            requireType(tag, key, Tag.TAG_COMPOUND, "Messages");
            messages.put(id, decodeMessage(tag.getCompound(key), id));
        }
        for (long id : messages.keySet()) {
            if (id >= nextMailId) {
                throw invalid("Message id " + id + " is not below nextMailId " + nextMailId);
            }
        }
        return messages;
    }

    private StoredMail decodeMessage(CompoundTag tag, long expectedId) {
        requireOnlyKeys(tag, MESSAGE_KEYS, "message " + expectedId);
        requireType(tag, MAIL_ID, Tag.TAG_LONG, "message " + expectedId);
        requireType(tag, BROADCAST, Tag.TAG_BYTE, "message " + expectedId);
        requireType(tag, FROM_KEY, Tag.TAG_STRING, "message " + expectedId);
        requireType(tag, SUBJECT, Tag.TAG_STRING, "message " + expectedId);
        requireType(tag, BODY, Tag.TAG_STRING, "message " + expectedId);
        requireType(tag, SENT_AT, Tag.TAG_LONG, "message " + expectedId);
        requireType(tag, MONEY_ATTACHMENT, Tag.TAG_LONG, "message " + expectedId);

        long storedId = tag.getLong(MAIL_ID);
        if (storedId != expectedId) {
            throw invalid(
                    "Messages key " + expectedId + " does not match record id " + storedId
            );
        }
        boolean broadcast = tag.getBoolean(BROADCAST);
        String fromKey = tag.getString(FROM_KEY);
        String toKey = tag.contains(TO_KEY) ? tag.getString(TO_KEY) : null;
        long moneyAttachment = tag.getLong(MONEY_ATTACHMENT);
        List<ItemStack> itemSlots = decodeItemSlots(tag, broadcast);
        try {
            return new StoredMail(
                    expectedId,
                    broadcast,
                    fromKey,
                    toKey,
                    tag.getString(SUBJECT),
                    tag.getString(BODY),
                    tag.getLong(SENT_AT),
                    moneyAttachment,
                    itemSlots
            );
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid message " + expectedId + ": " + failure.getMessage(), failure);
        }
    }

    private List<ItemStack> decodeItemSlots(CompoundTag tag, boolean broadcast) {
        if (!tag.contains(ITEM_SLOTS)) {
            return List.of();
        }
        ListTag list = tag.getList(ITEM_SLOTS, Tag.TAG_COMPOUND);
        if (list.size() > StoredMail.MAX_ITEM_SLOTS) {
            throw invalid("Item slot count exceeds " + StoredMail.MAX_ITEM_SLOTS);
        }
        List<ItemStack> slots = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag itemTag = list.getCompound(index);
            if (itemTag.isEmpty()) {
                slots.add(ItemStack.EMPTY);
            } else {
                slots.add(ItemStack.of(itemTag));
            }
        }
        if (broadcast && !slots.isEmpty()) {
            throw invalid("broadcast mail must not carry item attachments");
        }
        return slots;
    }

    private Map<String, MailboxState> decodeMailboxes(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_MAILBOXES) {
            throw invalid("Mailbox count exceeds " + HARD_MAX_MAILBOXES);
        }
        Map<String, MailboxState> mailboxes = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            requireType(tag, key, Tag.TAG_COMPOUND, "Mailboxes");
            mailboxes.put(key, decodeMailbox(tag.getCompound(key)));
        }
        return mailboxes;
    }

    private MailboxState decodeMailbox(CompoundTag tag) {
        requireOnlyKeys(tag, BOX_KEYS, "mailbox");
        requireType(tag, BOX_INBOX, Tag.TAG_LIST, "mailbox");
        requireType(tag, BOX_BROADCAST_READ, Tag.TAG_LIST, "mailbox");
        ListTag inboxTag = tag.getList(BOX_INBOX, Tag.TAG_COMPOUND);
        if (inboxTag.size() > HARD_MAX_INBOX) {
            throw invalid("Inbox size exceeds " + HARD_MAX_INBOX);
        }
        List<MailboxEntry> inbox = new ArrayList<>(inboxTag.size());
        for (int index = 0; index < inboxTag.size(); index++) {
            inbox.add(decodeEntry(inboxTag.getCompound(index)));
        }
        ListTag broadcastTag = tag.getList(BOX_BROADCAST_READ, Tag.TAG_LONG);
        List<Long> broadcastRead = new ArrayList<>(broadcastTag.size());
        for (int index = 0; index < broadcastTag.size(); index++) {
            net.minecraft.nbt.NumericTag number =
                    (net.minecraft.nbt.NumericTag) broadcastTag.get(index);
            broadcastRead.add(number.getAsLong());
        }
        return new MailboxState(inbox, broadcastRead);
    }

    private MailboxEntry decodeEntry(CompoundTag tag) {
        requireOnlyKeys(tag, ENTRY_KEYS, "inbox entry");
        requireType(tag, MAIL_ID, Tag.TAG_LONG, "inbox entry");
        requireType(tag, ENTRY_READ, Tag.TAG_BYTE, "inbox entry");
        requireType(tag, ENTRY_MONEY_DELIVERED, Tag.TAG_BYTE, "inbox entry");
        return new MailboxEntry(
                tag.getLong(MAIL_ID),
                tag.getBoolean(ENTRY_READ),
                tag.getBoolean(ENTRY_MONEY_DELIVERED)
        );
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    public CompoundTag encode(MailStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());
        root.putLong(NEXT_MAIL_ID, snapshot.nextMailId());

        CompoundTag messagesTag = new CompoundTag();
        TreeMap<String, StoredMail> orderedMessages = new TreeMap<>();
        snapshot.messages().forEach(
                (id, mail) -> orderedMessages.put(Long.toString(id), mail)
        );
        orderedMessages.forEach(
                (key, mail) -> messagesTag.put(key, encodeMessage(mail))
        );
        root.put(MESSAGES, messagesTag);

        CompoundTag mailboxesTag = new CompoundTag();
        TreeMap<String, MailboxState> orderedMailboxes = new TreeMap<>(snapshot.mailboxes());
        orderedMailboxes.forEach((key, box) -> mailboxesTag.put(key, encodeMailbox(box)));
        root.put(MAILBOXES, mailboxesTag);
        return root;
    }

    private CompoundTag encodeMessage(StoredMail mail) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(MAIL_ID, mail.mailId());
        tag.putBoolean(BROADCAST, mail.broadcast());
        tag.putString(FROM_KEY, mail.fromKey());
        if (mail.toKey() != null) {
            tag.putString(TO_KEY, mail.toKey());
        }
        tag.putString(SUBJECT, mail.subject());
        tag.putString(BODY, mail.body());
        tag.putLong(SENT_AT, mail.sentAt());
        tag.putLong(MONEY_ATTACHMENT, mail.moneyAttachment());
        if (!mail.itemSlots().isEmpty()) {
            tag.put(ITEM_SLOTS, encodeItemSlots(mail.itemSlots()));
        }
        return tag;
    }

    private static ListTag encodeItemSlots(List<ItemStack> slots) {
        ListTag list = new ListTag();
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                list.add(new CompoundTag());
            } else {
                list.add(stack.save(new CompoundTag()));
            }
        }
        return list;
    }

    private CompoundTag encodeMailbox(MailboxState box) {
        CompoundTag tag = new CompoundTag();
        ListTag inboxTag = new ListTag();
        for (MailboxEntry entry : box.inbox()) {
            inboxTag.add(encodeEntry(entry));
        }
        tag.put(BOX_INBOX, inboxTag);
        ListTag broadcastTag = new ListTag();
        for (long id : box.broadcastRead()) {
            broadcastTag.add(net.minecraft.nbt.LongTag.valueOf(id));
        }
        tag.put(BOX_BROADCAST_READ, broadcastTag);
        return tag;
    }

    private static CompoundTag encodeEntry(MailboxEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(MAIL_ID, entry.mailId());
        tag.putBoolean(ENTRY_READ, entry.read());
        tag.putBoolean(ENTRY_MONEY_DELIVERED, entry.moneyDelivered());
        return tag;
    }

    // ------------------------------------------------------------------
    // size
    // ------------------------------------------------------------------

    /** Serialized (uncompressed) size of an encoded namespace snapshot. */
    public int encodedSize(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private long parseMailId(String value, String path) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw invalid(path + " mail id must be positive: " + value);
            }
            return id;
        } catch (NumberFormatException failure) {
            throw invalid("Invalid " + path + " mail key: " + value, failure);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(
                    path + " is missing required field " + key + " or has the wrong type"
            );
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static MailNbtException invalid(String message) {
        return new MailNbtException(message);
    }

    private static MailNbtException invalid(String message, Throwable cause) {
        return new MailNbtException(message, cause);
    }
}
