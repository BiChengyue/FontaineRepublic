package com.fontainerepublic.core;

import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.core.module.dependency.DependencyEdge;
import com.fontainerepublic.core.module.dependency.DependencyResolutionResult;
import com.fontainerepublic.core.module.dependency.DependencyResolver;
import com.fontainerepublic.core.module.runtime.AvailabilityStatus;
import com.fontainerepublic.core.module.runtime.FailureReason;
import com.fontainerepublic.core.module.runtime.ModuleAvailabilityRecord;
import com.fontainerepublic.core.module.runtime.ModuleState;
import com.fontainerepublic.core.module.runtime.RuntimeAvailabilityResult;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Instance-owned orchestrator for module registration and per-server runtime lifecycles.
 */
public final class CoreManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ModuleRegistry moduleRegistry;
    private final DependencyResolver dependencyResolver;

    private final LinkedHashMap<ModuleId, RuntimeModuleContainer> runtimeContainers =
            new LinkedHashMap<>();
    private final List<RuntimeModuleContainer> successfulInitializationOrder = new ArrayList<>();

    private DependencyResolutionResult dependencyResolutionResult;
    private RuntimeAvailabilityResult runtimeAvailabilityResult;

    public CoreManager(ModuleRegistry moduleRegistry) {
        this(moduleRegistry, new DependencyResolver());
    }

    public CoreManager(ModuleRegistry moduleRegistry, DependencyResolver dependencyResolver) {
        this.moduleRegistry = Objects.requireNonNull(moduleRegistry, "moduleRegistry");
        this.dependencyResolver = Objects.requireNonNull(dependencyResolver, "dependencyResolver");
    }

    public ModuleRegistry moduleRegistry() {
        return moduleRegistry;
    }

    public void closeRegistration() {
        moduleRegistry.closeRegistration();
    }

    /**
     * Performs pre-start validation without creating runtime objects or invoking factories.
     */
    public synchronized void preValidate() {
        ensureRuntimeClosed();
        if (moduleRegistry.isRegistrationOpen()) {
            throw new IllegalStateException("Module registration must be closed before server startup");
        }
    }

    /**
     * Creates and initializes a fresh runtime scope from the preserved module definitions.
     */
    public synchronized void startRuntime() {
        ensureRuntimeClosed();
        if (moduleRegistry.isRegistrationOpen()) {
            throw new IllegalStateException("Module registration must be closed before runtime startup");
        }

        DependencyResolutionResult resolution = dependencyResolver.resolve(moduleRegistry);
        List<ModuleDefinition> resolvedOrder = resolution.initializationOrder();
        RuntimeAvailabilityBuilder availability = new RuntimeAvailabilityBuilder(resolvedOrder);
        Map<ModuleId, Integer> orderIndex = buildOrderIndex(resolvedOrder);

        dependencyResolutionResult = resolution;

        createRuntimeContainers(resolution, availability, orderIndex);
        initializeRuntimeContainers(resolvedOrder, availability, orderIndex);

        runtimeAvailabilityResult = availability.snapshot();
        LOGGER.info(
                "[CoreManager] Runtime startup complete: {} available, {} unavailable",
                runtimeAvailabilityResult.records().size()
                        - runtimeAvailabilityResult.unavailableModules().size(),
                runtimeAvailabilityResult.unavailableModules().size()
        );
    }

    /**
     * Executes exactly-once shutdown in reverse successful initialization order.
     */
    public synchronized void stopRuntime() {
        ensureRuntimeStarted();
        for (int index = successfulInitializationOrder.size() - 1; index >= 0; index--) {
            RuntimeModuleContainer container = successfulInitializationOrder.get(index);
            if (container.initializationSucceeded() && !container.shutdownExecuted()) {
                shutdownOnce(container);
            }
        }
    }

    /**
     * Completes post-stop cleanup and discards all per-server runtime state.
     */
    public synchronized void closeRuntime() {
        ensureRuntimeStarted();

        for (RuntimeModuleContainer container : runtimeContainers.values()) {
            if (container.state() == ModuleState.DEPENDENCY_FAILURE) {
                container.discardDependencyFailure();
                continue;
            }
            if (container.state() != ModuleState.STOPPED) {
                throw new IllegalStateException(
                        "Runtime container is not ready for cleanup: "
                                + container.definition().id() + " is " + container.state()
                );
            }
            container.beginCleanup();
            container.terminate();
        }

        runtimeContainers.clear();
        successfulInitializationOrder.clear();
        runtimeAvailabilityResult = null;
        dependencyResolutionResult = null;
        LOGGER.info("[CoreManager] Runtime scope closed");
    }

    public synchronized Optional<DependencyResolutionResult> getDependencyResolutionSnapshot() {
        return Optional.ofNullable(dependencyResolutionResult);
    }

    public synchronized Optional<RuntimeAvailabilityResult> getRuntimeAvailabilitySnapshot() {
        return Optional.ofNullable(runtimeAvailabilityResult);
    }

    public synchronized Optional<ModuleAvailabilityRecord> getAvailability(ModuleId moduleId) {
        Objects.requireNonNull(moduleId, "moduleId");
        return getRuntimeAvailabilitySnapshot().flatMap(result -> result.getAvailability(moduleId));
    }

    public synchronized boolean isAvailable(ModuleId moduleId) {
        return getRuntimeAvailabilitySnapshot()
                .map(result -> result.isAvailable(moduleId))
                .orElse(false);
    }

    public synchronized Optional<RuntimeModuleContainer> getRuntimeContainer(ModuleId moduleId) {
        return Optional.ofNullable(runtimeContainers.get(Objects.requireNonNull(moduleId, "moduleId")));
    }

    public synchronized Map<ModuleId, RuntimeModuleContainer> getRuntimeContainers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(runtimeContainers));
    }

    private void createRuntimeContainers(
            DependencyResolutionResult resolution,
            RuntimeAvailabilityBuilder availability,
            Map<ModuleId, Integer> orderIndex
    ) {
        for (ModuleDefinition definition : resolution.initializationOrder()) {
            Optional<ModuleId> failedDependency = firstUnavailableRequired(
                    definition,
                    availability,
                    orderIndex
            );
            Set<ModuleId> resolvedDependencies = resolvedDependencies(definition, resolution);

            if (failedDependency.isPresent()) {
                publishDependencyFailureContainer(
                        definition,
                        resolvedDependencies,
                        failedDependency.get(),
                        availability
                );
                continue;
            }

            IModule instance;
            try {
                instance = Objects.requireNonNull(
                        definition.factory().createInstance(),
                        "Module factory returned null"
                );
            } catch (RuntimeException exception) {
                recordDirectFailure(
                        definition,
                        FailureReason.Category.FACTORY_CREATION,
                        exception,
                        availability
                );
                continue;
            }

            String runtimeId;
            try {
                runtimeId = instance.getName();
            } catch (RuntimeException exception) {
                recordDirectFailure(
                        definition,
                        FailureReason.Category.MODULE_ID_MISMATCH,
                        exception,
                        availability
                );
                continue;
            }

            if (!definition.id().value().equals(runtimeId)) {
                IllegalStateException mismatch = new IllegalStateException(
                        "Factory produced module ID '" + runtimeId
                                + "' for registered module " + definition.id()
                );
                recordDirectFailure(
                        definition,
                        FailureReason.Category.MODULE_ID_MISMATCH,
                        mismatch,
                        availability
                );
                continue;
            }

            runtimeContainers.put(
                    definition.id(),
                    RuntimeModuleContainer.registered(
                            definition,
                            instance,
                            resolvedDependencies
                    )
            );
        }
    }

    private void initializeRuntimeContainers(
            List<ModuleDefinition> resolvedOrder,
            RuntimeAvailabilityBuilder availability,
            Map<ModuleId, Integer> orderIndex
    ) {
        for (ModuleDefinition definition : resolvedOrder) {
            RuntimeModuleContainer container = runtimeContainers.get(definition.id());
            if (container == null || container.state() == ModuleState.DEPENDENCY_FAILURE) {
                continue;
            }

            Optional<ModuleId> failedDependency = firstUnavailableRequired(
                    definition,
                    availability,
                    orderIndex
            );
            if (failedDependency.isPresent()) {
                ModuleId source = failedDependency.get();
                container.markDependencyFailure(dependencyFailureException(definition.id(), source));
                availability.recordDependencyFailure(definition.id(), source);
                continue;
            }

            IModule instance = container.beginInitialization();
            try {
                instance.init();
                container.markInitializationSucceeded();
                successfulInitializationOrder.add(container);
                availability.recordAvailable(definition.id());
                LOGGER.info("[CoreManager] Module initialized: {}", definition.id());
            } catch (RuntimeException exception) {
                container.markInitializationFailed(exception);
                recordDirectFailure(
                        definition,
                        FailureReason.Category.INITIALIZATION,
                        exception,
                        availability
                );
                shutdownOnce(container);
            }
        }
    }

    private void publishDependencyFailureContainer(
            ModuleDefinition definition,
            Set<ModuleId> resolvedDependencies,
            ModuleId source,
            RuntimeAvailabilityBuilder availability
    ) {
        RuntimeModuleContainer container = RuntimeModuleContainer.dependencyFailure(
                definition,
                resolvedDependencies,
                dependencyFailureException(definition.id(), source)
        );
        availability.recordDependencyFailure(definition.id(), source);
        runtimeContainers.put(definition.id(), container);
    }

    private void recordDirectFailure(
            ModuleDefinition definition,
            FailureReason.Category category,
            RuntimeException exception,
            RuntimeAvailabilityBuilder availability
    ) {
        FailureReason reason = new FailureReason(category, diagnostic(exception));
        availability.recordDirectFailure(definition.id(), reason);
        LOGGER.error(
                "[CoreManager] Runtime failure for module {}: {}",
                definition.id(),
                reason.diagnostic(),
                exception
        );
    }

    private void shutdownOnce(RuntimeModuleContainer container) {
        Optional<IModule> claimed = container.claimShutdown();
        if (claimed.isEmpty()) {
            return;
        }
        try {
            claimed.get().shutdown();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "[CoreManager] Module shutdown failed: {}",
                    container.definition().id(),
                    exception
            );
        } finally {
            container.finishShutdown();
        }
    }

    private Optional<ModuleId> firstUnavailableRequired(
            ModuleDefinition definition,
            RuntimeAvailabilityBuilder availability,
            Map<ModuleId, Integer> orderIndex
    ) {
        Comparator<ModuleId> dependencyOrder = Comparator
                .comparingInt((ModuleId moduleId) -> orderIndex.getOrDefault(moduleId, Integer.MAX_VALUE))
                .thenComparing(ModuleId::value);
        return definition.requiredDependencies().stream()
                .filter(availability::isUnavailable)
                .sorted(dependencyOrder)
                .findFirst();
    }

    private Set<ModuleId> resolvedDependencies(
            ModuleDefinition definition,
            DependencyResolutionResult resolution
    ) {
        Set<DependencyEdge> removedOptional = Set.copyOf(resolution.removedOptionalEdges());
        LinkedHashSet<ModuleId> resolved = new LinkedHashSet<>();
        for (DependencyEdge edge : resolution.dependencyGraph()
                .getOrDefault(definition.id(), List.of())) {
            if (!moduleRegistry.hasModule(edge.dependency())) {
                continue;
            }
            if (edge.type() == DependencyEdge.Type.OPTIONAL && removedOptional.contains(edge)) {
                continue;
            }
            resolved.add(edge.dependency());
        }
        return Collections.unmodifiableSet(resolved);
    }

    private Map<ModuleId, Integer> buildOrderIndex(List<ModuleDefinition> resolvedOrder) {
        LinkedHashMap<ModuleId, Integer> orderIndex = new LinkedHashMap<>();
        for (int index = 0; index < resolvedOrder.size(); index++) {
            orderIndex.put(resolvedOrder.get(index).id(), index);
        }
        return Collections.unmodifiableMap(orderIndex);
    }

    private IllegalStateException dependencyFailureException(ModuleId moduleId, ModuleId source) {
        return new IllegalStateException(
                "Module " + moduleId + " requires unavailable runtime dependency " + source
        );
    }

    private String diagnostic(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message.trim();
    }

    private void ensureRuntimeClosed() {
        if (dependencyResolutionResult != null
                || runtimeAvailabilityResult != null
                || !runtimeContainers.isEmpty()
                || !successfulInitializationOrder.isEmpty()) {
            throw new IllegalStateException("A server runtime scope is already open");
        }
    }

    private void ensureRuntimeStarted() {
        if (dependencyResolutionResult == null || runtimeAvailabilityResult == null) {
            throw new IllegalStateException("No completed server runtime startup is available");
        }
    }

    private static final class RuntimeAvailabilityBuilder {
        private final LinkedHashMap<ModuleId, ModuleAvailabilityRecord> records =
                new LinkedHashMap<>();

        private RuntimeAvailabilityBuilder(List<ModuleDefinition> resolvedOrder) {
            for (int index = 0; index < resolvedOrder.size(); index++) {
                ModuleId moduleId = resolvedOrder.get(index).id();
                records.put(
                        moduleId,
                        new ModuleAvailabilityRecord(
                                moduleId,
                                AvailabilityStatus.PENDING,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                index
                        )
                );
            }
        }

        private boolean isUnavailable(ModuleId moduleId) {
            ModuleAvailabilityRecord record = records.get(moduleId);
            return record != null
                    && (record.status() == AvailabilityStatus.DIRECT_FAILURE
                    || record.status() == AvailabilityStatus.DEPENDENCY_FAILURE);
        }

        private void recordAvailable(ModuleId moduleId) {
            ModuleAvailabilityRecord pending = requirePending(moduleId);
            records.put(
                    moduleId,
                    new ModuleAvailabilityRecord(
                            moduleId,
                            AvailabilityStatus.AVAILABLE,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            pending.propagationOrderIndex()
                    )
            );
        }

        private void recordDirectFailure(ModuleId moduleId, FailureReason reason) {
            ModuleAvailabilityRecord pending = requirePending(moduleId);
            records.put(
                    moduleId,
                    new ModuleAvailabilityRecord(
                            moduleId,
                            AvailabilityStatus.DIRECT_FAILURE,
                            Optional.empty(),
                            Optional.of(moduleId),
                            Optional.of(Objects.requireNonNull(reason, "reason")),
                            pending.propagationOrderIndex()
                    )
            );
        }

        private void recordDependencyFailure(ModuleId moduleId, ModuleId source) {
            ModuleAvailabilityRecord pending = requirePending(moduleId);
            ModuleAvailabilityRecord sourceRecord = Objects.requireNonNull(
                    records.get(source),
                    "source availability record"
            );
            if (!isUnavailable(source)) {
                throw new IllegalStateException("Dependency failure source is not unavailable: " + source);
            }
            records.put(
                    moduleId,
                    new ModuleAvailabilityRecord(
                            moduleId,
                            AvailabilityStatus.DEPENDENCY_FAILURE,
                            Optional.of(source),
                            sourceRecord.rootCauseModule(),
                            sourceRecord.failureReason(),
                            pending.propagationOrderIndex()
                    )
            );
        }

        private RuntimeAvailabilityResult snapshot() {
            records.forEach((moduleId, record) -> {
                if (record.status() == AvailabilityStatus.PENDING) {
                    throw new IllegalStateException(
                            "Runtime availability was not finalized for " + moduleId
                    );
                }
            });
            return new RuntimeAvailabilityResult(records);
        }

        private ModuleAvailabilityRecord requirePending(ModuleId moduleId) {
            ModuleAvailabilityRecord record = Objects.requireNonNull(
                    records.get(moduleId),
                    "availability record"
            );
            if (record.status() != AvailabilityStatus.PENDING) {
                throw new IllegalStateException(
                        "Runtime availability is already final for " + moduleId
                );
            }
            return record;
        }
    }
}
