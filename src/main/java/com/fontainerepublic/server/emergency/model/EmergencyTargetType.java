package com.fontainerepublic.server.emergency.model;

/**
 * Closed set of supported typed stable emergency targets (FR-EMG-001-A §6).
 *
 * <p>{@link #MODULE_OWNED} is not an open escape hatch: every module-owned
 * target type requires a stable type id, a bounded codec, and separate
 * catalogue review. Player targets accept only a canonical UUID (name input
 * stays disabled until an approved historical lookup API exists).</p>
 */
public enum EmergencyTargetType {
    PLAYER_UUID,
    CASE_ID,
    LAW_ID,
    ELECTION_ID,
    OFFICE_ID,
    PARCEL_ID,
    CITY_ID,
    MODULE_OWNED
}
