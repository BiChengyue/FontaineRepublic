package com.fontainerepublic.server.emergency.api;

import com.fontainerepublic.server.emergency.model.EmergencyActorSource;
import com.fontainerepublic.server.emergency.model.EmergencyAttemptResult;
import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencyConfigPhase;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One bounded emergency preview/confirmation request (FR-EMG-001-A §5 / §8).
 *
 * <p>The request is canonical and bounded: module/action/version, typed
 * target, mandatory category and reason, and an ordered parameter map.
 * Strings are normalized (trimmed) and length-bounded; collections are
 * bounded. Names are never authority keys — player targets accept a canonical
 * UUID only.</p>
 *
 * @param moduleId      owning module id
 * @param actionId      stable action id within the module
 * @param actionVersion semantic version of the action
 * @param targetType    typed stable target
 * @param targetId      stable target id (canonical UUID for PLAYER_UUID)
 * @param category      mandatory emergency category
 * @param reason        non-empty bounded reason
 * @param parameters    bounded ordered parameter map (key = value)
 */
public record EmergencyRequest(
        String moduleId,
        String actionId,
        String actionVersion,
        EmergencyTargetType targetType,
        String targetId,
        EmergencyCategory category,
        String reason,
        Map<String, String> parameters
) {

    public EmergencyRequest {
        moduleId = normalize(moduleId, "moduleId", 32);
        actionId = normalize(actionId, "actionId", 64);
        actionVersion = normalize(actionVersion, "actionVersion", 16);
        targetType = Objects.requireNonNull(targetType, "targetType");
        targetId = normalize(targetId, "targetId", 128);
        category = Objects.requireNonNull(category, "category");
        reason = normalize(reason, "reason", 200);
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be empty");
        }
        if (parameters == null || parameters.isEmpty()) {
            parameters = Map.of();
        } else if (parameters.size() > MAX_PARAMETERS) {
            throw new IllegalArgumentException(
                    "parameters exceed the maximum of " + MAX_PARAMETERS
            );
        } else {
            java.util.LinkedHashMap<String, String> normalized =
                    new java.util.LinkedHashMap<>();
            parameters.forEach((key, value) -> {
                String normalizedKey = normalize(key, "parameter key", 32);
                String normalizedValue = normalize(value, "parameter value", 200);
                normalized.put(normalizedKey, normalizedValue);
            });
            parameters = Map.copyOf(normalized);
        }
        if (targetType == EmergencyTargetType.PLAYER_UUID && !isCanonicalUuid(targetId)) {
            throw new IllegalArgumentException(
                    "A PLAYER_UUID target must be a canonical UUID: " + targetId
            );
        }
    }

    /** Maximum number of parameters (bounded collections). */
    public static final int MAX_PARAMETERS = 16;

    /** Normalizes and bounds a string field (canonical encoding contract). */
    public static String normalize(String value, String field, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " exceeds the maximum length of " + maxLength
            );
        }
        return trimmed;
    }

    /** Whether the target id is a canonical lowercase hyphenated UUID. */
    public static boolean isCanonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Convenience accessor used by services and commands. */
    public Optional<UUID> targetUuid() {
        return targetType == EmergencyTargetType.PLAYER_UUID
                ? Optional.of(UUID.fromString(targetId))
                : Optional.empty();
    }

    /** Fixed order of the canonical parameter encoding (sorted keys). */
    public Set<String> parameterKeys() {
        return parameters.keySet();
    }
}
