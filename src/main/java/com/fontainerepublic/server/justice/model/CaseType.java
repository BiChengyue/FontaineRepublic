package com.fontainerepublic.server.justice.model;

/**
 * Closed set of case types (FR-JUS-001-A §3.1).
 *
 * <p>{@link #LAND} cases are created exclusively through the bounded
 * violation-report intake; the other types are filed by citizens on site.
 * Extending the set requires a reviewed design.</p>
 */
public enum CaseType {
    CIVIL,
    CRIMINAL,
    ADMINISTRATIVE,
    LAND
}
