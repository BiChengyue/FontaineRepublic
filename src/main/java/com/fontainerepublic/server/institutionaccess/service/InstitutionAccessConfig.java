package com.fontainerepublic.server.institutionaccess.service;

/**
 * Immutable server-configurable workflow parameters of the shared access
 * boundary (FR-INST-001-B §3/§4, adapted to zones by FR-INST-002-B §4).
 *
 * <p>All parameters are server-configurable through
 * {@link com.fontainerepublic.core.ConfigManager} (institution config
 * section); tests inject their own. Zone presence replaces terminal
 * distance: a player is on-site exactly when inside the registered zone
 * region, so no distance parameter exists. The final mutation-time
 * revalidation has no switch — it is mandatory and cannot be disabled by
 * configuration (FR-INST-001-B §4).</p>
 *
 * @param publicContextLifetimeMillis public workflow context lifetime
 *                                    (2 minutes default)
 * @param officialIdleTimeoutMillis   official routine idle timeout
 *                                    (10 minutes default)
 * @param officialHardLimitMillis     official routine hard session limit
 *                                    (60 minutes default)
 * @param highRiskLifetimeMillis      high-risk single-use authorization
 *                                    lifetime (30 seconds default)
 * @param maxZoneXSize                small-size budget: inclusive x extent
 *                                    of a zone (16 blocks default)
 * @param maxZoneYSize                small-size budget: inclusive y extent
 *                                    of a zone (8 blocks default)
 * @param maxZoneZSize                small-size budget: inclusive z extent
 *                                    of a zone (16 blocks default)
 * @param presenceCheckIntervalTicks  bounded presence-check interval
 *                                    (1 second default at 20 TPS)
 */
public record InstitutionAccessConfig(
        long publicContextLifetimeMillis,
        long officialIdleTimeoutMillis,
        long officialHardLimitMillis,
        long highRiskLifetimeMillis,
        int maxZoneXSize,
        int maxZoneYSize,
        int maxZoneZSize,
        int presenceCheckIntervalTicks
) {

    public static final InstitutionAccessConfig DEFAULT =
            new InstitutionAccessConfig(120_000L, 600_000L, 3_600_000L, 30_000L,
                    16, 8, 16, 20);

    public InstitutionAccessConfig {
        if (publicContextLifetimeMillis <= 0) {
            throw new IllegalArgumentException(
                    "publicContextLifetimeMillis must be positive"
            );
        }
        if (officialIdleTimeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "officialIdleTimeoutMillis must be positive"
            );
        }
        if (officialHardLimitMillis < officialIdleTimeoutMillis) {
            throw new IllegalArgumentException(
                    "officialHardLimitMillis must not be below the idle timeout"
            );
        }
        if (highRiskLifetimeMillis <= 0) {
            throw new IllegalArgumentException("highRiskLifetimeMillis must be positive");
        }
        if (maxZoneXSize <= 0) {
            throw new IllegalArgumentException("maxZoneXSize must be positive");
        }
        if (maxZoneYSize <= 0) {
            throw new IllegalArgumentException("maxZoneYSize must be positive");
        }
        if (maxZoneZSize <= 0) {
            throw new IllegalArgumentException("maxZoneZSize must be positive");
        }
        if (presenceCheckIntervalTicks <= 0) {
            throw new IllegalArgumentException(
                    "presenceCheckIntervalTicks must be positive"
            );
        }
    }

    /**
     * Production binding: reads the workflow parameters from
     * {@link com.fontainerepublic.core.ConfigManager} (institution config
     * section), falling back to {@link #DEFAULT} values when the config is
     * not loaded.
     */
    public InstitutionAccessConfig withConfigManagerValues() {
        return new InstitutionAccessConfig(
                com.fontainerepublic.core.ConfigManager.institutionPublicContextLifetimeMillis(),
                com.fontainerepublic.core.ConfigManager.institutionOfficialIdleTimeoutMillis(),
                com.fontainerepublic.core.ConfigManager.institutionOfficialHardLimitMillis(),
                com.fontainerepublic.core.ConfigManager.institutionHighRiskLifetimeMillis(),
                com.fontainerepublic.core.ConfigManager.institutionMaxZoneXSize(),
                com.fontainerepublic.core.ConfigManager.institutionMaxZoneYSize(),
                com.fontainerepublic.core.ConfigManager.institutionMaxZoneZSize(),
                com.fontainerepublic.core.ConfigManager.institutionPresenceCheckIntervalTicks()
        );
    }
}
