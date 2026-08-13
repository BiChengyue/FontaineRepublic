package com.fontainerepublic.server.emergency;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyActionRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyReceiptProvider;
import com.fontainerepublic.server.emergency.api.EmergencyService;
import com.fontainerepublic.server.emergency.persistence.EmergencyNbtCodec;
import com.fontainerepublic.server.emergency.persistence.EmergencyRepository;
import com.fontainerepublic.server.emergency.service.ConfigManagerAuthorityConfigSource;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyActionRegistry;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyService;
import com.fontainerepublic.server.emergency.service.EmergencyAuthorityConfigSource;
import com.fontainerepublic.server.emergency.service.EmergencyTokenTable;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Infrastructure module binding the shared emergency authority to one server
 * runtime (FR-EMG-001-A §16 / implementation task §3).
 *
 * <p>Depends on {@code player-data} (identity infrastructure is guaranteed
 * ready) and registers after the FR-CORE-002 durable commit gate so journal
 * and configuration commits are acknowledged. The module owns the frozen
 * emergency-action registry (business modules contribute descriptors during
 * the Mod-lifetime window) and the server-runtime EmergencyService with its
 * token table. It never touches business-module namespaces and never
 * implements business actions.</p>
 */
public final class EmergencyModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("emergency");
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmergencyRepository repository;
    private EmergencyActionRegistry actionRegistry;
    private EmergencyService service;
    private EmergencyTokenTable tokenTable;
    private volatile boolean shutdownRequested;
    private final AtomicLong epochSource = new AtomicLong();

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Emergency Authority",
                        "1.0.0",
                        Optional.of("Shared Hydro Archon emergency authority infrastructure"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(PlayerDataModule.MODULE_ID),
                Set.of(),
                45,
                EmergencyModule::new
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
        repository = EmergencyRepository.createProduction(new EmergencyNbtCodec());
        actionRegistry = new DefaultEmergencyActionRegistry();
        long epoch = epochSource.incrementAndGet();
        tokenTable = new EmergencyTokenTable(epoch);
        EmergencyAuthorityConfigSource configSource =
                ConfigManagerAuthorityConfigSource.INSTANCE;
        service = new DefaultEmergencyService(
                repository,
                actionRegistry,
                tokenTable,
                System::currentTimeMillis,
                epoch,
                configSource,
                Map.of()
        );
        // Startup acceptance of a staged authority change; drift fails closed.
        service.acceptStagedAtStartup();
        LOGGER.info(
                "[Emergency] Runtime initialized (revision={}, records={}, phase={})",
                repository.snapshot().storeRevision(),
                repository.recordCount(),
                repository.config().phase()
        );
    }

    @Override
    public void shutdown() {
        if (service != null) {
            service.invalidateTokens();
        }
        service = null;
        tokenTable = null;
        repository = null;
        LOGGER.info("[Emergency] Runtime closed");
    }

    public EmergencyService service() {
        if (service == null) {
            throw new IllegalStateException("Emergency service is not active");
        }
        return service;
    }

    public EmergencyActionRegistry actionRegistry() {
        if (actionRegistry == null) {
            throw new IllegalStateException("Emergency action registry is not active");
        }
        return actionRegistry;
    }

    /** Freezes the action registry after all business contributions. */
    public void freezeActionRegistry() {
        if (actionRegistry != null && !actionRegistry.isFrozen()) {
            actionRegistry.freeze();
        }
    }

    /** Registers a receipt provider (business modules, runtime binding). */
    public void registerReceiptProvider(EmergencyReceiptProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (service instanceof DefaultEmergencyService defaultService) {
            defaultService.bindReceiptProvider(provider);
        } else {
            throw new IllegalStateException("Emergency service is not active");
        }
    }
}
