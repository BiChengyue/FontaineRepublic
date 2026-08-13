package com.fontainerepublic.server.emergency.api;

import java.util.Map;
import java.util.Objects;

/**
 * Typed immutable request context accepted by an emergency-action provider's
 * preview and mutation capabilities (FR-EMG-001-A §5). Never carries NBT,
 * repositories, raw handles, or an accepted plan — the plan is produced by
 * preview and passed separately to mutation.
 *
 * @param moduleId      owning module id
 * @param actionId      stable action id
 * @param actionVersion action version
 * @param targetType    typed target
 * @param targetId      stable target id
 * @param category      emergency category
 * @param reason        normalized reason
 * @param parameters    canonical ordered parameters
 * @param attemptId     stable attempt identity
 * @param at            server-assigned epoch millis
 */
public record EmergencyMutationEnvelope(
        String moduleId,
        String actionId,
        String actionVersion,
        String targetType,
        String targetId,
        String category,
        String reason,
        Map<String, String> parameters,
        long attemptId,
        long at
) {

    public EmergencyMutationEnvelope {
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        actionId = Objects.requireNonNull(actionId, "actionId");
        actionVersion = Objects.requireNonNull(actionVersion, "actionVersion");
        targetType = Objects.requireNonNull(targetType, "targetType");
        targetId = Objects.requireNonNull(targetId, "targetId");
        category = Objects.requireNonNull(category, "category");
        reason = Objects.requireNonNull(reason, "reason");
        parameters = Map.copyOf(parameters);
        if (attemptId <= 0) {
            throw new IllegalArgumentException("attemptId must be positive");
        }
        if (at <= 0) {
            throw new IllegalArgumentException("at must be positive");
        }
    }
}
