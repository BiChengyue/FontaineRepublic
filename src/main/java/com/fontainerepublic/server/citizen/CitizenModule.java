package com.fontainerepublic.server.citizen;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.api.SubjectDirectory;
import com.fontainerepublic.server.citizen.persistence.CitizenNbtCodec;
import com.fontainerepublic.server.citizen.persistence.CitizenRepository;
import com.fontainerepublic.server.citizen.service.DefaultCitizenService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding citizenship status and political rank to one
 * server runtime (FR-CIT-001-A).
 *
 * <p>Depends on {@code player-data} and {@code subject-registry} (the ordered
 * {@code PlayerData -> subject -> citizen} chain must be ready before
 * provisioning) and registers after the FR-CORE-002 durable commit gate so
 * every citizenship mutation is acknowledged. The module never touches
 * economy, land, cases, offices, registry numbers, permissions, or client
 * projections; rank is a political classification and never a technical
 * permission.</p>
 */
public final class CitizenModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("citizen");
    private static final Logger LOGGER = LogUtils.getLogger();

    private CitizenRepository repository;
    private CitizenService service;
    private volatile PlayerDataService boundPlayerData;
    private volatile SubjectRegistryService boundSubjectRegistry;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Citizen",
                        "1.0.0",
                        Optional.of("Citizenship status and political rank infrastructure"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(PlayerDataModule.MODULE_ID, SubjectRegistryModule.MODULE_ID),
                Set.of(),
                50,
                CitizenModule::new
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
        repository = CitizenRepository.createProduction(new CitizenNbtCodec());
        service = new DefaultCitizenService(
                repository,
                System::currentTimeMillis,
                new ModulePlayerPresence(),
                new ModuleSubjectDirectory()
        );
        LOGGER.info(
                "[Citizen] Runtime initialized (revision={}, citizens={})",
                repository.snapshot().storeRevision(),
                repository.size()
        );
    }

    /**
     * Binds the authoritative PlayerData and subject-registry services after
     * the runtime start so provisioning can enforce the §4.1 chain. Until
     * bound, ensure calls fail closed with {@code PLAYER_DATA_UNAVAILABLE} /
     * {@code SUBJECT_REGISTRY_UNAVAILABLE}.
     */
    public void bindServices(
            PlayerDataService playerDataService,
            SubjectRegistryService subjectRegistryService
    ) {
        this.boundPlayerData = playerDataService;
        this.boundSubjectRegistry = subjectRegistryService;
    }

    @Override
    public void shutdown() {
        service = null;
        repository = null;
        boundPlayerData = null;
        boundSubjectRegistry = null;
        LOGGER.info("[Citizen] Runtime closed");
    }

    public CitizenService service() {
        if (service == null) {
            throw new IllegalStateException("Citizen service is not active");
        }
        return service;
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

    private final class ModuleSubjectDirectory implements SubjectDirectory {
        @Override
        public boolean isAvailable() {
            return boundSubjectRegistry != null;
        }

        @Override
        public SubjectRecord ensureSubject(UUID playerId) {
            SubjectRegistryService bound = boundSubjectRegistry;
            if (bound == null) {
                throw new IllegalStateException(
                        "Subject registry is not bound; ensureSubject must not be called"
                );
            }
            return bound.ensurePlayerSubject(playerId);
        }

        @Override
        public boolean hasSubject(SubjectId subjectId) {
            SubjectRegistryService bound = boundSubjectRegistry;
            return bound != null && bound.findBySubjectId(subjectId).isPresent();
        }
    }
}
