package com.fontainerepublic.server.mail.api;

/**
 * Outcome of a mail read/claim (FR-MAIL-001-A §6.2). {@code claimed}
 * indicates the attachment was fully delivered and cannot be re-claimed
 * (anti-duplication); {@code pendingItems} is the count of item slots that
 * could not be delivered because the receiver's inventory was full and remain
 * in the mail for a later claim.
 */
public record MailClaimResult(
        boolean mailRead,
        boolean claimed,
        boolean moneyCredited,
        int itemsDelivered,
        int pendingItems,
        String status
) {

    public static final String STATUS_READ = "READ";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String STATUS_NO_SUCH_MAIL = "NO_SUCH_MAIL";
    public static final String STATUS_NO_FUNDS = "NO_FUNDS";

    public static MailClaimResult readOnly() {
        return new MailClaimResult(true, false, false, 0, 0, STATUS_READ);
    }

    public static MailClaimResult unauthorized() {
        return new MailClaimResult(false, false, false, 0, 0, STATUS_UNAUTHORIZED);
    }

    public static MailClaimResult noSuchMail() {
        return new MailClaimResult(false, false, false, 0, 0, STATUS_NO_SUCH_MAIL);
    }

    public static MailClaimResult noFunds() {
        return new MailClaimResult(true, false, false, 0, 0, STATUS_NO_FUNDS);
    }
}
