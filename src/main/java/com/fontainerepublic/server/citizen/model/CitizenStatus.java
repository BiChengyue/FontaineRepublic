package com.fontainerepublic.server.citizen.model;

/**
 * Server-authoritative citizenship status (FR-CIT-001-A §3.1).
 *
 * <p>Status is a political/administrative state owned by FR-CIT; it never
 * grants or revokes technical permissions. The initial default for every
 * provisioned player is {@link #CITIZEN} (constitution: citizenship is the
 * baseline political identity).</p>
 */
public enum CitizenStatus {
    /** Full citizenship (baseline political identity; default). */
    CITIZEN,

    /** Not a citizen. */
    NON_CITIZEN,

    /** Citizenship suspended (specific eligibility rules belong to later designs). */
    SUSPENDED,

    /** Citizenship revoked (specific eligibility rules belong to later designs). */
    REVOKED
}
