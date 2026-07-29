package com.fontainerepublic.server.network;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Fresh per-server Network Foundation runtime state.
 */
public final class NetworkRuntimeModule implements IModule {
    public static final ModuleId MODULE_ID = new ModuleId("network");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PRIORITY = 0;

    private final LongSupplier nanoClock;
    private NetworkRuntimeService service;

    public NetworkRuntimeModule() {
        this(System::nanoTime);
    }

    NetworkRuntimeModule(LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Network Foundation",
                        "1.0.0",
                        Optional.of("Framework-level transport runtime"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(),
                Set.of(),
                PRIORITY,
                NetworkRuntimeModule::new
        );
        if (!registry.register(definition)) {
            throw new IllegalStateException("Unable to register module " + MODULE_ID);
        }
    }

    @Override
    public String getName() {
        return MODULE_ID.value();
    }

    @Override
    public void init() {
        if (service != null) {
            throw new IllegalStateException("Network runtime is already active");
        }
        service = new NetworkRuntimeService(new PacketRateLimiter(), nanoClock);
        LOGGER.info("[Network] Runtime initialized with empty transport state");
    }

    @Override
    public void shutdown() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        LOGGER.info("[Network] Runtime closed and transport state cleared");
    }

    public Optional<NetworkRuntimeService> activeService() {
        if (service == null || !service.isActive()) {
            return Optional.empty();
        }
        return Optional.of(service);
    }
}
