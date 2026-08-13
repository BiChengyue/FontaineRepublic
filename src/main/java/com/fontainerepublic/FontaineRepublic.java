package com.fontainerepublic;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.core.module.runtime.ModuleState;
import com.fontainerepublic.core.module.test.TestModule;
import com.fontainerepublic.common.network.NetworkBootstrap;
import com.fontainerepublic.server.command.CommandBootstrap;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
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

    public FontaineRepublic() {
        LOGGER.info("[FontaineRepublic] Loading");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
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
        if (runtimeValidationEnabled) {
            TestModule.registerAll(coreManager.moduleRegistry());
            LOGGER.warn(
                    "[FontaineRepublic] Runtime validation modules enabled by -D{}=true",
                    RUNTIME_VALIDATION_PROPERTY
            );
        }
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
}
