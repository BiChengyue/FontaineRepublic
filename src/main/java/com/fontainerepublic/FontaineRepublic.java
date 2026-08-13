package com.fontainerepublic;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.core.module.runtime.ModuleState;
import com.fontainerepublic.core.module.test.TestModule;
import com.fontainerepublic.common.network.NetworkBootstrap;
import com.fontainerepublic.server.command.BankCommand;
import com.fontainerepublic.server.command.CitizenCommand;
import com.fontainerepublic.server.command.CommandBootstrap;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.command.MoneyCommand;
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
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.justice.CourtCommand;
import com.fontainerepublic.server.justice.JusticeModule;
import com.fontainerepublic.server.login.LoginProvisioningHook;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.parliament.ParliamentCommand;
import com.fontainerepublic.server.parliament.ParliamentModule;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

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

    public FontaineRepublic() {
        LOGGER.info("[FontaineRepublic] Loading");
        @SuppressWarnings("removal") // Forge 1.20.1 API surface (deprecated for removal on newer JDKs)
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(commandBootstrap::onRegisterCommands);
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
        bindGovernmentServices();
        bindParliamentServices();
        bindJusticeServices();
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
                .ifPresent(module -> module.bindServices(playerData, subjectRegistry));
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
                        audit
                ));
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
        DataManager.beginShutdown();
        DataManager.saveAll();
        coreManager.stopRuntime();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        coreManager.closeRuntime();
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
}
