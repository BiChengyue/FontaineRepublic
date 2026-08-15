package com.fontainerepublic;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.core.module.runtime.ModuleState;
import com.fontainerepublic.core.module.test.TestModule;
import com.fontainerepublic.common.item.FRItems;
import com.fontainerepublic.common.network.NetworkBootstrap;
import com.fontainerepublic.server.command.BankCommand;
import com.fontainerepublic.server.command.CitizenCommand;
import com.fontainerepublic.server.command.CommandBootstrap;
import com.fontainerepublic.server.command.CommunicatorCommand;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.command.MoneyCommand;
import com.fontainerepublic.server.command.LandCommand;
import com.fontainerepublic.server.command.TradeCommand;
import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.command.registration.CommandContributionSpec;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.economy.EconomyModule;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.emergency.EmergencyModule;
import com.fontainerepublic.server.government.GovernmentCommand;
import com.fontainerepublic.server.government.GovernmentModule;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.institution.presentation.InstitutionPresentationSync;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.justice.CourtCommand;
import com.fontainerepublic.server.justice.JusticeModule;
import com.fontainerepublic.server.justice.api.JusticeService;
import com.fontainerepublic.server.login.LoginProvisioningHook;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.landclaim.LandClaimModule;
import com.fontainerepublic.server.landclaim.api.LandClaimService;
import com.fontainerepublic.server.mail.MailModule;
import com.fontainerepublic.server.mail.api.MailService;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.parliament.ParliamentCommand;
import com.fontainerepublic.server.parliament.ParliamentModule;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.trade.TradeModule;
import com.fontainerepublic.server.trade.api.TradeService;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

@Mod(FontaineRepublic.MOD_ID)
public class FontaineRepublic {
    public static final String MOD_ID = "fontainerepublic";
    public static final String MOD_VERSION = "0.1.0-alpha";
    private static final String RUNTIME_VALIDATION_PROPERTY =
            "fontainerepublic.debugValidation";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CoreManager coreManager = new CoreManager(new ModuleRegistry());
    private final NetworkBootstrap networkBootstrap = new NetworkBootstrap(coreManager);
    private final CommandContributionRegistry commandContributionRegistry =
            new CommandContributionRegistry();
    private final CommandBootstrap commandBootstrap = new CommandBootstrap(
            commandContributionRegistry,
            new CommandRuntimeResolver(coreManager)
    );
    private final boolean runtimeValidationEnabled =
            Boolean.getBoolean(RUNTIME_VALIDATION_PROPERTY);
    private final LoginProvisioningHook loginProvisioningHook =
            new LoginProvisioningHook(this::citizenService, this::economyService);
    private volatile InstitutionPresentationSync institutionPresentationSync;

    public FontaineRepublic() {
        LOGGER.info("[FontaineRepublic] Loading");
        @SuppressWarnings("removal") // Forge 1.20.1 API surface (deprecated for removal on newer JDKs)
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        // FR-ITEM-003: re-register the independent custom Message Water Mirror
        // item. The FR client is now required (no-mod-client join is dropped),
        // so the vanilla-clock HMAC carrier (FR-ITEM-002-A) is removed and the
        // ordinary unstackable item is registered here on the common event bus.
        FRItems.ITEMS.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(commandBootstrap::onRegisterCommands);
        // Client-only surface (GUI/HUD/forms): FMLClientSetupEvent fires only
        // on the physical client, so the handler below — and therefore
        // ClientManager and every client/ class it references — is never
        // executed or loaded on a dedicated server (FR-CLIENT-001-A §4.3 side
        // isolation). Forge's DistExecutor.safeRunWhenOn cannot be used here
        // because its safe-referent validation rejects mod-owned client
        // classes; the client-setup listener is the equivalent isolation.
        modEventBus.addListener(this::onClientSetup);
    }

    /**
     * Client-side initialization (FR-CLIENT-001-IMPL-B): invoked only when
     * the physical client fires {@link FMLClientSetupEvent}. The method body
     * reference to {@code ClientManager} resolves lazily, so a dedicated
     * server never loads any {@code client/} class.
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        com.fontainerepublic.client.ClientManager.init();
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ConfigManager.load();
        NetworkRuntimeModule.register(coreManager.moduleRegistry());
        PlayerDataModule.register(coreManager.moduleRegistry());
        AuditModule.register(coreManager.moduleRegistry());
        SubjectRegistryModule.register(coreManager.moduleRegistry());
        CitizenModule.register(coreManager.moduleRegistry());
        LandModule.register(coreManager.moduleRegistry());
        EconomyModule.register(coreManager.moduleRegistry());
        InstitutionAccessModule.register(coreManager.moduleRegistry());
        GovernmentModule.register(coreManager.moduleRegistry());
        ParliamentModule.register(coreManager.moduleRegistry());
        JusticeModule.register(coreManager.moduleRegistry());
        EmergencyModule.register(coreManager.moduleRegistry());
        TradeModule.register(coreManager.moduleRegistry());
        MailModule.register(coreManager.moduleRegistry());
        LandClaimModule.register(coreManager.moduleRegistry());
        if (runtimeValidationEnabled) {
            TestModule.registerAll(coreManager.moduleRegistry());
            LOGGER.warn(
                    "[FontaineRepublic] Runtime validation modules enabled by -D{}=true",
                    RUNTIME_VALIDATION_PROPERTY
            );
        }
        commandContributionRegistry.register(new CommandContributionSpec(
                "money",
                MoneyCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "bank",
                BankCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "citizen",
                CitizenCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "government",
                GovernmentCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "parliament",
                ParliamentCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "court",
                CourtCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "land",
                LandCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "communicator",
                CommunicatorCommand::create
        ));
        commandContributionRegistry.register(new CommandContributionSpec(
                "trade",
                TradeCommand::create
        ));
        event.enqueueWork(() -> {
            commandContributionRegistry.freeze();
            networkBootstrap.registerProductionMessagesAndFreeze();
            coreManager.closeRegistration();
        });
        LOGGER.info("[FontaineRepublic] Core initialized");
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        coreManager.preValidate();
    }

    private void onServerStarting(ServerStartingEvent event) {
        DataManager.init(event.getServer());
        coreManager.startRuntime();
        bindSubjectRegistryPlayerData();
        bindCitizenServices();
        bindLandServices();
        bindInstitutionAccessServices();
        bindEconomyServices();
        bindEmergencyContributions();
        bindGovernmentServices();
        bindParliamentServices();
        bindJusticeServices();
        bindTradeServices();
        bindMailServices();
        bindLandClaimServices();
        if (runtimeValidationEnabled) {
            TestModule.logAvailability(coreManager);
        }
    }

    /**
     * Binds the authoritative PlayerData service to the subject registry after
     * the runtime start (dependency order is guaranteed by module resolution,
     * but the service reference is only resolvable once containers exist).
     */
    private void bindSubjectRegistryPlayerData() {
        PlayerDataService playerData = playerDataService().orElse(null);
        coreManager.getRuntimeContainer(SubjectRegistryModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(SubjectRegistryModule.class::isInstance)
                .map(SubjectRegistryModule.class::cast)
                .ifPresent(module -> module.bindPlayerDataService(playerData));
    }

    /**
     * Binds the authoritative PlayerData and subject-registry services to the
     * citizen module after the runtime start (dependency order is guaranteed
     * by module resolution, but the service references are only resolvable
     * once containers exist).
     */
    private void bindCitizenServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        coreManager.getRuntimeContainer(CitizenModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(CitizenModule.class::isInstance)
                .map(CitizenModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        playerData,
                        subjectRegistry,
                        networkBootstrap.sendService()
                ));
    }

    /**
     * Binds the authoritative PlayerData and subject-registry services to the
     * land module after the runtime start (dependency order is guaranteed by
     * module resolution, but the service references are only resolvable once
     * containers exist).
     */
    private void bindLandServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        coreManager.getRuntimeContainer(LandModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(LandModule.class::isInstance)
                .map(LandModule.class::cast)
                .ifPresent(module -> module.bindServices(playerData, subjectRegistry));
    }

    /**
     * Binds the authoritative PlayerData, subject-registry, institution-access,
     * and audit services to the economy module after the runtime start
     * (dependency order is guaranteed by module resolution, but the service
     * references are only resolvable once containers exist).
     */
    private void bindEconomyServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        InstitutionAccessService institutionAccess =
                institutionAccessService().orElse(null);
        AuditService audit = auditService().orElse(null);
        coreManager.getRuntimeContainer(EconomyModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(EconomyModule.class::isInstance)
                .map(EconomyModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        playerData,
                        subjectRegistry,
                        institutionAccess,
                        audit,
                        networkBootstrap.sendService()
                ));
    }

    /**
     * Registers the Economy emergency-action descriptors into the shared
     * FR-EMG registry, freezes the registry, and binds the Economy receipt
     * provider (FR-EMG-001-A 搂5/搂16; FR-EMG-ECO-001). Registration happens at
     * runtime bind time, immediately before the freeze, so every business
     * descriptor is frozen before any preview can run.
     */
    private void bindEmergencyContributions() {
        coreManager.getRuntimeContainer(EmergencyModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(EmergencyModule.class::isInstance)
                .map(EmergencyModule.class::cast)
                .ifPresent(emergencyModule -> {
                    coreManager.getRuntimeContainer(EconomyModule.MODULE_ID)
                            .flatMap(container -> container.instance())
                            .filter(EconomyModule.class::isInstance)
                            .map(EconomyModule.class::cast)
                            .ifPresent(economyModule -> economyModule.registerEmergencyActions(
                                    emergencyModule.actionRegistry()
                            ));
                    emergencyModule.freezeActionRegistry();
                    coreManager.getRuntimeContainer(EconomyModule.MODULE_ID)
                            .flatMap(container -> container.instance())
                            .filter(EconomyModule.class::isInstance)
                            .map(EconomyModule.class::cast)
                            .ifPresent(economyModule ->
                                    emergencyModule.registerReceiptProvider(
                                            economyModule.emergencyReceiptProvider()
                            ));
                });
    }

    /**
     * Binds the authoritative PlayerData, subject-registry, land, and audit
     * services to the institution-access module after the runtime start
     * (dependency order is guaranteed by module resolution, but the service
     * references are only resolvable once containers exist).
     */
    private void bindInstitutionAccessServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        LandService land = landService().orElse(null);
        AuditService audit = auditService().orElse(null);
        coreManager.getRuntimeContainer(InstitutionAccessModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(InstitutionAccessModule.class::isInstance)
                .map(InstitutionAccessModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        playerData,
                        subjectRegistry,
                        land,
                        audit
                ));
    }

    /**
     * Binds the authoritative PlayerData, subject-registry, institution-access,
     * and audit services to the government module after the runtime start
     * (dependency order is guaranteed by module resolution, but the service
     * references are only resolvable once containers exist).
     */
    private void bindGovernmentServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        InstitutionAccessService institutionAccess =
                institutionAccessService().orElse(null);
        AuditService audit = auditService().orElse(null);
        coreManager.getRuntimeContainer(GovernmentModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(GovernmentModule.class::isInstance)
                .map(GovernmentModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        playerData,
                        subjectRegistry,
                        institutionAccess,
                        audit
                ));
    }

    /**
     * Binds the authoritative PlayerData, citizen, institution-access, audit,
     * and subject-registry services to the parliament module after the
     * runtime start (dependency order is guaranteed by module resolution, but
     * the service references are only resolvable once containers exist).
     */
    private void bindParliamentServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        CitizenService citizen = citizenService().orElse(null);
        InstitutionAccessService institutionAccess =
                institutionAccessService().orElse(null);
        AuditService audit = auditService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        coreManager.getRuntimeContainer(ParliamentModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(ParliamentModule.class::isInstance)
                .map(ParliamentModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        playerData,
                        citizen,
                        institutionAccess,
                        audit,
                        subjectRegistry
                ));
    }

    /**
     * Binds the authoritative PlayerData, citizen, institution-access, and
     * audit services to the justice module after the runtime start
     * (dependency order is guaranteed by module resolution, but the service
     * references are only resolvable once containers exist).
     */
    private void bindJusticeServices() {
        PlayerDataService playerData = playerDataService().orElse(null);
        CitizenService citizen = citizenService().orElse(null);
        InstitutionAccessService institutionAccess =
                institutionAccessService().orElse(null);
        AuditService audit = auditService().orElse(null);
        coreManager.getRuntimeContainer(JusticeModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(JusticeModule.class::isInstance)
                .map(JusticeModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        playerData,
                        citizen,
                        institutionAccess,
                        audit
                ));
    }

    private java.util.Optional<LandService> landService() {
        return coreManager.getRuntimeContainer(LandModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(LandModule.class::isInstance)
                .map(LandModule.class::cast)
                .map(LandModule::service);
    }

    private java.util.Optional<AuditService> auditService() {
        return coreManager.getRuntimeContainer(AuditModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(AuditModule.class::isInstance)
                .map(AuditModule.class::cast)
                .map(AuditModule::service);
    }

    private java.util.Optional<InstitutionAccessService> institutionAccessService() {
        return coreManager.getRuntimeContainer(InstitutionAccessModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(InstitutionAccessModule.class::isInstance)
                .map(InstitutionAccessModule.class::cast)
                .map(InstitutionAccessModule::service);
    }

    private java.util.Optional<SubjectRegistryService> subjectRegistryService() {
        return coreManager.getRuntimeContainer(SubjectRegistryModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(SubjectRegistryModule.class::isInstance)
                .map(SubjectRegistryModule.class::cast)
                .map(SubjectRegistryModule::service);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        // FR-TRADE-001: drop every live trade session before the durable
        // data manager shuts down — the intent model escrows nothing, so
        // cancelling is a pure in-memory discard (no refunds needed).
        tradeService().ifPresent(TradeService::shutdown);
        DataManager.beginShutdown();
        DataManager.saveAll();
        coreManager.stopRuntime();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        coreManager.closeRuntime();
        institutionPresentationSync = null;
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        playerDataService().ifPresentOrElse(
                service -> {
                    try {
                        service.recordLogin(
                                player.getUUID(),
                                player.getGameProfile().getName()
                        );
                        loginProvisioningHook.provision(
                                player.getUUID(),
                                player.getGameProfile().getName()
                        );
                        institutionPresentationSync().sync(player.getUUID());
                        // FR-MAIL-001-A: push the mailbox sync + unread alert
                        // at login (no-client parity).
                        mailService().ifPresent(mail -> mail.requestSync(
                                player.getUUID()
                        ));
                    } catch (RuntimeException failed) {
                        LOGGER.error(
                                "[FontaineRepublic] PlayerData login failed for {} ({}): {}",
                                player.getGameProfile().getName(),
                                player.getUUID(),
                                failed.getMessage(),
                                failed
                        );
                    }
                },
                () -> LOGGER.warn(
                        "[FontaineRepublic] PlayerData service is not available at login for {} ({})",
                        player.getGameProfile().getName(),
                        player.getUUID()
                )
        );
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        playerDataService().ifPresentOrElse(
                service -> {
                    try {
                        service.recordLogout(player.getUUID());
                    } catch (RuntimeException failed) {
                        LOGGER.error(
                                "[FontaineRepublic] PlayerData logout failed for {} ({}): {}",
                                player.getGameProfile().getName(),
                                player.getUUID(),
                                failed.getMessage(),
                                failed
                        );
                    }
                },
                () -> LOGGER.warn(
                        "[FontaineRepublic] PlayerData service is not available at logout for {} ({})",
                        player.getGameProfile().getName(),
                        player.getUUID()
                )
        );
        // FR-TRADE-001: cancel every trade session of the departing player.
        tradeService().ifPresent(service -> {
            try {
                service.playerDisconnected(player.getUUID());
            } catch (RuntimeException failed) {
                LOGGER.warn(
                        "[FontaineRepublic] Trade logout cancellation failed for {} ({}): {}",
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        failed.getMessage()
                );
            }
        });
    }

    private java.util.Optional<PlayerDataService> playerDataService() {
        return coreManager.getRuntimeContainer(PlayerDataModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(PlayerDataModule.class::isInstance)
                .map(PlayerDataModule.class::cast)
                .map(PlayerDataModule::service);
    }

    private java.util.Optional<CitizenService> citizenService() {
        return coreManager.getRuntimeContainer(CitizenModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(CitizenModule.class::isInstance)
                .map(CitizenModule.class::cast)
                .map(CitizenModule::service);
    }

    private java.util.Optional<EconomyService> economyService() {
        return coreManager.getRuntimeContainer(EconomyModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(EconomyModule.class::isInstance)
                .map(EconomyModule.class::cast)
                .map(EconomyModule::service);
    }

    private java.util.Optional<GovernmentService> governmentService() {
        return coreManager.getRuntimeContainer(GovernmentModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(GovernmentModule.class::isInstance)
                .map(GovernmentModule.class::cast)
                .map(GovernmentModule::service);
    }

    private java.util.Optional<ParliamentService> parliamentService() {
        return coreManager.getRuntimeContainer(ParliamentModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(ParliamentModule.class::isInstance)
                .map(ParliamentModule.class::cast)
                .map(ParliamentModule::service);
    }

    private java.util.Optional<JusticeService> justiceService() {
        return coreManager.getRuntimeContainer(JusticeModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(JusticeModule.class::isInstance)
                .map(JusticeModule.class::cast)
                .map(JusticeModule::service);
    }

    /**
     * Binds the authoritative economy and network send services to the trade
     * module after the runtime start (dependency order is guaranteed by
     * module resolution, but the service references are only resolvable once
     * containers exist).
     */
    private void bindTradeServices() {
        EconomyService economy = economyService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        AuditService audit = auditService().orElse(null);
        coreManager.getRuntimeContainer(TradeModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(TradeModule.class::isInstance)
                .map(TradeModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        economy,
                        subjectRegistry,
                        audit,
                        networkBootstrap.sendService()
                ));
    }

    private java.util.Optional<TradeService> tradeService() {
        return coreManager.getRuntimeContainer(TradeModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(TradeModule.class::isInstance)
                .map(TradeModule.class::cast)
                .map(TradeModule::service);
    }

    /**
     * Binds the authoritative economy, subject-registry, player-directory,
     * citizen and government services to the mail module after the runtime
     * start (dependency order guaranteed by module resolution).
     */
    private void bindMailServices() {
        EconomyService economy = economyService().orElse(null);
        SubjectRegistryService subjectRegistry = subjectRegistryService().orElse(null);
        var playerDirectory = coreManager.getRuntimeContainer(PlayerDataModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(PlayerDataModule.class::isInstance)
                .map(PlayerDataModule.class::cast)
                .map(PlayerDataModule::directoryService)
                .orElse(null);
        CitizenService citizen = citizenService().orElse(null);
        GovernmentService government = governmentService().orElse(null);
        coreManager.getRuntimeContainer(MailModule.MODULE_ID)
                .flatMap(container -> container.instance())
                .filter(MailModule.class::isInstance)
                .map(MailModule.class::cast)
                .ifPresent(module -> module.bindServices(
                        economy,
                        subjectRegistry,
                        playerDirectory,
                        citizen,
                        government,
                        networkBootstrap.sendService()
                ));
    }

    private java.util.Optional<MailService> mailService() {
        return coreManager.getRuntimeContainer(MailModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(MailModule.class::isInstance)
                .map(MailModule.class::cast)
                .map(MailModule::service);
    }

    /**
     * Binds the authoritative land service to the land-claim module after the
     * runtime start (dependency order guaranteed by module resolution, but the
     * service reference is only resolvable once containers exist).
     *
     * <p>FR-LAND-CLAIM-001-FIX-01 F5: if the hard land dependency is
     * unexpectedly unavailable, the module fails closed — one explicit warning
     * is logged, {@code LandClaimRuntime} stays unbound and command/C2S
     * resolution returns empty instead of a startup NPE.</p>
     */
    private void bindLandClaimServices() {
        landService().ifPresent(land ->
                coreManager.getRuntimeContainer(LandClaimModule.MODULE_ID)
                        .flatMap(container -> container.instance())
                        .filter(LandClaimModule.class::isInstance)
                        .map(LandClaimModule.class::cast)
                        .ifPresent(module -> module.bindServices(land))
        );
    }

    private java.util.Optional<LandClaimService> landClaimService() {
        return coreManager.getRuntimeContainer(LandClaimModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(LandClaimModule.class::isInstance)
                .map(LandClaimModule.class::cast)
                .map(LandClaimModule::service);
    }

    /**
     * Lazily builds the login snapshot sender of the government/parliament/
     * court/land public summaries (FR-CLIENT-001-IMPL-B3a/B3b). Built on first
     * login: the send service is only available after the message-table
     * freeze, and the institution service suppliers are resolved per
     * invocation so the sync always observes the current ACTIVE runtime.
     */
    private InstitutionPresentationSync institutionPresentationSync() {
        InstitutionPresentationSync sync = institutionPresentationSync;
        if (sync == null) {
            synchronized (this) {
                sync = institutionPresentationSync;
                if (sync == null) {
                    sync = new InstitutionPresentationSync(
                            networkBootstrap.sendService(),
                            this::governmentService,
                            this::parliamentService,
                            this::justiceService,
                            this::landService,
                            System::currentTimeMillis,
                            FontaineRepublic::onlineServerPlayer
                    );
                    institutionPresentationSync = sync;
                }
            }
        }
        return sync;
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
}
