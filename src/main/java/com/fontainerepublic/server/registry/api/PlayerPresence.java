package com.fontainerepublic.server.registry.api;

import java.util.UUID;

/**
 * Authoritative PlayerData precondition for natural-person provisioning
 * (FR-ID-001-A §8.1).
 *
 * <p>The registry never derives owner identity from a game name and never
 * inspects PlayerData NBT; it only asks whether the authoritative PlayerData
 * service holds a record for the canonical UUID.</p>
 */
public interface PlayerPresence {

    /** Whether the authoritative player-data service is available. */
    boolean isAvailable();

    /** Whether a verified PlayerData record exists for the UUID. */
    boolean hasPlayerRecord(UUID playerId);
}
