package com.fontainerepublic.server.mail.api;

import java.util.List;

/**
 * Bounded read projection of one mailbox (FR-MAIL-001-A §2.3/§6.6):
 * the recipient's own direct mail plus any derived unread broadcasts, and the
 * exact unread count for the alert HUD/chat. Display-only; never authoritative.
 */
public record MailboxView(List<MailEntryView> entries, int unreadCount, long at) {

    public static final int MAX_VIEW = 64;

    /** One bounded mail entry in the projection. */
    public record MailEntryView(
            long mailId,
            boolean broadcast,
            String fromDisplay,
            String subject,
            String body,
            long sentAt,
            boolean read,
            long moneyAttachment,
            boolean attachmentsClaimed,
            int itemSlotCount
    ) {
    }
}
