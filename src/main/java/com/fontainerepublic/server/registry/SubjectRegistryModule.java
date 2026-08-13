package com.fontainerepublic.server.registry;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryNbtCodec;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryRepository;
import com.fontainerepublic.server.registry.service.DefaultSubjectBootstrapService;
import com.fontainerepublic.server.registry.service.DefaultSubjectRegistryService;
import com.fontainerepublic.server.registry.service.SubjectBootstrapService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding the unified digital subject registry to one
 * server runtime (FR-ID-001-A §20).
 *
 * <p>Depends on {@code player-data} (authoritative UUID records must be ready
 * before natural-person provisioning) and registers after the FR-CORE-002
 * durable commit gate so every permanent number commit is acknowledged. The
 * module never touches the FR-EMG namespace and never derives owner identity
 * from a game name, FR-EMG authority, or any configured holder UUID.</p>
 *
 * <p>The original-person bootstrap ({@code 10-000001-61}) is implemented per
 * the separately approved audited bootstrap design
 * (FR-ID-BOOTSTRAP-001-A): console-only, one-time, immutable. This module also
 * materializes the fixed reservations and the constant
 * {@code OFFICE_ID:HYDRO_ARCHON} office subject.</p>
 */
public final class SubjectRegistryModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("subject-registry");
    private static final Logger LOGGER = LogUtils.getLogger();

    private SubjectRegistryRepository repository;
    private SubjectRegistryService service;
    private SubjectBootstrapService bootstrapService;
    private volatile PlayerDataService boundPlayerData;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Subject Registry",
                        "1.0.0",
                        Optional.of("Unified digital subject registry core"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(PlayerDataModule.MODULE_ID),
                Set.of(),
                40,
                SubjectRegistryModule::new
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
        repository = SubjectRegistryRepository.createProduction(new SubjectRegistryNbtCodec());
        ModulePlayerPresence presence = new ModulePlayerPresence();
        service = new DefaultSubjectRegistryService(
                repository,
                System::currentTimeMillis,
                presence
        );
        bootstrapService = new DefaultSubjectBootstrapService(
                repository,
                System::currentTimeMillis,
                presence
        );
        LOGGER.info(
                "[SubjectRegistry] Runtime initialized (revision={}, subjects={}, bootstrap={})",
                repository.snapshot().storeRevision(),
                repository.size(),
                repository.bootstrapState().phase()
        );
    }

    /**
     * Binds the authoritative PlayerData service after the runtime start so
     * provisioning can enforce the §8.1 precondition. Until bound, ensure calls
     * fail closed with {@code PLAYER_DATA_UNAVAILABLE}.
     */
    public void bindPlayerDataService(PlayerDataService playerDataService) {
        this.boundPlayerData = playerDataService;
    }

    @Override
    public void shutdown() {
        service = null;
        bootstrapService = null;
        repository = null;
        boundPlayerData = null;
        LOGGER.info("[SubjectRegistry] Runtime closed");
    }

    public SubjectRegistryService service() {
        if (service == null) {
            throw new IllegalStateException("Subject-registry service is not active");
        }
        return service;
    }

    public SubjectBootstrapService bootstrapService() {
        if (bootstrapService == null) {
            throw new IllegalStateException("Subject bootstrap service is not active");
        }
        return bootstrapService;
    }

    private final class ModulePlayerPresence implements PlayerPresence {
        @Override
        public boolean isAvailable() {
            return boundPlayerData != null;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            PlayerDataService bound = boundPlayerData;
            return bound != null && bound.find(playerId).isPresent();
        }
    }
}
