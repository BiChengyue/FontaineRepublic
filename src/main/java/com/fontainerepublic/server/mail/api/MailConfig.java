package com.fontainerepublic.server.mail.api;

/**
 * Bounded mail fee/cooldown configuration (FR-MAIL-001-A §6.3/§6.6),
 * read from {@link com.fontainerepublic.core.ConfigManager} at module build
 * time and supplied explicitly in tests.
 */
public record MailConfig(
        long postageFee,
        long attachmentFee,
        long broadcastFee,
        long broadcastCooldownMillis,
        java.util.UUID hydroArchonUuid
) {

    public MailConfig {
        if (postageFee < 0 || attachmentFee < 0 || broadcastFee < 0) {
            throw new IllegalArgumentException("fees must not be negative");
        }
        if (broadcastCooldownMillis < 0) {
            throw new IllegalArgumentException("broadcastCooldownMillis must not be negative");
        }
    }

    /** A plain letter with no attachments. */
    public long plainLetterFee() {
        return postageFee;
    }

    /** Per-attachment surcharge (money attachment or an item slot each). */
    public long perAttachmentFee() {
        return attachmentFee;
    }
}
