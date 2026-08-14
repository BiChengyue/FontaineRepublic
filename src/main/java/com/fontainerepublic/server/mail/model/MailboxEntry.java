package com.fontainerepublic.server.mail.model;

/**
 * One reference to a stored mail inside a recipient mailbox
 * (FR-MAIL-001-A §2.1). Direct mails only; broadcast mails are derived
 * per-citizen and tracked by the mailbox's broadcast-read set instead.
 *
 * <p>{@code read} marks the message as opened (viewed by the recipient);
 * {@code moneyDelivered} marks the money attachment as already credited (so a
 * later claim can never re-credit it — anti-duplication). Item slots are
 * cleared in place on the shared {@link StoredMail} as they are delivered, so
 * no separate item-count flag is needed here.</p>
 */
public record MailboxEntry(long mailId, boolean read, boolean moneyDelivered) {

    public MailboxEntry {
        if (mailId <= 0) {
            throw new IllegalArgumentException("mailId must be positive");
        }
    }

    public MailboxEntry withRead() {
        return new MailboxEntry(mailId, true, moneyDelivered);
    }

    public MailboxEntry withMoneyDelivered() {
        return new MailboxEntry(mailId, read, true);
    }
}
