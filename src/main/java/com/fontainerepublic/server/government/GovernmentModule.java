package com.fontainerepublic.server.government;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.government.persistence.GovernmentNbtCodec;
import com.fontainerepublic.server.government.persistence.GovernmentRepository;
import com.fontainerepublic.server.government.service.DefaultGovernmentService;
import com.fontainerepublic.server.government.service.HolderDirectory;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding ministries, government positions, and offices
 * to one server runtime (FR-GOV-001-A).
 *
 * <p>Depends on {@code player-data} and {@code subject-registry} (holder and
 * actor resolution), {@code audit} (authoritative-mutation recording), and
 * {@code institution-access} (the mandatory {@code ONSITE_OFFICIAL_DUTY}
 * on-site boundary for appoint/dismiss). It never implements legislative,
 * judicial, or fiscal authority, GUI, packets, or any rank/office-to-
 * permission mapping — political office is never a technical permission.</p>
 */
public final class GovernmentModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("government");
    private static final Logger LOGGER = LogUtils.getLogger();

    private GovernmentRepository repository;
    private GovernmentService service;
    private volatile PlayerDataService boundPlayerData;
    private volatile SubjectRegistryService boundSubjectRegistry;
    private volatile InstitutionAccessService boundInstitutionAccess;
    private volatile AuditService boundAudit;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Government",
                        "1.0.0",
                        Optional.of("Ministries, government positions, and official appointments"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID
                ),
                Set.of(),
                80,
                GovernmentModule::new
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
        repository = GovernmentRepository.createProduction(
                new GovernmentNbtCodec()
        );
        LOGGER.info(
                "[Government] Store loaded (revision={}, ministries={}, positions={}, offices={})",
                repository.snapshot().storeRevision(),
                repository.ministryCount(),
                repository.positionCount(),
                repository.officeCount()
        );
    }

    /**
     * Binds the authoritative services after the runtime start and builds the
     * runtime service. Until bound, every actor/holder resolution fails closed
     * with {@code HOLDER_DIRECTORY_UNAVAILABLE}.
     */
    public void bindServices(
            PlayerDataService playerDataService,
            SubjectRegistryService subjectRegistryService,
            InstitutionAccessService institutionAccessService,
            AuditService auditService
    ) {
        this.boundPlayerData = playerDataService;
        this.boundSubjectRegistry = subjectRegistryService;
        this.boundInstitutionAccess = institutionAccessService;
        this.boundAudit = auditService;
        this.service = new DefaultGovernmentService(
                repository,
                System::currentTimeMillis,
                new ModuleHolderDirectory(),
                requireInstitutionAccess(),
                boundAudit
        );
        LOGGER.info(
                "[Government] Runtime initialized (revision={}, ministries={}, positions={}, offices={})",
                repository.snapshot().storeRevision(),
                repository.ministryCount(),
                repository.positionCount(),
                repository.officeCount()
        );
    }

    @Override
    public void shutdown() {
        service = null;
        repository = null;
        boundPlayerData = null;
        boundSubjectRegistry = null;
        boundInstitutionAccess = null;
        boundAudit = null;
        LOGGER.info("[Government] Runtime closed");
    }

    public GovernmentService service() {
        if (service == null) {
            throw new IllegalStateException(
                    "Government service is not bound"
            );
        }
        return service;
    }

    private InstitutionAccessService requireInstitutionAccess() {
        InstitutionAccessService bound = boundInstitutionAccess;
        if (bound == null) {
            throw new IllegalStateException(
                    "Institution-access service is not available for government"
            );
        }
        return bound;
    }

    /**
     * Actor/holder resolution through the authoritative PlayerData and FR-ID
     * subject services (never raw NBT, never a game name).
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
