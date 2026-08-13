package com.fontainerepublic.server.institutionaccess.service;

/**
 * Immutable server-configurable workflow parameters of the shared access
 * boundary (FR-INST-001-B §3/§4).
 *
 * <p>All parameters are server-configurable through
 * {@link com.fontainerepublic.core.ConfigManager} (institution config
 * section); tests inject their own. The final mutation-time revalidation has
 * no switch — it is mandatory and cannot be disabled by configuration
 * (FR-INST-001-B §4).</p>
 *
 * @param publicDistanceBlocks       public workflow terminal distance
 * @param publicContextLifetimeMillis public workflow context lifetime
 * @param officialIdleTimeoutMillis  official routine idle timeout
 * @param officialHardLimitMillis    official routine hard session limit
 * @param highRiskDistanceBlocks     high-risk workflow terminal distance
 * @param highRiskLifetimeMillis     high-risk single-use authorization lifetime
 * @param presenceCheckIntervalTicks bounded presence-check interval (1 second
 *                                   default at 20 TPS)
 */
public record InstitutionAccessConfig(
        int publicDistanceBlocks,
        long publicContextLifetimeMillis,
        long officialIdleTimeoutMillis,
        long officialHardLimitMillis,
        int highRiskDistanceBlocks,
        long highRiskLifetimeMillis,
        int presenceCheckIntervalTicks
) {

    public static final InstitutionAccessConfig DEFAULT =
            new InstitutionAccessConfig(6, 120_000L, 600_000L, 3_600_000L, 6, 30_000L, 20);

    public InstitutionAccessConfig {
        if (publicDistanceBlocks <= 0) {
            throw new IllegalArgumentException("publicDistanceBlocks must be positive");
        }
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
        if (highRiskDistanceBlocks <= 0) {
            throw new IllegalArgumentException("highRiskDistanceBlocks must be positive");
        }
        if (highRiskLifetimeMillis <= 0) {
            throw new IllegalArgumentException("highRiskLifetimeMillis must be positive");
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
                com.fontainerepublic.core.ConfigManager.institutionPublicDistanceBlocks(),
                com.fontainerepublic.core.ConfigManager.institutionPublicContextLifetimeMillis(),
                com.fontainerepublic.core.ConfigManager.institutionOfficialIdleTimeoutMillis(),
                com.fontainerepublic.core.ConfigManager.institutionOfficialHardLimitMillis(),
                com.fontainerepublic.core.ConfigManager.institutionHighRiskDistanceBlocks(),
                com.fontainerepublic.core.ConfigManager.institutionHighRiskLifetimeMillis(),
                com.fontainerepublic.core.ConfigManager.institutionPresenceCheckIntervalTicks()
        );
    }
}
