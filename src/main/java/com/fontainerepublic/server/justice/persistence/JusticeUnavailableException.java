package com.fontainerepublic.server.justice.persistence;

/**
 * Fail-closed justice unavailability (FR-JUS-001-A §6/§7).
 *
 * <p>Thrown when a mutation cannot be completed with authority: missing
 * PlayerData/subject preconditions, an unavailable citizen directory, a
 * missing case/evidence/verdict, an invalid on-site context at the final
 * mutation boundary, an illegal state transition, a duplicate intake of the
 * same Land report, inadmissible evidence at the verdict boundary, capacity
 * exhaustion, or an underlying store failure. A failed mutation publishes
 * nothing — no case, evidence, verdict, transition, or revision.</p>
 */
public final class JusticeUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the justice store cannot serve. */
    public static final String CODE_JUSTICE_UNAVAILABLE = "JUSTICE_UNAVAILABLE";

    /** Player-data service is not available at the mutation boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Citizen directory service is not available at the mutation boundary. */
    public static final String CODE_CITIZEN_DIRECTORY_UNAVAILABLE =
            "CITIZEN_DIRECTORY_UNAVAILABLE";

    /** The case referenced by the request does not exist. */
    public static final String CODE_CASE_NOT_FOUND = "CASE_NOT_FOUND";

    /** The evidence referenced by the request does not exist. */
    public static final String CODE_EVIDENCE_NOT_FOUND = "EVIDENCE_NOT_FOUND";

    /** The verdict referenced by the request does not exist. */
    public static final String CODE_VERDICT_NOT_FOUND = "VERDICT_NOT_FOUND";

    /** The requested state transition is illegal for the closed pipeline. */
    public static final String CODE_ILLEGAL_TRANSITION = "ILLEGAL_TRANSITION";

    /** The actor is not an active citizen at the mutation boundary. */
    public static final String CODE_NOT_CITIZEN = "NOT_CITIZEN";

    /** The on-site context was not VALID at the final mutation boundary. */
    public static final String CODE_ON_SITE_CONTEXT_INVALID = "ON_SITE_CONTEXT_INVALID";

    /** The evidence is not admissible at the final mutation boundary. */
    public static final String CODE_EVIDENCE_NOT_ADMISSIBLE = "EVIDENCE_NOT_ADMISSIBLE";

    /** The Land violation report was already filed (one case per report). */
    public static final String CODE_REPORT_ALREADY_FILED = "REPORT_ALREADY_FILED";

    /** The evidence is not SUBMITTED and cannot be ruled. */
    public static final String CODE_EVIDENCE_ALREADY_RULED = "EVIDENCE_ALREADY_RULED";

    /** The request (draft/argument) is invalid. */
    public static final String CODE_INVALID_REQUEST = "INVALID_REQUEST";

    /** The justice store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public JusticeUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public JusticeUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
