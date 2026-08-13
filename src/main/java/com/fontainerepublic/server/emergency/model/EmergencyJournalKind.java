package com.fontainerepublic.server.emergency.model;

/**
 * Kind of an authoritative record in the shared emergency journal
 * (FR-EMG-001-A §4 / §10.3).
 *
 * <p>{@link #ATTEMPT} records emergency-action attempts (attempt-intent plus
 * terminal outcomes). {@link #CONFIG_EVENT} records bootstrap, staged change,
 * acceptance, drift detection, and recovery events — authoritative
 * configuration records, not business-module receipts.</p>
 */
public enum EmergencyJournalKind {
    ATTEMPT,
    CONFIG_EVENT
}
