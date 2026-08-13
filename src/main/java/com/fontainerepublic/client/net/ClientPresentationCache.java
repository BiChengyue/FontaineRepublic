package com.fontainerepublic.client.net;

import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Non-authoritative in-memory presentation cache (FR-CLIENT-001-A §3/§4.3).
 *
 * <p>This cache is display-only and never participates in any decision,
 * permission check, or mutation. It is populated exclusively from S2C
 * presentation payloads, is bounded (a fixed-size transaction ring), and is
 * cleared on logout ({@link #clear()}) so a later login never shows stale
 * presentation. No data here is persisted.</p>
 */
public final class ClientPresentationCache {

    /** Bounded presentation transaction ring size. */
    public static final int MAX_TRANSACTIONS = 50;

    private static final ClientPresentationCache INSTANCE = new ClientPresentationCache();

    private final Object lock = new Object();
    private BalanceSyncPacket balance;
    private final List<TransactionNotifyPacket> transactions = new ArrayList<>();
    private final List<NotificationPacket.NotificationEntry> notifications = new ArrayList<>();

    private ClientPresentationCache() {
    }

    public static ClientPresentationCache instance() {
        return INSTANCE;
    }

    /** Replaces the balance snapshot (idempotent presentation update). */
    public void setBalance(BalanceSyncPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            balance = snapshot;
        }
    }

    /** Appends one transaction notice, dropping the oldest beyond the bound. */
    public void appendTransaction(TransactionNotifyPacket notice) {
        Objects.requireNonNull(notice, "notice");
        synchronized (lock) {
            transactions.add(notice);
            while (transactions.size() > MAX_TRANSACTIONS) {
                transactions.remove(0);
            }
        }
    }

    /** Replaces the pending-notification presentation (never appends). */
    public void setNotifications(List<NotificationPacket.NotificationEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        synchronized (lock) {
            notifications.clear();
            notifications.addAll(entries);
        }
    }

    /** Clears every presentation entry on logout/disconnect. */
    public void clear() {
        synchronized (lock) {
            balance = null;
            transactions.clear();
            notifications.clear();
        }
    }

    public BalanceSyncPacket balanceSnapshot() {
        synchronized (lock) {
            return balance;
        }
    }

    public List<TransactionNotifyPacket> transactionNotices() {
        synchronized (lock) {
            return List.copyOf(transactions);
        }
    }

    public List<NotificationPacket.NotificationEntry> notificationEntries() {
        synchronized (lock) {
            return List.copyOf(notifications);
        }
    }
}
