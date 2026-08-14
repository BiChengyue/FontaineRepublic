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

    /** Default horizontal half-width of a claimed land parcel (3×3 plane). */
    public static final int DEFAULT_LAND_CLAIM_HALF_WIDTH = 1;

    /** Default vertical height of a claimed land parcel in blocks. */
    public static final int DEFAULT_LAND_CLAIM_HEIGHT = 8;

    /** Default zone type name of a claimed land parcel. */
    public static final String DEFAULT_LAND_CLAIM_ZONE_TYPE = "RESIDENTIAL";

    /** Default economy currency display name (FR-ECO-001-C §7). */
    public static final String DEFAULT_ECONOMY_CURRENCY_DISPLAY_NAME = "Mora";

    /** Default economy currency symbol (empty = none). */
    public static final String DEFAULT_ECONOMY_CURRENCY_SYMBOL = "";

    /** Default thousands grouping for currency presentation. */
    public static final boolean DEFAULT_ECONOMY_CURRENCY_GROUPING = true;

    /** Default server-owned transfer cooldown in milliseconds. */
    public static final int DEFAULT_ECONOMY_TRANSFER_COOLDOWN_MILLIS = 1000;

    /** Default public workflow context lifetime in milliseconds (2 minutes). */
    public static final long DEFAULT_INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS = 120_000L;

    /** Default official routine idle timeout in milliseconds (10 minutes). */
    public static final long DEFAULT_INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS = 600_000L;

    /** Default official routine hard session limit in milliseconds (60 minutes). */
    public static final long DEFAULT_INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS = 3_600_000L;

    /** Default high-risk single-use authorization lifetime in milliseconds (30 seconds). */
    public static final long DEFAULT_INSTITUTION_HIGH_RISK_LIFETIME_MILLIS = 30_000L;

    /** Default small-size x budget of an institution zone (FR-INST-002-B §2). */
    public static final int DEFAULT_INSTITUTION_MAX_ZONE_X_SIZE = 16;

    /** Default small-size y budget of an institution zone (FR-INST-002-B §2). */
    public static final int DEFAULT_INSTITUTION_MAX_ZONE_Y_SIZE = 8;

    /** Default small-size z budget of an institution zone (FR-INST-002-B §2). */
    public static final int DEFAULT_INSTITUTION_MAX_ZONE_Z_SIZE = 16;

    /** Default bounded presence-check interval in ticks (1 second at 20 TPS). */
    public static final int DEFAULT_INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS = 20;

    /** Default: no Hydro Archon emergency authority UUID configured. */
    public static final String DEFAULT_EMERGENCY_HYDRO_ARCHON_UUID = "";

    /** Default trade settlement tax rate in percent (FR-TRADE-001-A §6.1). */
    public static final int DEFAULT_TRADE_TAX_RATE_PERCENT = 5;

    /** Default trade tax rate in basis points (FR-TRADE-002-A §9: 500 = 5%). */
    public static final int DEFAULT_TRADE_TAX_RATE_BPS = 500;

    /** Default pending trade-request timeout in seconds (FR-TRADE-002-A §7). */
    public static final int DEFAULT_TRADE_REQUEST_TIMEOUT_SECONDS = 30;

    /** Default repeated-request cooldown in seconds (FR-TRADE-002-A §7). */
    public static final int DEFAULT_TRADE_REQUEST_COOLDOWN_SECONDS = 5;

    /** Default both-ready locked countdown in seconds (FR-TRADE-002-A §7). */
    public static final int DEFAULT_TRADE_LOCKED_COUNTDOWN_SECONDS = 5;

    /** Default maximum XP points one player may offer (FR-TRADE-002-A §8.2). */
    public static final long DEFAULT_TRADE_MAX_OFFER_XP = 1_000_000_000L;

    /** Default mail postage fee per letter (FR-MAIL-001-A §6.3). */
    public static final long DEFAULT_MAIL_POSTAGE_FEE = 10;

    /** Default mail per-attachment fee (money attachment or item slot each). */
    public static final long DEFAULT_MAIL_ATTACHMENT_FEE = 100;

    /** Default mail broadcast fee (institution sends are free by default). */
    public static final long DEFAULT_MAIL_BROADCAST_FEE = 0;

    /** Default mail broadcast cooldown in milliseconds (5 minutes). */
    public static final long DEFAULT_MAIL_BROADCAST_COOLDOWN_MILLIS = 300_000L;

    private static final ForgeConfigSpec.IntValue COMMIT_MIN_INTERVAL_MILLIS;
    private static final ForgeConfigSpec.IntValue COMMIT_MAX_BYTES_PER_NAMESPACE;

    private static final ForgeConfigSpec.BooleanValue LAND_REQUIRE_ACTIVE_SUBJECT;
    private static final ForgeConfigSpec.BooleanValue LAND_PUBLIC_ACCESS_ALLOWED;
    private static final ForgeConfigSpec.BooleanValue LAND_RESTRICTED_REQUIRES_USAGE_RIGHT;
    private static final ForgeConfigSpec.BooleanValue LAND_PRIVATE_REQUIRES_USAGE_RIGHT;
    private static final ForgeConfigSpec.IntValue LAND_CLAIM_HALF_WIDTH;
    private static final ForgeConfigSpec.IntValue LAND_CLAIM_HEIGHT;
    private static final ForgeConfigSpec.ConfigValue<String> LAND_CLAIM_ZONE_TYPE;

    private static final ForgeConfigSpec.ConfigValue<String> ECONOMY_CURRENCY_DISPLAY_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> ECONOMY_CURRENCY_SYMBOL;
    private static final ForgeConfigSpec.BooleanValue ECONOMY_CURRENCY_GROUPING;
    private static final ForgeConfigSpec.IntValue ECONOMY_TRANSFER_COOLDOWN_MILLIS;

    private static final ForgeConfigSpec.LongValue INSTITUTION_PUBLIC_CONTEXT_LIFETIME_MILLIS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_OFFICIAL_IDLE_TIMEOUT_MILLIS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_OFFICIAL_HARD_LIMIT_MILLIS;
    private static final ForgeConfigSpec.LongValue INSTITUTION_HIGH_RISK_LIFETIME_MILLIS;
    private static final ForgeConfigSpec.IntValue INSTITUTION_MAX_ZONE_X_SIZE;
    private static final ForgeConfigSpec.IntValue INSTITUTION_MAX_ZONE_Y_SIZE;
    private static final ForgeConfigSpec.IntValue INSTITUTION_MAX_ZONE_Z_SIZE;
    private static final ForgeConfigSpec.IntValue INSTITUTION_PRESENCE_CHECK_INTERVAL_TICKS;
    private static final ForgeConfigSpec.ConfigValue<String> EMERGENCY_HYDRO_ARCHON_UUID;
    private static final ForgeConfigSpec.IntValue TRADE_TAX_RATE_PERCENT;
    private static final ForgeConfigSpec.IntValue TRADE_TAX_RATE_BPS;
    private static final ForgeConfigSpec.IntValue TRADE_REQUEST_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue TRADE_REQUEST_COOLDOWN_SECONDS;
    private static final ForgeConfigSpec.IntValue TRADE_LOCKED_COUNTDOWN_SECONDS;
    private static final ForgeConfigSpec.LongValue TRADE_MAX_OFFER_XP;
    private static final ForgeConfigSpec.LongValue MAIL_POSTAGE_FEE;
    private static final ForgeConfigSpec.LongValue MAIL_ATTACHMENT_FEE;
    private static final ForgeConfigSpec.LongValue MAIL_BROADCAST_FEE;
    private static final ForgeConfigSpec.LongValue MAIL_BROADCAST_COOLDOWN_MILLIS;
    private static final ForgeConfigSpec.ConfigValue<String> MAIL_MANAGER_PARLIAMENT;
    private static final ForgeConfigSpec.ConfigValue<String> MAIL_MANAGER_COURT;
    private static final ForgeConfigSpec.ConfigValue<String> MAIL_MANAGER_BANK;
    private static final ForgeConfigSpec.ConfigValue<String> MAIL_MANAGER_GOVERNMENT;

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
        builder.comment(
                "FR-LAND-CLAIM-001 communicator land-claim default parcel shape."
        ).push("claim");
        LAND_CLAIM_HALF_WIDTH = builder
                .comment(
                        "Horizontal half-width of a claimed parcel in blocks "
                                + "around the clicked block (halfWidth 1 = a "
                                + "3x3 surface). Range [0, 16]."
                )
                .defineInRange(
                        "halfWidth",
                        DEFAULT_LAND_CLAIM_HALF_WIDTH,
                        0,
                        16
                );
        LAND_CLAIM_HEIGHT = builder
                .comment(
                        "Vertical height of a claimed parcel in blocks, from "
                                + "the clicked block upward. Range [1, 64]."
                )
                .defineInRange(
                        "height",
                        DEFAULT_LAND_CLAIM_HEIGHT,
                        1,
                        64
                );
        LAND_CLAIM_ZONE_TYPE = builder
                .comment(
                        "Planning designation of a claimed parcel (a ZoneType "
                                + "enum name); an invalid value falls back to "
                                + "RESIDENTIAL."
                )
                .define(
                        "zoneType",
                        DEFAULT_LAND_CLAIM_ZONE_TYPE
                );
        builder.pop();
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
        INSTITUTION_MAX_ZONE_X_SIZE = builder
                .comment(
                        "Small-size x budget of an institution zone in blocks "
                                + "(FR-INST-002-B §2). Range [1, 1024]."
                )
                .defineInRange(
                        "maxZoneXSize",
                        DEFAULT_INSTITUTION_MAX_ZONE_X_SIZE,
                        1,
                        1024
                );
        INSTITUTION_MAX_ZONE_Y_SIZE = builder
                .comment(
                        "Small-size y budget of an institution zone in blocks "
                                + "(FR-INST-002-B §2). Range [1, 256]."
                )
                .defineInRange(
                        "maxZoneYSize",
                        DEFAULT_INSTITUTION_MAX_ZONE_Y_SIZE,
                        1,
                        256
                );
        INSTITUTION_MAX_ZONE_Z_SIZE = builder
                .comment(
                        "Small-size z budget of an institution zone in blocks "
                                + "(FR-INST-002-B §2). Range [1, 1024]."
                )
                .defineInRange(
                        "maxZoneZSize",
                        DEFAULT_INSTITUTION_MAX_ZONE_Z_SIZE,
                        1,
                        1024
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

        builder.comment(
                "FR-EMG-001 shared emergency authority configuration. "
                        + "The configured Hydro Archon UUID is the candidate "
                        + "value; the shared emergency namespace holds the last "
                        + "accepted digest and revision. Merely editing this "
                        + "file is NOT an authorized change — drift fails "
                        + "closed until the real local console recovers."
        ).push("emergency");
        EMERGENCY_HYDRO_ARCHON_UUID = builder
                .comment(
                        "Candidate Hydro Archon Minecraft UUID (canonical "
                                + "lowercase hyphenated). Empty = none."
                )
                .define(
                        "hydroArchonUuid",
                        DEFAULT_EMERGENCY_HYDRO_ARCHON_UUID
                );
        builder.pop();

        builder.comment(
                "FR-TRADE-001 communicator trade settlement tax. The rate "
                        + "applies only to paying sides of a trade settlement "
                        + "(floor(offer * rate / 100), see FR-TRADE-001-A "
                        + "§6.1); 0 disables the tax."
        ).push("trade");
        TRADE_TAX_RATE_PERCENT = builder
                .comment(
                        "Trade settlement tax rate in percent. Range [0, 100]; "
                                + "0 disables the tax."
                )
                .defineInRange(
                        "tradeTaxRatePercent",
                        DEFAULT_TRADE_TAX_RATE_PERCENT,
                        0,
                        100
                );
        TRADE_TAX_RATE_BPS = builder
                .comment(
                        "FR-TRADE-002-A trade tax quote rate in basis points "
                                + "(500 = 5%; tax = floor(offer * rateBps / 10000)). "
                                + "Range [0, 10000]; 0 disables the tax. This is the "
                                + "server-authoritative quote authority of the trade "
                                + "module; completed settlements are never recalculated."
                )
                .defineInRange(
                        "tradeTaxRateBps",
                        DEFAULT_TRADE_TAX_RATE_BPS,
                        0,
                        10_000
                );
        TRADE_REQUEST_TIMEOUT_SECONDS = builder
                .comment(
                        "FR-TRADE-002-A pending trade-request timeout in seconds. "
                                + "Range [1, 3600]."
                )
                .defineInRange(
                        "requestTimeoutSeconds",
                        DEFAULT_TRADE_REQUEST_TIMEOUT_SECONDS,
                        1,
                        3_600
                );
        TRADE_REQUEST_COOLDOWN_SECONDS = builder
                .comment(
                        "FR-TRADE-002-A repeated-request cooldown between the same "
                                + "two players in seconds. Range [0, 3600]; 0 disables."
                )
                .defineInRange(
                        "requestCooldownSeconds",
                        DEFAULT_TRADE_REQUEST_COOLDOWN_SECONDS,
                        0,
                        3_600
                );
        TRADE_LOCKED_COUNTDOWN_SECONDS = builder
                .comment(
                        "FR-TRADE-002-A both-ready confirmation countdown in seconds. "
                                + "Range [1, 60]."
                )
                .defineInRange(
                        "lockedCountdownSeconds",
                        DEFAULT_TRADE_LOCKED_COUNTDOWN_SECONDS,
                        1,
                        60
                );
        TRADE_MAX_OFFER_XP = builder
                .comment(
                        "FR-TRADE-002-A maximum XP points one player may offer. "
                                + "Range [1, 10000000000]."
                )
                .defineInRange(
                        "maxOfferXp",
                        DEFAULT_TRADE_MAX_OFFER_XP,
                        1L,
                        10_000_000_000L
                );
        builder.pop();

        builder.comment(
                "FR-MAIL-001 communicator mail feature. Postage/attachment fees "
                        + "are charged from a personal sender and credit the treasury; "
                        + "institution senders are free. A mailbox manager is a "
                        + "comma-separated list of player UUIDs; when empty the "
                        + "Hydro Archon holds it by default."
        ).push("mail");
        MAIL_POSTAGE_FEE = builder
                .comment("Fixed postage fee per letter. Range [0, 1000000].")
                .defineInRange(
                        "postageFee",
                        DEFAULT_MAIL_POSTAGE_FEE,
                        0L,
                        1_000_000L
                );
        MAIL_ATTACHMENT_FEE = builder
                .comment("Fee per attachment (a money attachment or an item slot "
                        + "each). Range [0, 1000000].")
                .defineInRange(
                        "attachmentFee",
                        DEFAULT_MAIL_ATTACHMENT_FEE,
                        0L,
                        1_000_000L
                );
        MAIL_BROADCAST_FEE = builder
                .comment("Fee per institution broadcast. Range [0, 1000000]; "
                        + "0 (default) = free.")
                .defineInRange(
                        "broadcastFee",
                        DEFAULT_MAIL_BROADCAST_FEE,
                        0L,
                        1_000_000L
                );
        MAIL_BROADCAST_COOLDOWN_MILLIS = builder
                .comment("Minimum interval between broadcasts per institution "
                        + "in milliseconds. Range [0, 86400000]; 0 disables.")
                .defineInRange(
                        "broadcastCooldownMillis",
                        DEFAULT_MAIL_BROADCAST_COOLDOWN_MILLIS,
                        0L,
                        86_400_000L
                );
        MAIL_MANAGER_PARLIAMENT = builder
                .comment("Mailbox managers (player UUIDs, comma-separated) of "
                        + "the parliament mailbox; empty = Hydro Archon default.")
                .define(
                        "mailboxManagerParliament",
                        ""
                );
        MAIL_MANAGER_COURT = builder
                .comment("Mailbox managers (player UUIDs, comma-separated) of "
                        + "the court mailbox; empty = Hydro Archon default.")
                .define(
                        "mailboxManagerCourt",
                        ""
                );
        MAIL_MANAGER_BANK = builder
                .comment("Mailbox managers (player UUIDs, comma-separated) of "
                        + "the central-bank mailbox; empty = Hydro Archon default.")
                .define(
                        "mailboxManagerBank",
                        ""
                );
        MAIL_MANAGER_GOVERNMENT = builder
                .comment("Mailbox managers (player UUIDs, comma-separated) of "
                        + "the government mailbox; empty = Hydro Archon default.")
                .define(
                        "mailboxManagerGovernment",
                        ""
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

    /**
     * Configured default half-width of a claimed parcel (FR-LAND-CLAIM-001-A
     * §3.2); falls back to the default when the config is not loaded yet.
     */
    public static int landClaimHalfWidth() {
        try {
            return LAND_CLAIM_HALF_WIDTH.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_CLAIM_HALF_WIDTH;
        }
    }

    /** Configured default vertical height of a claimed parcel; falls back. */
    public static int landClaimHeight() {
        try {
            return LAND_CLAIM_HEIGHT.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_CLAIM_HEIGHT;
        }
    }

    /**
     * Configured default zone type name of a claimed parcel (a ZoneType enum
     * name); falls back to RESIDENTIAL when the config is not loaded yet. An
     * invalid configured value is resolved by the caller to RESIDENTIAL.
     */
    public static String landClaimZoneTypeName() {
        try {
            String value = LAND_CLAIM_ZONE_TYPE.get();
            return value == null ? DEFAULT_LAND_CLAIM_ZONE_TYPE : value;
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_LAND_CLAIM_ZONE_TYPE;
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
     * Configured public workflow context lifetime; falls back to the default
     * when the config is not loaded yet.
     */
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

    /** Configured small-size x budget of an institution zone; falls back to
     *  the default when the config is not loaded yet. */
    public static int institutionMaxZoneXSize() {
        try {
            return INSTITUTION_MAX_ZONE_X_SIZE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_MAX_ZONE_X_SIZE;
        }
    }

    /** Configured small-size y budget of an institution zone; falls back to
     *  the default when the config is not loaded yet. */
    public static int institutionMaxZoneYSize() {
        try {
            return INSTITUTION_MAX_ZONE_Y_SIZE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_MAX_ZONE_Y_SIZE;
        }
    }

    /** Configured small-size z budget of an institution zone; falls back to
     *  the default when the config is not loaded yet. */
    public static int institutionMaxZoneZSize() {
        try {
            return INSTITUTION_MAX_ZONE_Z_SIZE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_INSTITUTION_MAX_ZONE_Z_SIZE;
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

    /**
     * Candidate Hydro Archon emergency authority UUID (canonical lowercase
     * hyphenated string, or empty when none); falls back to the default when
     * the config is not loaded yet.
     */
    public static String emergencyHydroArchonUuid() {
        try {
            String value = EMERGENCY_HYDRO_ARCHON_UUID.get();
            return value == null ? DEFAULT_EMERGENCY_HYDRO_ARCHON_UUID : value;
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_EMERGENCY_HYDRO_ARCHON_UUID;
        }
    }

    /**
     * Sets the candidate Hydro Archon emergency authority UUID. The value is
     * the candidate only; the durable accepted state lives in the shared
     * emergency namespace.
     */
    public static void setEmergencyHydroArchonUuid(String canonicalUuid) {
        try {
            EMERGENCY_HYDRO_ARCHON_UUID.set(canonicalUuid == null
                    ? DEFAULT_EMERGENCY_HYDRO_ARCHON_UUID : canonicalUuid);
        } catch (IllegalStateException notLoaded) {
            // Config not loaded; caller should treat as unavailable.
            throw new IllegalStateException(
                    "Emergency authority config is not loaded", notLoaded
            );
        }
    }

    /**
     * Configured trade settlement tax rate in percent; falls back to the
     * default when the config is not loaded yet (FR-TRADE-001-A §6.1).
     */
    public static int tradeTaxRatePercent() {
        try {
            return TRADE_TAX_RATE_PERCENT.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_TRADE_TAX_RATE_PERCENT;
        }
    }

    /**
     * Configured trade tax quote rate in basis points; falls back to the
     * default (500 = 5%) when the config is not loaded yet (FR-TRADE-002-A
     * §9).
     */
    public static int tradeTaxRateBps() {
        try {
            return TRADE_TAX_RATE_BPS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_TRADE_TAX_RATE_BPS;
        }
    }

    /** Configured pending trade-request timeout in seconds; falls back. */
    public static int tradeRequestTimeoutSeconds() {
        try {
            return TRADE_REQUEST_TIMEOUT_SECONDS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_TRADE_REQUEST_TIMEOUT_SECONDS;
        }
    }

    /** Configured repeated-request cooldown in seconds; falls back. */
    public static int tradeRequestCooldownSeconds() {
        try {
            return TRADE_REQUEST_COOLDOWN_SECONDS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_TRADE_REQUEST_COOLDOWN_SECONDS;
        }
    }

    /** Configured both-ready locked countdown in seconds; falls back. */
    public static int tradeLockedCountdownSeconds() {
        try {
            return TRADE_LOCKED_COUNTDOWN_SECONDS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_TRADE_LOCKED_COUNTDOWN_SECONDS;
        }
    }

    /** Configured maximum XP points one player may offer; falls back. */
    public static long tradeMaxOfferXp() {
        try {
            return TRADE_MAX_OFFER_XP.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_TRADE_MAX_OFFER_XP;
        }
    }

    /** Configured mail postage fee per letter (FR-MAIL-001-A §6.3). */
    public static long mailPostageFee() {
        try {
            return MAIL_POSTAGE_FEE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_MAIL_POSTAGE_FEE;
        }
    }

    /** Configured mail per-attachment fee. */
    public static long mailAttachmentFee() {
        try {
            return MAIL_ATTACHMENT_FEE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_MAIL_ATTACHMENT_FEE;
        }
    }

    /** Configured mail broadcast fee (0 = free). */
    public static long mailBroadcastFee() {
        try {
            return MAIL_BROADCAST_FEE.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_MAIL_BROADCAST_FEE;
        }
    }

    /** Configured mail broadcast cooldown in milliseconds (0 = disabled). */
    public static long mailBroadcastCooldownMillis() {
        try {
            return MAIL_BROADCAST_COOLDOWN_MILLIS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_MAIL_BROADCAST_COOLDOWN_MILLIS;
        }
    }

    /** Configured parliament-mailbox manager player UUIDs (comma-separated). */
    public static String mailManagerParliament() {
        return mailManager(MAIL_MANAGER_PARLIAMENT);
    }

    /** Configured court-mailbox manager player UUIDs (comma-separated). */
    public static String mailManagerCourt() {
        return mailManager(MAIL_MANAGER_COURT);
    }

    /** Configured central-bank-mailbox manager player UUIDs. */
    public static String mailManagerBank() {
        return mailManager(MAIL_MANAGER_BANK);
    }

    /** Configured government-mailbox manager player UUIDs. */
    public static String mailManagerGovernment() {
        return mailManager(MAIL_MANAGER_GOVERNMENT);
    }

    private static String mailManager(ForgeConfigSpec.ConfigValue<String> value) {
        try {
            String raw = value.get();
            return raw == null ? "" : raw.trim();
        } catch (IllegalStateException notLoaded) {
            return "";
        }
    }
}
