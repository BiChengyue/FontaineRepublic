package com.fontainerepublic.server.economy;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.economy.api.CurrencyPresentation;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.SubjectDirectory;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyNbtCodec;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.service.DefaultEconomyService;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure module binding the economy Phase 1 player services plus the
 * on-site official Central-Bank duties to one server runtime (FR-ECO-001-A
 * §8, FR-ECO-001-C, FR-ECO-002-A).
 *
 * <p>Depends on {@code player-data} and {@code subject-registry} (the ordered
 * {@code PlayerData -> subject -> account} chain must be ready before
 * provisioning), {@code audit} (authoritative-mutation recording of official
 * duties), and {@code institution-access} (the mandatory on-site boundary for
 * the official bank surface); it registers after the FR-CORE-002 durable
 * commit gate so every economy mutation is acknowledged. The module never
 * implements cash/ATM/interest/tax/market, GUI, or authoritative client
 * packets; the emergency {@code economy.issue} / {@code economy.reclaim}
 * catalogue remains blocked on the FR-EMG gates.</p>
 */
public final class EconomyModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("economy");
    private static final Logger LOGGER = LogUtils.getLogger();

    private EconomyRepository repository;
    private EconomyService service;
    private volatile PlayerDataService boundPlayerData;
    private volatile SubjectRegistryService boundSubjectRegistry;
    private volatile InstitutionAccessService boundInstitutionAccess;
    private volatile AuditService boundAudit;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Economy",
                        "1.0.0",
                        Optional.of("Phase 1 economy player services (SubjectId accounts)"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID
                ),
                Set.of(),
                60,
                EconomyModule::new
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
        repository = EconomyRepository.createProduction(new EconomyNbtCodec());
        LOGGER.info(
                "[Economy] Store loaded (revision={}, accounts={}, nextTransactionId={})",
                repository.storeRevision(),
                repository.size(),
                repository.nextTransactionId()
        );
    }

    /**
     * Binds the authoritative services after the runtime start and builds the
     * runtime service. Until bound, ensure calls fail closed with
     * {@code PLAYER_DATA_UNAVAILABLE} / {@code SUBJECT_REGISTRY_UNAVAILABLE};
     * official duties additionally require the institution-access boundary.
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
        this.service = new DefaultEconomyService(
                repository,
                System::currentTimeMillis,
                new ModulePlayerPresence(),
                new ModuleSubjectDirectory(),
                productionLimits(),
                new CurrencyPresentation(
                        ConfigManager.economyCurrencyDisplayName(),
                        ConfigManager.economyCurrencySymbol(),
                        ConfigManager.economyCurrencyGrouping()
                ),
                requireInstitutionAccess(),
                boundAudit
        );
        LOGGER.info(
                "[Economy] Runtime initialized (revision={}, accounts={}, nextTransactionId={})",
                repository.storeRevision(),
                repository.size(),
                repository.nextTransactionId()
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
        LOGGER.info("[Economy] Runtime closed");
    }

    public EconomyService service() {
        if (service == null) {
            throw new IllegalStateException("Economy service is not active");
        }
        return service;
    }

    private InstitutionAccessService requireInstitutionAccess() {
        InstitutionAccessService bound = boundInstitutionAccess;
        if (bound == null) {
            throw new IllegalStateException(
                    "Institution-access service is not available for economy"
            );
        }
        return bound;
    }

    private static EconomyLimits productionLimits() {
        return new EconomyLimits(
                10_000,
                100_000,
                128,
                EconomyLimits.DEFAULT_MAX_BALANCE,
                100,
                50,
                8 * 1024 * 1024,
                ConfigManager.economyTransferCooldownMillis()
        );
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

        @Override
        public Optional<SubjectStatus> status(SubjectId subjectId) {
            SubjectRegistryService bound = boundSubjectRegistry;
            if (bound == null) {
                return Optional.empty();
            }
            return bound.status(subjectId);
        }
    }
}
