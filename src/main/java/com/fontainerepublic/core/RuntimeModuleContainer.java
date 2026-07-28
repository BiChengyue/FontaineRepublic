package com.fontainerepublic.core;

import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.runtime.ModuleState;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * CoreManager-owned runtime state for one module in one server lifecycle.
 *
 * <p>Lifecycle mutations are package-private so only the core orchestrator can drive them.
 * Public callers receive read-only state and instance queries.</p>
 */
public final class RuntimeModuleContainer {
    private final ModuleDefinition definition;
    private final Set<ModuleId> resolvedDependencies;
    private Optional<IModule> internalInstance;

    private ModuleState state = ModuleState.REGISTERED;
    private Throwable failureCause;
    private boolean initializationStarted;
    private boolean initializationSucceeded;
    private boolean shutdownExecuted;

    private RuntimeModuleContainer(
            ModuleDefinition definition,
            Optional<IModule> instance,
            Set<ModuleId> resolvedDependencies
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.internalInstance = Objects.requireNonNull(instance, "instance");
        this.resolvedDependencies = Set.copyOf(
                Objects.requireNonNull(resolvedDependencies, "resolvedDependencies")
        );
    }

    static RuntimeModuleContainer registered(
            ModuleDefinition definition,
            IModule instance,
            Set<ModuleId> resolvedDependencies
    ) {
        return new RuntimeModuleContainer(
                definition,
                Optional.of(Objects.requireNonNull(instance, "instance")),
                resolvedDependencies
        );
    }

    static RuntimeModuleContainer dependencyFailure(
            ModuleDefinition definition,
            Set<ModuleId> resolvedDependencies,
            Throwable failureCause
    ) {
        RuntimeModuleContainer container = new RuntimeModuleContainer(
                definition,
                Optional.empty(),
                resolvedDependencies
        );
        container.markDependencyFailure(failureCause);
        return container;
    }

    public ModuleDefinition definition() {
        return definition;
    }

    public synchronized ModuleState state() {
        return state;
    }

    public Set<ModuleId> resolvedDependencies() {
        return resolvedDependencies;
    }

    /**
     * Returns no instance for dependency-failed or terminated containers.
     */
    public synchronized Optional<IModule> instance() {
        if (state == ModuleState.DEPENDENCY_FAILURE || state == ModuleState.TERMINATED) {
            return Optional.empty();
        }
        return internalInstance;
    }

    public synchronized Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    public synchronized boolean initializationStarted() {
        return initializationStarted;
    }

    public synchronized boolean initializationSucceeded() {
        return initializationSucceeded;
    }

    public synchronized boolean shutdownExecuted() {
        return shutdownExecuted;
    }

    synchronized void markDependencyFailure(Throwable cause) {
        requireState(ModuleState.REGISTERED);
        failureCause = Objects.requireNonNull(cause, "cause");
        state = ModuleState.DEPENDENCY_FAILURE;
    }

    synchronized IModule beginInitialization() {
        requireState(ModuleState.REGISTERED);
        IModule instance = requireInstance();
        initializationStarted = true;
        state = ModuleState.INITIALIZING;
        return instance;
    }

    synchronized void markInitializationSucceeded() {
        requireState(ModuleState.INITIALIZING);
        initializationSucceeded = true;
        state = ModuleState.ACTIVE;
    }

    synchronized void markInitializationFailed(Throwable cause) {
        requireState(ModuleState.INITIALIZING);
        failureCause = Objects.requireNonNull(cause, "cause");
        state = ModuleState.INIT_FAILURE;
    }

    synchronized Optional<IModule> claimShutdown() {
        if (shutdownExecuted) {
            return Optional.empty();
        }
        if (state != ModuleState.ACTIVE && state != ModuleState.INIT_FAILURE) {
            throw invalidTransition(state, ModuleState.STOPPING);
        }
        IModule instance = requireInstance();
        shutdownExecuted = true;
        state = ModuleState.STOPPING;
        return Optional.of(instance);
    }

    synchronized void finishShutdown() {
        requireState(ModuleState.STOPPING);
        state = ModuleState.STOPPED;
    }

    synchronized void beginCleanup() {
        requireState(ModuleState.STOPPED);
        state = ModuleState.CLEANUP;
    }

    synchronized void terminate() {
        requireState(ModuleState.CLEANUP);
        internalInstance = Optional.empty();
        state = ModuleState.TERMINATED;
    }

    synchronized void discardDependencyFailure() {
        requireState(ModuleState.DEPENDENCY_FAILURE);
        internalInstance = Optional.empty();
    }

    private IModule requireInstance() {
        if (state == ModuleState.DEPENDENCY_FAILURE || state == ModuleState.TERMINATED) {
            throw new IllegalStateException("Module instance is unavailable in state " + state);
        }
        return internalInstance.orElseThrow(
                () -> new IllegalStateException("Module instance is not available for " + definition.id())
        );
    }

    private void requireState(ModuleState expected) {
        if (state != expected) {
            throw invalidTransition(state, expected);
        }
    }

    private IllegalStateException invalidTransition(ModuleState from, ModuleState to) {
        return new IllegalStateException(
                "Invalid module lifecycle transition for " + definition.id() + ": " + from + " -> " + to
        );
    }
}
