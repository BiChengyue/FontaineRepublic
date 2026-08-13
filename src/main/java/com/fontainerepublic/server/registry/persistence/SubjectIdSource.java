package com.fontainerepublic.server.registry.persistence;

import java.util.UUID;

/**
 * Server-owned source of fresh {@link com.fontainerepublic.server.registry.model.SubjectId}
 * UUIDs (FR-ID-001-A §3.1/§18).
 *
 * <p>Production uses {@link UUID#randomUUID()}; tests inject deterministic
 * sequences to exercise SubjectId collision retry. The registry rejects any
 * collision with an existing SubjectId and never accepts a client-selected
 * SubjectId.</p>
 */
@FunctionalInterface
public interface SubjectIdSource {

    /** Returns the next candidate UUID. */
    UUID nextUuid();
}
