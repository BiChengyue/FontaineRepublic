package com.fontainerepublic.server.trade.service;

import com.fontainerepublic.common.network.display.TradeStateSyncPacket;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.trade.api.ServerTradePlayerAccess;
import com.fontainerepublic.server.trade.api.TradeService;
import com.fontainerepublic.server.trade.model.TradeOfferSlot;
import com.fontainerepublic.server.trade.model.TradePhase;
import com.fontainerepublic.server.trade.model.TradeSession;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Main-thread-serialized implementation of the communicator trade state
 * machine (FR-TRADE-001-A §2/§4, intent model).
 *
 * <p>Sessions are in-memory only and hold intentions: offered money amounts
 * and offer slots (source index + snapshot). Money is never pre-escrowed and
 * items never leave the owners' inventories before execution, so cancel /
 * disconnect / shutdown simply drops the session with nothing to refund. The
 * 5-second {@code LOCKED} confirmation window is driven by the server tick;
 * execution atomically re-validates online presence, the communicator gate,
 * every offered source slot (dupe guard) and the receiving inventory
 * capacity, then commits the economy settlement
 * ({@code floor(offer * taxRate / 100)} per paying side, fail closed) and
 * moves the items — any validation failure rejects the whole trade. Every
 * state change pushes a per-viewer {@link TradeStateSyncPacket} to both
 * parties; the client only displays and submits intentions.</p>
 */
public final class DefaultTradeService implements TradeService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SETTLEMENT_MEMO = "communicator trade settlement";

    private final EconomyService economy;
    private final ServerTradePlayerAccess playerAccess;
    private final NetworkSendService sendService;
    private final LongSupplier tickSource;
    private final LongSupplier clock;
    private final int taxRatePercent;

    private final Map<Long, TradeSession> sessions = new LinkedHashMap<>();
    private long nextSessionId = 1L;

    public DefaultTradeService(
            EconomyService economy,
            ServerTradePlayerAccess playerAccess,
            NetworkSendService sendService,
            LongSupplier tickSource,
            LongSupplier clock,
            int taxRatePercent
    ) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.playerAccess = Objects.requireNonNull(playerAccess, "playerAccess");
        this.sendService = Objects.requireNonNull(sendService, "sendService");
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (taxRatePercent < 0 || taxRatePercent > 100) {
            throw new IllegalArgumentException(
                    "taxRatePercent must be within [0, 100]: " + taxRatePercent
            );
        }
        this.taxRatePercent = taxRatePercent;
    }

    // ------------------------------------------------------------------
    // state transitions (all on the logical main thread)
    // ------------------------------------------------------------------

    @Override
    public void request(UUID actor, UUID target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (actor.equals(target)) {
            message(actor, "You cannot trade with yourself.");
            return;
        }
        if (playerAccess.onlinePlayer(target).isEmpty()) {
            message(actor, "That player is not online.");
            return;
        }
        if (activeSessionOf(actor).isPresent() || activeSessionOf(target).isPresent()) {
            message(actor, "One of you is already in a trade session.");
            return;
        }
        if (!playerAccess.holdsCommunicator(actor)) {
            message(actor, "Hold the Portable Communicator to trade.");
            return;
        }
        if (!playerAccess.holdsCommunicator(target)) {
            message(actor, "The target must hold a Portable Communicator.");
            return;
        }
        long now = tickSource.getAsLong();
        TradeSession session = new TradeSession(
                nextSessionId++,
                actor,
                target,
                TradePhase.REQUESTED,
                0L,
                0L,
                emptySlots(),
                emptySlots(),
                false,
                false,
                0L,
                now,
                0L
        );
        sessions.put(session.sessionId(), session);
        pushToBoth(session);
    }

    @Override
    public void respond(UUID actor, long sessionId, boolean accept) {
        Objects.requireNonNull(actor, "actor");
        TradeSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (!session.isMember(actor)) {
            message(actor, "You are not part of this trade session.");
            return;
        }
        if (session.isInitiator(actor)) {
            message(actor, "Only the invited player may accept a trade request.");
            return;
        }
        if (session.phase() != TradePhase.REQUESTED) {
            return;
        }
        if (accept) {
            long now = tickSource.getAsLong();
            TradeSession opened = session.withPhase(
                    TradePhase.OPEN,
                    session.requestTick(),
                    now,
                    0L
            );
            sessions.put(sessionId, opened);
            pushToBoth(opened);
        } else {
            disposeCancelled(session, "The invited player declined the trade request.");
        }
    }

    @Override
    public void offerMoney(UUID actor, long sessionId, long amount) {
        Objects.requireNonNull(actor, "actor");
        TradeSession session = sessions.get(sessionId);
        if (!openOrLocked(session, actor)) {
            return;
        }
        if (amount < 0 || amount > TradeOfferMoneyPacket.MAX_OFFER_AMOUNT) {
            message(actor, "Offer amount is out of range.");
            return;
        }
        TradeSession updated = session
                .withMoney(actor, amount)
                .withAgree(session.initiator(), false)
                .withAgree(session.partner(), false);
        updated = revertLocked(updated);
        sessions.put(sessionId, updated);
        pushToBoth(updated);
    }

    @Override
    public void offerItem(UUID actor, long sessionId, int slot, int inventoryIndex) {
        Objects.requireNonNull(actor, "actor");
        TradeSession session = sessions.get(sessionId);
        if (!openOrLocked(session, actor)) {
            return;
        }
        if (slot < 0 || slot >= TradeOfferItemPacket.SLOT_COUNT) {
            message(actor, "Offer slot is out of range.");
            return;
        }
        TradeOfferSlot next;
        if (inventoryIndex == TradeOfferItemPacket.WITHDRAW_MARKER) {
            next = TradeOfferSlot.EMPTY;
        } else {
            if (inventoryIndex < 0 || inventoryIndex > TradeOfferItemPacket.MAX_INVENTORY_INDEX) {
                message(actor, "Inventory slot is out of range.");
                return;
            }
            ItemStack source = playerAccess.mainInventoryStack(actor, inventoryIndex);
            if (source.isEmpty()) {
                message(actor, "That inventory slot is empty.");
                return;
            }
            next = new TradeOfferSlot(inventoryIndex, source);
        }
        TradeSession updated = session
                .withItem(actor, slot, next)
                .withAgree(session.initiator(), false)
                .withAgree(session.partner(), false);
        updated = revertLocked(updated);
        sessions.put(sessionId, updated);
        pushToBoth(updated);
    }

    @Override
    public void agree(UUID actor, long sessionId, boolean agree) {
        Objects.requireNonNull(actor, "actor");
        TradeSession session = sessions.get(sessionId);
        if (session == null || !session.isMember(actor)) {
            return;
        }
        if (session.phase() != TradePhase.OPEN && session.phase() != TradePhase.LOCKED) {
            return;
        }
        TradeSession updated = session.withAgree(actor, agree);
        if (agree && session.phase() == TradePhase.OPEN) {
            boolean bothReady = updated.agreeOf(session.initiator())
                    && updated.agreeOf(session.partner());
            if (bothReady) {
                long now = tickSource.getAsLong();
                updated = updated.withPhase(
                        TradePhase.LOCKED,
                        updated.requestTick(),
                        updated.openTick(),
                        now
                );
            }
        } else if (!agree && session.phase() == TradePhase.LOCKED) {
            updated = updated.withPhase(
                    TradePhase.OPEN,
                    updated.requestTick(),
                    updated.openTick(),
                    0L
            );
        }
        sessions.put(sessionId, updated);
        pushToBoth(updated);
    }

    @Override
    public void cancel(UUID actor, long sessionId) {
        Objects.requireNonNull(actor, "actor");
        TradeSession session = sessions.get(sessionId);
        if (session == null || !session.isMember(actor)) {
            return;
        }
        disposeCancelled(session, "The trade was cancelled.");
    }

    // ------------------------------------------------------------------
    // tick-driven lifecycle
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        long now = tickSource.getAsLong();
        List<TradeSession> snapshot = new ArrayList<>(sessions.values());
        for (TradeSession session : snapshot) {
            if (sessions.get(session.sessionId()) != session) {
                continue; // already disposed by a nested transition
            }
            if (playerAccess.onlinePlayer(session.initiator()).isEmpty()
                    || playerAccess.onlinePlayer(session.partner()).isEmpty()) {
                disposeCancelled(session, "A trade party went offline; the trade was cancelled.");
                continue;
            }
            if (session.phase() == TradePhase.LOCKED
                    && now - session.lockTick() >= TradeSession.LOCKED_TICKS) {
                execute(session);
            }
        }
    }

    @Override
    public void playerDisconnected(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        List<TradeSession> affected = new ArrayList<>();
        for (TradeSession session : sessions.values()) {
            if (session.isMember(playerId)) {
                affected.add(session);
            }
        }
        for (TradeSession session : affected) {
            disposeCancelled(session, "A trade party went offline; the trade was cancelled.");
        }
    }

    @Override
    public void shutdown() {
        if (!sessions.isEmpty()) {
            LOGGER.info("[Trade] Shutting down with {} live session(s); dropping them "
                    + "(nothing was escrowed or moved)", sessions.size());
            sessions.clear();
        }
    }

    // ------------------------------------------------------------------
    // atomic execution
    // ------------------------------------------------------------------

    /**
     * Executes a {@code LOCKED} session on the main thread: re-validates
     * presence, communicator gate, every offered source slot (dupe guard)
     * and the receiving capacity, then commits the economy settlement and
     * moves the items. Any failure rejects the whole trade (fail closed).
     */
    private void execute(TradeSession session) {
        long sessionId = session.sessionId();
        String rejection = validateExecution(session);
        if (rejection != null) {
            disposeCancelled(session, rejection);
            return;
        }
        try {
            SubjectId a = economy.ensureAccountForPlayer(session.initiator()).subjectId();
            SubjectId b = economy.ensureAccountForPlayer(session.partner()).subjectId();
            TradeSettlementReceipt receipt = economy.executeTradeSettlement(
                    a,
                    b,
                    session.initiatorMoney(),
                    session.partnerMoney(),
                    taxRatePercent,
                    SETTLEMENT_MEMO
            );
            LOGGER.debug(
                    "[Trade] Session {} settled: aOffered={}, bOffered={}, "
                            + "aTax={}, bTax={}, ids={}",
                    sessionId,
                    receipt.aOffered(),
                    receipt.bOffered(),
                    receipt.aTax(),
                    receipt.bTax(),
                    receipt.transactionIds()
            );
        } catch (EconomyUnavailableException failure) {
            LOGGER.debug(
                    "[Trade] Session {} settlement rejected ({}): {}",
                    sessionId,
                    failure.failureCode(),
                    failure.getMessage()
            );
            disposeCancelled(session, "The trade could not be settled ("
                    + humanCode(failure.failureCode()) + ").");
            return;
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[Trade] Session {} settlement failed unexpectedly: {}",
                    sessionId,
                    failure.getMessage()
            );
            disposeCancelled(session, "The trade could not be settled.");
            return;
        }
        moveItems(session);
        sessions.remove(sessionId);
        pushToBoth(session.withPhase(
                TradePhase.COMPLETED,
                session.requestTick(),
                session.openTick(),
                session.lockTick()
        ));
    }

    /**
     * Pre-execution validation; returns a rejection message or {@code null}
     * when the trade may proceed. Runs entirely before any mutation so a
     * rejection leaves every account and inventory untouched.
     */
    private String validateExecution(TradeSession session) {
        if (session.phase() != TradePhase.LOCKED) {
            return "The trade is no longer confirmed.";
        }
        if (playerAccess.onlinePlayer(session.initiator()).isEmpty()
                || playerAccess.onlinePlayer(session.partner()).isEmpty()) {
            return "A trade party is no longer online.";
        }
        if (!playerAccess.holdsCommunicator(session.initiator())
                || !playerAccess.holdsCommunicator(session.partner())) {
            return "Both players must hold the Portable Communicator.";
        }
        for (UUID party : List.of(session.initiator(), session.partner())) {
            for (TradeOfferSlot slot : session.itemsOf(party)) {
                if (slot.isEmpty()) {
                    continue;
                }
                ItemStack current = playerAccess.mainInventoryStack(
                        party,
                        slot.inventoryIndex()
                );
                if (!matches(current, slot.stack())) {
                    return "An offered item changed since it was offered; "
                            + "the trade was cancelled.";
                }
            }
        }
        for (UUID party : List.of(session.initiator(), session.partner())) {
            UUID other = session.otherOf(party);
            for (TradeOfferSlot slot : session.itemsOf(party)) {
                if (!slot.isEmpty()
                        && !playerAccess.hasRoomFor(other, slot.stack())) {
                    return "The receiving inventory is full; the trade was cancelled.";
                }
            }
        }
        return null;
    }

    /** Dupe-guard equality: same item, same tags, same count. */
    private static boolean matches(ItemStack current, ItemStack expected) {
        return !current.isEmpty()
                && current.getCount() == expected.getCount()
                && ItemStack.isSameItemSameTags(current, expected);
    }

    /**
     * Moves every offered item from its source slot into the counterparty's
     * inventory. Capacity was pre-validated and the source slots were
     * re-validated, so on the main thread this is deterministic; a defensive
     * add failure (theoretically unreachable) leaves the item in the source
     * inventory and logs an error — nothing is ever dropped.
     */
    private void moveItems(TradeSession session) {
        for (UUID party : List.of(session.initiator(), session.partner())) {
            UUID other = session.otherOf(party);
            for (TradeOfferSlot slot : session.itemsOf(party)) {
                if (slot.isEmpty()) {
                    continue;
                }
                boolean moved = playerAccess.addToInventory(other, slot.stack());
                if (moved) {
                    playerAccess.setMainInventoryStack(
                            party,
                            slot.inventoryIndex(),
                            ItemStack.EMPTY
                    );
                } else {
                    LOGGER.error(
                            "[Trade] Defensive move failure for session {}: item "
                                    + "stays in the source inventory (never dropped)",
                            session.sessionId()
                    );
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private boolean openOrLocked(TradeSession session, UUID actor) {
        if (session == null) {
            return false;
        }
        if (!session.isMember(actor)) {
            message(actor, "You are not part of this trade session.");
            return false;
        }
        if (session.phase() != TradePhase.OPEN
                && session.phase() != TradePhase.LOCKED) {
            message(actor, "The trade is not open for offers.");
            return false;
        }
        return true;
    }

    /** Reverts a {@code LOCKED} session to {@code OPEN} (offer change). */
    private TradeSession revertLocked(TradeSession session) {
        if (session.phase() == TradePhase.LOCKED) {
            return session.withPhase(
                    TradePhase.OPEN,
                    session.requestTick(),
                    session.openTick(),
                    0L
            );
        }
        return session;
    }

    private void disposeCancelled(TradeSession session, String reason) {
        long sessionId = session.sessionId();
        if (sessions.remove(sessionId) == null) {
            return;
        }
        TradeSession cancelled = session.withPhase(
                TradePhase.CANCELLED,
                session.requestTick(),
                session.openTick(),
                session.lockTick()
        );
        message(session.initiator(), reason);
        message(session.partner(), reason);
        pushToBoth(cancelled);
    }

    private Optional<TradeSession> activeSessionOf(UUID playerId) {
        for (TradeSession session : sessions.values()) {
            if (session.isMember(playerId)) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<TradeSession> sessionOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return activeSessionOf(playerId);
    }

    @Override
    public int activeSessionCount() {
        return sessions.size();
    }

    // ------------------------------------------------------------------
    // S2C presentation
    // ------------------------------------------------------------------

    private void pushToBoth(TradeSession session) {
        pushSnapshot(session, session.initiator());
        pushSnapshot(session, session.partner());
    }

    private void pushSnapshot(TradeSession session, UUID viewer) {
        boolean ownIsInitiator = session.isInitiator(viewer);
        int countdown = countdownSeconds(session);
        TradeStateSyncPacket packet = new TradeStateSyncPacket(
                session.sessionId(),
                session.phase().ordinal(),
                countdown,
                ownIsInitiator ? session.initiatorMoney() : session.partnerMoney(),
                session.itemStacksOf(viewer),
                session.agreeOf(viewer),
                ownIsInitiator ? session.partnerMoney() : session.initiatorMoney(),
                session.itemStacksOf(session.otherOf(viewer)),
                session.agreeOf(session.otherOf(viewer)),
                clock.getAsLong()
        );
        playerAccess.onlinePlayer(viewer).ifPresent(
                player -> sendService.trySendToPlayer(player, packet)
        );
    }

    private int countdownSeconds(TradeSession session) {
        if (session.phase() != TradePhase.LOCKED) {
            return 0;
        }
        long elapsed = tickSource.getAsLong() - session.lockTick();
        if (elapsed >= TradeSession.LOCKED_TICKS) {
            return 0;
        }
        return (int) Math.ceil((TradeSession.LOCKED_TICKS - elapsed) / 20.0);
    }

    private void message(UUID playerId, String text) {
        playerAccess.message(playerId, text);
    }

    private static List<TradeOfferSlot> emptySlots() {
        List<TradeOfferSlot> slots = new ArrayList<>(TradeOfferItemPacket.SLOT_COUNT);
        for (int index = 0; index < TradeOfferItemPacket.SLOT_COUNT; index++) {
            slots.add(TradeOfferSlot.EMPTY);
        }
        return slots;
    }

    private static String humanCode(String code) {
        return code == null || code.isBlank() ? "settlement rejected" : code;
    }
}
