package com.fontainerepublic.server.emergency.model;

/**
 * Terminal/attempt outcome of one shared emergency attempt
 * (FR-EMG-001-A §8.2 / §10.3).
 *
 * <p>{@link #PENDING} is the durable attempt-intent recorded before any
 * business provider is entered. The terminal codes distinguish request
 * rejection, authorization failure, business-provider failure, commit
 * failure, and success so audit distinguishes attempts from mutations.</p>
 */
public enum EmergencyAttemptResult {
    PENDING,
    SUCCESS,
    REJECTED_SOURCE,
    REJECTED_INPUT,
    REJECTED_UNKNOWN_ACTION,
    REJECTED_ACTION_UNAVAILABLE,
    REJECTED_TOKEN,
    REJECTED_PARAMETERS,
    REJECTED_REVISION,
    PROVIDER_FAILURE,
    COMMIT_FAILURE
}
