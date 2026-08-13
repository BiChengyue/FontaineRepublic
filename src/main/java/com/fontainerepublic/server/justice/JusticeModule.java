package com.fontainerepublic.server.justice;

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
import com.fontainerepublic.server.justice.api.JusticeService;
import com.fontainerepublic.server.justice.persistence.JusticeIdSource;
import com.fontainerepublic.server.justice.persistence.JusticeNbtCodec;
import com.fontainerepublic.server.justice.persistence.JusticeRepository;
import com.fontainerepublic.server.justice.service.DefaultJusticeService;
import com.fontainerepublic.server.justice.service.JusticeCitizenDirectory;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding the judicial pipeline — cases, evidence, and
 * verdicts — to one server runtime (FR-JUS-001-A).
 *
 * <p>Depends on {@code player-data} (actor resolution), {@code citizen}
 * (standing), {@code audit} (authoritative-mutation recording),
 * {@code institution-access} (the mandatory on-site boundary: public service
 * for filing/evidence/review-request, official duty for acceptance,
 * advancement, admissibility rulings, verdicts, and review decisions), and
 * {@code land} (the violation-report intake value object). It never executes
 * a verdict (a ruling never mutates another module's state), never implements
 * verdict execution, constitutional review, or the AI oracle, and never maps
 * a rank or office to a technical permission.</p>
 */
public final class JusticeModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("justice");
    private static final Logger LOGGER = LogUtils.getLogger();

    private JusticeRepository repository;
    private JusticeService service;
    private volatile PlayerDataService boundPlayerData;
    private volatile CitizenService boundCitizen;
    private volatile InstitutionAccessService boundInstitutionAccess;
    private volatile AuditService boundAudit;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Justice",
                        "1.0.0",
                        Optional.of("Judicial pipeline: cases, evidence, verdicts, review"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID,
                        LandModule.MODULE_ID
                ),
                Set.of(),
                95,
                JusticeModule::new
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
        repository = JusticeRepository.createProduction(
                new JusticeNbtCodec()
        );
        LOGGER.info(
                "[Justice] Store loaded (revision={}, cases={}, evidence={}, "
                        + "verdicts={}, transitions={})",
                repository.snapshot().storeRevision(),
                repository.snapshot().cases().size(),
                repository.snapshot().evidence().size(),
                repository.snapshot().verdicts().size(),
                repository.snapshot().transitions().size()
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
            AuditService auditService
    ) {
        this.boundPlayerData = playerDataService;
        this.boundCitizen = citizenService;
        this.boundInstitutionAccess = institutionAccessService;
        this.boundAudit = auditService;
        this.service = new DefaultJusticeService(
                repository,
                System::currentTimeMillis,
                new RandomJusticeIdSource(),
                new ModuleCitizenDirectory(),
                requireInstitutionAccess(),
                boundAudit
        );
        LOGGER.info(
                "[Justice] Runtime initialized (revision={}, cases={}, evidence={}, "
                        + "verdicts={})",
                repository.snapshot().storeRevision(),
                repository.snapshot().cases().size(),
                repository.snapshot().evidence().size(),
                repository.snapshot().verdicts().size()
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
        LOGGER.info("[Justice] Runtime closed");
    }

    public JusticeService service() {
        if (service == null) {
            throw new IllegalStateException(
                    "Justice service is not bound"
            );
        }
        return service;
    }

    private InstitutionAccessService requireInstitutionAccess() {
        InstitutionAccessService bound = boundInstitutionAccess;
        if (bound == null) {
            throw new IllegalStateException(
                    "Institution-access service is not available for justice"
            );
        }
        return bound;
    }

    /**
     * Actor/standing resolution through the authoritative PlayerData and FR-CIT
     * services (never raw NBT, never a game name).
     */
    private final class ModuleCitizenDirectory implements JusticeCitizenDirectory {
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

    /** Production id source: random UUIDs, never client-selected. */
    private static final class RandomJusticeIdSource implements JusticeIdSource {
        @Override
        public UUID nextUuid() {
            return UUID.randomUUID();
        }
    }
}
