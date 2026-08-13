package com.fontainerepublic.server.justice.persistence;

import java.util.UUID;

/**
 * Server-side id source for justice identities (case/evidence/verdict).
 * The production binding uses random UUIDs; tests inject a deterministic
 * source. Ids are never selected by a client.
 */
public interface JusticeIdSource {

    /** Returns the next immutable identity UUID. */
    UUID nextUuid();
}
