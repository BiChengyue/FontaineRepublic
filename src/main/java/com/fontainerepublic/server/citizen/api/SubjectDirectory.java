package com.fontainerepublic.server.citizen.api;

import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;

import java.util.UUID;

/**
 * Authoritative FR-ID subject preconditions for citizenship provisioning
 * (FR-CIT-001-A §4.1).
 *
 * <p>The citizen module never inspects subject NBT and never allocates
 * subjects itself; it only asks the subject registry for the ordered chain
 * {@code PlayerData -> subject -> citizen}. The production implementation
 * delegates to {@link com.fontainerepublic.server.registry.api.SubjectRegistryService}.</p>
 */
public interface SubjectDirectory {

    /** Whether the subject registry service is currently available. */
    boolean isAvailable();

    /**
     * Idempotently ensures the natural-person subject of the player UUID and
     * returns it (delegates to
     * {@code SubjectRegistryService#ensurePlayerSubject}).
     *
     * @throws com.fontainerepublic.server.registry.persistence.SubjectRegistryUnavailableException
     *         when the subject cannot be provisioned
     */
    SubjectRecord ensureSubject(UUID playerId);

    /** Whether a subject with the given id currently exists. */
    boolean hasSubject(SubjectId subjectId);
}
