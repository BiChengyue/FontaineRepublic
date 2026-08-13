package com.fontainerepublic.server.emergency.persistence;

/**
 * Fail-closed exception of the shared emergency namespace
 * (FR-EMG-001-A §14 availability matrix).
 */
public final class EmergencyUnavailableException extends RuntimeException {

    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";
    public static final String CODE_CORRUPTION = "CORRUPTION";
    public static final String CODE_NOT_ACTIVE = "NOT_ACTIVE";
    public static final String CODE_STOPPING = "STOPPING";
    public static final String CODE_DRIFT = "DRIFT";
    public static final String CODE_THREAD_VIOLATION = "THREAD_VIOLATION";

    private final String failureCode;

    public EmergencyUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public EmergencyUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
