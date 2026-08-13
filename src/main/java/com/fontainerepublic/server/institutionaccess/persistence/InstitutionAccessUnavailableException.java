package com.fontainerepublic.server.institutionaccess.persistence;

/**
 * Fail-closed institution-access unavailability (FR-INST-002-A §7).
 *
 * <p>Thrown when a mutation or context operation cannot be completed with
 * authority: unavailable player-data/subject/land services, an invalid or
 * unprovisioned actor, an unknown facility/terminal or parcel, a state-machine
 * violation, a terminal outside its facility region, an unsupported
 * capability, capacity exhaustion, or an underlying store failure. A failed
 * mutation publishes no facility, no terminal, no revision, and no context.</p>
 */
public final class InstitutionAccessUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the store cannot serve. */
    public static final String CODE_UNAVAILABLE = "INSTITUTION_ACCESS_UNAVAILABLE";

    /** Holder-resolution services are not available. */
    public static final String CODE_HOLDER_DIRECTORY_UNAVAILABLE = "HOLDER_DIRECTORY_UNAVAILABLE";

    /** The actor UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** The actor cannot be resolved to an active subject. */
    public static final String CODE_INVALID_HOLDER = "INVALID_HOLDER";

    /** The land service is not available for parcel resolution. */
    public static final String CODE_LAND_UNAVAILABLE = "LAND_UNAVAILABLE";

    /** The referenced FR-LAND parcel does not exist. */
    public static final String CODE_PARCEL_NOT_FOUND = "PARCEL_NOT_FOUND";

    /** The parcel is already bound to another facility. */
    public static final String CODE_PARCEL_ALREADY_BOUND = "PARCEL_ALREADY_BOUND";

    /** No facility exists for the requested id. */
    public static final String CODE_FACILITY_NOT_FOUND = "FACILITY_NOT_FOUND";

    /** The requested facility state transition is not permitted. */
    public static final String CODE_INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    /** The facility is not in an ACTIVE state. */
    public static final String CODE_FACILITY_NOT_ACTIVE = "FACILITY_NOT_ACTIVE";

    /** No terminal exists for the requested id. */
    public static final String CODE_TERMINAL_NOT_FOUND = "TERMINAL_NOT_FOUND";

    /** The terminal is not in an ACTIVE state. */
    public static final String CODE_TERMINAL_NOT_ACTIVE = "TERMINAL_NOT_ACTIVE";

    /** The terminal institution type does not match its facility. */
    public static final String CODE_INSTITUTION_MISMATCH = "INSTITUTION_MISMATCH";

    /** The terminal position is not inside its facility's parcel region. */
    public static final String CODE_TERMINAL_OUTSIDE_REGION = "TERMINAL_OUTSIDE_REGION";

    /** The requested capability is not allowed by the terminal. */
    public static final String CODE_CAPABILITY_NOT_ALLOWED = "CAPABILITY_NOT_ALLOWED";

    /** Only on-site capability classes may anchor an on-site context. */
    public static final String CODE_UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";

    /** The player is not within the configured interaction distance. */
    public static final String CODE_PLAYER_OUT_OF_RANGE = "PLAYER_OUT_OF_RANGE";

    /** The player dimension does not match the terminal dimension. */
    public static final String CODE_DIMENSION_MISMATCH = "DIMENSION_MISMATCH";

    /** A high-risk authorization requires a valid official routine session. */
    public static final String CODE_OFFICIAL_SESSION_REQUIRED = "OFFICIAL_SESSION_REQUIRED";

    /** A high-risk authorization requires a secure terminal. */
    public static final String CODE_SECURE_TERMINAL_REQUIRED = "SECURE_TERMINAL_REQUIRED";

    /** The request failed validation. */
    public static final String CODE_INVALID_REQUEST = "INVALID_REQUEST";

    /** The store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public InstitutionAccessUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public InstitutionAccessUnavailableException(
            String failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
