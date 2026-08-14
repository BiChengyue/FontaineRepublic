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
import com.fontainerepublic.server.economy.emergency.EconomyEmergencyProvider;
import com.fontainerepublic.server.economy.emergency.EconomyEmergencyProviders;
import com.fontainerepublic.server.economy.emergency.EconomyEmergencyReceiptProvider;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyNbtCodec;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.presentation.EconomyPresentationNotifier;
import com.fontainerepublic.server.economy.presentation.PresentationAwareEconomyService;
import com.fontainerepublic.server.economy.presentation.ServerEconomyPresentationNotifier;
import com.fontainerepublic.server.economy.service.DefaultEconomyService;
import com.fontainerepublic.server.emergency.api.EmergencyActionDescriptor;
import com.fontainerepublic.server.emergency.api.EmergencyActionProvider;
import com.fontainerepublic.server.emergency.api.EmergencyActionRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyMutationEnvelope;
import com.fontainerepublic.server.emergency.api.EmergencyMutationResult;
import com.fontainerepublic.server.emergency.api.EmergencyPlan;
import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
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
    private volatile EconomyEmergencyProvider emergencyProvider;
    private volatile EconomyEmergencyReceiptProvider receiptProvider;
    private volatile PlayerDataService boundPlayerData;
    private volatile SubjectRegistryService boundSubjectRegistry;
    private volatile InstitutionAccessService boundInstitutionAccess;
    private volatile AuditService boundAudit;
    private volatile NetworkSendService boundSendService;
    private volatile EconomyPresentationNotifier presentationNotifier;

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
                        InstitutionAccessModule.MODULE_ID,
                        NetworkRuntimeModule.MODULE_ID
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
            AuditService auditService,
            NetworkSendService sendService
    ) {
        this.boundPlayerData = playerDataService;
        this.boundSubjectRegistry = subjectRegistryService;
        this.boundInstitutionAccess = institutionAccessService;
        this.boundAudit = auditService;
        this.boundSendService = sendService;
        this.presentationNotifier = new ServerEconomyPresentationNotifier(
                sendService,
                subjectRegistryService,
                System::currentTimeMillis,
                new CurrencyPresentation(
                        ConfigManager.economyCurrencyDisplayName(),
                        ConfigManager.economyCurrencySymbol(),
                        ConfigManager.economyCurrencyGrouping()
                ),
                EconomyModule::onlineServerPlayer
        );
        this.emergencyProvider = new EconomyEmergencyProvider(
                repository,
                EconomyEmergencyProvider.PROVIDER_IDENTITY_ISSUE,
                this::resolveEmergencyTarget
        );
        this.receiptProvider = new EconomyEmergencyReceiptProvider(repository);
        EconomyEmergencyProviders.bind(
                wrapEmergency(emergencyProvider),
                wrapEmergency(new EconomyEmergencyProvider(
                        repository,
                        EconomyEmergencyProvider.PROVIDER_IDENTITY_RECLAIM,
                        this::resolveEmergencyTarget
                ))
        );
        EconomyService baseService = new DefaultEconomyService(
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
        this.service = new PresentationAwareEconomyService(
                baseService,
                presentationNotifier
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
        EconomyEmergencyProviders.unbind();
        emergencyProvider = null;
        receiptProvider = null;
        presentationNotifier = null;
        repository = null;
        boundPlayerData = null;
        boundSubjectRegistry = null;
        boundInstitutionAccess = null;
        boundAudit = null;
        boundSendService = null;
        LOGGER.info("[Economy] Runtime closed");
    }

    public EconomyService service() {
        if (service == null) {
            throw new IllegalStateException("Economy service is not active");
        }
        return service;
    }

    /**
     * Registers the two Economy emergency-action descriptors
     * ({@code economy.issue}, {@code economy.reclaim}) into the shared FR-EMG
     * registry before it is frozen (FR-EMG-001-A 搂5). The descriptors hold
     * only immutable metadata and the stateless runtime resolver; they never
     * capture this module or the provider.
     */
    public void registerEmergencyActions(EmergencyActionRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(new EmergencyActionDescriptor(
                "economy",
                EconomyEmergencyProvider.ACTION_ISSUE,
                "1.0.0",
                EmergencyTargetType.PLAYER_UUID,
                Set.of(
                        EmergencyCategory.DEBUG,
                        EmergencyCategory.CORRECTION,
                        EmergencyCategory.COMPENSATION,
                        EmergencyCategory.DISASTER_RELIEF,
                        EmergencyCategory.EMERGENCY_RESPONSE
                ),
                Set.of(EconomyEmergencyProvider.PARAM_AMOUNT),
                EconomyEmergencyProvider.PROVIDER_IDENTITY_ISSUE,
                EconomyEmergencyProvider.PROVIDER_VERSION,
                true,
                EconomyEmergencyProviders::resolveIssue
        ));
        registry.register(new EmergencyActionDescriptor(
                "economy",
                EconomyEmergencyProvider.ACTION_RECLAIM,
                "1.0.0",
                EmergencyTargetType.PLAYER_UUID,
                Set.of(
                        EmergencyCategory.DEBUG,
                        EmergencyCategory.CORRECTION,
                        EmergencyCategory.COMPENSATION,
                        EmergencyCategory.DISASTER_RELIEF,
                        EmergencyCategory.EMERGENCY_RESPONSE
                ),
                Set.of(EconomyEmergencyProvider.PARAM_AMOUNT),
                EconomyEmergencyProvider.PROVIDER_IDENTITY_RECLAIM,
                EconomyEmergencyProvider.PROVIDER_VERSION,
                true,
                EconomyEmergencyProviders::resolveReclaim
        ));
    }

    /** Read-only receipt provider for FR-EMG reconciliation. */
    public EconomyEmergencyReceiptProvider emergencyReceiptProvider() {
        EconomyEmergencyReceiptProvider bound = receiptProvider;
        if (bound == null) {
            throw new IllegalStateException(
                    "Economy receipt provider is not active"
            );
        }
        return bound;
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

    /**
     * Wraps an emergency provider so a successful applied mutation refreshes
     * the target player's balance presentation (FR-CLIENT-001-A §3.3). The
     * wrapper never alters the provider's own result; presentation failures
     * are swallowed for no-client parity.
     */
    private EmergencyActionProvider wrapEmergency(EconomyEmergencyProvider delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new EmergencyActionProvider() {
            @Override
            public String providerIdentity() {
                return delegate.providerIdentity();
            }

            @Override
            public String providerVersion() {
                return delegate.providerVersion();
            }

            @Override
            public EmergencyPlan preview(EmergencyMutationEnvelope envelope) {
                return delegate.preview(envelope);
            }

            @Override
            public EmergencyMutationResult apply(
                    EmergencyPlan plan,
                    EmergencyMutationEnvelope envelope
            ) {
                EmergencyMutationResult result = delegate.apply(plan, envelope);
                if (result.applied()) {
                    notifyEmergencyBalance(envelope);
                }
                return result;
            }
        };
    }

    private void notifyEmergencyBalance(EmergencyMutationEnvelope envelope) {
        try {
            SubjectId target = SubjectId.of(UUID.fromString(envelope.targetId()));
            EconomyPresentationNotifier notifier = presentationNotifier;
            if (notifier == null) {
                return;
            }
            repository.findAccount(target).ifPresent(
                    account -> notifier.balanceChanged(target, account)
            );
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Emergency presentation sync failed: {}",
                    failure.getMessage()
            );
        }
    }

    /** Production online-player resolution via the current server. */
    private static java.util.Optional<ServerPlayer> onlineServerPlayer(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(
                server.getPlayerList().getPlayer(playerId)
        );
    }

    /**
     * Resolves a target player UUID to its authoritative natural-person
     * {@link SubjectId} (FR-ECO-001-C-ACCOUNT-ALIGN-01): emergency actions
     * must credit/debit the account keyed by the player's real subject, never
     * a synthesized {@code SubjectId.of(playerUuid)}.
     */
    private Optional<SubjectId> resolveEmergencyTarget(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        SubjectRegistryService bound = boundSubjectRegistry;
        if (bound == null) {
            return Optional.empty();
        }
        return bound.findSubjectForPlayer(playerId)
                .map(SubjectRecord::subjectId);
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
