package com.fontainerepublic.server.government.model;

/**
 * Lifecycle state of a ministry (FR-GOV-001-A §3.2).
 *
 * <p>{@link #SUSPENDED} is reserved for future reviewed policy; the current
 * service contract creates ministries only in {@link #ACTIVE} and exposes no
 * state-change method.</p>
 */
public enum MinistryState {
    ACTIVE,
    SUSPENDED
}
