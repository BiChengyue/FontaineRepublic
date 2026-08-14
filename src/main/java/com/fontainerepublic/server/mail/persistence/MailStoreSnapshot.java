package com.fontainerepublic.server.mail.persistence;

import com.fontainerepublic.server.mail.model.MailboxState;
import com.fontainerepublic.server.mail.model.StoredMail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validating immutable snapshot of the complete {@code "mail"} namespace
 * (FR-MAIL-001-A §2.1). Encoded and decoded by {@link MailNbtCodec}; the
 * constructor enforces the key/identity and volume invariants so a decoded
 * snapshot is always internally consistent.
 */
public final class MailStoreSnapshot {

    public static final int CURRENT_STORE_VERSION = 1;

    private final int storeVersion;
    private final long storeRevision;
    private final long nextMailId;
    private final Map<Long, StoredMail> messages;
    private final Map<String, MailboxState> mailboxes;

    public MailStoreSnapshot(
            int storeVersion,
            long storeRevision,
            long nextMailId,
            Map<Long, StoredMail> messages,
            Map<String, MailboxState> mailboxes
    ) {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new MailNbtException("unsupported mail store version: " + storeVersion);
        }
        if (storeRevision < 0) {
            throw new MailNbtException("storeRevision must not be negative");
        }
        if (nextMailId < 1) {
            throw new MailNbtException("nextMailId must be positive");
        }
        this.storeVersion = storeVersion;
        this.storeRevision = storeRevision;
        this.nextMailId = nextMailId;
        this.messages = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(messages, "messages"))
        );
        this.mailboxes = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(mailboxes, "mailboxes"))
        );
        validateIdentity();
    }

    /** Every message's key matches its embedded mailId, and every mailbox
     *  inbox reference targets an existing message (fail closed). */
    private void validateIdentity() {
        for (Map.Entry<Long, StoredMail> entry : messages.entrySet()) {
            if (Long.compare(entry.getKey(), entry.getValue().mailId()) != 0) {
                throw new MailNbtException(
                        "messages key " + entry.getKey()
                                + " does not match mailId " + entry.getValue().mailId()
                );
            }
        }
        for (Map.Entry<String, MailboxState> entry : mailboxes.entrySet()) {
            if (com.fontainerepublic.server.mail.api.MailboxKey.parse(entry.getKey()) == null) {
                throw new MailNbtException("mailboxes key is not canonical: " + entry.getKey());
            }
            for (var inboxEntry : entry.getValue().inbox()) {
                if (!messages.containsKey(inboxEntry.mailId())) {
                    throw new MailNbtException(
                            "mailbox " + entry.getKey() + " references unknown mail "
                                    + inboxEntry.mailId()
                    );
                }
                if (messages.get(inboxEntry.mailId()).broadcast()) {
                    throw new MailNbtException(
                            "mailbox " + entry.getKey()
                                    + " inbox must not reference a broadcast mail "
                                    + inboxEntry.mailId()
                    );
                }
            }
        }
    }

    public int storeVersion() {
        return storeVersion;
    }

    public long storeRevision() {
        return storeRevision;
    }

    public long nextMailId() {
        return nextMailId;
    }

    public Map<Long, StoredMail> messages() {
        return messages;
    }

    public Map<String, MailboxState> mailboxes() {
        return mailboxes;
    }
}
