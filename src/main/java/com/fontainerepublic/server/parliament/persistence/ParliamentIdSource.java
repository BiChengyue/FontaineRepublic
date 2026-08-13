package com.fontainerepublic.server.parliament.persistence;

import java.util.UUID;

/**
 * Server-side id source for parliament identities (proposal/vote/bill).
 * The production binding uses random UUIDs; tests inject a deterministic
 * source. Ids are never selected by a client.
 */
public interface ParliamentIdSource {

    /** Returns the next immutable identity UUID. */
    UUID nextUuid();
}
