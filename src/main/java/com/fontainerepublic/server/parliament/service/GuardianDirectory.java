package com.fontainerepublic.server.parliament.service;

import java.util.UUID;

/**
 * Hydro Archon identity resolution boundary of the parliament module
 * (FR-PAR-002 task §3.3).
 *
 * <p>The water god is the natural person holding the fixed original personal
 * registry number (FR-ID: {@code 10-000001-61}) — resolved through the FR-ID
 * subject registry, never raw NBT and never a game name. The production
 * binding is wired by the parliament module after runtime start; until
 * bound, every query reports unavailable and every guardian mutation fails
 * closed. The real local Dedicated Server console is a second, independent
 * channel (classified with the bootstrap classifier pattern) and needs no
 * player identity.</p>
 */
public interface GuardianDirectory {

    /** Whether the underlying subject-registry service is bound. */
    boolean isAvailable();

    /**
     * Whether the UUID is the Hydro Archon player: the active natural-person
     * subject carrying the fixed original personal registry number. Returns
     * {@code false} on unavailable services or a missing record (fail
     * closed).
     */
    boolean isHydroArchon(UUID playerId);
}
