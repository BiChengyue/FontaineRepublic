package com.fontainerepublic.server.command;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.RuntimeModuleContainer;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.runtime.AvailabilityStatus;
import com.fontainerepublic.core.module.runtime.ModuleAvailabilityRecord;
import com.fontainerepublic.core.module.runtime.ModuleState;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.economy.EconomyModule;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.service.SubjectBootstrapService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateless execution-time facade over the current Core runtime.
 */
public final class CommandRuntimeResolver {
    private final CoreManager coreManager;

    public CommandRuntimeResolver(CoreManager coreManager) {
        this.coreManager = Objects.requireNonNull(coreManager, "coreManager");
    }

    public RuntimeSnapshot snapshot() {
        return coreManager.getRuntimeAvailabilitySnapshot()
                .map(availability -> {
                    Map<ModuleId, RuntimeModuleContainer> containers =
                            coreManager.getRuntimeContainers();
                    List<ModuleDiagnostic> modules = availability.records()
                            .values()
                            .stream()
                            .map(record -> diagnostic(
                                    record,
                                    containers.get(record.moduleId())
                            ))
                            .sorted(Comparator.comparing(ModuleDiagnostic::moduleId))
                            .toList();
                    long active = modules.stream()
                            .filter(ModuleDiagnostic::available)
                            .count();
                    long unavailable = modules.stream()
                            .filter(module -> !module.available())
                            .count();
                    return new RuntimeSnapshot(
                            true,
                            coreManager.getDependencyResolutionSnapshot().isPresent(),
                            modules,
                            active,
                            unavailable
                    );
                })
                .orElseGet(RuntimeSnapshot::unavailable);
    }

    /**
     * Execution-time resolution of the subject bootstrap service
     * (foundation-owned admin child adapter; resolved per invocation).
     */
    public Optional<SubjectBootstrapService> subjectBootstrapService() {
        return coreManager.getRuntimeContainer(SubjectRegistryModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(SubjectRegistryModule.class::isInstance)
                .map(SubjectRegistryModule.class::cast)
                .map(SubjectRegistryModule::bootstrapService);
    }

    /**
     * Execution-time resolution of the current ACTIVE economy service
     * (FR-ECO-001-A §6.2; resolved per invocation, never cached).
     */
    public Optional<EconomyService> economyService() {
        return coreManager.getRuntimeContainer(EconomyModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(EconomyModule.class::isInstance)
                .map(EconomyModule.class::cast)
                .map(EconomyModule::service);
    }

    /**
     * Execution-time resolution of the current ACTIVE citizen service
     * (FR-CIT-001-A §5; resolved per invocation, never cached).
     */
    public Optional<CitizenService> citizenService() {
        return coreManager.getRuntimeContainer(CitizenModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(CitizenModule.class::isInstance)
                .map(CitizenModule.class::cast)
                .map(CitizenModule::service);
    }

    private ModuleDiagnostic diagnostic(
            ModuleAvailabilityRecord availability,
            RuntimeModuleContainer container
    ) {
        String lifecycleState = container == null
                ? "NOT_CREATED"
                : container.state().name();
        String failureCategory = availability.failureReason()
                .map(reason -> reason.category().name())
                .orElse("NONE");
        return new ModuleDiagnostic(
                availability.moduleId().value(),
                lifecycleState,
                availability.status() == AvailabilityStatus.AVAILABLE,
                failureCategory
        );
    }

    public record RuntimeSnapshot(
            boolean runtimeAvailable,
            boolean dependencyResolutionComplete,
            List<ModuleDiagnostic> modules,
            long activeModules,
            long unavailableModules
    ) {
        public RuntimeSnapshot {
            modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
            if (activeModules < 0 || unavailableModules < 0) {
                throw new IllegalArgumentException("Module counts must not be negative");
            }
        }

        private static RuntimeSnapshot unavailable() {
            return new RuntimeSnapshot(false, false, List.of(), 0, 0);
        }
    }

    public record ModuleDiagnostic(
            String moduleId,
            String state,
            boolean available,
            String failureCategory
    ) {
        public ModuleDiagnostic {
            moduleId = Objects.requireNonNull(moduleId, "moduleId");
            state = Objects.requireNonNull(state, "state");
            failureCategory = Objects.requireNonNull(failureCategory, "failureCategory");
        }
    }
}
