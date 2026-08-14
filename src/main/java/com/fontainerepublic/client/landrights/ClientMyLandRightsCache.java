package com.fontainerepublic.client.landrights;

import com.fontainerepublic.common.landrights.MyLandRightsPagePacket;

import java.util.Objects;

/**
 * Non-authoritative in-memory page state of the "我的地块" view
 * (FR-LAND-002-A §5).
 *
 * <p>Holds only the <em>current</em> page of the authenticated player's own
 * rights plus the correlation watermark needed to accept only the matching
 * response: the client accepts only the response for the most recent request id
 * it issued, discards stale request ids and lower {@code storeRevision}, and
 * never regresses within an equal revision by an older request id/generatedAt.
 * The cache is presentation-only, cleared on logout/disconnect, and never
 * persisted.</p>
 */
public final class ClientMyLandRightsCache {

    private static final ClientMyLandRightsCache INSTANCE =
            new ClientMyLandRightsCache();

    private final Object lock = new Object();
    private int nextRequestId;
    private int pendingRequestId;
    private boolean accepted;
    private long acceptedStoreRevision;
    private long acceptedGeneratedAt;
    private int acceptedRequestId;
    private MyLandRightsPagePacket page;

    /**
     * Public so headless foundation tests can exercise correlation against an
     * isolated instance; production uses {@link #instance()}.
     */
    public ClientMyLandRightsCache() {
    }

    public static ClientMyLandRightsCache instance() {
        return INSTANCE;
    }

    /** Allocates a fresh positive request id (monotonic within the session). */
    public int allocateRequestId() {
        synchronized (lock) {
            nextRequestId++;
            if (nextRequestId <= 0) {
                nextRequestId = 1;
            }
            return nextRequestId;
        }
    }

    /** Records the request id the screen is currently awaiting. */
    public void markPendingRequest(int requestId) {
        synchronized (lock) {
            pendingRequestId = requestId;
        }
    }

    /** The request id the screen most recently sent, or 0 before any. */
    public int pendingRequestId() {
        synchronized (lock) {
            return pendingRequestId;
        }
    }

    /**
     * Correlation acceptance (FR-LAND-002-A §5.3): true only when the packet is
     * the response to the current request and not a regression of an older
     * page. Accepted pages update the cached page; stale/lower ones are
     * discarded.
     */
    public boolean accept(MyLandRightsPagePacket packet) {
        Objects.requireNonNull(packet, "packet");
        synchronized (lock) {
            // Only the response to the current pending request is a candidate.
            if (packet.requestId() != pendingRequestId) {
                return false;
            }
            // A lower store revision never overwrites a newer one.
            if (accepted && packet.storeRevision() < acceptedStoreRevision) {
                return false;
            }
            // Within an equal revision an older page does not regress the view.
            if (accepted && packet.storeRevision() == acceptedStoreRevision
                    && packet.generatedAt() < acceptedGeneratedAt) {
                return false;
            }
            accepted = true;
            acceptedStoreRevision = packet.storeRevision();
            acceptedGeneratedAt = packet.generatedAt();
            acceptedRequestId = packet.requestId();
            page = packet;
            return true;
        }
    }

    /** The current accepted page, or {@code null} before the first response. */
    public MyLandRightsPagePacket page() {
        synchronized (lock) {
            return page;
        }
    }

    /** Whether a page has been accepted (false once a reset is signalled). */
    public boolean hasPage() {
        synchronized (lock) {
            return page != null;
        }
    }

    /**
     * Clears the temporary page state (close / disconnect / new first-page
     * request). The <em>next</em> first-page request always starts from
     * {@code expectedStoreRevision == 0} and a fresh request id.
     */
    public void clear() {
        synchronized (lock) {
            page = null;
            accepted = false;
            acceptedStoreRevision = 0L;
            acceptedGeneratedAt = 0L;
            acceptedRequestId = 0;
            pendingRequestId = 0;
        }
    }
}
