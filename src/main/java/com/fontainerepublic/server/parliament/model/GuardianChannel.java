package com.fontainerepublic.server.parliament.model;

/**
 * Closed set of authoritative channels for the water-god's constitutional
 * actions (FR-PAR-002 task §3.3): guardian review decisions and the
 * amendment constitutional consent.
 *
 * <p>{@link #HYDRO_ARCHON_PLAYER} requires the acting player to be the
 * bound Hydro Archon (resolved through the FR-ID registry) and physically
 * on-site in a parliament zone; {@link #LOCAL_CONSOLE} requires the real
 * local Dedicated Server console (classified with the bootstrap classifier
 * pattern) and carries no on-site requirement. Everything else is rejected
 * before any mutation.</p>
 */
public enum GuardianChannel {
    HYDRO_ARCHON_PLAYER,
    LOCAL_CONSOLE
}
