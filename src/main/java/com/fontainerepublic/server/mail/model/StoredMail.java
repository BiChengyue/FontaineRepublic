package com.fontainerepublic.server.mail.model;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable content of one stored mail message (FR-MAIL-001-A §2.1/§6.2).
 *
 * <p>Direct mail (sent to one {@code toKey} mailbox) and broadcast mail
 * (on to every citizen) are both stored once, keyed by the global
 * {@code mailId}. {@code broadcast} distinguishes the two; a broadcast
 * carries {@code null} {@code toKey} (delivered by derivation, never into a
 * mailbox inbox) and never carries attachments. Money attachment is an
 * intention ({@code moneyAttachment > 0} means "credit this much at claim",
 * not a pre-escrow); item attachments (≤4 slots) hold the items that were
 * moved from the sender's inventory at send time. Item slots whose stacks are
 * moved into the receiver's inventory at claim are cleared in place (the
 * message's item list is copied on each claim).</p>
 */
public final class StoredMail {

    /** Hard cap on body length (FR-MAIL-001-A §2.1). */
    public static final int MAX_BODY = 2000;
    /** Hard cap on subject length. */
    public static final int MAX_SUBJECT = 64;
    /** Hard cap on money attachment. */
    public static final long MAX_MONEY_ATTACHMENT = 1_000_000_000L;
    /** Hard cap on item attachment slots. */
    public static final int MAX_ITEM_SLOTS = 4;

    private final long mailId;
    private final boolean broadcast;
    private final String fromKey;
    private final String toKey; // nullable for broadcast
    private final String subject;
    private final String body;
    private final long sentAt;
    private final long moneyAttachment;
    private final List<ItemStack> itemSlots;

    public StoredMail(
            long mailId,
            boolean broadcast,
            String fromKey,
            String toKey,
            String subject,
            String body,
            long sentAt,
            long moneyAttachment,
            List<ItemStack> itemSlots
    ) {
        if (mailId <= 0) {
            throw new IllegalArgumentException("mailId must be positive");
        }
        this.mailId = mailId;
        this.fromKey = Objects.requireNonNull(fromKey, "fromKey");
        if (broadcast) {
            this.toKey = null;
        } else {
            this.toKey = Objects.requireNonNull(toKey, "toKey");
        }
        this.broadcast = broadcast;
        if (subject == null || subject.length() > MAX_SUBJECT) {
            throw new IllegalArgumentException(
                    "subject must be present and at most " + MAX_SUBJECT + " characters"
            );
        }
        this.subject = subject;
        if (body == null || body.length() > MAX_BODY) {
            throw new IllegalArgumentException(
                    "body must be present and at most " + MAX_BODY + " characters"
            );
        }
        this.body = body;
        if (sentAt <= 0) {
            throw new IllegalArgumentException("sentAt must be positive");
        }
        this.sentAt = sentAt;
        if (moneyAttachment < 0 || moneyAttachment > MAX_MONEY_ATTACHMENT) {
            throw new IllegalArgumentException(
                    "moneyAttachment must be within [0, " + MAX_MONEY_ATTACHMENT + "]"
            );
        }
        if (broadcast && (moneyAttachment > 0 || !itemSlots.isEmpty())) {
            throw new IllegalArgumentException(
                    "broadcast mail cannot carry attachments"
            );
        }
        this.moneyAttachment = moneyAttachment;
        if (itemSlots == null || itemSlots.size() > MAX_ITEM_SLOTS) {
            throw new IllegalArgumentException(
                    "itemSlots must be at most " + MAX_ITEM_SLOTS + " entries"
            );
        }
        List<ItemStack> frozen = new ArrayList<>(MAX_ITEM_SLOTS);
        for (ItemStack stack : itemSlots) {
            frozen.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        this.itemSlots = Collections.unmodifiableList(frozen);
    }

    public long mailId() {
        return mailId;
    }

    public boolean broadcast() {
        return broadcast;
    }

    public String fromKey() {
        return fromKey;
    }

    public String toKey() {
        return toKey;
    }

    public String subject() {
        return subject;
    }

    public String body() {
        return body;
    }

    public long sentAt() {
        return sentAt;
    }

    public long moneyAttachment() {
        return moneyAttachment;
    }

    public List<ItemStack> itemSlots() {
        return itemSlots;
    }

    /** True when every attachment has been fully delivered (money cleared
     *  and every item slot moved out). Used to decide the anti-dup
     *  irreversible "claimed" state. */
    public boolean fullyClaimed() {
        return moneyAttachment == 0 && allItemsEmpty();
    }

    private boolean allItemsEmpty() {
        for (ItemStack stack : itemSlots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Copy with the money-attachment intention cleared (post-delivery). */
    public StoredMail withMoneyDelivered() {
        return new StoredMail(
                mailId,
                broadcast,
                fromKey,
                toKey,
                subject,
                body,
                sentAt,
                0L,
                itemSlots
        );
    }

    /** Copy with the item slot at {@code index} cleared. */
    public StoredMail withItemDelivered(int index) {
        if (index < 0 || index >= itemSlots.size()) {
            throw new IllegalArgumentException("item slot out of range: " + index);
        }
        List<ItemStack> next = new ArrayList<>(itemSlots);
        next.set(index, ItemStack.EMPTY);
        return new StoredMail(
                mailId,
                broadcast,
                fromKey,
                toKey,
                subject,
                body,
                sentAt,
                moneyAttachment,
                next
        );
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StoredMail that
                && this.mailId == that.mailId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(mailId);
    }
}
