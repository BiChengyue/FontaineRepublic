package com.fontainerepublic.server.emergency.api;

import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One bounded, immutable emergency-action descriptor contributed by a business
 * module (FR-EMG-001-A §5 shared contribution contract).
 *
 * <p>A descriptor contains immutable metadata plus a stable runtime provider
 * resolver only; it MUST NOT capture a per-server module, Service, repository,
 * world, player, command source, or mutable action implementation. The
 * provider resolver is invoked at preview/confirm time to resolve the current
 * ACTIVE business provider.</p>
 *
 * @param moduleId              stable owning module id
 * @param actionId              stable action id unique within the module
 * @param actionVersion         semantic version of the action
 * @param targetType            supported typed target
 * @param allowedCategories     allowed emergency categories
 * @param parameterKeys         bounded canonical parameter-key schema
 *                              (empty = no parameters)
 * @param providerIdentity      stable provider identity (module/provider id)
 * @param providerVersion       provider implementation digest/version
 * @param offlineSafe           whether the action may apply to an offline
 *                              SavedData-backed target
 * @param providerResolver      resolves the current ACTIVE provider at runtime
 *                              (never captured)
 */
public record EmergencyActionDescriptor(
        String moduleId,
        String actionId,
        String actionVersion,
        EmergencyTargetType targetType,
        Set<EmergencyCategory> allowedCategories,
        Set<String> parameterKeys,
        String providerIdentity,
        String providerVersion,
        boolean offlineSafe,
        EmergencyProviderResolver providerResolver
) {

    public EmergencyActionDescriptor {
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        actionId = Objects.requireNonNull(actionId, "actionId");
        actionVersion = Objects.requireNonNull(actionVersion, "actionVersion");
        targetType = Objects.requireNonNull(targetType, "targetType");
        allowedCategories = Set.copyOf(
                Objects.requireNonNull(allowedCategories, "allowedCategories")
        );
        if (allowedCategories.isEmpty()) {
            throw new IllegalArgumentException(
                    "An action descriptor must allow at least one category"
            );
        }
        parameterKeys = Set.copyOf(
                Objects.requireNonNull(parameterKeys, "parameterKeys")
        );
        providerIdentity = Objects.requireNonNull(providerIdentity, "providerIdentity");
        providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        providerResolver = Objects.requireNonNull(providerResolver, "providerResolver");
    }

    /** Stable unique registry key: moduleId + "/" + actionId + "/" + version. */
    public String key() {
        return moduleId + "/" + actionId + "/" + actionVersion;
    }
}
