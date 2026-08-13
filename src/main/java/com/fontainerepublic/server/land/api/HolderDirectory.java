package com.fontainerepublic.server.land.api;

import java.util.UUID;

/**
 * Holder/actor resolution boundary of the land module (FR-LAND-001-A §4).
 *
 * <p>Holders and actors are resolved through the PlayerData and FR-ID subject
 * services (never raw NBT, never a game name). The production binding is wired
 * by the land module after runtime start; until bound, every query reports
 * unavailable and mutations fail closed.</p>
 */
public interface HolderDirectory {

    /** Whether the underlying PlayerData/subject services are bound. */
    boolean isAvailable();

    /** Whether the UUID has an authoritative PlayerData record. */
    boolean hasPlayerRecord(UUID playerId);

    /**
     * Whether the UUID resolves to an active subject in the FR-ID registry.
     * Returns {@code false} on unavailable services (fail closed).
     */
    boolean hasActiveSubject(UUID playerId);
}
