package com.fontainerepublic.server.citizen.persistence;

/**
 * Fail-closed citizen unavailability (FR-CIT-001-A §7).
 *
 * <p>Thrown when a mutation cannot be completed with authority: missing
 * PlayerData preconditions, an unavailable subject registry, a missing or
 * inconsistent subject binding, no citizen record, capacity exhaustion, or an
 * underlying store failure. A failed mutation publishes no citizen record, no
 * status/rank change, and no revision.</p>
 */
public final class CitizenUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the citizen store cannot serve. */
    public static final String CODE_CITIZEN_UNAVAILABLE = "CITIZEN_UNAVAILABLE";

    /** Player-data service is not available at the provisioning boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Subject registry service is not available at the provisioning boundary. */
    public static final String CODE_SUBJECT_REGISTRY_UNAVAILABLE = "SUBJECT_REGISTRY_UNAVAILABLE";

    /** The record's bound subject no longer exists (inconsistent state). */
    public static final String CODE_SUBJECT_MISSING = "SUBJECT_MISSING";

    /** No citizen record exists for the requested player. */
    public static final String CODE_NO_CITIZEN_RECORD = "NO_CITIZEN_RECORD";

    /** The citizen store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public CitizenUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public CitizenUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
