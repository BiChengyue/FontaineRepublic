package com.fontainerepublic.server.mail.api;

/**
 * Bounded outcome of a mail send or broadcast (FR-MAIL-001-A §3/§6).
 * The code maps to a stable server-side failure reason; the friendly message
 * is for chat feedback.
 */
public record MailSendResult(boolean success, String code, String message, long mailId) {

    public static final String CODE_OK = "OK";
    public static final String CODE_NOT_HOLDING = "NOT_HOLDING";
    public static final String CODE_RECIPIENT_UNKNOWN = "RECIPIENT_UNKNOWN";
    public static final String CODE_RECIPIENT_AMBIGUOUS = "RECIPIENT_AMBIGUOUS";
    public static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String CODE_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String CODE_INVALID = "INVALID";
    public static final String CODE_COOLDOWN = "COOLDOWN";
    public static final String CODE_STORE = "STORE";
    public static final String CODE_SELF = "SELF";
    public static final String CODE_NO_SUBJECT = "NO_SUBJECT";

    public static MailSendResult ok(long mailId) {
        return new MailSendResult(true, CODE_OK, "Mail sent.", mailId);
    }

    public static MailSendResult fail(String code, String message) {
        return new MailSendResult(false, code, message, 0L);
    }

    public static final MailSendResult OK = ok(0L);
}
