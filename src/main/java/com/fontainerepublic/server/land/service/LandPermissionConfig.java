package com.fontainerepublic.server.land.service;

/**
 * Immutable config-driven land access policy (FR-LAND-001-A §4/§5).
 *
 * <p>The resolver's behavior is driven entirely by this configuration — no
 * rank/OP bypass exists anywhere. Defaults are the conservative republic
 * posture: an active subject is required, public parcels are open to such
 * players, and restricted/private parcels require a live usage right.
 * Production values come from {@link com.fontainerepublic.core.ConfigManager}
 * (land config section); tests inject their own.</p>
 *
 * @param requireActiveSubject       all land access requires a player with an
 *                                   active FR-ID subject
 * @param publicAccessAllowed        PUBLIC parcels are open to qualified
 *                                   players
 * @param restrictedRequiresUsageRight RESTRICTED parcels require a live usage
 *                                   right (false degrades them to PUBLIC)
 * @param privateRequiresUsageRight  PRIVATE parcels require a live usage
 *                                   right
 */
public record LandPermissionConfig(
        boolean requireActiveSubject,
        boolean publicAccessAllowed,
        boolean restrictedRequiresUsageRight,
        boolean privateRequiresUsageRight
) {

    public static final LandPermissionConfig DEFAULT = new LandPermissionConfig(
            true,
            true,
            true,
            true
    );

    /**
     * Production binding: reads the land access policy from
     * {@link com.fontainerepublic.core.ConfigManager} (land config section),
     * falling back to {@link #DEFAULT} values when the config is not loaded.
     */
    public LandPermissionConfig withConfigManagerValues() {
        return new LandPermissionConfig(
                com.fontainerepublic.core.ConfigManager.landRequireActiveSubject(),
                com.fontainerepublic.core.ConfigManager.landPublicAccessAllowed(),
                com.fontainerepublic.core.ConfigManager.landRestrictedRequiresUsageRight(),
                com.fontainerepublic.core.ConfigManager.landPrivateRequiresUsageRight()
        );
    }
}
