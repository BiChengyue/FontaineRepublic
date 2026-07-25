package com.fontainerepublic;

import com.fontainerepublic.core.ConfigManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(FontaineRepublic.MOD_ID)
public class FontaineRepublic {
    public static final String MOD_ID = "fontainerepublic";
    private static final Logger LOGGER = LogUtils.getLogger();

    public FontaineRepublic() {
        LOGGER.info("[FontaineRepublic] Loading");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ConfigManager.load();
        LOGGER.info("[FontaineRepublic] Core initialized");
    }
}
