package com.fontainerepublic.server.institutionaccess.model;

/**
 * Primary interaction class of every institutional action (FR-INST-001-A §4).
 *
 * <p>Only the on-site classes participate in the shared access boundary.
 * {@link #REMOTE_INFORMATION}, {@link #REMOTE_PERSONAL_SERVICE} and
 * {@link #REMOTE_PREPARATION} require no physical presence and therefore no
 * on-site context. {@link #EMERGENCY_RECOVERY} is a special audited authority
 * owned by FR-EMG and never issued through the normal on-site path.</p>
 */
public enum CapabilityClass {
    REMOTE_INFORMATION,
    REMOTE_PERSONAL_SERVICE,
    REMOTE_PREPARATION,
    ONSITE_PUBLIC_SERVICE,
    ONSITE_OFFICIAL_DUTY,
    EMERGENCY_RECOVERY
}
