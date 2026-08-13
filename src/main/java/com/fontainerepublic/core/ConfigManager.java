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

    /** Default economy currency display name (FR-ECO-001-C §7). */
    public static final String DEFAULT_ECONOMY_CURRENCY_DISPLAY_NAME = "Mora";

    /** Default economy currency symbol (empty = none). */
    public static final String DEFAULT_ECONOMY_CURRENCY_SYMBOL = "";

    /** Default thousands grouping for currency presentation. */
    public static final boolean DEFAULT_ECONOMY_CURRENCY_GROUPING = true;

    /** Default server-owned transfer cooldown in milliseconds. */
    public static final int DEFAULT_ECONOMY_TRANSFER_COOLDOWN_MILLIS = 1000;

    /** Default public workflow terminal distance in blocks (FR-INST-001-B §3.1). */
    public static final int DEFAULT_INSTITUTION_PUBLIC_DISTANCE_BLOCKS = 6;

    /** Default public workflow context lifetime in milliseconds (2 minutes). */
    public static final long DEFAULT_INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS = 120_000L;

    /** Default official routine idle timeout in milliseconds (10 minutes). */
    public static final long DEFAULT_INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS = 600_000L;

    /** Default official routine hard session limit in milliseconds (60 minutes). */
    public static final long DEFAULT_INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS = 3_600_000L;

    /** Default high-risk workflow terminal distance in blocks (FR-INST-001-B §3.3). */
    public static final int DEFAULT_INSTITUTION_HIGH_RISK_DISTANCE_BLOCKS = 6;

    /** Default high-risk single-use authorization lifetime in milliseconds (30 seconds). */
    public static final long DEFAULT_INSTITUTION_HIGH_RISK_LIFETIME_MILLIS = 30_000L;

    /** Default bounded presence-check interval in ticks (1 second at 20 TPS). */
    public static final int DEFAULT_INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS = 20;

    private static final ForgeConfigSpec.IntValue COMMIT_MIN_INTERVAL_MILLIS;
    private static final ForgeConfigSpec.IntValue COMMIT_MAX_BYTES_PER_NAMESPACE;

    private static final ForgeConfigSpec.BooleanValue LAND_REQUIRE_ACTIVE_SUBJECT;
    private static final ForgeConfigSpec.BooleanValue LAND_PUBLIC_ACCESS_ALLOWED;
    private static final ForgeConfigSpec.BooleanValue LAND_RESTRICTED_REQUIRES_USAGE_RIGHT;
    private static final ForgeConfigSpec.BooleanValue LAND_PRIVATE_REQUIRES_USAGE_RIGHT;

    private static final ForgeConfigSpec.ConfigValue<String> ECONOMY_CURRENCY_DISPLAY_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> ECONOMY_CURRENCY_SYMBOL;
    private static final ForgeConfigSpec.BooleanValue ECONOMY_CURRENCY_GROUPING;
    private static final ForgeConfigSpec.IntValue ECONOMY_TRANSFER_COOLDOWN_MILLIS;

    private static final ForgeConfigSpec.IntValue INSTITUTION_PUBLIC_DISTANCE_BLOCKS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS;
    private static final ForgeConfigSpec.IntValue INSTITUTION_HIGH_RISK_DISTANCE_BLOCKS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_HIGH_RISK_LIFETIME_MILLIS;
    private static final ForgeConfigSpec.IntValue INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS;

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

        builder.comment("FR-ECO-001 economy player services.").push("economy");
        // Presentation is projection only: it never changes stored numeric
        // values, transaction semantics, supply, or authority (FR-ECO-001-C
        // §7). The transfer cooldown is a server-owned abuse-control gate,
        // never monetary authority; 0 disables it.
        ECONOMY_CURRENCY_DISPLAY_NAME = builder
                .comment("Currency display name used in presentation only.")
                .define(
                        "currencyDisplayName",
                        DEFAULT_ECONOMY_CURRENCY_DISPLAY_NAME
                );
        ECONOMY_CURRENCY_SYMBOL = builder
                .comment("Currency symbol appended in presentation (may be empty).")
                .define(
                        "currencySymbol",
                        DEFAULT_ECONOMY_CURRENCY_SYMBOL
                );
        ECONOMY_CURRENCY_GROUPING = builder
                .comment("Group thousands with commas in presentation only.")
                .define(
                        "currencyGrouping",
                        DEFAULT_ECONOMY_CURRENCY_GROUPING
                );
        ECONOMY_TRANSFER_COOLDOWN_MILLIS = builder
                .comment(
                        "Server-owned minimum interval between transfers from "
                                + "the same actor in milliseconds. Range [0, 60000]; "
                                + "0 disables the cooldown."
                )
                .defineInRange(
                        "transferCooldownMillis",
                        DEFAULT_ECONOMY_TRANSFER_COOLDOWN_MILLIS,
                        0,
                        60_000
                );
        builder.pop();

        builder.comment(
                "FR-INST-002 shared institution access workflow parameters "
                        + "(FR-INST-001-B §3). The final mutation-time "
                        + "revalidation has no switch and cannot be disabled."
        ).push("institution");
        INSTITUTION_PUBLIC_DISTANCE_BLOCKS = builder
                .comment(
                        "Public workflow terminal distance in blocks. "
                                + "Range [1, 256]."
                )
                .defineInRange(
                        "publicDistanceBlocks",
                        DEFAULT_INSTITUTION_PUBLIC_DISTANCE_BLOCKS,
                        1,
                        256
                );
        INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS = builder
                .comment(
                        "Public workflow context maximum lifetime in "
                                + "milliseconds. Range [1000, 3600000]."
                )
                .defineInRange(
                        "publicContextLifetimeMillis",
                        DEFAULT_INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS,
                        1_000,
                        3_600_000
                );
        INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS = builder
                .comment(
                        "Official routine idle timeout in milliseconds. "
                                + "Range [1000, 3600000]."
                )
                .defineInRange(
                        "officialIdleTimeoutMillis",
                        DEFAULT_INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS,
                        1_000,
                        3_600_000
                );
        INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS = builder
                .comment(
                        "Official routine hard session limit in milliseconds. "
                                + "Range [60000, 86400000]."
                )
                .defineInRange(
                        "officialHardLimitMillis",
                        DEFAULT_INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS,
                        60_000,
                        86_400_000
                );
        INSTITUTION_HIGH_RISK_DISTANCE_BLOCKS = builder
                .comment(
                        "High-risk workflow terminal distance in blocks. "
                                + "Range [1, 256]."
                )
                .defineInRange(
                        "highRiskDistanceBlocks",
                        DEFAULT_INSTITUTION_HIGH_RISK_DISTANCE_BLOCKS,
                        1,
                        256
                );
        INSTITUTION_HIGH_RISK_LIFETIME_MILLIS = builder
                .comment(
                        "High-risk single-use authorization lifetime in "
                                + "milliseconds. Range [1000, 600000]."
                )
                .defineInRange(
                        "highRiskLifetimeMillis",
                        DEFAULT_INSTITUTION_HIGH_RISK_LIFETIME_MILLIS,
                        1_000,
                        600_000
                );
        INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS = builder
                .comment(
                        "Bounded presence-check interval in ticks (20 ticks = "
                                + "1 second at 20 TPS). Range [1, 200]."
                )
                .defineInRange(
                        "presenceCheckIntervalTicks",
                        DEFAULT_INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS,
                        1,
                        200
                );
        builder.pop();
        SPEC = builder.build();
    }

    @SuppressWarnings("removal") // Forge 1.20.1 API surface (deprecated for removal on newer JDKs)
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

    /** Configured economy currency display name; falls back to the default. */
    public static String economyCurrencyDisplayName() {
        try {
            String value = ECONOMY_CURRENCY_DISPLAY_NAME.get();
            return value == null || value.isEmpty()
                    ? DEFAULT_ECONOMY_CURRENCY_DISPLAY_NAME
                    : value;
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_ECONOMY_CURRENCY_DISPLAY_NAME;
        }
    }

    /** Configured economy currency symbol; falls back to the default. */
    public static String economyCurrencySymbol() {
        try {
            String value = ECONOMY_CURRENCY_SYMBOL.get();
            return value == null ? DEFAULT_ECONOMY_CURRENCY_SYMBOL : value;
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_ECONOMY_CURRENCY_SYMBOL;
        }
    }

    /** Configured economy thousands grouping; falls back to the default. */
    public static boolean economyCurrencyGrouping() {
        try {
            return ECONOMY_CURRENCY_GROUPING.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_ECONOMY_CURRENCY_GROUPING;
        }
    }

    /**
     * Configured server-owned transfer cooldown; falls back to the default
     * when the config is not loaded yet.
     */
    public static int economyTransferCooldownMillis() {
        try {
            return ECONOMY_TRANSFER_COOLDOWN_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_ECONOMY_TRANSFER_COOLDOWN_MILLIS;
        }
    }

    /**
     * Configured public workflow terminal distance; falls back to the default
     * when the config is not loaded yet.
     */
    public static int institutionPublicDistanceBlocks() {
        try {
            return INSTITUTION_PUBLIC_DISTANCE_BLOCKS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_PUBLIC_DISTANCE_BLOCKS;
        }
    }

    /** Configured public workflow context lifetime; falls back to the default. */
    public static long institutionPublicContextLifetimeMillis() {
        try {
            return INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS;
        }
    }

    /** Configured official routine idle timeout; falls back to the default. */
    public static long institutionOfficialIdleTimeoutMillis() {
        try {
            return INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS;
        }
    }

    /** Configured official routine hard session limit; falls back to the default. */
    public static long institutionOfficialHardLimitMillis() {
        try {
            return INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS;
        }
    }

    /** Configured high-risk workflow terminal distance; falls back to the default. */
    public static int institutionHighRiskDistanceBlocks() {
        try {
            return INSTITUTION_HIGH_RISK_DISTANCE_BLOCKS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_HIGH_RISK_DISTANCE_BLOCKS;
        }
    }

    /** Configured high-risk authorization lifetime; falls back to the default. */
    public static long institutionHighRiskLifetimeMillis() {
        try {
            return INSTITUTION_HIGH_RISK_LIFETIME_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_HIGH_RISK_LIFETIME_MILLIS;
        }
    }

    /**
     * Configured bounded presence-check interval; falls back to the default
     * when the config is not loaded yet.
     */
    public static int institutionPresenceCheckIntervalTicks() {
        try {
            return INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS;
        }
    }
}
