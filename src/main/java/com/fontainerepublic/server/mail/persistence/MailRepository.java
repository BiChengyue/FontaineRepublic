package com.fontainerepublic.server.mail.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.mail.api.MailboxKey;
import com.fontainerepublic.server.mail.model.MailboxEntry;
import com.fontainerepublic.server.mail.model.MailboxState;
import com.fontainerepublic.server.mail.model.StoredMail;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Single-writer repository owning the {@code "mail"} NBT namespace
 * (FR-MAIL-001-A §2.1).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "mail"} key. All mutations run on the logical server owner thread;
 * every mutation builds one complete immutable replacement snapshot, bumps
 * the store revision once and publishes the new in-memory state only after
 * the FR-CORE-002 durable gate reports {@code COMMITTED}. A failed write has
 * no side effects. Money-attachment claim is a two-store coordination with
 * the economy module (the economy transfer commits first, then this store
 * records the delivery marker); the {@code moneyDelivered} flag is the
 * authority guard that prevents re-crediting (anti-duplication).</p>
 */
public final class MailRepository {

    /** Reserved module-data key for the mail namespace. */
    public static final String MODULE_DATA_KEY = "mail";

    private final MailStore store;
    private final MailNbtCodec codec;
    private final MailLimits limits;
    private final Thread ownerThread;

    private final LinkedHashMap<Long, StoredMail> messages = new LinkedHashMap<>();
    private final LinkedHashMap<String, MailboxState> mailboxes = new LinkedHashMap<>();
    private long storeRevision;
    private long nextMailId;

    /** Creates a repository backed by the production {@link DataManager} store. */
    public static MailRepository createProduction(MailNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new MailRepository(
                new DataManagerMailStore(),
                codec,
                MailLimits.DEFAULT
        );
    }

    public MailRepository(
            MailStore store,
            MailNbtCodec codec,
            MailLimits limits
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
            this.nextMailId = 1L;
        } else {
            MailStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface
    // ------------------------------------------------------------------

    public long storeRevision() {
        requireOwnerThread();
        return storeRevision;
    }

    public long nextMailId() {
        requireOwnerThread();
        return nextMailId;
    }

    public MailStoreSnapshot snapshot() {
        requireOwnerThread();
        return new MailStoreSnapshot(
                MailStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                nextMailId,
                messages,
                mailboxes
        );
    }

    public java.util.Optional<StoredMail> findMessage(long mailId) {
        requireOwnerThread();
        return java.util.Optional.ofNullable(messages.get(mailId));
    }

    public java.util.Optional<MailboxState> mailbox(MailboxKey key) {
        requireOwnerThread();
        return java.util.Optional.ofNullable(mailboxes.get(key.key()));
    }

    public MailboxState requireMailbox(MailboxKey key) {
        return mailbox(key).orElseThrow(
                () -> new MailUnavailableException(
                        MailUnavailableException.CODE_NO_MAILBOX,
                        "No mailbox for " + key.key()
                )
        );
    }

    public List<StoredMail> broadcasts() {
        requireOwnerThread();
        List<StoredMail> result = new ArrayList<>();
        for (StoredMail mail : messages.values()) {
            if (mail.broadcast()) {
                result.add(mail);
            }
        }
        return List.copyOf(result);
    }

    /** Count of stored messages (direct + broadcast). */
    public int messageCount() {
        requireOwnerThread();
        return messages.size();
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Lazy, idempotent provisioning of a mailbox for a recipient key
     * (FR-MAIL-001-A §2.1): creates an empty mailbox on first touch; returns
     * the existing one unchanged on repeat calls.
     */
    public MailboxState ensureMailbox(MailboxKey key, long timestamp) {
        requireOwnerThread();
        Objects.requireNonNull(key, "key");
        MailboxState existing = mailboxes.get(key.key());
        if (existing != null) {
            return existing;
        }
        if (mailboxes.size() >= limits.maxMailboxes()) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Mailbox count would exceed the budget of " + limits.maxMailboxes()
            );
        }
        requireStoreRevisionSpace();
        MailboxState created = MailboxState.empty();
        LinkedHashMap<String, MailboxState> next = new LinkedHashMap<>(mailboxes);
        next.put(key.key(), created);
        commitAndPublish(buildSnapshot(storeRevision + 1, nextMailId, messages, next));
        return created;
    }

    /**
     * Stores a direct mail and appends the recipient's inbox reference in one
     * snapshot. The message must already embed the recipient as {@code toKey}
     * and must carry this repository's next unused mail id.
     */
    public void deliverDirect(StoredMail mail) {
        requireOwnerThread();
        Objects.requireNonNull(mail, "mail");
        if (mail.broadcast()) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_INVALID_ARGUMENT,
                    "deliverDirect requires a direct mail"
            );
        }
        requireNextId(mail.mailId());
        MailboxState box = mailboxes.get(mail.toKey());
        if (box == null) {
            if (mailboxes.size() >= limits.maxMailboxes()) {
                throw new MailUnavailableException(
                        MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                        "Mailbox count would exceed the budget of " + limits.maxMailboxes()
                );
            }
            box = MailboxState.empty();
        }
        requireStoreRevisionSpace();

        LinkedHashMap<Long, StoredMail> nextMessages = new LinkedHashMap<>(messages);
        nextMessages.put(mail.mailId(), mail);
        LinkedHashMap<String, MailboxState> nextMailboxes = new LinkedHashMap<>(mailboxes);
        nextMailboxes.put(
                mail.toKey(),
                box.withInboxEntry(new MailboxEntry(mail.mailId(), false, false))
        );
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextMailId(mail.mailId()),
                nextMessages,
                nextMailboxes
        ));
    }

    /**
     * Stores a broadcast mail (no inbox references — delivered by derivation
     * to every citizen). The id must be this repository's next unused mail id.
     */
    public void storeBroadcast(StoredMail broadcast) {
        requireOwnerThread();
        Objects.requireNonNull(broadcast, "broadcast");
        if (!broadcast.broadcast()) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_INVALID_ARGUMENT,
                    "storeBroadcast requires a broadcast mail"
            );
        }
        requireNextId(broadcast.mailId());
        requireStoreRevisionSpace();

        LinkedHashMap<Long, StoredMail> nextMessages = new LinkedHashMap<>(messages);
        nextMessages.put(broadcast.mailId(), broadcast);
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextMailId(broadcast.mailId()),
                nextMessages,
                mailboxes
        ));
    }

    private void requireNextId(long mailId) {
        if (mailId != nextMailId) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_INVALID_ARGUMENT,
                    "mail id " + mailId + " is not the repository's next id " + nextMailId
            );
        }
        if (nextMailId == Long.MAX_VALUE) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Mail id space exhausted"
            );
        }
    }

    private static long nextMailId(long mailId) {
        return mailId + 1L;
    }

    /**
     * Marks a direct mail read (or a broadcast read for a person mailbox).
     * Broadcast suppression is per-mailbox; direct entries are flipped in the
     * recipient's inbox.
     */
    public void markRead(MailboxKey owner, long mailId) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        MailboxState box = mailboxes.get(owner.key());
        if (box == null) {
            return; // nothing read in an unknown mailbox
        }
        StoredMail mail = messages.get(mailId);
        if (mail == null) {
            return;
        }
        requireStoreRevisionSpace();
        MailboxState next;
        if (mail.broadcast()) {
            next = box.withBroadcastRead(mailId);
        } else {
            List<MailboxEntry> updated = new ArrayList<>(box.inbox());
            boolean found = false;
            for (int index = 0; index < updated.size(); index++) {
                MailboxEntry entry = updated.get(index);
                if (entry.mailId() == mailId) {
                    updated.set(index, entry.withRead());
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            next = new MailboxState(updated, box.broadcastRead());
        }
        if (next.equals(box)) {
            return;
        }
        LinkedHashMap<String, MailboxState> nextMailboxes = new LinkedHashMap<>(mailboxes);
        nextMailboxes.put(owner.key(), next);
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextMailId,
                messages,
                nextMailboxes
        ));
    }

    /**
     * Deletes a direct mail from a recipient's inbox, or suppresses a
     * broadcast for that recipient (marks it read so it no longer counts
     * unread).
     */
    public void deleteMail(MailboxKey owner, long mailId) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        MailboxState box = mailboxes.get(owner.key());
        if (box == null) {
            return;
        }
        StoredMail mail = messages.get(mailId);
        requireStoreRevisionSpace();
        MailboxState next;
        if (mail != null && mail.broadcast()) {
            next = box.withBroadcastRead(mailId);
        } else {
            boolean had = box.inbox().stream().anyMatch(e -> e.mailId() == mailId);
            if (!had) {
                return;
            }
            next = box.removeInboxEntry(mailId);
        }
        LinkedHashMap<String, MailboxState> nextMailboxes = new LinkedHashMap<>(mailboxes);
        nextMailboxes.put(owner.key(), next);
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextMailId,
                messages,
                nextMailboxes
        ));
    }

    /**
     * Records that the money attachment of {@code mailId} has been delivered
     * to the recipient: clears the message's money intention and marks the
     * entry {@code moneyDelivered}. Called by the claim flow after the economy
     * transfer has committed. Anti-duplication: once cleared, no re-claim can
     * re-credit it.
     */
    public void markMoneyDelivered(MailboxKey owner, long mailId) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        MailboxState box = mailboxes.get(owner.key());
        if (box == null) {
            return;
        }
        StoredMail mail = messages.get(mailId);
        if (mail == null || mail.broadcast() || mail.moneyAttachment() == 0L) {
            return;
        }
        requireStoreRevisionSpace();

        StoredMail cleared = mail.withMoneyDelivered();
        LinkedHashMap<Long, StoredMail> nextMessages = new LinkedHashMap<>(messages);
        nextMessages.put(mailId, cleared);

        List<MailboxEntry> updated = new ArrayList<>(box.inbox());
        for (int index = 0; index < updated.size(); index++) {
            MailboxEntry entry = updated.get(index);
            if (entry.mailId() == mailId) {
                updated.set(index, entry.withMoneyDelivered());
                break;
            }
        }
        MailboxState nextBox = new MailboxState(updated, box.broadcastRead());
        LinkedHashMap<String, MailboxState> nextMailboxes = new LinkedHashMap<>(mailboxes);
        nextMailboxes.put(owner.key(), nextBox);
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextMailId,
                nextMessages,
                nextMailboxes
        ));
    }

    /**
     * Clears the delivered item slot indices on a direct mail (they were moved
     * into the receiver's inventory). Indices are removed in one snapshot.
     */
    public void clearDeliveredItems(MailboxKey owner, long mailId, List<Integer> deliveredSlots) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(deliveredSlots, "deliveredSlots");
        if (mailboxes.get(owner.key()) == null) {
            return;
        }
        StoredMail mail = messages.get(mailId);
        if (mail == null || mail.broadcast() || deliveredSlots.isEmpty()) {
            return;
        }
        requireStoreRevisionSpace();
        List<Integer> distinct = deliveredSlots.stream().distinct().sorted().toList();
        List<Integer> validIndices = new ArrayList<>();
        for (int index : distinct) {
            if (index >= 0 && index < mail.itemSlots().size()
                    && !mail.itemSlots().get(index).isEmpty()) {
                validIndices.add(index);
            }
        }
        if (validIndices.isEmpty()) {
            return;
        }
        StoredMail toClear = mail;
        for (int index : validIndices) {
            toClear = toClear.withItemDelivered(index);
        }
        LinkedHashMap<Long, StoredMail> nextMessages = new LinkedHashMap<>(messages);
        nextMessages.put(mailId, toClear);
        commitAndPublish(buildSnapshot(
                storeRevision + 1,
                nextMailId,
                nextMessages,
                mailboxes
        ));
    }

    /** Reports whether the stored direct/broadcast mail is fully claimed. */
    public boolean isFullyClaimed(long mailId) {
        requireOwnerThread();
        StoredMail mail = messages.get(mailId);
        return mail != null && mail.fullyClaimed();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private MailStoreSnapshot buildSnapshot(
            long nextStoreRevision,
            long nextMailId,
            Map<Long, StoredMail> messages,
            Map<String, MailboxState> mailboxes
    ) {
        return new MailStoreSnapshot(
                MailStoreSnapshot.CURRENT_STORE_VERSION,
                nextStoreRevision,
                nextMailId,
                messages,
                mailboxes
        );
    }

    private void commitAndPublish(MailStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Mail namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }
        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_STORE_FAILURE,
                    "Mail store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of mail failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    private void publish(MailStoreSnapshot snapshot) {
        messages.clear();
        messages.putAll(snapshot.messages());
        mailboxes.clear();
        mailboxes.putAll(snapshot.mailboxes());
        storeRevision = snapshot.storeRevision();
        nextMailId = snapshot.nextMailId();
    }

    private void enforceLoadedCapacity(MailStoreSnapshot snapshot) {
        if (snapshot.messages().size() > limits.maxMessages()) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded message count " + snapshot.messages().size()
                            + " exceeds the budget of " + limits.maxMessages()
            );
        }
        if (snapshot.mailboxes().size() > limits.maxMailboxes()) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded mailbox count " + snapshot.mailboxes().size()
                            + " exceeds the budget of " + limits.maxMailboxes()
            );
        }
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw new MailUnavailableException(
                    MailUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Mail store revision space exhausted"
            );
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "MailRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerMailStore implements MailStore {
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
