package com.fontainerepublic.server.audit.persistence;

/**
 * Fail-closed audit unavailability (FR-AUD-001-A §7): capacity exhausted or
 * the underlying store rejected a write. The audit ledger refuses new writes
 * instead of silently losing records; business modules continue but must not
 * claim audit-covered compliance while the audit is unavailable.
 */
public final class AuditUnavailableException extends RuntimeException {
    /** Stable, non-secret code for capacity exhaustion (bounded total bytes). */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** Stable, non-secret code for an underlying store write failure. */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public AuditUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public AuditUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
