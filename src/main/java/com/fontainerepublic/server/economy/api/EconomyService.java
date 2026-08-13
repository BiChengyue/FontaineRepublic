package com.fontainerepublic.server.economy.api;

import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the economy module Phase 1 player
 * services (FR-ECO-001-A §4.1 aligned by FR-ECO-001-C-ACCOUNT-ALIGN-01) plus
 * the on-site official Central-Bank duties (FR-ECO-002-A).
 *
 * <p>Personal accounts are keyed by the natural-person {@link SubjectId};
 * UUID inputs converge to the same SubjectId through the PlayerData and FR-ID
 * services. The service exposes no other-player balance, no leaderboard, no
 * cash/ATM/interest/tax/market, no GUI, and no authoritative client packet.
 * All mutations run on the logical server owner thread and publish only
 * after the FR-CORE-002 durable gate reports {@code COMMITTED}; total
 * digital supply {@code = sum(accounts) + treasury} is conserved by every
 * ordinary transfer and by every official issuance/withdrawal.</p>
 *
 * <p>Central-bank official duties (deposit / withdraw / freeze / unfreeze)
 * are gated on a valid {@code ONSITE_OFFICIAL_DUTY} on-site context issued
 * from a registered central-bank facility terminal; the service revalidates
 * the context at the final mutation boundary. The public treasury total is a
 * read-only aggregate.</p>
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

    // ------------------------------------------------------------------
    // central-bank official duties (FR-ECO-002-A, on-site gated)
    // ------------------------------------------------------------------

    /**
     * Read-only, public national-treasury total (FR-ECO-002-A §2 default 2).
     * Exact aggregate only; no detail, no enumeration, no other-account
     * disclosure.
     */
    long getTreasuryBalance();

    /**
     * Official issuance (FR-ECO-002-A §3): credits the target personal
     * account and debits the treasury by the same amount in one atomic
     * snapshot, appending an immutable {@code DEPOSIT} transaction. Total
     * supply {@code = sum(accounts) + treasury} is conserved exactly. The
     * mutation requires a valid {@code ONSITE_OFFICIAL_DUTY} on-site context
     * (registered central-bank facility + terminal) revalidated at the final
     * mutation boundary; the treasury must hold the amount; a frozen target
     * is rejected. On any failure nothing is published.
     *
     * @param memo optional, normalized, bounded, display-only official reason
     */
    EconomyTransaction deposit(
            SubjectId to,
            long amount,
            String memo,
            OnSiteContext context
    );

    /**
     * Official withdrawal (FR-ECO-002-A §3): debits the personal account and
     * credits the treasury by the same amount in one atomic snapshot,
     * appending an immutable {@code WITHDRAWAL} transaction. Total supply
     * {@code = sum(accounts) + treasury} is conserved exactly. The mutation
     * requires a valid {@code ONSITE_OFFICIAL_DUTY} on-site context
     * revalidated at the final mutation boundary; the source must hold the
     * amount and must not be frozen. On any failure nothing is published.
     *
     * @param memo optional, normalized, bounded, display-only official reason
     */
    EconomyTransaction withdraw(
            SubjectId from,
            long amount,
            String memo,
            OnSiteContext context
    );

    /**
     * Official account freeze (FR-ECO-002-A §3): flips the account freeze
     * flag in one atomic snapshot; the account then rejects every
     * balance-changing mutation (transfer, deposit, withdrawal) until
     * unfrozen. Requires a valid {@code ONSITE_OFFICIAL_DUTY} on-site
     * context revalidated at the final mutation boundary. On any failure
     * nothing is published.
     */
    EconomyAccount freeze(SubjectId subjectId, String memo, OnSiteContext context);

    /**
     * Official account unfreeze (FR-ECO-002-A §3): clears the freeze flag in
     * one atomic snapshot. Requires a valid {@code ONSITE_OFFICIAL_DUTY}
     * on-site context revalidated at the final mutation boundary. On any
     * failure nothing is published.
     */
    EconomyAccount unfreeze(SubjectId subjectId, String memo, OnSiteContext context);
}
