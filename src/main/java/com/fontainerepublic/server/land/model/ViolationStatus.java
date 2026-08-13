package com.fontainerepublic.server.land.model;

/**
 * Lifecycle status of a violation report (FR-LAND-001-A §3.4).
 *
 * <p>Reports are created {@code OPEN} by FR-LAND and are read-only here;
 * adjudication, evidence, and verdict belong to the future Justice module,
 * which will define further transitions. This enum has no transition API.</p>
 */
public enum ViolationStatus {
    /** Report submitted; awaiting adjudication by Justice (later). */
    OPEN
}
