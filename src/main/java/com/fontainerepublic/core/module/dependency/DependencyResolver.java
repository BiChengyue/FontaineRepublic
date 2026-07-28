package com.fontainerepublic.core.module.dependency;

import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.core.module.dependency.DependencyEdge.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic structural dependency resolver.
 */
public final class DependencyResolver {
    private static final System.Logger LOGGER = System.getLogger(DependencyResolver.class.getName());
    private static final Comparator<DependencyEdge> EDGE_ORDER = Comparator
            .comparing(DependencyEdge::dependent)
            .thenComparing(DependencyEdge::dependency)
            .thenComparing(DependencyEdge::type);

    public DependencyResolutionResult resolve(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        return resolve(registry.getDefinitionsInRegistrationOrder());
    }

    public DependencyResolutionResult resolve(List<ModuleDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");

        LinkedHashMap<ModuleId, ModuleDefinition> definitionsById = indexDefinitions(definitions);
        LinkedHashMap<ModuleId, Integer> registrationOrder = registrationOrder(definitionsById);
        LinkedHashMap<ModuleId, List<DependencyEdge>> declaredGraph =
                buildDeclaredGraph(definitionsById);

        LinkedHashSet<ModuleId> unresolvable = findMissingRequiredDependencies(
                definitionsById,
                declaredGraph
        );

        Map<ModuleId, List<ModuleId>> requiredAdjacency = buildRequiredAdjacency(
                definitionsById,
                declaredGraph
        );
        unresolvable.addAll(findRequiredCycleMembers(requiredAdjacency));
        propagateRequiredStructuralFailures(definitionsById, requiredAdjacency, unresolvable);

        LinkedHashMap<ModuleId, List<DependencyEdge>> activeGraph = buildActiveGraph(
                definitionsById,
                declaredGraph,
                unresolvable
        );
        List<DependencyEdge> removedOptionalEdges = removeOptionalCycles(activeGraph);

        List<ModuleDefinition> initializationOrder = topologicalOrder(
                definitionsById,
                registrationOrder,
                unresolvable,
                activeGraph
        );
        List<ModuleDefinition> unresolvableDefinitions = definitionsById.values().stream()
                .filter(definition -> unresolvable.contains(definition.id()))
                .toList();

        DependencyResolutionResult.State state = unresolvableDefinitions.isEmpty()
                ? DependencyResolutionResult.State.VALID
                : DependencyResolutionResult.State.INVALID;

        return new DependencyResolutionResult(
                state,
                initializationOrder,
                unresolvableDefinitions,
                declaredGraph,
                removedOptionalEdges
        );
    }

    private LinkedHashMap<ModuleId, ModuleDefinition> indexDefinitions(
            List<ModuleDefinition> definitions
    ) {
        LinkedHashMap<ModuleId, ModuleDefinition> indexed = new LinkedHashMap<>();
        for (ModuleDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definitions element");
            if (indexed.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate ModuleId in resolver input: " + definition.id());
            }
        }
        return indexed;
    }

    private LinkedHashMap<ModuleId, Integer> registrationOrder(
            LinkedHashMap<ModuleId, ModuleDefinition> definitionsById
    ) {
        LinkedHashMap<ModuleId, Integer> order = new LinkedHashMap<>();
        int index = 0;
        for (ModuleId moduleId : definitionsById.keySet()) {
            order.put(moduleId, index++);
        }
        return order;
    }

    private LinkedHashMap<ModuleId, List<DependencyEdge>> buildDeclaredGraph(
            LinkedHashMap<ModuleId, ModuleDefinition> definitionsById
    ) {
        LinkedHashMap<ModuleId, List<DependencyEdge>> graph = new LinkedHashMap<>();
        for (ModuleDefinition definition : definitionsById.values()) {
            List<DependencyEdge> edges = new ArrayList<>();
            definition.requiredDependencies().stream()
                    .sorted()
                    .map(dependency -> new DependencyEdge(definition.id(), dependency, Type.REQUIRED))
                    .forEach(edges::add);
            definition.optionalDependencies().stream()
                    .sorted()
                    .map(dependency -> new DependencyEdge(definition.id(), dependency, Type.OPTIONAL))
                    .forEach(edges::add);
            edges.sort(EDGE_ORDER);
            graph.put(definition.id(), edges);
        }
        return graph;
    }

    private LinkedHashSet<ModuleId> findMissingRequiredDependencies(
            Map<ModuleId, ModuleDefinition> definitionsById,
            Map<ModuleId, List<DependencyEdge>> declaredGraph
    ) {
        LinkedHashSet<ModuleId> unresolvable = new LinkedHashSet<>();
        for (Map.Entry<ModuleId, List<DependencyEdge>> entry : declaredGraph.entrySet()) {
            for (DependencyEdge edge : entry.getValue()) {
                if (edge.type() == Type.REQUIRED && !definitionsById.containsKey(edge.dependency())) {
                    unresolvable.add(edge.dependent());
                    LOGGER.log(
                            System.Logger.Level.ERROR,
                            "Missing required dependency: {0} requires {1}",
                            edge.dependent(),
                            edge.dependency()
                    );
                } else if (edge.type() == Type.OPTIONAL
                        && !definitionsById.containsKey(edge.dependency())) {
                    LOGGER.log(
                            System.Logger.Level.INFO,
                            "Missing optional dependency ignored: {0} optionally depends on {1}",
                            edge.dependent(),
                            edge.dependency()
                    );
                }
            }
        }
        return unresolvable;
    }

    private Map<ModuleId, List<ModuleId>> buildRequiredAdjacency(
            Map<ModuleId, ModuleDefinition> definitionsById,
            Map<ModuleId, List<DependencyEdge>> declaredGraph
    ) {
        LinkedHashMap<ModuleId, List<ModuleId>> adjacency = new LinkedHashMap<>();
        for (ModuleId moduleId : definitionsById.keySet()) {
            List<ModuleId> dependencies = declaredGraph.get(moduleId).stream()
                    .filter(edge -> edge.type() == Type.REQUIRED)
                    .map(DependencyEdge::dependency)
                    .filter(definitionsById::containsKey)
                    .sorted()
                    .toList();
            adjacency.put(moduleId, dependencies);
        }
        return adjacency;
    }

    private Set<ModuleId> findRequiredCycleMembers(
            Map<ModuleId, List<ModuleId>> requiredAdjacency
    ) {
        LinkedHashSet<ModuleId> cycleMembers = new LinkedHashSet<>();
        for (Map.Entry<ModuleId, List<ModuleId>> entry : requiredAdjacency.entrySet()) {
            ModuleId moduleId = entry.getKey();
            for (ModuleId dependency : entry.getValue()) {
                if (isReachable(dependency, moduleId, requiredAdjacency, new HashSet<>())) {
                    cycleMembers.add(moduleId);
                    break;
                }
            }
        }
        if (!cycleMembers.isEmpty()) {
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Required dependency cycle members: {0}",
                    cycleMembers
            );
        }
        return cycleMembers;
    }

    private void propagateRequiredStructuralFailures(
            Map<ModuleId, ModuleDefinition> definitionsById,
            Map<ModuleId, List<ModuleId>> requiredAdjacency,
            Set<ModuleId> unresolvable
    ) {
        boolean changed;
        do {
            changed = false;
            for (ModuleId moduleId : definitionsById.keySet()) {
                if (unresolvable.contains(moduleId)) {
                    continue;
                }
                boolean dependencyUnavailable = requiredAdjacency.get(moduleId).stream()
                        .anyMatch(unresolvable::contains);
                if (dependencyUnavailable) {
                    changed |= unresolvable.add(moduleId);
                }
            }
        } while (changed);
    }

    private LinkedHashMap<ModuleId, List<DependencyEdge>> buildActiveGraph(
            Map<ModuleId, ModuleDefinition> definitionsById,
            Map<ModuleId, List<DependencyEdge>> declaredGraph,
            Set<ModuleId> unresolvable
    ) {
        LinkedHashMap<ModuleId, List<DependencyEdge>> activeGraph = new LinkedHashMap<>();
        for (ModuleId moduleId : definitionsById.keySet()) {
            if (unresolvable.contains(moduleId)) {
                continue;
            }
            List<DependencyEdge> activeEdges = declaredGraph.get(moduleId).stream()
                    .filter(edge -> definitionsById.containsKey(edge.dependency()))
                    .filter(edge -> !unresolvable.contains(edge.dependency()))
                    .sorted(EDGE_ORDER)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            activeGraph.put(moduleId, activeEdges);
        }
        return activeGraph;
    }

    private List<DependencyEdge> removeOptionalCycles(
            LinkedHashMap<ModuleId, List<DependencyEdge>> activeGraph
    ) {
        List<DependencyEdge> removed = new ArrayList<>();
        while (true) {
            List<DependencyEdge> candidates = activeGraph.values().stream()
                    .flatMap(List::stream)
                    .filter(edge -> edge.type() == Type.OPTIONAL)
                    .filter(edge -> isReachable(
                            edge.dependency(),
                            edge.dependent(),
                            dependencyAdjacency(activeGraph),
                            new HashSet<>()
                    ))
                    .sorted(EDGE_ORDER)
                    .toList();

            if (candidates.isEmpty()) {
                if (containsCycle(dependencyAdjacency(activeGraph))) {
                    throw new IllegalStateException("Unresolved required cycle remained in active graph");
                }
                return List.copyOf(removed);
            }

            DependencyEdge removedEdge = candidates.get(0);
            activeGraph.get(removedEdge.dependent()).remove(removedEdge);
            removed.add(removedEdge);
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Optional dependency cycle resolved: removed optional edge {0} -> {1}",
                    removedEdge.dependent(),
                    removedEdge.dependency()
            );
        }
    }

    private Map<ModuleId, List<ModuleId>> dependencyAdjacency(
            Map<ModuleId, List<DependencyEdge>> graph
    ) {
        LinkedHashMap<ModuleId, List<ModuleId>> adjacency = new LinkedHashMap<>();
        graph.forEach((moduleId, edges) -> adjacency.put(
                moduleId,
                edges.stream().map(DependencyEdge::dependency).sorted().toList()
        ));
        return adjacency;
    }

    private boolean containsCycle(Map<ModuleId, List<ModuleId>> adjacency) {
        for (Map.Entry<ModuleId, List<ModuleId>> entry : adjacency.entrySet()) {
            for (ModuleId dependency : entry.getValue()) {
                if (isReachable(dependency, entry.getKey(), adjacency, new HashSet<>())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReachable(
            ModuleId current,
            ModuleId target,
            Map<ModuleId, List<ModuleId>> adjacency,
            Set<ModuleId> visited
    ) {
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (ModuleId next : adjacency.getOrDefault(current, List.of())) {
            if (isReachable(next, target, adjacency, visited)) {
                return true;
            }
        }
        return false;
    }

    private List<ModuleDefinition> topologicalOrder(
            Map<ModuleId, ModuleDefinition> definitionsById,
            Map<ModuleId, Integer> registrationOrder,
            Set<ModuleId> unresolvable,
            Map<ModuleId, List<DependencyEdge>> activeGraph
    ) {
        LinkedHashMap<ModuleId, Integer> indegree = new LinkedHashMap<>();
        LinkedHashMap<ModuleId, List<ModuleId>> dependentsByDependency = new LinkedHashMap<>();
        for (ModuleId moduleId : definitionsById.keySet()) {
            if (!unresolvable.contains(moduleId)) {
                indegree.put(moduleId, 0);
                dependentsByDependency.put(moduleId, new ArrayList<>());
            }
        }

        activeGraph.forEach((dependent, edges) -> {
            for (DependencyEdge edge : edges) {
                indegree.compute(dependent, (ignored, value) -> Objects.requireNonNull(value) + 1);
                dependentsByDependency.get(edge.dependency()).add(dependent);
            }
        });

        Comparator<ModuleId> moduleOrder = Comparator
                .comparingInt((ModuleId id) -> definitionsById.get(id).priority())
                .thenComparingInt(registrationOrder::get)
                .thenComparing(ModuleId::value);
        dependentsByDependency.values().forEach(dependents -> dependents.sort(moduleOrder));

        List<ModuleId> ready = indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted(moduleOrder)
                .toList();
        List<ModuleDefinition> ordered = new ArrayList<>();

        while (!ready.isEmpty()) {
            List<ModuleId> currentLevel = new ArrayList<>(ready);
            currentLevel.sort(moduleOrder);
            LinkedHashSet<ModuleId> nextLevel = new LinkedHashSet<>();

            for (ModuleId moduleId : currentLevel) {
                ordered.add(definitionsById.get(moduleId));
                for (ModuleId dependent : dependentsByDependency.get(moduleId)) {
                    int remaining = indegree.compute(
                            dependent,
                            (ignored, value) -> Objects.requireNonNull(value) - 1
                    );
                    if (remaining == 0) {
                        nextLevel.add(dependent);
                    }
                }
            }

            ready = nextLevel.stream().sorted(moduleOrder).toList();
        }

        if (ordered.size() != indegree.size()) {
            throw new IllegalStateException("Dependency graph remained cyclic after optional-edge removal");
        }
        return List.copyOf(ordered);
    }
}
