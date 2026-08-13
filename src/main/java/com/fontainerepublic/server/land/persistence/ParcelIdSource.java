package com.fontainerepublic.server.land.persistence;

import java.util.UUID;

/**
 * Server-owned source of fresh {@link com.fontainerepublic.server.land.model.ParcelId}
 * UUIDs (FR-LAND-001-A §3.1).
 *
 * <p>Production uses {@link UUID#randomUUID()}; tests inject deterministic
 * sequences to exercise ParcelId collision retry. The repository rejects any
 * collision with an existing ParcelId and never accepts a client-selected
 * ParcelId.</p>
 */
@FunctionalInterface
public interface ParcelIdSource {

    /** Returns the next candidate UUID. */
    UUID nextUuid();
}
