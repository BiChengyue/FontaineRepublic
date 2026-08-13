package com.fontainerepublic.server.economy.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.model.TransactionType;
import com.fontainerepublic.server.registry.model.SubjectId;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-writer repository owning the {@code "economy"} NBT namespace
 * (FR-ECO-001-A §5.1, FR-ECO-001-C §4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "economy"} key. All mutations run on the logical server owner
 * thread; every mutation builds one complete immutable replacement snapshot
 * (debit + credit + transaction + revisions + notification + next id in one
 * unit), increments the store revision once and each affected account
 * revision once, and publishes the new in-memory state only after the
 * FR-CORE-002 durable gate reports {@code COMMITTED}. A failed write has no
 * side effects: no balance, transaction, notification, receipt, or revision
 * change, and no downstream event.</p>
 *
 * <p>On an empty namespace the repository starts with the zero-money store
 * (revision 0, first unused transaction id 1, treasury 0). A present
 * namespace is decoded strictly and fail-closed. The bounded operational
 * transaction buffer prunes the oldest entries without changing balances,
 * supply, revisions, transaction-id monotonicity, or notification state
 * (FR-ECO-001-C §5.3).</p>
 */
public final class EconomyRepository {

    /** Reserved module-data key for the economy namespace. */
    public static final String MODULE_DATA_KEY = "economy";

    private final EconomyStore store;
    private final EconomyNbtCodec codec;
    private final EconomyLimits limits;
    private final Thread ownerThread;

    private final LinkedHashMap<SubjectId, EconomyAccount> accounts = new LinkedHashMap<>();
    private final LinkedHashMap<Long, EconomyTransaction> transactions = new LinkedHashMap<>();
    private final Map<SubjectId, Deque<NotificationSummary>> pendingNotifications =
            new LinkedHashMap<>();
    private long storeRevision;
    private long nextTransactionId;
    private long treasuryBalance;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static EconomyRepository createProduction(EconomyNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new EconomyRepository(
                new DataManagerEconomyStore(),
                codec,
                EconomyLimits.DEFAULT
        );
    }

    public EconomyRepository(
            EconomyStore store,
            EconomyNbtCodec codec,
            EconomyLimits limits
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
            this.nextTransactionId = 1L;
            this.treasuryBalance = 0L;
        } else {
            EconomyStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups and participant projections only)
    // ------------------------------------------------------------------

    public Optional<EconomyAccount> findAccount(SubjectId subjectId) {
        requireOwnerThread();
        return Optional.ofNullable(
                accounts.get(Objects.requireNonNull(subjectId, "subjectId"))
        );
    }

    public EconomyAccount requireAccount(SubjectId subjectId) {
        return findAccount(subjectId).orElseThrow(
                () -> new EconomyUnavailableException(
                        EconomyUnavailableException.CODE_NO_ACCOUNT,
                        "No economy account for subject " + subjectId
                )
        );
    }

    public int size() {
        requireOwnerThread();
        return accounts.size();
    }

    public long storeRevision() {
        requireOwnerThread();
        return storeRevision;
    }

    public long nextTransactionId() {
        requireOwnerThread();
        return nextTransactionId;
    }

    public EconomyStoreSnapshot snapshot() {
        requireOwnerThread();
        return new EconomyStoreSnapshot(
                EconomyStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                nextTransactionId,
                treasuryBalance,
                accounts,
                transactions,
                notificationsCopy()
        );
    }

    /**
     * Bounded participant projection of the transaction history: records in
     * ascending id order where the subject participated, id strictly greater
     * than {@code afterId}, at most {@code limit} records. The caller bounds
     * {@code limit}; the returned list is immutable.
     */
    public List<EconomyTransaction> participantTransactions(
            SubjectId participant,
            long afterId,
            int limit
    ) {
        requireOwnerThread();
        Objects.requireNonNull(participant, "participant");
        if (limit <= 0) {
            return List.of();
        }
        List<EconomyTransaction> result = new ArrayList<>();
        for (EconomyTransaction transaction : transactions.values()) {
            if (transaction.transactionId() <= afterId) {
                continue;
            }
            if (!participant.equals(transaction.from())
                    && !participant.equals(transaction.to())) {
                continue;
            }
            result.add(transaction);
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    public List<NotificationSummary> pendingNotifications(SubjectId subjectId) {
        requireOwnerThread();
        Deque<NotificationSummary> queue = pendingNotifications.get(
                Objects.requireNonNull(subjectId, "subjectId")
        );
        return queue == null ? List.of() : List.copyOf(queue);
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Lazy, idempotent provisioning of the zero-balance account for a subject
     * (FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.2): creates it with balance 0,
     * revision 1 and the given timestamp; returns the existing account
     * unchanged on repeat calls. Fails closed on capacity exhaustion or a
     * store rejection — nothing is published on failure.
     */
    public EconomyAccount ensure(SubjectId subjectId, long timestamp) {
        requireOwnerThread();
        Objects.requireNonNull(subjectId, "subjectId");
        if (timestamp <= 0) {
            throw new IllegalArgumentException("timestamp must be positive");
        }

        EconomyAccount existing = accounts.get(subjectId);
        if (existing != null) {
            return existing;
        }
        if (accounts.size() >= limits.maxAccounts()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Account count would exceed the budget of " + limits.maxAccounts()
            );
        }
        requireStoreRevisionSpace();

        EconomyAccount created = new EconomyAccount(
                EconomyAccount.CURRENT_SCHEMA_VERSION,
                subjectId,
                0L,
                1L,
                timestamp,
                0L,
                false
        );
        LinkedHashMap<SubjectId, EconomyAccount> nextAccounts =
                new LinkedHashMap<>(accounts);
        nextAccounts.put(subjectId, created);
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextTransactionId,
                treasuryBalance,
                nextAccounts,
                transactions,
                pendingNotifications
        ));
        return created;
    }

    /**
     * Ordinary atomic transfer (FR-ECO-001-C §4.3): one replacement snapshot
     * containing the debited source, the credited target (lazily created at
     * zero when absent), one TRANSFER transaction, the advanced next id,
     * affected revisions +1 exactly once, and a pending incoming notification
     * for the recipient. Stale expected revisions reject at the final
     * boundary; nothing is published on failure.
     */
    public TransferReceipt transfer(
            SubjectId from,
            SubjectId to,
            long amount,
            String memo,
            long expectedFromAccountRevision,
            long expectedStoreRevision,
            long timestamp
    ) {
        requireOwnerThread();
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amount <= 0) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Amount must be positive"
            );
        }
        if (from.equals(to)) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_SELF_TRANSFER,
                    "A player cannot transfer to their own account"
            );
        }
        if (expectedStoreRevision != storeRevision) {
            throw stale("store");
        }

        EconomyAccount source = accounts.get(from);
        if (source == null) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_NO_ACCOUNT,
                    "No economy account for source subject " + from
            );
        }
        if (source.accountRevision() != expectedFromAccountRevision) {
            throw stale("account " + from);
        }
        if (source.balance() < amount) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS,
                    "Insufficient balance for subject " + from
            );
        }
        if (nextTransactionId == Long.MAX_VALUE) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Economy transaction id space exhausted"
            );
        }
        requireStoreRevisionSpace();

        EconomyAccount target = accounts.get(to);
        if (target == null) {
            if (accounts.size() >= limits.maxAccounts()) {
                throw new EconomyUnavailableException(
                        EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                        "Account count would exceed the budget of " + limits.maxAccounts()
                );
            }
            target = new EconomyAccount(
                    EconomyAccount.CURRENT_SCHEMA_VERSION,
                    to,
                    0L,
                    1L,
                    timestamp,
                    0L,
                    false
            );
        }
        long newTargetBalance;
        try {
            newTargetBalance = Math.addExact(target.balance(), amount);
        } catch (ArithmeticException overflow) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_OVERFLOW,
                    "Target balance would overflow"
            );
        }
        if (newTargetBalance > limits.maxBalance()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_OVERFLOW,
                    "Target balance would exceed the maximum of " + limits.maxBalance()
            );
        }

        long newId = nextTransactionId;
        EconomyAccount newSource = source.withBalance(
                source.balance() - amount,
                newId
        );
        EconomyAccount newTarget = target.withBalance(newTargetBalance, newId);
        EconomyTransaction transaction = new EconomyTransaction(
                EconomyTransaction.CURRENT_SCHEMA_VERSION,
                newId,
                timestamp,
                from,
                to,
                amount,
                TransactionType.TRANSFER,
                memo
        );

        LinkedHashMap<SubjectId, EconomyAccount> nextAccounts =
                new LinkedHashMap<>(accounts);
        nextAccounts.put(from, newSource);
        nextAccounts.put(to, newTarget);

        LinkedHashMap<Long, EconomyTransaction> nextTransactions =
                new LinkedHashMap<>(transactions);
        nextTransactions.put(newId, transaction);
        if (nextTransactions.size() > limits.maxTransactions()) {
            Long oldest = nextTransactions.keySet().iterator().next();
            nextTransactions.remove(oldest);
        }

        Map<SubjectId, Deque<NotificationSummary>> nextNotifications =
                copyNotifications(pendingNotifications);
        Deque<NotificationSummary> queue = nextNotifications.computeIfAbsent(
                to,
                ignored -> new ArrayDeque<>()
        );
        queue.addLast(new NotificationSummary(
                NotificationSummary.CURRENT_SCHEMA_VERSION,
                newId,
                newId,
                timestamp,
                from,
                amount,
                memo
        ));
        while (queue.size() > limits.maxPendingNotificationsPerSubject()) {
            queue.pollFirst();
        }

        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                newId + 1,
                treasuryBalance,
                nextAccounts,
                nextTransactions,
                nextNotifications
        ));
        return new TransferReceipt(
                newId,
                timestamp,
                from,
                to,
                amount,
                memo,
                true
        );
    }

    /**
     * Official central-bank issuance (FR-ECO-002-A §4): one replacement
     * snapshot that credits the target personal account, debits the treasury
     * by the same amount, and appends one {@code DEPOSIT} transaction
     * ({@code from} is the system, hence {@code null}). Total supply
     * {@code = sum(accounts) + treasury} is conserved exactly. The treasury
     * must hold the amount; a frozen target is rejected. Stale expected
     * revisions reject at the final boundary; nothing is published on
     * failure. No pending notification is created for the recipient (official
     * duty; the command surface reports the outcome).
     */
    public EconomyTransaction deposit(
            SubjectId to,
            long amount,
            String memo,
            long expectedToAccountRevision,
            long expectedStoreRevision,
            long timestamp
    ) {
        requireOwnerThread();
        Objects.requireNonNull(to, "to");
        if (amount <= 0) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Amount must be positive"
            );
        }
        if (expectedStoreRevision != storeRevision) {
            throw stale("store");
        }

        EconomyAccount target = accounts.get(to);
        if (target == null) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_NO_ACCOUNT,
                    "No economy account for target subject " + to
            );
        }
        if (target.accountRevision() != expectedToAccountRevision) {
            throw stale("account " + to);
        }
        requireNotFrozen(target);
        if (treasuryBalance < amount) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_TREASURY_INSUFFICIENT,
                    "Treasury does not hold the requested amount"
            );
        }
        if (nextTransactionId == Long.MAX_VALUE) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Economy transaction id space exhausted"
            );
        }
        requireStoreRevisionSpace();

        long newBalance;
        try {
            newBalance = Math.addExact(target.balance(), amount);
        } catch (ArithmeticException overflow) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_OVERFLOW,
                    "Target balance would overflow"
            );
        }
        if (newBalance > limits.maxBalance()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_OVERFLOW,
                    "Target balance would exceed the maximum of " + limits.maxBalance()
            );
        }

        long newId = nextTransactionId;
        EconomyAccount newTarget = target.withBalance(newBalance, newId);
        EconomyTransaction transaction = new EconomyTransaction(
                EconomyTransaction.CURRENT_SCHEMA_VERSION,
                newId,
                timestamp,
                null,
                to,
                amount,
                TransactionType.DEPOSIT,
                memo
        );

        LinkedHashMap<SubjectId, EconomyAccount> nextAccounts =
                new LinkedHashMap<>(accounts);
        nextAccounts.put(to, newTarget);

        LinkedHashMap<Long, EconomyTransaction> nextTransactions =
                new LinkedHashMap<>(transactions);
        nextTransactions.put(newId, transaction);
        if (nextTransactions.size() > limits.maxTransactions()) {
            Long oldest = nextTransactions.keySet().iterator().next();
            nextTransactions.remove(oldest);
        }

        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                newId + 1,
                treasuryBalance - amount,
                nextAccounts,
                nextTransactions,
                pendingNotifications
        ));
        return transaction;
    }

    /**
     * Official central-bank withdrawal (FR-ECO-002-A §4): one replacement
     * snapshot that debits the personal account, credits the treasury by the
     * same amount, and appends one {@code WITHDRAWAL} transaction ({@code to}
     * is the system, hence {@code null}). Total supply
     * {@code = sum(accounts) + treasury} is conserved exactly. The source
     * must hold the amount and must not be frozen. Stale expected revisions
     * reject at the final boundary; nothing is published on failure.
     */
    public EconomyTransaction withdraw(
            SubjectId from,
            long amount,
            String memo,
            long expectedFromAccountRevision,
            long expectedStoreRevision,
            long timestamp
    ) {
        requireOwnerThread();
        Objects.requireNonNull(from, "from");
        if (amount <= 0) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_AMOUNT_INVALID,
                    "Amount must be positive"
            );
        }
        if (expectedStoreRevision != storeRevision) {
            throw stale("store");
        }

        EconomyAccount source = accounts.get(from);
        if (source == null) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_NO_ACCOUNT,
                    "No economy account for source subject " + from
            );
        }
        if (source.accountRevision() != expectedFromAccountRevision) {
            throw stale("account " + from);
        }
        requireNotFrozen(source);
        if (source.balance() < amount) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS,
                    "Insufficient balance for subject " + from
            );
        }
        if (nextTransactionId == Long.MAX_VALUE) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Economy transaction id space exhausted"
            );
        }
        requireStoreRevisionSpace();

        long newTreasury;
        try {
            newTreasury = Math.addExact(treasuryBalance, amount);
        } catch (ArithmeticException overflow) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_OVERFLOW,
                    "Treasury balance would overflow"
            );
        }
        if (newTreasury > limits.maxBalance()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_OVERFLOW,
                    "Treasury balance would exceed the maximum of " + limits.maxBalance()
            );
        }

        long newId = nextTransactionId;
        EconomyAccount newSource = source.withBalance(
                source.balance() - amount,
                newId
        );
        EconomyTransaction transaction = new EconomyTransaction(
                EconomyTransaction.CURRENT_SCHEMA_VERSION,
                newId,
                timestamp,
                from,
                null,
                amount,
                TransactionType.WITHDRAWAL,
                memo
        );

        LinkedHashMap<SubjectId, EconomyAccount> nextAccounts =
                new LinkedHashMap<>(accounts);
        nextAccounts.put(from, newSource);

        LinkedHashMap<Long, EconomyTransaction> nextTransactions =
                new LinkedHashMap<>(transactions);
        nextTransactions.put(newId, transaction);
        if (nextTransactions.size() > limits.maxTransactions()) {
            Long oldest = nextTransactions.keySet().iterator().next();
            nextTransactions.remove(oldest);
        }

        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                newId + 1,
                newTreasury,
                nextAccounts,
                nextTransactions,
                pendingNotifications
        ));
        return transaction;
    }

    /**
     * Official central-bank freeze/unfreeze (FR-ECO-002-A §4): flips the
     * account freeze flag in one replacement snapshot with the account
     * revision +1 exactly once. No balance, transaction, or notification
     * changes. Freezing an already-frozen account (or unfreezing an unfrozen
     * one) is rejected; stale expected revisions reject at the final
     * boundary; nothing is published on failure.
     */
    public EconomyAccount setFrozen(
            SubjectId subjectId,
            boolean frozen,
            long expectedAccountRevision,
            long expectedStoreRevision
    ) {
        requireOwnerThread();
        Objects.requireNonNull(subjectId, "subjectId");
        if (expectedStoreRevision != storeRevision) {
            throw stale("store");
        }

        EconomyAccount account = accounts.get(subjectId);
        if (account == null) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_NO_ACCOUNT,
                    "No economy account for subject " + subjectId
            );
        }
        if (account.accountRevision() != expectedAccountRevision) {
            throw stale("account " + subjectId);
        }
        if (account.frozen() == frozen) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_FROZEN,
                    frozen
                            ? "Account is already frozen"
                            : "Account is not frozen"
            );
        }
        requireStoreRevisionSpace();

        EconomyAccount updated = account.withFrozen(frozen);
        LinkedHashMap<SubjectId, EconomyAccount> nextAccounts =
                new LinkedHashMap<>(accounts);
        nextAccounts.put(subjectId, updated);

        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextTransactionId,
                treasuryBalance,
                nextAccounts,
                transactions,
                pendingNotifications
        ));
        return updated;
    }

    private void requireNotFrozen(EconomyAccount account) {
        if (account.frozen()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_FROZEN,
                    "Account of subject " + account.subjectId() + " is frozen"
            );
        }
    }

    /**
     * Acknowledges one pending notification (FR-ECO-001-C §6). A later
     * Economy mutation that never changes the underlying transaction;
     * idempotent no-op when the notification is unknown (nothing committed).
     *
     * @return {@code true} when a notification was actually removed
     */
    public boolean acknowledgeNotification(SubjectId subjectId, long notificationId) {
        requireOwnerThread();
        Objects.requireNonNull(subjectId, "subjectId");
        if (notificationId <= 0) {
            throw new IllegalArgumentException("notificationId must be positive");
        }
        Deque<NotificationSummary> queue = pendingNotifications.get(subjectId);
        if (queue == null) {
            return false;
        }
        NotificationSummary removed = null;
        for (NotificationSummary summary : queue) {
            if (summary.notificationId() == notificationId) {
                removed = summary;
                break;
            }
        }
        if (removed == null) {
            return false;
        }
        requireStoreRevisionSpace();

        Map<SubjectId, Deque<NotificationSummary>> nextNotifications =
                copyNotifications(pendingNotifications);
        Deque<NotificationSummary> nextQueue = nextNotifications.get(subjectId);
        nextQueue.remove(removed);
        if (nextQueue.isEmpty()) {
            nextNotifications.remove(subjectId);
        }
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextTransactionId,
                treasuryBalance,
                accounts,
                transactions,
                nextNotifications
        ));
        return true;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private EconomyStoreSnapshot buildSnapshot(
            long nextStoreRevision,
            long nextTransactionId,
            long treasuryBalance,
            Map<SubjectId, EconomyAccount> accounts,
            Map<Long, EconomyTransaction> transactions,
            Map<SubjectId, Deque<NotificationSummary>> notifications
    ) {
        Map<SubjectId, List<NotificationSummary>> frozen = new LinkedHashMap<>();
        notifications.forEach((subjectId, queue) -> frozen.put(subjectId, List.copyOf(queue)));
        return new EconomyStoreSnapshot(
                EconomyStoreSnapshot.CURRENT_STORE_VERSION,
                nextStoreRevision,
                nextTransactionId,
                treasuryBalance,
                accounts,
                transactions,
                frozen
        );
    }

    private static Map<SubjectId, Deque<NotificationSummary>> copyNotifications(
            Map<SubjectId, Deque<NotificationSummary>> source
    ) {
        Map<SubjectId, Deque<NotificationSummary>> copy = new LinkedHashMap<>();
        source.forEach((subjectId, queue) -> copy.put(subjectId, new ArrayDeque<>(queue)));
        return copy;
    }

    private Map<SubjectId, List<NotificationSummary>> notificationsCopy() {
        Map<SubjectId, List<NotificationSummary>> copy = new LinkedHashMap<>();
        pendingNotifications.forEach(
                (subjectId, queue) -> copy.put(subjectId, List.copyOf(queue))
        );
        return copy;
    }

    private EconomyUnavailableException stale(String what) {
        return new EconomyUnavailableException(
                EconomyUnavailableException.CODE_STALE_REVISION,
                "Stale " + what + " revision at the final mutation boundary"
        );
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Economy store revision space exhausted"
            );
        }
    }

    private void commitAndPublish(EconomyStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Economy namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_STORE_FAILURE,
                    "Economy store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of economy failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(EconomyStoreSnapshot snapshot) {
        accounts.clear();
        accounts.putAll(snapshot.accounts());
        transactions.clear();
        transactions.putAll(snapshot.transactions());
        pendingNotifications.clear();
        snapshot.pendingNotifications().forEach(
                (subjectId, list) -> pendingNotifications.put(
                        subjectId, new ArrayDeque<>(list)
                )
        );
        storeRevision = snapshot.storeRevision();
        nextTransactionId = snapshot.nextTransactionId();
        treasuryBalance = snapshot.treasuryBalance();
    }

    private void enforceLoadedCapacity(EconomyStoreSnapshot snapshot) {
        if (snapshot.accounts().size() > limits.maxAccounts()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded account count " + snapshot.accounts().size()
                            + " exceeds the budget of " + limits.maxAccounts()
            );
        }
        if (snapshot.transactions().size() > limits.maxTransactions()) {
            throw new EconomyUnavailableException(
                    EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded transaction count " + snapshot.transactions().size()
                            + " exceeds the budget of " + limits.maxTransactions()
            );
        }
        for (Map.Entry<SubjectId, List<NotificationSummary>> entry
                : snapshot.pendingNotifications().entrySet()) {
            if (entry.getValue().size() > limits.maxPendingNotificationsPerSubject()) {
                throw new EconomyUnavailableException(
                        EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                        "Loaded pending notification count for " + entry.getKey()
                                + " exceeds the budget of "
                                + limits.maxPendingNotificationsPerSubject()
                );
            }
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "EconomyRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerEconomyStore implements EconomyStore {
        @Override
        public CompoundTag load() {
            return DataManager.getModuleData(MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            return DataManager.commitModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }
    }
}
