package com.fontainerepublic.server.institutionaccess.api;

import java.util.Objects;

/**
 * Outcome of the final mutation-time revalidation of an on-site context
 * (FR-INST-001-A §7.3, FR-INST-001-B §4).
 *
 * <p>The final revalidation is mandatory at every mutation boundary and
 * cannot be disabled by configuration. {@link ValidationStatus#VALID} means
 * the business mutation may proceed; every other outcome fails closed with a
 * stable machine-readable {@code reason} code.</p>
 */
public record ValidationResult(
        ValidationStatus status,
        String reason
) {

    public static final String REASON_OK = "OK";
    public static final String REASON_NOT_ISSUED = "NOT_ISSUED";
    public static final String REASON_EXPIRED = "EXPIRED";
    public static final String REASON_CAPABILITY_MISMATCH = "CAPABILITY_MISMATCH";
    public static final String REASON_CONSUMED = "CONSUMED";
    public static final String REASON_INVALIDATED = "INVALIDATED";
    public static final String REASON_FACILITY_NOT_ACTIVE = "FACILITY_NOT_ACTIVE";
    public static final String REASON_FACILITY_REVISION = "FACILITY_REVISION";
    public static final String REASON_ZONE_NOT_ACTIVE = "ZONE_NOT_ACTIVE";
    public static final String REASON_ZONE_REVISION = "ZONE_REVISION";
    public static final String REASON_ZONE_NOT_IN_REGION = "ZONE_NOT_IN_REGION";
    public static final String REASON_PLAYER_OUT_OF_RANGE = "PLAYER_OUT_OF_RANGE";
    public static final String REASON_DIMENSION_MISMATCH = "DIMENSION_MISMATCH";

    public ValidationResult {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
    }

    public boolean valid() {
        return status == ValidationStatus.VALID;
    }

    /** Static factory for a VALID result. */
    public static ValidationResult ok() {
        return new ValidationResult(ValidationStatus.VALID, REASON_OK);
    }

    public static ValidationResult invalid(String reason) {
        return new ValidationResult(ValidationStatus.INVALID, reason);
    }
}
