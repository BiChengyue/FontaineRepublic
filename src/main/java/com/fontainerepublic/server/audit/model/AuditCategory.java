package com.fontainerepublic.server.audit.model;

/**
 * Initial closed set of audit categories (FR-AUD-001-A §3.2).
 *
 * <p>Extensible only by a reviewed design. {@link #EMERGENCY_REFERENCE}
 * carries only safe cross-references (action id, timestamp) to FR-EMG
 * records; it never duplicates emergency content and FR-AUD never reads the
 * FR-EMG namespace (FR-EMG isolation).</p>
 */
public enum AuditCategory {
    IDENTITY,
    LAND,
    FINANCE,
    LEGISLATION,
    ADMINISTRATION,
    JUDICIAL,
    GOVERNANCE,
    EMERGENCY_REFERENCE,
    SYSTEM
}
