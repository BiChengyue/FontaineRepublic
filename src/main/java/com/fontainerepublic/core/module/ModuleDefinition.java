package com.fontainerepublic.core.module;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable static contract for a registered module.
 */
public record ModuleDefinition(
        ModuleId id,
        ModuleMetadata metadata,
        Set<ModuleId> requiredDependencies,
        Set<ModuleId> optionalDependencies,
        int priority,
        IModuleFactory factory
) {
    public ModuleDefinition {
        id = Objects.requireNonNull(id, "id");
        metadata = Objects.requireNonNull(metadata, "metadata");
        requiredDependencies = Set.copyOf(Objects.requireNonNull(requiredDependencies, "requiredDependencies"));
        optionalDependencies = Set.copyOf(Objects.requireNonNull(optionalDependencies, "optionalDependencies"));
        var overlappingDependencies = requiredDependencies.stream()
                .filter(optionalDependencies::contains)
                .sorted()
                .toList();
        if (!overlappingDependencies.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dependencies cannot be both required and optional: " + overlappingDependencies
            );
        }
        factory = Objects.requireNonNull(factory, "factory");
    }
}
