package com.fontainerepublic.core.module.test;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.core.module.runtime.ModuleAvailabilityRecord;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal runtime module used to validate the FR-CORE-001 lifecycle pipeline.
 */
public final class TestModule implements IModule {
    private static final ModuleId ALPHA = new ModuleId("alpha");
    private static final ModuleId BRAVO = new ModuleId("bravo");
    private static final ModuleId CHARLIE = new ModuleId("charlie");
    private static final ModuleId DELTA = new ModuleId("delta");
    private static final ModuleId ECHO = new ModuleId("echo");

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ModuleId id;
    private final boolean failInitialization;

    private TestModule(ModuleId id, boolean failInitialization) {
        this.id = Objects.requireNonNull(id, "id");
        this.failInitialization = failInitialization;
    }

    public static void registerAll(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        register(registry, ALPHA, 50, Set.of(), false);
        register(registry, BRAVO, 50, Set.of(ALPHA), false);
        register(registry, CHARLIE, 30, Set.of(ALPHA), false);
        register(registry, DELTA, 30, Set.of(), true);
        register(registry, ECHO, 30, Set.of(DELTA), false);
    }

    public static void logAvailability(CoreManager coreManager) {
        Objects.requireNonNull(coreManager, "coreManager");
        logAvailability(coreManager, ALPHA);
        logAvailability(coreManager, BRAVO);
        logAvailability(coreManager, CHARLIE);
        logAvailability(coreManager, DELTA);
        logAvailability(coreManager, ECHO);
    }

    private static void register(
            ModuleRegistry registry,
            ModuleId id,
            int priority,
            Set<ModuleId> requiredDependencies,
            boolean failInitialization
    ) {
        boolean registered = registry.register(new ModuleDefinition(
                id,
                metadata(id),
                requiredDependencies,
                Set.of(),
                priority,
                () -> {
                    LOGGER.info("[TestModule:{}] Factory", id);
                    return new TestModule(id, failInitialization);
                }
        ));
        if (!registered) {
            throw new IllegalStateException("Failed to register TestModule: " + id);
        }
        LOGGER.info("[TestModule:{}] Registered", id);
    }

    private static void logAvailability(CoreManager coreManager, ModuleId id) {
        ModuleAvailabilityRecord record = coreManager.getAvailability(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing runtime availability for TestModule: " + id
                ));
        LOGGER.info("[TestModule:{}] Availability: {}", id, record.status());
    }

    private static ModuleMetadata metadata(ModuleId id) {
        return new ModuleMetadata(
                id.value() + " Test Module",
                "1",
                Optional.of("FR-CORE-001 L4 failure propagation validation"),
                Optional.empty()
        );
    }

    @Override
    public String getName() {
        return id.value();
    }

    @Override
    public void init() {
        LOGGER.info("[TestModule:{}] Initialized", id);
        if (failInitialization) {
            LOGGER.info("[TestModule:{}] Init failure", id);
            throw new IllegalStateException(
                    "Intentional L4 initialization failure for " + id
            );
        }
    }

    @Override
    public void shutdown() {
        LOGGER.info("[TestModule:{}] Shutdown", id);
    }
}
