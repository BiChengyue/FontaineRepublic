package com.fontainerepublic.server.economy.presentation;

import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.registry.model.SubjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Economy service decorator that publishes presentation notifications after
 * the authoritative mutation succeeded (FR-CLIENT-001-A §3.3).
 *
 * <p>Every presentation call is advisory and guarded: a throwing notifier,
 * absent remote channel, or offline player never changes the business result
 * (no-client parity). The decorator never intercepts or alters the delegated
 * service's own outcome or exceptions.</p>
 */
public final class PresentationAwareEconomyService implements EconomyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            PresentationAwareEconomyService.class
    );

    private final EconomyService delegate;
    private final EconomyPresentationNotifier notifier;

    public PresentationAwareEconomyService(
            EconomyService delegate,
            EconomyPresentationNotifier notifier
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @Override
    public EconomyAccount ensureAccountForPlayer(UUID playerId) {
        EconomyAccount account = delegate.ensureAccountForPlayer(playerId);
        try {
            List<NotificationSummary> pending = delegate.pendingNotifications(account.subjectId());
            notifier.syncAccount(playerId, account, pending);
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Presentation sync failed for {}: {}",
                    playerId,
                    failure.getMessage()
            );
        }
        return account;
    }

    @Override
    public EconomyAccount ensureAccount(SubjectId subjectId) {
        EconomyAccount account = delegate.ensureAccount(subjectId);
        try {
            notifier.balanceChanged(subjectId, account);
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Presentation balance refresh failed for {}: {}",
                    subjectId,
                    failure.getMessage()
            );
        }
        return account;
    }

    @Override
    public Optional<EconomyAccount> getAccount(SubjectId subjectId) {
        return delegate.getAccount(subjectId);
    }

    @Override
    public long getBalance(SubjectId subjectId) {
        return delegate.getBalance(subjectId);
    }

    @Override
    public EconomyPage<EconomyTransaction> getRecentTransactions(
            SubjectId subjectId,
            long afterId,
            int limit
    ) {
        return delegate.getRecentTransactions(subjectId, afterId, limit);
    }

    @Override
    public TransferReceipt transfer(SubjectId from, SubjectId to, long amount, String memo) {
        TransferReceipt receipt = delegate.transfer(from, to, amount, memo);
        notifyTransfer(receipt);
        return receipt;
    }

    @Override
    public TransferReceipt transferByPlayer(
            UUID fromPlayerId,
            UUID toPlayerId,
            long amount,
            String memo
    ) {
        TransferReceipt receipt = delegate.transferByPlayer(fromPlayerId, toPlayerId, amount, memo);
        notifyTransfer(receipt);
        return receipt;
    }

    @Override
    public List<NotificationSummary> pendingNotifications(SubjectId subjectId) {
        return delegate.pendingNotifications(subjectId);
    }

    @Override
    public void acknowledgeNotification(SubjectId subjectId, long notificationId) {
        delegate.acknowledgeNotification(subjectId, notificationId);
    }

    @Override
    public String formatBalance(long amount) {
        return delegate.formatBalance(amount);
    }

    @Override
    public long getTreasuryBalance() {
        return delegate.getTreasuryBalance();
    }

    @Override
    public EconomyTransaction deposit(
            SubjectId to,
            long amount,
            String memo,
            OnSiteContext context
    ) {
        EconomyTransaction transaction = delegate.deposit(to, amount, memo, context);
        try {
            delegate.getAccount(to).ifPresent(
                    account -> notifier.balanceChanged(to, account)
            );
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Presentation balance refresh failed after deposit for {}: {}",
                    to,
                    failure.getMessage()
            );
        }
        return transaction;
    }

    @Override
    public EconomyTransaction withdraw(
            SubjectId from,
            long amount,
            String memo,
            OnSiteContext context
    ) {
        EconomyTransaction transaction = delegate.withdraw(from, amount, memo, context);
        try {
            delegate.getAccount(from).ifPresent(
                    account -> notifier.balanceChanged(from, account)
            );
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Presentation balance refresh failed after withdrawal for {}: {}",
                    from,
                    failure.getMessage()
            );
        }
        return transaction;
    }

    @Override
    public EconomyAccount freeze(SubjectId subjectId, String memo, OnSiteContext context) {
        return delegate.freeze(subjectId, memo, context);
    }

    @Override
    public EconomyAccount unfreeze(SubjectId subjectId, String memo, OnSiteContext context) {
        return delegate.unfreeze(subjectId, memo, context);
    }

    private void notifyTransfer(TransferReceipt receipt) {
        try {
            EconomyAccount from = delegate.getAccount(receipt.from()).orElse(null);
            EconomyAccount to = delegate.getAccount(receipt.to()).orElse(null);
            notifier.transferCompleted(receipt, from, to);
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Presentation transfer notice failed for transaction {}: {}",
                    receipt.transactionId(),
                    failure.getMessage()
            );
        }
    }
}
