package com.fontainerepublic.server.registry.model;

/**
 * Stable result code of one bootstrap attempt
 * (FR-ID-BOOTSTRAP-001-A §2.4, implementation task §3.2).
 *
 * <p>{@link #PENDING} is the intermediate record written first through the
 * FR-CORE-002 gate; every attempt then terminates with exactly one of the
 * terminal codes. The terminal codes distinguish rejection / incomplete /
 * idempotent no-op / persistence failure / success so restart recovery and
 * audit consumers can correlate retries without raw secrets.</p>
 */
public enum BootstrapAttemptResult {

    /** Attempt recorded durably but not yet resolved (crash-recoverable). */
    PENDING,

    /** Rejected because the invocation source is not the local console. */
    REJECTED_SOURCE,

    /** Rejected because the input (UUID form, reason) is invalid. */
    REJECTED_INPUT,

    /** Rejected because the binding is already committed and immutable. */
    REJECTED,

    /** Incomplete: the target UUID has no authoritative PlayerData record. */
    PLAYER_NOT_PROVISIONED,

    /** The durable store rejected the terminal commit; attempt is retryable. */
    PERSISTENCE_FAILURE,

    /** Replay of an already-bound target: no second binding was attempted. */
    IDEMPOTENT_NOOP,

    /** The original personal subject was bound in one committed snapshot. */
    SUCCESS
}
