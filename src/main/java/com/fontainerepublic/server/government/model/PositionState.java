package com.fontainerepublic.server.government.model;

/**
 * Lifecycle state of a government position (FR-GOV-001-A §3.1).
 *
 * <p>{@link #FILLED} and {@link #VACANT} are produced by the current
 * appoint/dismiss contract. {@link #SUSPENDED} is reserved for future
 * reviewed policy; no service method currently produces it.</p>
 */
public enum PositionState {
    FILLED,
    VACANT,
    SUSPENDED
}
