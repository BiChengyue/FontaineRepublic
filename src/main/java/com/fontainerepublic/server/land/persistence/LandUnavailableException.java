package com.fontainerepublic.server.land.persistence;

/**
 * Fail-closed land unavailability (FR-LAND-001-A §7).
 *
 * <p>Thrown when a mutation or event-time resolution cannot be completed with
 * authority: unavailable PlayerData/subject-registry services, an invalid or
 * unprovisioned holder/actor, an unknown parcel, a duplicate or missing usage
 * right, an invalid create request, capacity exhaustion, or an underlying
 * store failure. A failed mutation publishes no parcel, no usage right, no
 * report, and no revision.</p>
 */
public final class LandUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the land store cannot serve. */
    public static final String CODE_LAND_UNAVAILABLE = "LAND_UNAVAILABLE";

    /** Player-data/subject holder resolution services are not available. */
    public static final String CODE_HOLDER_DIRECTORY_UNAVAILABLE = "HOLDER_DIRECTORY_UNAVAILABLE";

    /** Player-data service is not available at the boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Subject registry service is not available at the boundary. */
    public static final String CODE_SUBJECT_REGISTRY_UNAVAILABLE = "SUBJECT_REGISTRY_UNAVAILABLE";

    /** The holder/actor cannot be resolved to an active subject. */
    public static final String CODE_INVALID_HOLDER = "INVALID_HOLDER";

    /** Alpha accepts only {@code PLAYER_UUID} usage-right holders. */
    public static final String CODE_UNSUPPORTED_HOLDER_KIND = "UNSUPPORTED_HOLDER_KIND";

    /** No parcel exists for the requested id. */
    public static final String CODE_PARCEL_NOT_FOUND = "PARCEL_NOT_FOUND";

    /** A parcel already exists for the requested id (server-id collision). */
    public static final String CODE_PARCEL_EXISTS = "PARCEL_EXISTS";

    /** The create request failed validation (dimension/region). */
    public static final String CODE_INVALID_REQUEST = "INVALID_REQUEST";

    /** The holder already holds a usage right on the parcel (use renew). */
    public static final String CODE_DUPLICATE_GRANT = "DUPLICATE_GRANT";

    /** The holder holds no usage right to renew or revoke. */
    public static final String CODE_NO_USAGE_RIGHT = "NO_USAGE_RIGHT";

    /** The land store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public LandUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public LandUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
