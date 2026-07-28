package com.fontainerepublic;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
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
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CoreManager coreManager = new CoreManager(new ModuleRegistry());

    public FontaineRepublic() {
        LOGGER.info("[FontaineRepublic] Loading");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ConfigManager.load();
        event.enqueueWork(coreManager::closeRegistration);
        LOGGER.info("[FontaineRepublic] Core initialized");
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        coreManager.preValidate();
    }

    private void onServerStarting(ServerStartingEvent event) {
        DataManager.init(event.getServer());
        coreManager.startRuntime();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        DataManager.saveAll();
        coreManager.stopRuntime();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        coreManager.closeRuntime();
    }
}
