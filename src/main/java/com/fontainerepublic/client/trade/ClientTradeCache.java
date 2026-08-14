package com.fontainerepublic.client.trade;

import com.fontainerepublic.common.network.display.TradeStateSyncPacket;

import java.util.Objects;

/**
 * Non-authoritative in-memory trade-session cache (FR-TRADE-001-A §5).
 *
 * <p>Holds only the latest per-viewer {@link TradeStateSyncPacket} snapshot
 * received from the server; the client never decides any trade state. The
 * cache is a single session (the server allows one active session per
 * player), is cleared on logout and never persisted.</p>
 */
public final class ClientTradeCache {

    private static final ClientTradeCache INSTANCE = new ClientTradeCache();

    private final Object lock = new Object();
    private TradeStateSyncPacket snapshot;
    private long lastSeenSessionId = -1L;
    private boolean ownRequestInFlight;

    private ClientTradeCache() {
    }

    public static ClientTradeCache instance() {
        return INSTANCE;
    }

    /** Replaces the latest trade snapshot. */
    public void setSnapshot(TradeStateSyncPacket message) {
        Objects.requireNonNull(message, "message");
        synchronized (lock) {
            snapshot = message;
            lastSeenSessionId = message.sessionId();
        }
    }

    /** The latest snapshot, or {@code null} before the first sync. */
    public TradeStateSyncPacket snapshot() {
        synchronized (lock) {
            return snapshot;
        }
    }

    /**
     * True while a request sent by this client has not yet produced a
     * session snapshot (used only to render the "waiting for reply" hint;
     * the server remains authoritative).
     */
    public boolean ownRequestInFlight() {
        synchronized (lock) {
            return ownRequestInFlight;
        }
    }

    /** Marks a locally initiated request (pure UI hint). */
    public void markOwnRequestInFlight() {
        synchronized (lock) {
            ownRequestInFlight = true;
        }
    }

    /** Clears the locally initiated request hint once a session snapshot
     *  arrives (or the request was refused). */
    public void clearOwnRequestInFlight() {
        synchronized (lock) {
            ownRequestInFlight = false;
        }
    }

    /** Clears every cached state (logout / disconnect). */
    public void clear() {
        synchronized (lock) {
            snapshot = null;
            lastSeenSessionId = -1L;
            ownRequestInFlight = false;
        }
    }
}
