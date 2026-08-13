package com.fontainerepublic.server.emergency.model;

/**
 * Closed actor classification for the shared emergency authority boundary
 * (FR-EMG-001-A §4).
 *
 * <p>{@link #HYDRO_ARCHON} is addressed by canonical Minecraft UUID; the
 * configured UUID is never a name. {@link #SERVER_CONSOLE} is the real local
 * Dedicated Server console, recorded as {@code SERVER_CONSOLE}; console
 * actions never impersonate the Hydro Archon UUID.</p>
 */
public enum EmergencyActorType {
    HYDRO_ARCHON,
    SERVER_CONSOLE
}
