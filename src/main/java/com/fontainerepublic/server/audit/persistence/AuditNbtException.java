package com.fontainerepublic.server.audit.persistence;

/**
 * Raised when the audit namespace NBT is structurally invalid, inconsistent
 * with its own digest chain, or violates the strict codec schema
 * (FR-AUD-001-A §7: store corrupt -> fail closed; no auto-repair).
 */
public final class AuditNbtException extends RuntimeException {
    public AuditNbtException(String message) {
        super(message);
    }

    public AuditNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
