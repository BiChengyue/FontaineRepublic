package com.fontainerepublic.server.institutionaccess.model;

/**
 * Facility lifecycle state (FR-INST-001-A §6.1).
 *
 * <ul>
 *   <li>{@link #ACTIVE} — normal institutional actions allowed.</li>
 *   <li>{@link #SUSPENDED} — public information remains available, mutations
 *       blocked.</li>
 *   <li>{@link #RELOCATING} — old facility blocked while controlled relocation
 *       occurs.</li>
 *   <li>{@link #DISABLED} — facility invalid or administratively withdrawn
 *       (terminal state; no transition out of it).</li>
 * </ul>
 */
public enum FacilityState {
    ACTIVE,
    SUSPENDED,
    RELOCATING,
    DISABLED
}
