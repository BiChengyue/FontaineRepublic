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

    /** Default: all land access requires an active FR-ID subject. */
    public static final boolean DEFAULT_LAND_REQUIRE_ACTIVE_SUBJECT = true;

    /** Default: PUBLIC parcels are open to qualified players. */
    public static final boolean DEFAULT_LAND_PUBLIC_ACCESS_ALLOWED = true;

    /** Default: RESTRICTED parcels require a live usage right. */
    public static final boolean DEFAULT_LAND_RESTRICTED_REQUIRES_USAGE_RIGHT = true;

    /** Default: PRIVATE parcels require a live usage right. */
    public static final boolean DEFAULT_LAND_PRIVATE_REQUIRES_USAGE_RIGHT = true;

    private static final ForgeConfigSpec.IntValue COMMIT_MIN_INTERVAL_MILLIS;
    private static final ForgeConfigSpec.IntValue COMMIT_MAX_BYTES_PER_NAMESPACE;

    private static final ForgeConfigSpec.BooleanValue LAND_REQUIRE_ACTIVE_SUBJECT;
    private static final ForgeConfigSpec.BooleanValue LAND_PUBLIC_ACCESS_ALLOWED;
    private static final ForgeConfigSpec.BooleanValue LAND_RESTRICTED_REQUIRES_USAGE_RIGHT;
    private static final ForgeConfigSpec.BooleanValue LAND_PRIVATE_REQUIRES_USAGE_RIGHT;

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

        builder.comment("FR-LAND-001 config-driven land access policy.").push("land");
        // All four switches are deliberately keepable: operators may open
        // public land or require usage rights, but rank/OP never bypasses the
        // resolver (GOD rule) — these booleans are the only levers.
        LAND_REQUIRE_ACTIVE_SUBJECT = builder
                .comment(
                        "Require an active FR-ID subject for any land access "
                                + "(build/break/interact)."
                )
                .define(
                        "requireActiveSubject",
                        DEFAULT_LAND_REQUIRE_ACTIVE_SUBJECT
                );
        LAND_PUBLIC_ACCESS_ALLOWED = builder
                .comment("Allow qualified players on PUBLIC parcels.")
                .define("publicAccessAllowed", DEFAULT_LAND_PUBLIC_ACCESS_ALLOWED);
        LAND_RESTRICTED_REQUIRES_USAGE_RIGHT = builder
                .comment(
                        "Require a live usage right on RESTRICTED parcels; when "
                                + "false they degrade to the PUBLIC policy."
                )
                .define(
                        "restrictedRequiresUsageRight",
                        DEFAULT_LAND_RESTRICTED_REQUIRES_USAGE_RIGHT
                );
        LAND_PRIVATE_REQUIRES_USAGE_RIGHT = builder
                .comment(
                        "Require a live usage right on PRIVATE parcels; when "
                                + "false they are denied entirely."
                )
                .define(
                        "privateRequiresUsageRight",
                        DEFAULT_LAND_PRIVATE_REQUIRES_USAGE_RIGHT
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

    /**
     * Configured land subject gate; falls back to the default when the config
     * is not loaded yet.
     */
    public static boolean landRequireActiveSubject() {
        try {
            return LAND_REQUIRE_ACTIVE_SUBJECT.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_REQUIRE_ACTIVE_SUBJECT;
        }
    }

    /** Configured PUBLIC-parcel openness; falls back to the default. */
    public static boolean landPublicAccessAllowed() {
        try {
            return LAND_PUBLIC_ACCESS_ALLOWED.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_PUBLIC_ACCESS_ALLOWED;
        }
    }

    /** Configured RESTRICTED usage-right gate; falls back to the default. */
    public static boolean landRestrictedRequiresUsageRight() {
        try {
            return LAND_RESTRICTED_REQUIRES_USAGE_RIGHT.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_RESTRICTED_REQUIRES_USAGE_RIGHT;
        }
    }

    /** Configured PRIVATE usage-right gate; falls back to the default. */
    public static boolean landPrivateRequiresUsageRight() {
        try {
            return LAND_PRIVATE_REQUIRES_USAGE_RIGHT.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_PRIVATE_REQUIRES_USAGE_RIGHT;
        }
    }
}
