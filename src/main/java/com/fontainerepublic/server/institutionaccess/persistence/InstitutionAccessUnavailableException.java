package com.fontainerepublic.server.institutionaccess.persistence;

/**
 * Fail-closed institution-access unavailability (FR-INST-002-A §7,
 * FR-INST-002-B §7).
 *
 * <p>Thrown when a mutation or context operation cannot be completed with
 * authority: unavailable player-data/subject/land services, an invalid or
 * unprovisioned actor, an unknown facility/zone or parcel, a state-machine
 * violation, a zone outside its facility parcel region or beyond the
 * small-size budget, a capability not allowed by the zone kind or set, an
 * unsupported capability, capacity exhaustion, or an underlying store
 * failure. A failed mutation publishes no facility, no zone, no revision,
 * and no context.</p>
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

    /** No zone exists for the requested id. */
    public static final String CODE_ZONE_NOT_FOUND = "ZONE_NOT_FOUND";

    /** The zone is not in an ACTIVE state. */
    public static final String CODE_ZONE_NOT_ACTIVE = "ZONE_NOT_ACTIVE";

    /** The zone institution type does not match its facility. */
    public static final String CODE_INSTITUTION_MISMATCH = "INSTITUTION_MISMATCH";

    /** The zone region is not inside its facility's parcel region. */
    public static final String CODE_ZONE_OUTSIDE_REGION = "ZONE_OUTSIDE_REGION";

    /** The zone region exceeds the small-size budget of the workflow. */
    public static final String CODE_ZONE_SIZE_EXCEEDED = "ZONE_SIZE_EXCEEDED";

    /** The requested capability is not allowed by the zone. */
    public static final String CODE_CAPABILITY_NOT_ALLOWED = "CAPABILITY_NOT_ALLOWED";

    /** The capability set is not a subset of the zone kind's allowed classes. */
    public static final String CODE_KIND_CAPABILITY_MISMATCH = "KIND_CAPABILITY_MISMATCH";

    /** Only on-site capability classes may anchor an on-site context. */
    public static final String CODE_UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";

    /** The player is not inside the zone region. */
    public static final String CODE_PLAYER_OUT_OF_RANGE = "PLAYER_OUT_OF_RANGE";

    /** The player dimension does not match the zone dimension. */
    public static final String CODE_DIMENSION_MISMATCH = "DIMENSION_MISMATCH";

    /** A high-risk authorization requires a valid official routine session. */
    public static final String CODE_OFFICIAL_SESSION_REQUIRED = "OFFICIAL_SESSION_REQUIRED";

    /** A high-risk authorization requires a SECURE zone. */
    public static final String CODE_SECURE_ZONE_REQUIRED = "SECURE_ZONE_REQUIRED";

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
