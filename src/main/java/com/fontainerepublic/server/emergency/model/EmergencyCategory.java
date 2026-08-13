package com.fontainerepublic.server.emergency.model;

/**
 * Closed set of mandatory emergency categories (FR-EMG-001-A §7).
 *
 * <p>A category never authorizes an action by itself; every action requires
 * exactly one category, a non-empty bounded reason, actor identity, a stable
 * target, an explicit action id, and parameters.</p>
 */
public enum EmergencyCategory {
    DEBUG,
    CORRECTION,
    COMPENSATION,
    DISASTER_RELIEF,
    EMERGENCY_RESPONSE
}
