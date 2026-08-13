package com.fontainerepublic.server.registry.api;

import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;

import java.util.Objects;

/**
 * Purpose-bounded public projection of a subject (FR-ID-001-A §7/§13).
 *
 * <p>Contains only routing-safe fields: internal {@link SubjectId}, type, and
 * public number. It never discloses the owner UUID, profiles, balances,
 * citizenship, roles, or permissions.</p>
 */
public record SubjectProjection(
        SubjectId subjectId,
        RegistryNumber registryNumber,
        SubjectType subjectType,
        SubjectStatus status
) {

    public SubjectProjection {
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        registryNumber = Objects.requireNonNull(registryNumber, "registryNumber");
        subjectType = Objects.requireNonNull(subjectType, "subjectType");
        status = Objects.requireNonNull(status, "status");
    }
}
