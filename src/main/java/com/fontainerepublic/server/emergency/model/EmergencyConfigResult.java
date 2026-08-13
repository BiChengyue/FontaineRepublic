package com.fontainerepublic.server.emergency.model;

/**
 * Outcome of an authority configuration event (FR-EMG-001-A §4 lifecycle).
 */
public enum EmergencyConfigResult {
    CONFIG_BOOTSTRAPPED,
    CONFIG_STAGED,
    CONFIG_ACCEPTED,
    CONFIG_REJECTED_SOURCE,
    CONFIG_REJECTED_INPUT,
    CONFIG_DRIFT_DETECTED,
    CONFIG_RECOVERED
}
