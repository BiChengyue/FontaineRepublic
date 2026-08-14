package com.fontainerepublic.client.landclaim;

import com.fontainerepublic.common.landclaim.LandClaimResultPacket;
import com.fontainerepublic.common.landclaim.LandInspectResultPacket;

import java.util.Objects;

/**
 * Non-authoritative in-memory land-claim cache (FR-LAND-CLAIM-001-A §4/§5).
 *
 * <p>Holds only the latest inspection result and the latest claim result the
 * {@code LandLocationScreen} renders; the client never decides any land state.
 * Cleared on logout and never persisted.</p>
 */
public final class ClientLandClaimCache {

    private static final ClientLandClaimCache INSTANCE = new ClientLandClaimCache();

    private final Object lock = new Object();
    private LandInspectResultPacket inspectResult;
    private LandClaimResultPacket claimResult;

    private ClientLandClaimCache() {
    }

    public static ClientLandClaimCache instance() {
        return INSTANCE;
    }

    /** Replaces the latest inspection result. */
    public void setInspectResult(LandInspectResultPacket result) {
        Objects.requireNonNull(result, "result");
        synchronized (lock) {
            inspectResult = result;
        }
    }

    /** The latest inspection result, or {@code null} before any arrives. */
    public LandInspectResultPacket inspectResult() {
        synchronized (lock) {
            return inspectResult;
        }
    }

    /** Replaces the latest claim result. */
    public void setClaimResult(LandClaimResultPacket result) {
        Objects.requireNonNull(result, "result");
        synchronized (lock) {
            claimResult = result;
        }
    }

    /** The latest claim result, or {@code null} before any arrives. */
    public LandClaimResultPacket claimResult() {
        synchronized (lock) {
            return claimResult;
        }
    }

    /** Clears every cached state (logout / disconnect). */
    public void clear() {
        synchronized (lock) {
            inspectResult = null;
            claimResult = null;
        }
    }
}
