package com.fontainerepublic.server.mail.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Durable state of one recipient mailbox (FR-MAIL-001-A §2.1).
 *
 * <p>{@code inbox} holds the direct-mail references (newest-first), bounded to
 * {@link #MAX_INBOX}; the oldest entry is pruned without affecting money/items
 * already delivered. {@code broadcastRead} is the bounded set of broadcast
 * mail ids the recipient has already read/suppressed, used to derive the
 * unread-broadcast count and to prevent re-alerts.</p>
 */
public final class MailboxState {

    /** Hard cap on direct-mail references per mailbox (FR-MAIL-001-A §2.1). */
    public static final int MAX_INBOX = 100;

    /** Hard cap on retained broadcast-read ids per mailbox. */
    public static final int MAX_BROADCAST_READ = 256;

    private final List<MailboxEntry> inbox;
    private final List<Long> broadcastRead;

    public MailboxState(List<MailboxEntry> inbox, List<Long> broadcastRead) {
        this.inbox = boundedInbox(inbox);
        this.broadcastRead = boundedBroadcastRead(broadcastRead);
    }

    public static MailboxState empty() {
        return new MailboxState(List.of(), List.of());
    }

    public List<MailboxEntry> inbox() {
        return Collections.unmodifiableList(inbox);
    }

    public List<Long> broadcastRead() {
        return Collections.unmodifiableList(broadcastRead);
    }

    public boolean isBroadcastRead(long mailId) {
        return broadcastRead.contains(mailId);
    }

    /** Returns a copy with a direct-mail reference appended (newest-first,
     *  bounded by {@link #MAX_INBOX}). */
    public MailboxState withInboxEntry(MailboxEntry entry) {
        List<MailboxEntry> next = new ArrayList<>();
        next.add(entry);
        next.addAll(inbox);
        return new MailboxState(boundedInbox(next), broadcastRead);
    }

    /** Returns a copy with the referenced direct-mail entry replaced. */
    public MailboxState replaceInboxEntry(MailboxEntry updated) {
        List<MailboxEntry> next = new ArrayList<>(inbox);
        for (int index = 0; index < next.size(); index++) {
            if (next.get(index).mailId() == updated.mailId()) {
                next.set(index, updated);
                break;
            }
        }
        return new MailboxState(boundedInbox(next), broadcastRead);
    }

    /** Returns a copy with the referenced direct-mail entry removed. */
    public MailboxState removeInboxEntry(long mailId) {
        List<MailboxEntry> next = new ArrayList<>(inbox);
        next.removeIf(entry -> entry.mailId() == mailId);
        return new MailboxState(boundedInbox(next), broadcastRead);
    }

    /** Returns a copy with the broadcast id marked as read (bounded). */
    public MailboxState withBroadcastRead(long mailId) {
        LinkedHashSet<Long> next = new LinkedHashSet<>(broadcastRead);
        next.add(mailId);
        return new MailboxState(inbox, boundedBroadcastRead(new ArrayList<>(next)));
    }

    private static List<MailboxEntry> boundedInbox(List<MailboxEntry> entries) {
        List<MailboxEntry> limited = new ArrayList<>(entries);
        while (limited.size() > MAX_INBOX) {
            limited.remove(limited.size() - 1);
        }
        return Collections.unmodifiableList(limited);
    }

    private static List<Long> boundedBroadcastRead(List<Long> ids) {
        LinkedHashSet<Long> set = new LinkedHashSet<>(ids);
        while (set.size() > MAX_BROADCAST_READ) {
            set.remove(set.iterator().next());
        }
        return Collections.unmodifiableList(new ArrayList<>(set));
    }
}
