package com.fontainerepublic.server.registry.persistence;

/**
 * Fail-closed subject-registry unavailability (FR-ID-001-A §8.3).
 *
 * <p>Thrown when a mutation cannot be completed with authority: allocation
 * exhaustion, capacity exhaustion, an underlying store failure, missing
 * PlayerData preconditions, or bootstrap state this implementation rejects.
 * A failed mutation publishes no subject, number, index, or revision.</p>
 */
public final class SubjectRegistryUnavailableException extends RuntimeException {

    /** Registry cannot serve because persistence/init failed closed. */
    public static final String CODE_REGISTRY_UNAVAILABLE = "REGISTRY_UNAVAILABLE";

    /** Player-data service is not available at the provisioning boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Random serial source is exhausted (true allocation exhaustion). */
    public static final String CODE_ALLOCATION_EXHAUSTED = "ALLOCATION_EXHAUSTED";

    /** Bounded retry attempts were exhausted without a free candidate. */
    public static final String CODE_ALLOCATION_RETRIES_EXHAUSTED =
            "ALLOCATION_RETRIES_EXHAUSTED";

    /** Bounded SubjectId collision retries were exhausted. */
    public static final String CODE_SUBJECT_ID_EXHAUSTED = "SUBJECT_ID_EXHAUSTED";

    /** The registry has reached its encoded subject/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    /** Snapshot state requires the not-yet-approved original-person bootstrap. */
    public static final String CODE_BOOTSTRAP_BLOCKED = "BOOTSTRAP_BLOCKED";

    private final String failureCode;

    public SubjectRegistryUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public SubjectRegistryUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
