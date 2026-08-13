package com.fontainerepublic.core;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

public class ConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec SPEC;

    /** Default minimum interval between durable commits (FR-CORE-002 §3.6). */
    public static final int DEFAULT_COMMIT_MIN_INTERVAL_MILLIS = 100;

    /** Default per-namespace commit byte budget (FR-CORE-002 §3.6). */
    public static final int DEFAULT_COMMIT_MAX_BYTES_PER_NAMESPACE = 8 * 1024 * 1024;

    private static final ForgeConfigSpec.IntValue COMMIT_MIN_INTERVAL_MILLIS;
    private static final ForgeConfigSpec.IntValue COMMIT_MAX_BYTES_PER_NAMESPACE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("FR-CORE-002 durable commit gate bounds.").push("core");
        // Range lower bounds deliberately forbid disabling the checks.
        COMMIT_MIN_INTERVAL_MILLIS = builder
                .comment(
                        "Minimum interval between durable commits in milliseconds. "
                                + "Range [100, 60000]; the rate guard cannot be disabled."
                )
                .defineInRange(
                        "commitMinIntervalMillis",
                        DEFAULT_COMMIT_MIN_INTERVAL_MILLIS,
                        100,
                        60_000
                );
        COMMIT_MAX_BYTES_PER_NAMESPACE = builder
                .comment(
                        "Maximum serialized bytes per committed namespace snapshot. "
                                + "Range [1024, 67108864]; the bound check cannot be disabled."
                )
                .defineInRange(
                        "commitMaxBytesPerNamespace",
                        DEFAULT_COMMIT_MAX_BYTES_PER_NAMESPACE,
                        1024,
                        64 * 1024 * 1024
                );
        builder.pop();
        SPEC = builder.build();
    }

    public static void load() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
        LOGGER.info("[ConfigManager] Loaded");
    }

    /**
     * Configured commit minimum interval; falls back to the default when the
     * config is not loaded yet (e.g. off-server contexts).
     */
    public static int commitMinIntervalMillis() {
        try {
            return COMMIT_MIN_INTERVAL_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_COMMIT_MIN_INTERVAL_MILLIS;
        }
    }

    /**
     * Configured per-namespace commit byte budget; falls back to the default
     * when the config is not loaded yet.
     */
    public static int commitMaxBytesPerNamespace() {
        try {
            return COMMIT_MAX_BYTES_PER_NAMESPACE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_COMMIT_MAX_BYTES_PER_NAMESPACE;
        }
    }
}
