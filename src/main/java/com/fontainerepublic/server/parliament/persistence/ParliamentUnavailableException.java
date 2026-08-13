package com.fontainerepublic.server.parliament.persistence;

/**
 * Fail-closed parliament unavailability (FR-PAR-001-A §6/§7).
 *
 * <p>Thrown when a mutation cannot be completed with authority: missing
 * PlayerData/subject preconditions, an unavailable citizen directory or
 * frozen roster, a missing proposal/vote/bill, an invalid on-site context at
 * the final mutation boundary, an illegal state transition, a duplicate
 * citizen vote, capacity exhaustion, or an underlying store failure. A
 * failed mutation publishes nothing — no proposal, vote, bill, transition,
 * tally, or revision.</p>
 */
public final class ParliamentUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the parliament store cannot serve. */
    public static final String CODE_PARLIAMENT_UNAVAILABLE = "PARLIAMENT_UNAVAILABLE";

    /** Player-data service is not available at the mutation boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Subject registry service is not available at the mutation boundary. */
    public static final String CODE_SUBJECT_REGISTRY_UNAVAILABLE = "SUBJECT_REGISTRY_UNAVAILABLE";

    /** Citizen directory service is not available at the mutation boundary. */
    public static final String CODE_CITIZEN_DIRECTORY_UNAVAILABLE = "CITIZEN_DIRECTORY_UNAVAILABLE";

    /** The frozen citizen roster (denominator) is not available at vote open. */
    public static final String CODE_ROSTER_UNAVAILABLE = "ROSTER_UNAVAILABLE";

    /** The proposal referenced by the request does not exist. */
    public static final String CODE_PROPOSAL_NOT_FOUND = "PROPOSAL_NOT_FOUND";

    /** The vote referenced by the request does not exist. */
    public static final String CODE_VOTE_NOT_FOUND = "VOTE_NOT_FOUND";

    /** The bill referenced by the request does not exist. */
    public static final String CODE_BILL_NOT_FOUND = "BILL_NOT_FOUND";

    /** The requested state transition is illegal for the closed state machine. */
    public static final String CODE_ILLEGAL_TRANSITION = "ILLEGAL_TRANSITION";

    /** The ballot is not OPEN for casting votes. */
    public static final String CODE_BALLOT_NOT_OPEN = "BALLOT_NOT_OPEN";

    /** The citizen already voted on this ballot (one vote per citizen). */
    public static final String CODE_ALREADY_VOTED = "ALREADY_VOTED";

    /** The voter is not an active citizen at the mutation boundary. */
    public static final String CODE_NOT_CITIZEN = "NOT_CITIZEN";

    /** The on-site context was not VALID at the final mutation boundary. */
    public static final String CODE_ON_SITE_CONTEXT_INVALID = "ON_SITE_CONTEXT_INVALID";

    /** The request (draft/argument) is invalid. */
    public static final String CODE_INVALID_REQUEST = "INVALID_REQUEST";

    /** The parliament store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    private final String failureCode;

    public ParliamentUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public ParliamentUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
