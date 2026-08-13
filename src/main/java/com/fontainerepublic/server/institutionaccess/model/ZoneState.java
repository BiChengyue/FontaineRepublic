package com.fontainerepublic.server.institutionaccess.model;

/**
 * Zone lifecycle state (FR-INST-002-B §2).
 *
 * <ul>
 *   <li>{@link #ACTIVE} — the zone may anchor on-site contexts.</li>
 *   <li>{@link #SUSPENDED} — administratively paused; contexts invalidated,
 *       re-activation is a normal operation.</li>
 *   <li>{@link #DISABLED} — withdrawn; final state (not reachable through
 *       the current command surface; kept for state-machine symmetry).</li>
 * </ul>
 */
public enum ZoneState {
    ACTIVE,
    SUSPENDED,
    DISABLED
}
