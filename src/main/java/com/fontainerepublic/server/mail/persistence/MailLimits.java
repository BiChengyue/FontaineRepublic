package com.fontainerepublic.server.mail.persistence;

/**
 * Implementation-design bounds of the mail store (FR-MAIL-001-A §2.1/§6).
 * The per-mailbox inbox cap ({@value #MAX_INBOX}) lives on
 * {@link com.fontainerepublic.server.mail.model.MailboxState}; these are the
 * whole-namespace volume / byte bounds.
 *
 * @param maxMessages        maximum stored messages (direct + broadcast)
 * @param maxMailboxes       maximum distinct mailboxes before provisioning fails closed
 * @param maxBroadcastRead   maximum retained read-marker ids per mailbox
 * @param maxTotalBytes      serialized (uncompressed) namespace byte budget
 */
public record MailLimits(
        int maxMessages,
        int maxMailboxes,
        int maxBroadcastRead,
        int maxTotalBytes
) {

    public static final MailLimits DEFAULT = new MailLimits(
            200_000,
            20_000,
            256,
            8 * 1024 * 1024
    );

    public MailLimits {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be positive");
        }
        if (maxMailboxes <= 0) {
            throw new IllegalArgumentException("maxMailboxes must be positive");
        }
        if (maxBroadcastRead <= 0) {
            throw new IllegalArgumentException("maxBroadcastRead must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
