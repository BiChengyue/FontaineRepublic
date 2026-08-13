package com.fontainerepublic.server.economy.persistence;

import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyEmergencyReceipt;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete {@code "economy"}
 * namespace (FR-ECO-001-A §3.4, FR-ECO-001-C §4.1).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every {@code Accounts} key matches its record's canonical
 *       {@link SubjectId} (an account without a subject is rejected);</li>
 *   <li>balances are non-negative and bounded by {@code Long.MAX_VALUE / 2};</li>
 *   <li>every transaction id is positive and below {@code NextTransactionId};</li>
 *   <li>treasury balance is non-negative;</li>
 *   <li>total digital supply {@code = sum(accounts) + treasury} is reconciled
 *       without overflow (FR-ECO-001-C §3.4/§7);</li>
 *   <li>every pending notification belongs to an existing account;</li>
 *   <li>every permanent emergency success receipt has a positive, strictly
 *       ascending {@code sequence} matching its map key.</li>
 * </ul>
 * <p>Duplicates, mismatches, or any inconsistent field reject the whole
 * snapshot (fail closed).</p>
 */
public record EconomyStoreSnapshot(
        int storeVersion,
        long storeRevision,
        long nextTransactionId,
        long treasuryBalance,
        Map<SubjectId, EconomyAccount> accounts,
        Map<Long, EconomyTransaction> transactions,
        Map<SubjectId, List<NotificationSummary>> pendingNotifications,
        Map<Long, EconomyEmergencyReceipt> emergencyReceipts
) {

    public static final int CURRENT_STORE_VERSION = 1;

    /** Structural balance cap of the economy store (FR-ECO-001-A §5.4). */
    public static final long MAX_BALANCE = Long.MAX_VALUE / 2;

    public EconomyStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw invalid("Unsupported economy store version: " + storeVersion);
        }
        if (storeRevision < 0) {
            throw invalid("storeRevision must not be negative");
        }
        if (nextTransactionId <= 0) {
            throw invalid("nextTransactionId must be positive");
        }
        if (treasuryBalance < 0 || treasuryBalance > MAX_BALANCE) {
            throw invalid("treasuryBalance out of bounds: " + treasuryBalance);
        }
        accounts = preserveOrder(Objects.requireNonNull(accounts, "accounts"));
        transactions = preserveOrder(Objects.requireNonNull(transactions, "transactions"));
        pendingNotifications = preserveOrder(
                Objects.requireNonNull(pendingNotifications, "pendingNotifications")
        );
        emergencyReceipts = preserveOrder(
                Objects.requireNonNull(emergencyReceipts, "emergencyReceipts")
        );

        long supply = treasuryBalance;
        for (Map.Entry<SubjectId, EconomyAccount> entry : accounts.entrySet()) {
            SubjectId key = entry.getKey();
            EconomyAccount account = entry.getValue();
            if (!key.equals(account.subjectId())) {
                throw invalid(
                        "Accounts key " + key + " does not match record subjectId "
                                + account.subjectId()
                );
            }
            if (account.balance() > MAX_BALANCE) {
                throw invalid(
                        "Account " + key + " balance exceeds " + MAX_BALANCE
                );
            }
            supply = addExact(supply, account.balance(), "total digital supply");
            if (supply > MAX_BALANCE) {
                throw invalid("Total digital supply exceeds " + MAX_BALANCE);
            }
            if (account.lastTransactionId() < 0
                    || account.lastTransactionId() >= nextTransactionId) {
                throw invalid(
                        "Account " + key + " lastTransactionId is inconsistent: "
                                + account.lastTransactionId()
                );
            }
        }

        for (Map.Entry<Long, EconomyTransaction> entry : transactions.entrySet()) {
            Long key = entry.getKey();
            EconomyTransaction transaction = entry.getValue();
            if (!key.equals(transaction.transactionId())) {
                throw invalid(
                        "Transactions key " + key + " does not match record id "
                                + transaction.transactionId()
                );
            }
            if (transaction.transactionId() <= 0
                    || transaction.transactionId() >= nextTransactionId) {
                throw invalid(
                        "Transaction id out of range: " + transaction.transactionId()
                );
            }
        }

        for (Map.Entry<SubjectId, List<NotificationSummary>> entry
                : pendingNotifications.entrySet()) {
            SubjectId owner = entry.getKey();
            if (!accounts.containsKey(owner)) {
                throw invalid(
                        "Pending notification for a subject without an account: " + owner
                );
            }
            List<NotificationSummary> list = entry.getValue();
            if (list == null || list.isEmpty()) {
                throw invalid("Pending notification list must not be empty");
            }
        }

        long previousSequence = 0L;
        for (Map.Entry<Long, EconomyEmergencyReceipt> entry
                : emergencyReceipts.entrySet()) {
            Long key = entry.getKey();
            EconomyEmergencyReceipt receipt = entry.getValue();
            if (!key.equals(receipt.sequence())) {
                throw invalid(
                        "Emergency receipt key " + key + " does not match record sequence "
                                + receipt.sequence()
                );
            }
            if (receipt.sequence() <= previousSequence) {
                throw invalid(
                        "Emergency receipt sequences must be strictly ascending; found "
                                + receipt.sequence() + " after " + previousSequence
                );
            }
            previousSequence = receipt.sequence();
        }
    }

    /** Computes the current total digital supply. */
    public long totalSupply() {
        long supply = treasuryBalance;
        for (EconomyAccount account : accounts.values()) {
            supply = addExact(supply, account.balance(), "total digital supply");
        }
        return supply;
    }

    /** Highest permanent emergency receipt sequence (0 when none). */
    public long highestReceiptSequence() {
        long highest = 0L;
        for (EconomyEmergencyReceipt receipt : emergencyReceipts.values()) {
            highest = Math.max(highest, receipt.sequence());
        }
        return highest;
    }

    private static long addExact(long left, long right, String what) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw invalid(what + " overflow");
        }
    }

    /**
     * Preserves the caller's iteration order (ascending transaction id,
     * canonical subject order after codec decode) while making the map
     * immutable — pruning and participant pagination depend on order.
     */
    private static <K, V> Map<K, V> preserveOrder(Map<K, V> source) {
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(source));
    }

    private static EconomyNbtException invalid(String message) {
        return new EconomyNbtException(message);
    }
}
