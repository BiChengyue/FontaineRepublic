package com.fontainerepublic.server.economy.service;

import com.fontainerepublic.server.economy.api.CurrencyPresentation;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.SubjectDirectory;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>The on-site official Central-Bank duties (FR-ECO-002-A) validate every
 * mutation through {@link InstitutionAccessService#validateAtMutation} as
 * {@code ONSITE_OFFICIAL_DUTY} at the final mutation boundary — a null or
 * non-VALID context rejects the mutation fail-closed. Successful official
 * duties are recorded through the audit service (FINANCE, secret-digest
 * classification); audit failure never blocks an already-committed
 * mutation.</p>
 */
public final class DefaultEconomyService implements EconomyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEconomyService.class);

    private final EconomyRepository repository;
    private final LongSupplier clock;
    private final PlayerPresence playerPresence;
    private final SubjectDirectory subjectDirectory;
    private final EconomyLimits limits;
    private final CurrencyPresentation presentation;
    private final InstitutionAccessService institutionAccess;
    private final AuditService auditService;

    /** Server-owned cooldown: last successful transfer time per actor. */
    private final Map<SubjectId, Long> lastTransferAt = new HashMap<>();

    public DefaultEconomyService(
            EconomyRepository repository,
            LongSupplier clock,
            PlayerPresence playerPresence,
            SubjectDirectory subjectDirectory,
            EconomyLimits limits,
            CurrencyPresentation presentation,
            InstitutionAccessService institutionAccess,
            AuditService auditService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerPresence = Objects.requireNonNull(playerPresence, "playerPresence");
        this.subjectDirectory = Objects.requireNonNull(subjectDirectory, "subjectDirectory");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.institutionAccess =
                Objects.requireNonNull(institutionAccess, "institutionAccess");
        this.auditService = auditService;
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
        requireNotFrozen(source);
        EconomyAccount target = repository.findAccount(to).orElse(null);
        if (target != null) {
            // A lazily created target starts unfrozen; an existing frozen
            // target rejects the transfer too (no money may move into a
            // frozen account, FR-ECO-002-A §4).
            requireNotFrozen(target);
        }
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
    public TradeSettlementReceipt executeTradeSettlement(
            SubjectId a,
            SubjectId b,
            long aOffered,
            long bOffered,
            int taxRatePercent,
            String memo
    ) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.equals(b)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SELF_TRANSFER,
                    "A trade settlement cannot be a self settlement"
            );
        }
        if (aOffered < 0 || bOffered < 0) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Trade offers must not be negative"
            );
        }
        if (aOffered > limits.maxBalance() || bOffered > limits.maxBalance()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Trade offers must be at most " + limits.maxBalance()
            );
        }
        if (taxRatePercent < 0 || taxRatePercent > 100) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Trade tax rate must be within [0, 100]"
            );
        }
        String normalized = normalizeMemo(memo);
        long timestamp = now();
        // Deliberately no registry/active/frozen/cooldown gate here: this is
        // the system settlement channel of the trade module. The trade
        // service enforces the player-facing authority rules (online
        // parties, communicator gate, active subjects, frozen accounts) at
        // its own boundary; the repository enforces the offer + tax balance
        // rule and fail-closes the whole settlement on any violation.
        return repository.executeTradeSettlement(
                a,
                b,
                aOffered,
                bOffered,
                taxRatePercent,
                normalized,
                repository.storeRevision(),
                timestamp
        );
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
    // central-bank official duties (FR-ECO-002-A, on-site gated)
    // ------------------------------------------------------------------

    @Override
    public long getTreasuryBalance() {
        return repository.snapshot().treasuryBalance();
    }

    @Override
    public EconomyTransaction deposit(
            SubjectId to,
            long amount,
            String memo,
            OnSiteContext context
    ) {
        Objects.requireNonNull(to, "to");
        validateAmount(amount);
        String normalized = normalizeMemo(memo);
        requireActiveSubject(to);
        long timestamp = now();
        requireOfficialOnSite(context);
        // The target account is ensured lazily (subject already resolved and
        // ACTIVE); the official duty itself is the only way money enters an
        // account without a counterparty.
        EconomyAccount target = repository.ensure(to, timestamp);
        requireNotFrozen(target);
        EconomyTransaction transaction = repository.deposit(
                to,
                amount,
                normalized,
                target.accountRevision(),
                repository.storeRevision(),
                timestamp
        );
        auditOfficial(
                context.playerId(),
                "economy.deposit",
                to,
                amount,
                normalized,
                "Central bank deposit for subject " + to
        );
        return transaction;
    }

    @Override
    public EconomyTransaction withdraw(
            SubjectId from,
            long amount,
            String memo,
            OnSiteContext context
    ) {
        Objects.requireNonNull(from, "from");
        validateAmount(amount);
        String normalized = normalizeMemo(memo);
        requireActiveSubject(from);
        long timestamp = now();
        requireOfficialOnSite(context);
        EconomyAccount source = repository.requireAccount(from);
        requireNotFrozen(source);
        EconomyTransaction transaction = repository.withdraw(
                from,
                amount,
                normalized,
                source.accountRevision(),
                repository.storeRevision(),
                timestamp
        );
        auditOfficial(
                context.playerId(),
                "economy.withdraw",
                from,
                amount,
                normalized,
                "Central bank withdrawal for subject " + from
        );
        return transaction;
    }

    @Override
    public EconomyAccount freeze(SubjectId subjectId, String memo, OnSiteContext context) {
        Objects.requireNonNull(subjectId, "subjectId");
        normalizeMemo(memo);
        requireActiveSubject(subjectId);
        requireOfficialOnSite(context);
        EconomyAccount account = repository.requireAccount(subjectId);
        EconomyAccount frozen = repository.setFrozen(
                subjectId,
                true,
                account.accountRevision(),
                repository.storeRevision()
        );
        auditOfficial(
                context.playerId(),
                "economy.freeze",
                subjectId,
                0L,
                normalizeMemo(memo),
                "Central bank freeze for subject " + subjectId
        );
        return frozen;
    }

    @Override
    public EconomyAccount unfreeze(SubjectId subjectId, String memo, OnSiteContext context) {
        Objects.requireNonNull(subjectId, "subjectId");
        normalizeMemo(memo);
        requireActiveSubject(subjectId);
        requireOfficialOnSite(context);
        EconomyAccount account = repository.requireAccount(subjectId);
        EconomyAccount unfrozen = repository.setFrozen(
                subjectId,
                false,
                account.accountRevision(),
                repository.storeRevision()
        );
        auditOfficial(
                context.playerId(),
                "economy.unfreeze",
                subjectId,
                0L,
                normalizeMemo(memo),
                "Central bank unfreeze for subject " + subjectId
        );
        return unfrozen;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /**
     * Final mutation boundary of an official duty (FR-ECO-002-A §4): the
     * on-site context must revalidate as {@code ONSITE_OFFICIAL_DUTY} at the
     * authoritative position bound to the context. A null context and any
     * non-VALID outcome reject the mutation fail-closed with a stable code.
     */
    private void requireOfficialOnSite(OnSiteContext context) {
        ValidationResult result;
        if (context == null) {
            result = ValidationResult.invalid(ValidationResult.REASON_NOT_ISSUED);
        } else {
            result = institutionAccess.validateAtMutation(
                    context,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY,
                    now(),
                    context.dimension(),
                    context.blockX(),
                    context.blockY(),
                    context.blockZ()
            );
        }
        if (!result.valid()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "On-site ONSITE_OFFICIAL_DUTY context is not valid: "
                            + result.reason()
            );
        }
    }

    /** A frozen account rejects every balance-changing official mutation. */
    private void requireNotFrozen(EconomyAccount account) {
        if (account.frozen()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_FROZEN,
                    "Account of subject " + account.subjectId() + " is frozen"
            );
        }
    }

    /**
     * Records an official central-bank duty (FR-ECO-002-A §4) through the
     * audit service: FINANCE category, the on-site official as actor, the
     * affected subject as target, secret-digest classification (the amount is
     * never rendered into ordinary projections; the payload plaintext is
     * never persisted). Audit failure never blocks an already-committed
     * mutation (audit is a record, not an authority).
     */
    private void auditOfficial(
            UUID actor,
            String actionId,
            SubjectId target,
            long amount,
            String memo,
            String summary
    ) {
        if (auditService == null) {
            return;
        }
        try {
            CompoundTag payload = new CompoundTag();
            payload.putLong("Amount", amount);
            if (memo != null) {
                payload.putString("Memo", memo);
            }
            AuditReceipt receipt = auditService.recordAuthoritative(
                    new AuditDraft(
                            AuditActorType.PLAYER,
                            actor.toString(),
                            AuditCategory.FINANCE,
                            "economy",
                            actionId,
                            Optional.of("SUBJECT"),
                            Optional.of(target.toString()),
                            AuditClassification.SECRET_DIGEST_ONLY,
                            summary,
                            Optional.of(payload)
                    )
            );
            if (!receipt.committed()) {
                LOGGER.warn(
                        "[Economy] Audit of {} was not durably committed: {}",
                        actionId,
                        receipt.failureCode()
                );
            }
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[Economy] Audit recording failed for {}: {}",
                    actionId,
                    failure.getMessage()
            );
        }
    }

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
