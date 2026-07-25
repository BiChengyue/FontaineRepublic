package com.fontainerepublic.core;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

public class ConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        // Phase 0: empty configuration. Business entries added in subsequent phases.
        SPEC = builder.build();
    }

    public static void load() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
        LOGGER.info("[ConfigManager] Loaded");
    }
}
