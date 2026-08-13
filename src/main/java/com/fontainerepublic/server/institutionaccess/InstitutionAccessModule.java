package com.fontainerepublic.server.institutionaccess;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.event.LifecycleEventHandlers;
import com.fontainerepublic.server.institutionaccess.event.PresenceMonitor;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessNbtCodec;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessRepository;
import com.fontainerepublic.server.institutionaccess.service.ActorResolver;
import com.fontainerepublic.server.institutionaccess.service.DefaultInstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.service.InstitutionAccessConfig;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.land.api.LandService;
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
 * Infrastructure module binding the shared institution access boundary to one
 * server runtime (FR-INST-002-A).
 *
 * <p>Owns the facility/terminal directory (consuming FR-LAND parcels
 * read-only), the server-runtime on-site contexts, the bounded presence
 * monitor, and the lifecycle event handlers for the four institutions.
 * Depends on {@code land} (spatial data), {@code audit} (authoritative
 * directory mutations), {@code player-data} and {@code subject-registry}
 * (actor resolution). It never implements institution business permissions,
 * FR-EMG emergency actions, GUI, or packets.</p>
 */
public final class InstitutionAccessModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("institution-access");
    private static final Logger LOGGER = LogUtils.getLogger();

    private InstitutionAccessRepository repository;
    private InstitutionAccessService service;
    private PresenceMonitor presenceMonitor;
    private LifecycleEventHandlers lifecycleHandlers;
    private volatile PlayerDataService boundPlayerData;
    private volatile SubjectRegistryService boundSubjectRegistry;
    private volatile LandService boundLand;
    private volatile AuditService boundAudit;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Institution Access",
                        "1.0.0",
                        Optional.of("Shared facility/terminal/on-site-context boundary"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        LandModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID
                ),
                Set.of(),
                70,
                InstitutionAccessModule::new
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
        repository = InstitutionAccessRepository.createProduction(
                new InstitutionAccessNbtCodec()
        );
        LOGGER.info(
                "[InstitutionAccess] Directory loaded (revision={}, facilities={}, terminals={})",
                repository.snapshot().storeRevision(),
                repository.size(),
                repository.terminalCount()
        );
    }

    /**
     * Binds the authoritative services after the runtime start and builds the
     * runtime service, presence monitor, and lifecycle handlers. Until bound,
     * every actor/parcel resolution fails closed with
     * {@code HOLDER_DIRECTORY_UNAVAILABLE}/{@code LAND_UNAVAILABLE}.
     */
    public void bindServices(
            PlayerDataService playerDataService,
            SubjectRegistryService subjectRegistryService,
            LandService landService,
            AuditService auditService
    ) {
        this.boundPlayerData = playerDataService;
        this.boundSubjectRegistry = subjectRegistryService;
        this.boundLand = landService;
        this.boundAudit = auditService;
        InstitutionAccessConfig config =
                InstitutionAccessConfig.DEFAULT.withConfigManagerValues();
        DefaultInstitutionAccessService runtimeService =
                new DefaultInstitutionAccessService(
                        repository,
                        requireLand(),
                        new ModuleActorResolver(),
                        boundAudit,
                        System::currentTimeMillis,
                        config
                );
        this.service = runtimeService;
        this.presenceMonitor = new PresenceMonitor(
                runtimeService,
                config.presenceCheckIntervalTicks()
        );
        this.lifecycleHandlers = new LifecycleEventHandlers(runtimeService);
        MinecraftForge.EVENT_BUS.register(presenceMonitor);
        MinecraftForge.EVENT_BUS.register(lifecycleHandlers);
        LOGGER.info(
                "[InstitutionAccess] Runtime initialized (revision={}, facilities={}, terminals={})",
                repository.snapshot().storeRevision(),
                repository.size(),
                repository.terminalCount()
        );
    }

    @Override
    public void shutdown() {
        if (presenceMonitor != null) {
            MinecraftForge.EVENT_BUS.unregister(presenceMonitor);
        }
        if (lifecycleHandlers != null) {
            MinecraftForge.EVENT_BUS.unregister(lifecycleHandlers);
        }
        if (service instanceof DefaultInstitutionAccessService runtimeService) {
            runtimeService.clearContexts();
        }
        presenceMonitor = null;
        lifecycleHandlers = null;
        service = null;
        repository = null;
        boundPlayerData = null;
        boundSubjectRegistry = null;
        boundLand = null;
        boundAudit = null;
        LOGGER.info("[InstitutionAccess] Runtime closed; contexts cleared");
    }

    public InstitutionAccessService service() {
        if (service == null) {
            throw new IllegalStateException(
                    "Institution-access service is not bound"
            );
        }
        return service;
    }

    private LandService requireLand() {
        LandService land = boundLand;
        if (land == null) {
            throw new IllegalStateException(
                    "Land service is not available for institution access"
            );
        }
        return land;
    }

    /**
     * Actor resolution through the authoritative PlayerData and FR-ID subject
     * services (never raw NBT).
     */
    private final class ModuleActorResolver implements ActorResolver {
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
