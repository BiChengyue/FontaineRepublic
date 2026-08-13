package com.fontainerepublic.server.emergency.model;

/**
 * Authority configuration lifecycle phase (FR-EMG-001-A §4).
 *
 * <ul>
 *   <li>{@link #UNSET} — no player UUID has emergency authority; only the
 *       real local console may bootstrap the first UUID.</li>
 *   <li>{@link #STAGED} — a controlled change was confirmed and staged for
 *       the next server runtime; the running snapshot stays immutable.</li>
 *   <li>{@link #ACTIVE} — the current runtime snapshot is accepted and the
 *       configured UUID has emergency authority.</li>
 * </ul>
 */
public enum EmergencyConfigPhase {
    UNSET,
    STAGED,
    ACTIVE
}
