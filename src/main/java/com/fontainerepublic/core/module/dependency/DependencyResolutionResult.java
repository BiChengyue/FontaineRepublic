package com.fontainerepublic.core.module.dependency;

import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable structural dependency resolution result for one server lifecycle.
 */
public record DependencyResolutionResult(
        State state,
        List<ModuleDefinition> initializationOrder,
        List<ModuleDefinition> unresolvableDefinitions,
        Map<ModuleId, List<DependencyEdge>> dependencyGraph,
        List<DependencyEdge> removedOptionalEdges
) {
    public DependencyResolutionResult {
        state = Objects.requireNonNull(state, "state");
        initializationOrder = List.copyOf(
                Objects.requireNonNull(initializationOrder, "initializationOrder")
        );
        unresolvableDefinitions = List.copyOf(
                Objects.requireNonNull(unresolvableDefinitions, "unresolvableDefinitions")
        );
        dependencyGraph = immutableGraph(
                Objects.requireNonNull(dependencyGraph, "dependencyGraph")
        );
        removedOptionalEdges = List.copyOf(
                Objects.requireNonNull(removedOptionalEdges, "removedOptionalEdges")
        );
    }

    private static Map<ModuleId, List<DependencyEdge>> immutableGraph(
            Map<ModuleId, List<DependencyEdge>> source
    ) {
        LinkedHashMap<ModuleId, List<DependencyEdge>> copy = new LinkedHashMap<>();
        source.forEach((moduleId, edges) -> copy.put(
                Objects.requireNonNull(moduleId, "dependencyGraph key"),
                List.copyOf(Objects.requireNonNull(edges, "dependencyGraph edges"))
        ));
        return Collections.unmodifiableMap(copy);
    }

    public enum State {
        VALID,
        INVALID
    }
}
