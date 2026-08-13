package com.fontainerepublic.server.economy.persistence;

import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.model.TransactionType;
import com.fontainerepublic.server.registry.model.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "economy"}
 * namespace (FR-ECO-001-A §5.2).
 *
 * <p>Encoding writes every index in deterministic lexical key order so the
 * same immutable snapshot always produces an equivalent ordered NBT. Decoding
 * accepts only declared fields with exact NBT types, canonical subject keys,
 * valid TRANSFER transactions, positive amounts/revisions/timestamps, and
 * constructs the validating {@link EconomyStoreSnapshot} which enforces the
 * key/record identity and total-supply invariants. Unknown newer versions are
 * rejected; nothing is ever auto-repaired.</p>
 */
public final class EconomyNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String TREASURY_BALANCE = "TreasuryBalance";
    private static final String NEXT_TRANSACTION_ID = "NextTransactionId";
    private static final String ACCOUNTS = "Accounts";
    private static final String TRANSACTIONS = "Transactions";
    private static final String PENDING_NOTIFICATIONS = "PendingNotifications";

    private static final String ACCOUNT_VERSION = "AccountVersion";
    private static final String SUBJECT_ID = "SubjectId";
    private static final String BALANCE = "Balance";
    private static final String ACCOUNT_REVISION = "AccountRevision";
    private static final String CREATED_AT = "CreatedAt";
    private static final String LAST_TRANSACTION_ID = "LastTransactionId";

    private static final String TRANSACTION_VERSION = "TransactionVersion";
    private static final String TRANSACTION_ID = "TransactionId";
    private static final String TIMESTAMP = "Timestamp";
    private static final String FROM_SUBJECT = "FromSubject";
    private static final String TO_SUBJECT = "ToSubject";
    private static final String AMOUNT = "Amount";
    private static final String TYPE = "Type";
    private static final String MEMO = "Memo";

    private static final String NOTIFICATION_VERSION = "NotificationVersion";
    private static final String NOTIFICATION_ID = "NotificationId";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_ACCOUNTS = 100_000;
    private static final int HARD_MAX_TRANSACTIONS = 100_000;
    private static final int HARD_MAX_NOTIFICATIONS_PER_SUBJECT = 1_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            TREASURY_BALANCE,
            NEXT_TRANSACTION_ID,
            ACCOUNTS,
            TRANSACTIONS,
            PENDING_NOTIFICATIONS
    );
    private static final Set<String> ACCOUNT_KEYS = Set.of(
            ACCOUNT_VERSION,
            SUBJECT_ID,
            BALANCE,
            ACCOUNT_REVISION,
            CREATED_AT,
            LAST_TRANSACTION_ID
    );
    private static final Set<String> TRANSACTION_KEYS = Set.of(
            TRANSACTION_VERSION,
            TRANSACTION_ID,
            TIMESTAMP,
            FROM_SUBJECT,
            TO_SUBJECT,
            AMOUNT,
            TYPE,
            MEMO
    );
    private static final Set<String> NOTIFICATION_KEYS = Set.of(
            NOTIFICATION_VERSION,
            NOTIFICATION_ID,
            TRANSACTION_ID,
            TIMESTAMP,
            FROM_SUBJECT,
            AMOUNT,
            MEMO
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present economy namespace must be a valid, initialized
     * store.
     */
    public EconomyStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "economy namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "economy");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "economy");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "economy");
        requireType(root, TREASURY_BALANCE, Tag.TAG_LONG, "economy");
        requireType(root, NEXT_TRANSACTION_ID, Tag.TAG_LONG, "economy");
        requireType(root, ACCOUNTS, Tag.TAG_COMPOUND, "economy");
        requireType(root, TRANSACTIONS, Tag.TAG_COMPOUND, "economy");
        requireType(root, PENDING_NOTIFICATIONS, Tag.TAG_COMPOUND, "economy");

        int storeVersion = root.getInt(STORE_VERSION);
        long storeRevision = root.getLong(STORE_REVISION);
        long treasuryBalance = root.getLong(TREASURY_BALANCE);
        long nextTransactionId = root.getLong(NEXT_TRANSACTION_ID);

        Map<SubjectId, EconomyAccount> accounts = decodeAccounts(
                root.getCompound(ACCOUNTS)
        );
        Map<Long, EconomyTransaction> transactions = decodeTransactions(
                root.getCompound(TRANSACTIONS)
        );
        Map<SubjectId, List<NotificationSummary>> notifications =
                decodeNotifications(root.getCompound(PENDING_NOTIFICATIONS));

        return new EconomyStoreSnapshot(
                storeVersion,
                storeRevision,
                nextTransactionId,
                treasuryBalance,
                accounts,
                transactions,
                notifications
        );
    }

    private Map<SubjectId, EconomyAccount> decodeAccounts(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_ACCOUNTS) {
            throw invalid("Account count exceeds " + HARD_MAX_ACCOUNTS);
        }
        Map<SubjectId, EconomyAccount> accounts = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            SubjectId subjectId = parseCanonicalSubjectId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Accounts");
            EconomyAccount account = decodeAccount(tag.getCompound(key), subjectId);
            if (accounts.put(subjectId, account) != null) {
                throw invalid("Duplicate account subject: " + subjectId);
            }
        }
        return accounts;
    }

    private EconomyAccount decodeAccount(CompoundTag tag, SubjectId expectedSubject) {
        requireOnlyKeys(tag, ACCOUNT_KEYS, "account " + expectedSubject);
        requireType(tag, ACCOUNT_VERSION, Tag.TAG_INT, "account " + expectedSubject);
        requireType(tag, SUBJECT_ID, Tag.TAG_INT_ARRAY, "account " + expectedSubject);
        requireType(tag, BALANCE, Tag.TAG_LONG, "account " + expectedSubject);
        requireType(tag, ACCOUNT_REVISION, Tag.TAG_LONG, "account " + expectedSubject);
        requireType(tag, CREATED_AT, Tag.TAG_LONG, "account " + expectedSubject);
        requireType(tag, LAST_TRANSACTION_ID, Tag.TAG_LONG, "account " + expectedSubject);

        int version = tag.getInt(ACCOUNT_VERSION);
        if (version != EconomyAccount.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported account version for " + expectedSubject + ": " + version
            );
        }
        SubjectId storedSubject = parseSubjectId(tag, SUBJECT_ID, "account " + expectedSubject);
        if (!expectedSubject.equals(storedSubject)) {
            throw invalid(
                    "Accounts key " + expectedSubject
                            + " does not match record subjectId " + storedSubject
            );
        }
        try {
            return new EconomyAccount(
                    version,
                    expectedSubject,
                    tag.getLong(BALANCE),
                    tag.getLong(ACCOUNT_REVISION),
                    tag.getLong(CREATED_AT),
                    tag.getLong(LAST_TRANSACTION_ID)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid account " + expectedSubject + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<Long, EconomyTransaction> decodeTransactions(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_TRANSACTIONS) {
            throw invalid("Transaction count exceeds " + HARD_MAX_TRANSACTIONS);
        }
        Map<Long, EconomyTransaction> transactions = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            long id = parseTransactionId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Transactions");
            EconomyTransaction transaction = decodeTransaction(tag.getCompound(key), id);
            if (transactions.put(id, transaction) != null) {
                throw invalid("Duplicate transaction id: " + id);
            }
        }
        return transactions;
    }

    private EconomyTransaction decodeTransaction(CompoundTag tag, long expectedId) {
        requireOnlyKeys(tag, TRANSACTION_KEYS, "transaction " + expectedId);
        requireType(tag, TRANSACTION_VERSION, Tag.TAG_INT, "transaction " + expectedId);
        requireType(tag, TRANSACTION_ID, Tag.TAG_LONG, "transaction " + expectedId);
        requireType(tag, TIMESTAMP, Tag.TAG_LONG, "transaction " + expectedId);
        requireType(tag, FROM_SUBJECT, Tag.TAG_INT_ARRAY, "transaction " + expectedId);
        requireType(tag, TO_SUBJECT, Tag.TAG_INT_ARRAY, "transaction " + expectedId);
        requireType(tag, AMOUNT, Tag.TAG_LONG, "transaction " + expectedId);
        requireType(tag, TYPE, Tag.TAG_STRING, "transaction " + expectedId);

        int version = tag.getInt(TRANSACTION_VERSION);
        if (version != EconomyTransaction.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported transaction version for " + expectedId + ": " + version
            );
        }
        long storedId = tag.getLong(TRANSACTION_ID);
        if (storedId != expectedId) {
            throw invalid(
                    "Transactions key " + expectedId
                            + " does not match record id " + storedId
            );
        }
        SubjectId from = parseSubjectId(tag, FROM_SUBJECT, "transaction " + expectedId);
        SubjectId to = parseSubjectId(tag, TO_SUBJECT, "transaction " + expectedId);
        TransactionType type = enumValue(
                TransactionType.class,
                tag.getString(TYPE),
                "Type for transaction " + expectedId
        );
        String memo = optionalString(tag, MEMO, "transaction " + expectedId);
        try {
            return new EconomyTransaction(
                    version,
                    expectedId,
                    tag.getLong(TIMESTAMP),
                    from,
                    to,
                    tag.getLong(AMOUNT),
                    type,
                    memo
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid transaction " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<SubjectId, List<NotificationSummary>> decodeNotifications(CompoundTag tag) {
        Map<SubjectId, List<NotificationSummary>> notifications = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            SubjectId subjectId = parseCanonicalSubjectId(key);
            requireType(tag, key, Tag.TAG_LIST, "PendingNotifications");
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            if (list.size() > HARD_MAX_NOTIFICATIONS_PER_SUBJECT) {
                throw invalid(
                        "Pending notification count for " + subjectId
                                + " exceeds " + HARD_MAX_NOTIFICATIONS_PER_SUBJECT
                );
            }
            List<NotificationSummary> decoded = new ArrayList<>();
            for (int index = 0; index < list.size(); index++) {
                decoded.add(decodeNotification(list.getCompound(index), subjectId));
            }
            if (notifications.put(subjectId, List.copyOf(decoded)) != null) {
                throw invalid("Duplicate notification owner: " + subjectId);
            }
        }
        return notifications;
    }

    private NotificationSummary decodeNotification(CompoundTag tag, SubjectId owner) {
        requireOnlyKeys(tag, NOTIFICATION_KEYS, "notification for " + owner);
        requireType(tag, NOTIFICATION_VERSION, Tag.TAG_INT, "notification for " + owner);
        requireType(tag, NOTIFICATION_ID, Tag.TAG_LONG, "notification for " + owner);
        requireType(tag, TRANSACTION_ID, Tag.TAG_LONG, "notification for " + owner);
        requireType(tag, TIMESTAMP, Tag.TAG_LONG, "notification for " + owner);
        requireType(tag, FROM_SUBJECT, Tag.TAG_INT_ARRAY, "notification for " + owner);
        requireType(tag, AMOUNT, Tag.TAG_LONG, "notification for " + owner);

        int version = tag.getInt(NOTIFICATION_VERSION);
        if (version != NotificationSummary.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported notification version for " + owner + ": " + version
            );
        }
        SubjectId from = parseSubjectId(tag, FROM_SUBJECT, "notification for " + owner);
        String memo = optionalString(tag, MEMO, "notification for " + owner);
        try {
            return new NotificationSummary(
                    version,
                    tag.getLong(NOTIFICATION_ID),
                    tag.getLong(TRANSACTION_ID),
                    tag.getLong(TIMESTAMP),
                    from,
                    tag.getLong(AMOUNT),
                    memo
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid notification for " + owner + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: every index is written
     * in sorted lexical key order.
     */
    public CompoundTag encode(EconomyStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());
        root.putLong(TREASURY_BALANCE, snapshot.treasuryBalance());
        root.putLong(NEXT_TRANSACTION_ID, snapshot.nextTransactionId());

        CompoundTag accountsTag = new CompoundTag();
        TreeMap<String, EconomyAccount> orderedAccounts = new TreeMap<>();
        snapshot.accounts().forEach(
                (subjectId, account) -> orderedAccounts.put(subjectId.canonicalKey(), account)
        );
        orderedAccounts.forEach(
                (key, account) -> accountsTag.put(key, encodeAccount(account))
        );
        root.put(ACCOUNTS, accountsTag);

        CompoundTag transactionsTag = new CompoundTag();
        TreeMap<String, EconomyTransaction> orderedTransactions = new TreeMap<>();
        snapshot.transactions().forEach(
                (id, transaction) -> orderedTransactions.put(
                        Long.toString(transaction.transactionId()), transaction
                )
        );
        orderedTransactions.forEach(
                (key, transaction) -> transactionsTag.put(key, encodeTransaction(transaction))
        );
        root.put(TRANSACTIONS, transactionsTag);

        CompoundTag notificationsTag = new CompoundTag();
        TreeMap<String, List<NotificationSummary>> orderedNotifications = new TreeMap<>();
        snapshot.pendingNotifications().forEach(
                (subjectId, list) -> orderedNotifications.put(subjectId.canonicalKey(), list)
        );
        orderedNotifications.forEach(
                (key, list) -> {
                    ListTag tag = new ListTag();
                    for (NotificationSummary summary : list) {
                        tag.add(encodeNotification(summary));
                    }
                    notificationsTag.put(key, tag);
                }
        );
        root.put(PENDING_NOTIFICATIONS, notificationsTag);
        return root;
    }

    private CompoundTag encodeAccount(EconomyAccount account) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ACCOUNT_VERSION, account.schemaVersion());
        tag.putUUID(SUBJECT_ID, account.subjectId().value());
        tag.putLong(BALANCE, account.balance());
        tag.putLong(ACCOUNT_REVISION, account.accountRevision());
        tag.putLong(CREATED_AT, account.createdAt());
        tag.putLong(LAST_TRANSACTION_ID, account.lastTransactionId());
        return tag;
    }

    private CompoundTag encodeTransaction(EconomyTransaction transaction) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TRANSACTION_VERSION, transaction.schemaVersion());
        tag.putLong(TRANSACTION_ID, transaction.transactionId());
        tag.putLong(TIMESTAMP, transaction.timestamp());
        tag.putUUID(FROM_SUBJECT, transaction.from().value());
        tag.putUUID(TO_SUBJECT, transaction.to().value());
        tag.putLong(AMOUNT, transaction.amount());
        tag.putString(TYPE, transaction.type().name());
        if (transaction.memo() != null) {
            tag.putString(MEMO, transaction.memo());
        }
        return tag;
    }

    private CompoundTag encodeNotification(NotificationSummary summary) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NOTIFICATION_VERSION, summary.schemaVersion());
        tag.putLong(NOTIFICATION_ID, summary.notificationId());
        tag.putLong(TRANSACTION_ID, summary.transactionId());
        tag.putLong(TIMESTAMP, summary.timestamp());
        tag.putUUID(FROM_SUBJECT, summary.from().value());
        tag.putLong(AMOUNT, summary.amount());
        if (summary.memo() != null) {
            tag.putString(MEMO, summary.memo());
        }
        return tag;
    }

    // ------------------------------------------------------------------
    // size
    // ------------------------------------------------------------------

    /** Serialized (uncompressed) size of an encoded namespace snapshot. */
    public int encodedSize(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private SubjectId parseSubjectId(CompoundTag tag, String key, String path) {
        try {
            return SubjectId.of(tag.getUUID(key));
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid " + path + " " + key + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private SubjectId parseCanonicalSubjectId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Subject key is not canonical: " + value);
            }
            return SubjectId.of(parsed);
        } catch (IllegalArgumentException failure) {
            if (failure instanceof EconomyNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid subject key: " + value, failure);
        }
    }

    private long parseTransactionId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw invalid("Invalid transaction key: " + value, failure);
        }
    }

    private String optionalString(CompoundTag tag, String key, String path) {
        if (!tag.contains(key)) {
            return null;
        }
        requireType(tag, key, Tag.TAG_STRING, path);
        return tag.getString(key);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw invalid("Unsupported " + field + " value: " + value);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(
                    path + " is missing required field " + key + " or has the wrong type"
            );
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static EconomyNbtException invalid(String message) {
        return new EconomyNbtException(message);
    }

    private static EconomyNbtException invalid(String message, Throwable cause) {
        return new EconomyNbtException(message, cause);
    }
}
