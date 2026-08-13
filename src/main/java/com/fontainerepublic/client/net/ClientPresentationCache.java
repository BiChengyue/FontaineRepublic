package com.fontainerepublic.client.net;

import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.common.network.display.GovernmentInfoPacket;
import com.fontainerepublic.common.network.display.JusticeInfoPacket;
import com.fontainerepublic.common.network.display.LandInfoPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.ParliamentInfoPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Non-authoritative in-memory presentation cache (FR-CLIENT-001-A §3/§4.3).
 *
 * <p>This cache is display-only and never participates in any decision,
 * permission check, or mutation. It is populated exclusively from S2C
 * presentation payloads, is bounded (a fixed-size transaction ring and
 * bounded history/citizen snapshots), and is cleared on logout
 * ({@link #clear()}) so a later login never shows stale presentation. No data
 * here is persisted.</p>
 */
public final class ClientPresentationCache {

    /** Bounded presentation transaction ring size. */
    public static final int MAX_TRANSACTIONS = 50;

    /** Bounded presentation history page size (server pages are ≤128). */
    public static final int MAX_HISTORY_ENTRIES = 128;

    private static final ClientPresentationCache INSTANCE = new ClientPresentationCache();

    private final Object lock = new Object();
    private BalanceSyncPacket balance;
    private CitizenInfoPacket citizen;
    private TransactionHistorySyncPacket history;
    private GovernmentInfoPacket government;
    private ParliamentInfoPacket parliament;
    private JusticeInfoPacket justice;
    private LandInfoPacket land;
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

    /** Replaces the citizen-identity card snapshot (idempotent update). */
    public void setCitizen(CitizenInfoPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            citizen = snapshot;
        }
    }

    /** Replaces the transaction-history page snapshot (idempotent update). */
    public void setHistory(TransactionHistorySyncPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            history = snapshot;
        }
    }

    /** Replaces the government public-summary snapshot (idempotent update). */
    public void setGovernment(GovernmentInfoPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            government = snapshot;
        }
    }

    /** Replaces the parliament public-summary snapshot (idempotent update). */
    public void setParliament(ParliamentInfoPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            parliament = snapshot;
        }
    }

    /** Replaces the court public-summary snapshot (idempotent update). */
    public void setJustice(JusticeInfoPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            justice = snapshot;
        }
    }

    /** Replaces the land public-overview snapshot (idempotent update). */
    public void setLand(LandInfoPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            land = snapshot;
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
            citizen = null;
            history = null;
            government = null;
            parliament = null;
            justice = null;
            land = null;
            transactions.clear();
            notifications.clear();
        }
    }

    public BalanceSyncPacket balanceSnapshot() {
        synchronized (lock) {
            return balance;
        }
    }

    public CitizenInfoPacket citizenSnapshot() {
        synchronized (lock) {
            return citizen;
        }
    }

    public TransactionHistorySyncPacket historySnapshot() {
        synchronized (lock) {
            return history;
        }
    }

    public GovernmentInfoPacket governmentSnapshot() {
        synchronized (lock) {
            return government;
        }
    }

    public ParliamentInfoPacket parliamentSnapshot() {
        synchronized (lock) {
            return parliament;
        }
    }

    public JusticeInfoPacket justiceSnapshot() {
        synchronized (lock) {
            return justice;
        }
    }

    public LandInfoPacket landSnapshot() {
        synchronized (lock) {
            return land;
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
