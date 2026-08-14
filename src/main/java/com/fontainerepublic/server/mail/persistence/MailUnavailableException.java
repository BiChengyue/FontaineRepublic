package com.fontainerepublic.server.mail.persistence;

/**
 * Failure of a mail durable commit or mailbox mutation. Uses a stable string
 * {@code code} so server chat and tests can map failures without parsing
 * messages (FR-MAIL-001-A §4).
 */
public final class MailUnavailableException extends RuntimeException {

    public static final String CODE_NO_MAILBOX = "NO_MAILBOX";
    public static final String CODE_MAILBOX_FULL = "MAILBOX_FULL";
    public static final String CODE_NO_MESSAGE = "NO_MESSAGE";
    public static final String CODE_NO_SUCH_MAIL = "NO_SUCH_MAIL";
    public static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";
    public static final String CODE_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String CODE_STALE_REVISION = "STALE_REVISION";
    public static final String CODE_NOT_CLAIMABLE = "NOT_CLAIMABLE";
    public static final String CODE_BROADCAST_COOLDOWN = "BROADCAST_COOLDOWN";
    public static final String CODE_BROADCAST_UNAVAILABLE = "BROADCAST_UNAVAILABLE";

    private final String failureCode;

    public MailUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode == null ? "" : failureCode;
    }

    public MailUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode == null ? "" : failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
