package com.fontainerepublic.server.institutionaccess.api;

/**
 * Kind of one committed zone change (FR-INST-002-B §5).
 */
public enum ZoneChangeKind {
    ZONE_ADDED,
    ZONE_REMOVED,
    ZONE_RESIZED,
    ZONE_KIND_CHANGED,
    ZONE_SUSPENDED,
    ZONE_ACTIVATED
}
