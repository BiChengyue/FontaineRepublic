package com.fontainerepublic.server.parliament;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.persistence.ParliamentIdSource;
import com.fontainerepublic.server.parliament.persistence.ParliamentNbtCodec;
import com.fontainerepublic.server.parliament.persistence.ParliamentRepository;
import com.fontainerepublic.server.parliament.service.DefaultParliamentService;
import com.fontainerepublic.server.parliament.service.GuardianDirectory;
import com.fontainerepublic.server.parliament.service.ParliamentCitizenDirectory;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding the legislative pipeline — proposals,
 * ballots, and bills — to one server runtime (FR-PAR-001-A).
 *
 * <p>Depends on {@code player-data} (actor resolution), {@code citizen}
 * (voting eligibility), {@code audit} (authoritative-mutation recording), and
 * {@code institution-access} (the mandatory {@code ONSITE_OFFICIAL_DUTY}
 * on-site boundary for every legislative mutation). It never executes laws
 * (a passed bill never mutates another module's state), never implements the
 * guardian review, referendums, or the constitution-amendment pipeline, and
 * never maps a rank or office to a technical permission.</p>
 */
public final class ParliamentModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("parliament");
    private static final Logger LOGGER = LogUtils.getLogger();

    private ParliamentRepository repository;
    private ParliamentService service;
    private volatile PlayerDataService boundPlayerData;
    private volatile CitizenService boundCitizen;
    private volatile InstitutionAccessService boundInstitutionAccess;
    private volatile AuditService boundAudit;
    private volatile SubjectRegistryService boundSubjectRegistry;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Parliament",
                        "1.0.0",
                        Optional.of("Legislative pipeline: proposals, ballots, and bills"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID
                ),
                Set.of(),
                90,
                ParliamentModule::new
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
        repository = ParliamentRepository.createProduction(
                new ParliamentNbtCodec()
        );
        LOGGER.info(
                "[Parliament] Store loaded (revision={}, proposals={}, votes={}, "
                        + "bills={}, transitions={}, roster={})",
                repository.snapshot().storeRevision(),
                repository.snapshot().proposals().size(),
                repository.snapshot().votes().size(),
                repository.snapshot().bills().size(),
                repository.snapshot().transitions().size(),
                repository.snapshot().citizenRoster().size()
        );
    }

    /**
     * Binds the authoritative services after the runtime start and builds the
     * runtime service. Until bound, every actor/citizen resolution fails
     * closed with {@code CITIZEN_DIRECTORY_UNAVAILABLE}.
     */
    public void bindServices(
            PlayerDataService playerDataService,
            CitizenService citizenService,
            InstitutionAccessService institutionAccessService,
            AuditService auditService,
            SubjectRegistryService subjectRegistryService
    ) {
        this.boundPlayerData = playerDataService;
        this.boundCitizen = citizenService;
        this.boundInstitutionAccess = institutionAccessService;
        this.boundAudit = auditService;
        this.boundSubjectRegistry = subjectRegistryService;
        this.service = new DefaultParliamentService(
                repository,
                System::currentTimeMillis,
                new RandomParliamentIdSource(),
                new ModuleCitizenDirectory(),
                requireInstitutionAccess(),
                new ModuleGuardianDirectory(),
                boundAudit
        );
        LOGGER.info(
                "[Parliament] Runtime initialized (revision={}, proposals={}, "
                        + "votes={}, bills={}, roster={})",
                repository.snapshot().storeRevision(),
                repository.snapshot().proposals().size(),
                repository.snapshot().votes().size(),
                repository.snapshot().bills().size(),
                repository.snapshot().citizenRoster().size()
        );
    }

    @Override
    public void shutdown() {
        service = null;
        repository = null;
        boundPlayerData = null;
        boundCitizen = null;
        boundInstitutionAccess = null;
        boundAudit = null;
        boundSubjectRegistry = null;
        LOGGER.info("[Parliament] Runtime closed");
    }

    public ParliamentService service() {
        if (service == null) {
            throw new IllegalStateException(
                    "Parliament service is not bound"
            );
        }
        return service;
    }

    private InstitutionAccessService requireInstitutionAccess() {
        InstitutionAccessService bound = boundInstitutionAccess;
        if (bound == null) {
            throw new IllegalStateException(
                    "Institution-access service is not available for parliament"
            );
        }
        return bound;
    }

    /**
     * Actor/citizen resolution through the authoritative PlayerData and FR-CIT
     * services (never raw NBT, never a game name).
     */
    private final class ModuleCitizenDirectory implements ParliamentCitizenDirectory {
        @Override
        public boolean isAvailable() {
            return boundPlayerData != null && boundCitizen != null;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            PlayerDataService bound = boundPlayerData;
            return bound != null && bound.find(playerId).isPresent();
        }

        @Override
        public boolean isActiveCitizen(UUID playerId) {
            CitizenService bound = boundCitizen;
            if (bound == null) {
                return false;
            }
            return bound.getCitizen(playerId)
                    .map(record -> record.status() == CitizenStatus.CITIZEN)
                    .orElse(false);
        }
    }

    /**
     * Hydro Archon identity resolution through the FR-ID subject registry
     * (FR-PAR-002 task §3.3): the water god is the active natural person
     * holding the fixed original personal registry number
     * ({@code 10-000001-61}). Fail closed while unbound.
     */
    private final class ModuleGuardianDirectory implements GuardianDirectory {
        @Override
        public boolean isAvailable() {
            return boundSubjectRegistry != null;
        }

        @Override
        public boolean isHydroArchon(UUID playerId) {
            SubjectRegistryService bound = boundSubjectRegistry;
            if (bound == null) {
                return false;
            }
            return bound.findSubjectForPlayer(playerId)
                    .filter(record -> record.registryNumber()
                            .equals(RegistryNumber.FIXED_PERSONAL))
                    .filter(record -> record.status() == SubjectStatus.ACTIVE)
                    .map(SubjectRecord::subjectId)
                    .flatMap(bound::status)
                    .filter(status -> status == SubjectStatus.ACTIVE)
                    .isPresent();
        }
    }

    /** Production id source: random UUIDs, never client-selected. */
    private static final class RandomParliamentIdSource implements ParliamentIdSource {
        @Override
        public UUID nextUuid() {
            return UUID.randomUUID();
        }
    }
}
