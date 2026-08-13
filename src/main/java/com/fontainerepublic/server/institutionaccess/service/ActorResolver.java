package com.fontainerepublic.server.institutionaccess.service;

import java.util.UUID;

/**
 * Actor resolution chain for the shared access boundary: a player UUID is
 * resolvable only when the authoritative PlayerData and FR-ID subject
 * services both acknowledge it (never raw NBT).
 */
public interface ActorResolver {

    /** Whether the underlying services are bound and usable. */
    boolean isAvailable();

    /** Whether the UUID has an authoritative PlayerData record. */
    boolean hasPlayerRecord(UUID playerId);

    /** Whether the UUID resolves to an active FR-ID subject. */
    boolean hasActiveSubject(UUID playerId);
}
