package com.fontainerepublic.server.economy.persistence;

/**
 * Fail-closed economy unavailability (FR-ECO-001-C §4.2/§10).
 *
 * <p>Thrown when a mutation cannot be completed with authority: missing
 * PlayerData preconditions, an unavailable subject registry, a missing or
 * non-active subject, no account, invalid amount/self-transfer/insufficient
 * funds/overflow, transfer cooldown, a stale revision, capacity exhaustion,
 * or an underlying store failure. A failed operation publishes no balance,
 * transaction, notification, receipt, or revision change.</p>
 */
public final class EconomyUnavailableException extends RuntimeException {

    /** Persistence/init failed closed; the economy store cannot serve. */
    public static final String CODE_ECONOMY_UNAVAILABLE = "ECONOMY_UNAVAILABLE";

    /** Player-data service is not available at the provisioning boundary. */
    public static final String CODE_PLAYER_DATA_UNAVAILABLE = "PLAYER_DATA_UNAVAILABLE";

    /** The UUID has no authoritative PlayerData record yet. */
    public static final String CODE_PLAYER_NOT_PROVISIONED = "PLAYER_NOT_PROVISIONED";

    /** Subject registry service is not available at the mutation boundary. */
    public static final String CODE_SUBJECT_REGISTRY_UNAVAILABLE =
            "SUBJECT_REGISTRY_UNAVAILABLE";

    /** A required subject no longer exists (inconsistent state). */
    public static final String CODE_SUBJECT_MISSING = "SUBJECT_MISSING";

    /** Subject status is not ACTIVE; policy unresolved, fail closed. */
    public static final String CODE_SUBJECT_NOT_ACTIVE = "SUBJECT_NOT_ACTIVE";

    /** No account exists for the requested subject. */
    public static final String CODE_NO_ACCOUNT = "NO_ACCOUNT";

    /** Amount is zero, negative, or above the maximum balance. */
    public static final String CODE_AMOUNT_INVALID = "AMOUNT_INVALID";

    /** Transfer memo violates the normalization/bound rules. */
    public static final String CODE_MEMO_INVALID = "MEMO_INVALID";

    /** A player cannot transfer to their own account. */
    public static final String CODE_SELF_TRANSFER = "SELF_TRANSFER";

    /** Source balance is below the requested amount. */
    public static final String CODE_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    /** The mutation would exceed the maximum balance. */
    public static final String CODE_OVERFLOW = "OVERFLOW";

    /** Server-owned transfer cooldown is still active. */
    public static final String CODE_COOLDOWN = "COOLDOWN";

    /** Expected account/store revision no longer matches at the final boundary. */
    public static final String CODE_STALE_REVISION = "STALE_REVISION";

    /** The economy store has reached its encoded record/byte budget. */
    public static final String CODE_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED";

    /** The durable store rejected the snapshot (not COMMITTED). */
    public static final String CODE_STORE_FAILURE = "STORE_FAILURE";

    /**
     * The mandatory final on-site revalidation of an official-duty context
     * failed (FR-ECO-002-A §4): null context or any non-VALID outcome.
     */
    public static final String CODE_ON_SITE_CONTEXT_INVALID =
            "ON_SITE_CONTEXT_INVALID";

    /**
     * The target account is frozen; every balance-changing mutation is
     * rejected until the Central Bank unfreezes it (FR-ECO-002-A §4).
     */
    public static final String CODE_FROZEN = "FROZEN";

    /**
     * The treasury does not hold the requested amount; official issuance
     * (deposit) draws on the treasury and fails closed when it is empty
     * (FR-ECO-002-A §4: supply = sum(accounts) + treasury is conserved).
     */
    public static final String CODE_TREASURY_INSUFFICIENT =
            "TREASURY_INSUFFICIENT";

    private final String failureCode;

    public EconomyUnavailableException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public EconomyUnavailableException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
