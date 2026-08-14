package com.fontainerepublic.server.trade.api;

import com.fontainerepublic.server.trade.model.TradeSession;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative communicator trade service (FR-TRADE-001-A §4, intent
 * model).
 *
 * <p>Every method runs on the logical main server thread (the C2S dispatcher
 * and the server tick guarantee serialization). The service holds only
 * in-memory intent sessions; money is never pre-escrowed and items never
 * leave the owners' inventories before the atomic execution, so cancel /
 * disconnect / shutdown simply drops the session with nothing to refund. All
 * state changes are pushed to both parties as per-viewer
 * {@code TradeStateSyncPacket} snapshots; the client only displays and
 * submits intentions.</p>
 *
 * <p>Authority rules enforced at this boundary: both parties must be online
 * and hold the communicator (re-validated at execution), the acting player
 * must be a session member, amounts and slot indices are bounded, and the
 * execution atomically re-validates every offered source slot (dupe guard)
 * and the receiving inventory capacity before committing the economy
 * settlement ({@code floor(offer * taxRate / 100)} per paying side) and
 * moving the items.</p>
 */
public interface TradeService {

    /** Initiates a session: {@code REQUESTED} (actor → target). */
    void request(UUID actor, UUID target);

    /** Acceptance of the invited player: {@code REQUESTED -> OPEN} or
     *  {@code CANCELLED} on refusal. */
    void respond(UUID actor, long sessionId, boolean accept);

    /** Covering money offer (0 clears); any offer change resets both
     *  agreement flags and reverts {@code LOCKED -> OPEN}. */
    void offerMoney(UUID actor, long sessionId, long amount);

    /** Item offer (source main-inventory index, or {@code -1} to withdraw);
     *  any offer change resets both agreement flags and reverts
     *  {@code LOCKED -> OPEN}. */
    void offerItem(UUID actor, long sessionId, int slot, int inventoryIndex);

    /** Agreement toggle; both sides agreed enters the 5-second
     *  {@code LOCKED} window, any toggle during the window reverts to
     *  {@code OPEN} and restarts the countdown. */
    void agree(UUID actor, long sessionId, boolean agree);

    /** Cancellation by either party at any moment before execution. */
    void cancel(UUID actor, long sessionId);

    /** Server-tick driver: advances the {@code LOCKED} countdown and
     *  executes confirmed sessions; also drops sessions of players that went
     *  offline. */
    void tick();

    /** Disconnect hook: cancels every session of the player (the
     *  counterparty is notified; nothing to refund). */
    void playerDisconnected(UUID playerId);

    /** Server-stopping hook: cancels every remaining session. */
    void shutdown();

    /** The active session of the player, if any (read surface for the
     *  presentation layer and tests). */
    Optional<TradeSession> sessionOf(UUID playerId);

    /** Number of live sessions (tests / diagnostics). */
    int activeSessionCount();
}
