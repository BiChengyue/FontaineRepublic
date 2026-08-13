package com.fontainerepublic.server.emergency.model;

import java.util.Objects;

/**
 * Reconciliation status of one receipt provider's derived index
 * (FR-EMG-001-A §10.4).
 */
public enum EmergencyReconciliationStatus {
    COMPLETE_THROUGH,
    INCOMPLETE,
    TAMPER_OR_CORRUPTION_DETECTED
}
