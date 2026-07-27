package com.fontainerepublic.core.module.runtime;

import com.fontainerepublic.core.module.ModuleId;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable availability outcome for one structurally resolvable module.
 */
public record ModuleAvailabilityRecord(
        ModuleId moduleId,
        AvailabilityStatus status,
        Optional<ModuleId> directFailureSource,
        Optional<ModuleId> rootCauseModule,
        Optional<FailureReason> failureReason,
        int propagationOrderIndex
) {
    public ModuleAvailabilityRecord {
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        status = Objects.requireNonNull(status, "status");
        directFailureSource = Objects.requireNonNull(directFailureSource, "directFailureSource");
        rootCauseModule = Objects.requireNonNull(rootCauseModule, "rootCauseModule");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
    }
}
