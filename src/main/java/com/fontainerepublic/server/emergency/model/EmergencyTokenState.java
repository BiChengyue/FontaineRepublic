package com.fontainerepublic.server.emergency.model;

/**
 * Confirmation-token lifecycle state (FR-EMG-001-A §8.2).
 *
 * <pre>
 * ISSUED --atomic claim--> CLAIMED --every terminal outcome--> CONSUMED
 *    |                         |
 *    +--expiry/stop/logout-----+--never returns to ISSUED
 * </pre>
 */
public enum EmergencyTokenState {
    ISSUED,
    CLAIMED,
    CONSUMED
}
