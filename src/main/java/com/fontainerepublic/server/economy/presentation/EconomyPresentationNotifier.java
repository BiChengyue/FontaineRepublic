package com.fontainerepublic.server.economy.presentation;

import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.List;
import java.util.UUID;

/**
 * Presentation-side sink of the economy module (FR-CLIENT-001-A §3.3).
 *
 * <p>The notifier is strictly advisory: every invocation is best-effort and
 * presence-filtered, and a failure, absent channel, or offline player never
 * alters the business result (no-client parity). The production
 * implementation sends S2C display payloads through
 * {@code NetworkSendService.trySendToPlayer}; tests substitute a fake.</p>
 */
public interface EconomyPresentationNotifier {

    /**
     * Login/account-ready sync: balance snapshot plus the pending-notification
     * summary for the verified player.
     */
    void syncAccount(UUID playerId, EconomyAccount account, List<NotificationSummary> pending);

    /**
     * Balance changed for one subject (official duty, emergency action, or
     * account provisioning): refresh the target's balance presentation when
     * the player is online.
     */
    void balanceChanged(SubjectId subjectId, EconomyAccount account);

    /**
     * Ordinary transfer committed: refresh both participants' balance and
     * send each participant their own transaction notice (direction and
     * counterparty digest are receiver-relative).
     */
    void transferCompleted(TransferReceipt receipt, EconomyAccount from, EconomyAccount to);

    /**
     * Transaction-history page sync (FR-CLIENT-001-IMPL-B2): replaces the
     * receiver's history presentation with one bounded page. Sent after
     * {@link #syncAccount} on login/account-ready (first page only in the
     * first release); direction and counterparty digest are resolved
     * receiver-relative by the implementation.
     */
    void syncHistory(UUID playerId, EconomyPage<EconomyTransaction> page);
}
