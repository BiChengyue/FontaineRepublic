package com.fontainerepublic.server.mail.persistence;

/**
 * Strict NBT decode/validate failure of the {@code "mail"} namespace
 * (FR-MAIL-001-A §2.1). A corrupt namespace disables the module's
 * persistence (fail closed) rather than auto-repairing.
 */
public final class MailNbtException extends RuntimeException {

    public MailNbtException(String message) {
        super(message);
    }

    public MailNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
