package com.fontainerepublic.server.registry.model;

/**
 * Bootstrap lifecycle phase of the original personal binding
 * (FR-ID-BOOTSTRAP-001-A §4).
 *
 * <p>{@link #UNBOUND} is the initial and only retryable phase: attempts may
 * still be rejected, incomplete, or pending. {@link #BOUND} is terminal and
 * immutable once durably committed — no unbind, rebind, or transfer path
 * exists.</p>
 */
public enum BootstrapPhase {

    /** The original personal subject ({@code 10-000001-61}) is not bound yet. */
    UNBOUND,

    /** The original personal subject is durably bound to its designated UUID. */
    BOUND
}
