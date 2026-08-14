package com.fontainerepublic.client.mail;

import com.fontainerepublic.common.network.display.MailboxSyncPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Non-authoritative in-memory mail cache (FR-MAIL-001-A §5/§6.5).
 *
 * <p>Holds only the latest {@link MailboxSyncPacket} projection and the last
 * unread count from the server; the client never decides any mail state. The
 * cache is cleared on logout and never persisted.
 */
public final class ClientMailCache {

    private static final ClientMailCache INSTANCE = new ClientMailCache();

    private final Object lock = new Object();
    private MailboxSyncPacket sync;
    private int lastUnread = 0;
    private String lastSummary = "";

    private ClientMailCache() {
    }

    public static ClientMailCache instance() {
        return INSTANCE;
    }

    /** Replaces the latest mailbox sync projection. */
    public void setSync(MailboxSyncPacket message) {
        Objects.requireNonNull(message, "message");
        synchronized (lock) {
            sync = message;
            lastUnread = message.unreadCount();
        }
    }

    /** Marks the current unread count and a reminder summary. */
    public void setAlert(int unreadCount, String summary) {
        synchronized (lock) {
            lastUnread = unreadCount;
            lastSummary = summary == null ? "" : summary;
        }
    }

    public MailboxSyncPacket sync() {
        synchronized (lock) {
            return sync;
        }
    }

    public int unread() {
        synchronized (lock) {
            return lastUnread;
        }
    }

    public String lastSummary() {
        synchronized (lock) {
            return lastSummary;
        }
    }

    /** True when there is at least one unread mail (badge gate). */
    public boolean hasUnread() {
        synchronized (lock) {
            return lastUnread > 0;
        }
    }

    /** Clears every cached state (logout / disconnect). */
    public void clear() {
        synchronized (lock) {
            sync = null;
            lastUnread = 0;
            lastSummary = "";
        }
    }
}
