package com.fontainerepublic.server.economy.service;

import com.fontainerepublic.server.economy.api.CurrencyPresentation;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.SubjectDirectory;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link EconomyService} (FR-ECO-001-A §4.2 aligned
 * by FR-ECO-001-C-ACCOUNT-ALIGN-01).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link EconomyRepository}. It supplies the server clock, the ordered
 * {@code PlayerData -> subject -> account} provisioning chain, subject-status
 * fail-closed revalidation at the final mutation boundary, memo
 * normalization, the server-owned transfer cooldown gate, and the pure
 * currency presentation. Writes publish only after the durable gate commits;
 * the cooldown is an abuse-control gate only and never replaces the
 * account/store revision checks owned by the repository.</p>
 */
public final class DefaultEconomyService implements EconomyService {

    private final EconomyRepository repository;
    private final LongSupplier clock;
    private final PlayerPresence playerPresence;
    private final SubjectDirectory subjectDirectory;
    private final EconomyLimits limits;
    private final CurrencyPresentation presentation;

    /** Server-owned cooldown: last successful transfer time per actor. */
    private final Map<SubjectId, Long> lastTransferAt = new HashMap<>();

    public DefaultEconomyService(
            EconomyRepository repository,
            LongSupplier clock,
            PlayerPresence playerPresence,
            SubjectDirectory subjectDirectory,
            EconomyLimits limits,
            CurrencyPresentation presentation
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerPresence = Objects.requireNonNull(playerPresence, "playerPresence");
        this.subjectDirectory = Objects.requireNonNull(subjectDirectory, "subjectDirectory");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
    }

    @Override
    public EconomyAccount ensureAccountForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!playerPresence.isAvailable()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                    "Player-data service is not available for account provisioning"
            );
        }
        if (!playerPresence.hasPlayerRecord(playerId)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
        if (!subjectDirectory.isAvailable()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE,
                    "Subject registry is not available for account provisioning"
            );
        }
        SubjectId subjectId = subjectDirectory.ensureSubject(playerId).subjectId();
        return repository.ensure(subjectId, now());
    }

    @Override
    public EconomyAccount ensureAccount(SubjectId subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return repository.ensure(subjectId, now());
    }

    @Override
    public Optional<EconomyAccount> getAccount(SubjectId subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return repository.findAccount(subjectId);
    }

    @Override
    public long getBalance(SubjectId subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return repository.findAccount(subjectId)
                .map(EconomyAccount::balance)
                .orElse(0L);
    }

    @Override
    public EconomyPage<EconomyTransaction> getRecentTransactions(
            SubjectId subjectId,
            long afterId,
            int limit
    ) {
        Objects.requireNonNull(subjectId, "subjectId");
        if (afterId < 0) {
            throw new IllegalArgumentException("afterId must not be negative");
        }
        if (limit <= 0 || limit > limits.maxPageSize()) {
            throw new IllegalArgumentException(
                    "limit must be within [1, " + limits.maxPageSize() + "]"
            );
        }
        List<EconomyTransaction> items =
                repository.participantTransactions(subjectId, afterId, limit);
        long nextAfterId = items.isEmpty()
                ? afterId
                : items.get(items.size() - 1).transactionId();
        boolean hasMore = items.size() == limit
                && !repository.participantTransactions(subjectId, nextAfterId, 1).isEmpty();
        return new EconomyPage<>(items, nextAfterId, hasMore);
    }

    @Override
    public TransferReceipt transfer(SubjectId from, SubjectId to, long amount, String memo) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        validateAmount(amount);
        if (from.equals(to)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SELF_TRANSFER,
                    "A player cannot transfer to their own account"
            );
        }
        String normalized = normalizeMemo(memo);
        requireActiveSubject(from);
        requireActiveSubject(to);
        long timestamp = now();
        enforceCooldown(from, timestamp);

        EconomyAccount source = repository.requireAccount(from);
        TransferReceipt receipt = repository.transfer(
                from,
                to,
                amount,
                normalized,
                source.accountRevision(),
                repository.storeRevision(),
                timestamp
        );
        recordTransfer(from, timestamp);
        return receipt;
    }

    @Override
    public TransferReceipt transferByPlayer(
            UUID fromPlayerId,
            UUID toPlayerId,
            long amount,
            String memo
    ) {
        Objects.requireNonNull(fromPlayerId, "fromPlayerId");
        Objects.requireNonNull(toPlayerId, "toPlayerId");
        if (fromPlayerId.equals(toPlayerId)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SELF_TRANSFER,
                    "A player cannot transfer to their own account"
            );
        }
        requirePlayerRecord(fromPlayerId);
        requirePlayerRecord(toPlayerId);
        if (!subjectDirectory.isAvailable()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE,
                    "Subject registry is not available for transfer routing"
            );
        }
        SubjectId from = subjectDirectory.ensureSubject(fromPlayerId).subjectId();
        SubjectId to = subjectDirectory.ensureSubject(toPlayerId).subjectId();
        if (from.equals(to)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SELF_TRANSFER,
                    "Both UUIDs resolve to the same subject account"
            );
        }
        return transfer(from, to, amount, memo);
    }

    @Override
    public List<NotificationSummary> pendingNotifications(SubjectId subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return repository.pendingNotifications(subjectId);
    }

    @Override
    public void acknowledgeNotification(SubjectId subjectId, long notificationId) {
        Objects.requireNonNull(subjectId, "subjectId");
        repository.acknowledgeNotification(subjectId, notificationId);
    }

    @Override
    public String formatBalance(long amount) {
        return presentation.format(amount);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void validateAmount(long amount) {
        if (amount <= 0 || amount > limits.maxBalance()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Amount must be positive and at most " + limits.maxBalance()
            );
        }
    }

    /**
     * Optional memo normalization (FR-ECO-001-C §5.1): absence (null or blank)
     * is distinct from an empty magic value; control characters and Minecraft
     * formatting codes are rejected as formatting abuse; the approved
     * character bound is enforced.
     */
    private String normalizeMemo(String memo) {
        if (memo == null) {
            return null;
        }
        String trimmed = memo.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > limits.maxMemoLength()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_MEMO_INVALID,
                    "Memo exceeds the bound of " + limits.maxMemoLength() + " characters"
            );
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (Character.isISOControl(current) || current == '\u00A7') {
                throw new EconomyUnavailableException(
                        EconomyUnavailableException.CODE_MEMO_INVALID,
                        "Memo contains control or formatting characters"
                );
            }
        }
        return trimmed;
    }

    /**
     * Revalidates the subject at the final mutation boundary
     * (FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.3): non-ACTIVE status fails closed
     * where no policy resolves it.
     */
    private void requireActiveSubject(SubjectId subjectId) {
        if (!subjectDirectory.isAvailable()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE,
                    "Subject registry is not available at the transfer boundary"
            );
        }
        Optional<SubjectStatus> status = subjectDirectory.status(subjectId);
        if (status.isEmpty()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SUBJECT_MISSING,
                    "Subject " + subjectId + " does not exist in the registry"
            );
        }
        if (status.get() != SubjectStatus.ACTIVE) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SUBJECT_NOT_ACTIVE,
                    "Subject " + subjectId + " is not ACTIVE (" + status.get() + ")"
            );
        }
    }

    private void requirePlayerRecord(UUID playerId) {
        if (!playerPresence.isAvailable()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                    "Player-data service is not available for transfer routing"
            );
        }
        if (!playerPresence.hasPlayerRecord(playerId)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
    }

    /** Abuse-control gate only; never monetary authority (FR-ECO-001-C §4.4). */
    private void enforceCooldown(SubjectId actor, long timestamp) {
        if (limits.transferCooldownMillis() <= 0) {
            return;
        }
        Long last = lastTransferAt.get(actor);
        if (last != null && timestamp - last < limits.transferCooldownMillis()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_COOLDOWN,
                    "Transfer cooldown is still active for subject " + actor
            );
        }
    }

    private void recordTransfer(SubjectId actor, long timestamp) {
        lastTransferAt.put(actor, timestamp);
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
