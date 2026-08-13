package com.fontainerepublic.server.economy.api;

import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the economy module Phase 1 player
 * services (FR-ECO-001-A §4.1 aligned by FR-ECO-001-C-ACCOUNT-ALIGN-01).
 *
 * <p>Personal accounts are keyed by the natural-person {@link SubjectId};
 * UUID inputs converge to the same SubjectId through the PlayerData and FR-ID
 * services. The service exposes no other-player balance, no leaderboard, no
 * bank/treasury surface, no freeze, no cash/ATM/interest/tax/market, no
 * GUI, and no authoritative client packet. All mutations run on the logical
 * server owner thread and publish only after the FR-CORE-002 durable gate
 * reports {@code COMMITTED}; total digital supply is conserved by every
 * ordinary transfer.</p>
 */
public interface EconomyService {

    /**
     * Lazy, idempotent, owner-threaded provisioning of the zero-balance
     * account for a verified player UUID: ordered chain {@code PlayerData ->
     * FR-ID subject -> Economy account} (FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.2).
     * Repeated calls return the same persisted account unchanged.
     *
     * @throws com.fontainerepublic.server.economy.persistence.EconomyUnavailableException
     *         with a stable code when player-data or the subject registry is
     *         unavailable, the player is not provisioned, capacity is
     *         exhausted, or the durable store rejected the snapshot
     */
    EconomyAccount ensureAccountForPlayer(UUID playerId);

    /**
     * Lazy, idempotent provisioning of the zero-balance account for an
     * already-resolved {@link SubjectId}. The subject must already exist.
     */
    EconomyAccount ensureAccount(SubjectId subjectId);

    /** Exact lookup: the account of a subject, if any. */
    Optional<EconomyAccount> getAccount(SubjectId subjectId);

    /** Exact balance lookup of the caller's own account (0 when absent). */
    long getBalance(SubjectId subjectId);

    /**
     * Bounded participant projection of the transaction history: transactions
     * where the subject participated, with id strictly greater than
     * {@code afterId}, at most {@code limit} records ({@code limit} bounded
     * by the configured page size). Never exposes another player's records.
     */
    EconomyPage<EconomyTransaction> getRecentTransactions(
            SubjectId subjectId,
            long afterId,
            int limit
    );

    /**
     * Ordinary player-to-player transfer with UUID inputs; both UUIDs are
     * resolved to their SubjectId account before any mutation (online or
     * offline target already known to PlayerData). One atomic replacement
     * snapshot: debit + credit + one TRANSFER transaction + revisions +
     * pending notification for the recipient; total supply unchanged. The
     * receipt's transaction id is visible only after a durable
     * {@code COMMITTED}.
     *
     * @param memo optional, normalized, bounded (≤128 chars), display-only
     */
    TransferReceipt transfer(
            SubjectId from,
            SubjectId to,
            long amount,
            String memo
    );

    /**
     * UUID-input convenience route for {@link #transfer(SubjectId, SubjectId,
     * long, String)}; converges both players on the same SubjectId accounts.
     */
    TransferReceipt transferByPlayer(
            UUID fromPlayerId,
            UUID toPlayerId,
            long amount,
            String memo
    );

    /** Bounded pending incoming-transfer summaries for the subject. */
    List<NotificationSummary> pendingNotifications(SubjectId subjectId);

    /**
     * Acknowledges one pending notification. Idempotent no-op when the
     * notification is unknown; acknowledgement never changes the underlying
     * transaction (FR-ECO-001-C §6).
     */
    void acknowledgeNotification(SubjectId subjectId, long notificationId);

    /** Pure presentation of a non-negative amount; never alters values. */
    String formatBalance(long amount);
}
