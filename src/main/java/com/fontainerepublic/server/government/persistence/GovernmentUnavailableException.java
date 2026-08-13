package com.fontainerepublic.server.government.persistence;

/**
 * Fail-closed government unavailability (FR-GOV-001-A §6/§7).
 *
 * <p>Thrown when a mutation cannot be completed with authority: missing
 * PlayerData preconditions, an unavailable subject or holder directory, a
 * missing ministry/position, an invalid on-site context at the final mutation
 * boundary, capacity exhaustion, or an underlying store failure. A failed
 * mutation publishes no ministry, position, office, holder change, or
 * revision.</p>
 */
public final class GovernmentUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the government store cannot serve. */
    public static final String CODE_GOVERNMENT_UNAVAILABLE = "GOVERNMENT_UNAVAILABLE";

    /** Player-data service is not available at the mutation boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Subject/holder directory service is not available at the boundary. */
    public static final String CODE_HOLDER_DIRECTORY_UNAVAILABLE = "HOLDER_DIRECTORY_UNAVAILABLE";

    /** The holder is not resolvable to an active subject. */
    public static final String CODE_INVALID_HOLDER = "INVALID_HOLDER";

    /** The holder kind (e.g. OFFICE_ID) is not supported in Alpha. */
    public static final String CODE_UNSUPPORTED_HOLDER_KIND = "UNSUPPORTED_HOLDER_KIND";

    /** The ministry referenced by the request does not exist. */
    public static final String CODE_MINISTRY_NOT_FOUND = "MINISTRY_NOT_FOUND";

    /** The position referenced by the request does not exist. */
    public static final String CODE_POSITION_NOT_FOUND = "POSITION_NOT_FOUND";

    /** The position is already filled and cannot be appointed again. */
    public static final String CODE_POSITION_FILLED = "POSITION_FILLED";

    /** The position is suspended and cannot be appointed or dismissed. */
    public static final String CODE_POSITION_SUSPENDED = "POSITION_SUSPENDED";

    /** The on-site context was not VALID at the final mutation boundary. */
    public static final String CODE_ON_SITE_CONTEXT_INVALID = "ON_SITE_CONTEXT_INVALID";

    /** The request (draft/reason) is invalid. */
    public static final String CODE_INVALID_REQUEST = "INVALID_REQUEST";

    /** The government store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public GovernmentUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public GovernmentUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
