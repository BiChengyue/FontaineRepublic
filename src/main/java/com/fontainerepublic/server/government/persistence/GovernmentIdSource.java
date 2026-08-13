package com.fontainerepublic.server.government.persistence;

import java.util.UUID;

/**
 * Server-side id source for government identities (ministry/position/office).
 * The production binding uses random UUIDs; tests inject a deterministic
 * source. Ids are never selected by a client.
 */
public interface GovernmentIdSource {

    /** Returns the next immutable identity UUID. */
    UUID nextUuid();
}
