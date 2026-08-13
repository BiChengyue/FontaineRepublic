package com.fontainerepublic.server.audit;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.persistence.AuditNbtCodec;
import com.fontainerepublic.server.audit.persistence.AuditRepository;
import com.fontainerepublic.server.audit.service.DefaultAuditService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Infrastructure module binding the append-only audit ledger to one server
 * runtime (FR-AUD-001-A).
 *
 * <p>Depends on {@code player-data} (actor identity infrastructure is
 * guaranteed ready before the audit service); it is registered after the
 * FR-CORE-002 durable commit gate so authoritative entries can use
 * {@code DataManager.commitModuleData}. The audit module never touches the
 * FR-EMG namespace and never substitutes for the emergency journal.</p>
 */
public final class AuditModule implements IModule {
    public static final ModuleId MODULE_ID = new ModuleId("audit");
    private static final Logger LOGGER = LogUtils.getLogger();

    private AuditRepository repository;
    private AuditService service;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Audit",
                        "1.0.0",
                        Optional.of("Append-only national audit ledger"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(PlayerDataModule.MODULE_ID),
                Set.of(),
                40,
                AuditModule::new
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
        repository = AuditRepository.createProduction(new AuditNbtCodec());
        service = new DefaultAuditService(repository, System::currentTimeMillis);
        LOGGER.info(
                "[Audit] Runtime initialized (revision={}, entries={})",
                repository.snapshot().storeRevision(),
                repository.size()
        );
    }

    @Override
    public void shutdown() {
        service = null;
        repository = null;
        LOGGER.info("[Audit] Runtime closed");
    }

    public AuditService service() {
        if (service == null) {
            throw new IllegalStateException("Audit service is not active");
        }
        return service;
    }
}
