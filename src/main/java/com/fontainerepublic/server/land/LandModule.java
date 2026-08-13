package com.fontainerepublic.server.land;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.land.api.HolderDirectory;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.event.BuildEventHandler;
import com.fontainerepublic.server.land.event.InteractionEventHandler;
import com.fontainerepublic.server.land.event.LandEventPolicy;
import com.fontainerepublic.server.land.persistence.LandNbtCodec;
import com.fontainerepublic.server.land.persistence.LandRepository;
import com.fontainerepublic.server.land.service.ConfigDrivenPermissionResolver;
import com.fontainerepublic.server.land.service.DefaultLandService;
import com.fontainerepublic.server.land.service.LandPermissionConfig;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding republic land parcels, usage rights, zoning,
 * access policy, and violation-report entry to one server runtime
 * (FR-LAND-001-A).
 *
 * <p>Depends on {@code player-data} and {@code subject-registry} (holder and
 * actor resolution) and registers after the FR-CORE-002 durable commit gate
 * so every land mutation is acknowledged. Ownership is permanently REPUBLIC —
 * the module never exposes a transfer/sale/lease path. Land does not touch
 * economy, justice, institutions, client projections, or technical
 * permissions beyond its own config-driven access policy (rank/OP never
 * bypasses it).</p>
 */
public final class LandModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("land");
    private static final Logger LOGGER = LogUtils.getLogger();

    private LandRepository repository;
    private LandService service;
    private BuildEventHandler buildHandler;
    private InteractionEventHandler interactionHandler;
    private volatile PlayerDataService boundPlayerData;
    private volatile SubjectRegistryService boundSubjectRegistry;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Land",
                        "1.0.0",
                        Optional.of("Republic-owned land parcels, usage rights, zoning, access, violation reports"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(PlayerDataModule.MODULE_ID, SubjectRegistryModule.MODULE_ID),
                Set.of(),
                60,
                LandModule::new
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
        repository = LandRepository.createProduction(new LandNbtCodec());
        LandPermissionConfig permissionConfig = LandPermissionConfig.DEFAULT
                .withConfigManagerValues();
        ModuleHolderDirectory holders = new ModuleHolderDirectory();
        ConfigDrivenPermissionResolver resolver = new ConfigDrivenPermissionResolver(
                permissionConfig,
                repository,
                holders,
                System::currentTimeMillis
        );
        service = new DefaultLandService(
                repository,
                System::currentTimeMillis,
                holders,
                resolver
        );
        LandEventPolicy eventPolicy = new LandEventPolicy(repository, resolver);
        buildHandler = new BuildEventHandler(eventPolicy);
        interactionHandler = new InteractionEventHandler(eventPolicy);
        MinecraftForge.EVENT_BUS.register(buildHandler);
        MinecraftForge.EVENT_BUS.register(interactionHandler);
        LOGGER.info(
                "[Land] Runtime initialized (revision={}, parcels={}, reports={})",
                repository.snapshot().storeRevision(),
                repository.size(),
                repository.reportCount()
        );
    }

    /**
     * Binds the authoritative PlayerData and subject-registry services after
     * the runtime start so holder/actor resolution can enforce the §4 chain.
     * Until bound, every holder resolution fails closed with
     * {@code HOLDER_DIRECTORY_UNAVAILABLE}.
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
        if (buildHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(buildHandler);
        }
        if (interactionHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(interactionHandler);
        }
        buildHandler = null;
        interactionHandler = null;
        service = null;
        repository = null;
        boundPlayerData = null;
        boundSubjectRegistry = null;
        LOGGER.info("[Land] Runtime closed");
    }

    public LandService service() {
        if (service == null) {
            throw new IllegalStateException("Land service is not active");
        }
        return service;
    }

    /**
     * Holder/actor resolution through the authoritative PlayerData and FR-ID
     * subject services (never raw NBT).
     */
    private final class ModuleHolderDirectory implements HolderDirectory {
        @Override
        public boolean isAvailable() {
            return boundPlayerData != null && boundSubjectRegistry != null;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            PlayerDataService bound = boundPlayerData;
            return bound != null && bound.find(playerId).isPresent();
        }

        @Override
        public boolean hasActiveSubject(UUID playerId) {
            SubjectRegistryService bound = boundSubjectRegistry;
            if (bound == null) {
                return false;
            }
            Optional<SubjectRecord> subject = bound.findSubjectForPlayer(playerId);
            return subject.map(SubjectRecord::subjectId)
                    .flatMap(bound::status)
                    .filter(status -> status == SubjectStatus.ACTIVE)
                    .isPresent();
        }
    }
}
