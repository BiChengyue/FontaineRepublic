package com.fontainerepublic.server.playerdata.model;

/**
 * Provenance of the directory section (FR-DATA-003-A §8).
 *
 * <p>{@link #MIGRATED_FROM_LAST_KNOWN_ONLY} records the acknowledged blind
 * spot of the initial v1 migration: the directory reflects only the names
 * retained in the last PlayerData snapshot and cannot prove pre-migration
 * history. {@link #NONE} marks a directory built entirely from post-activation
 * server-verified observations.</p>
 */
public enum MigrationProvenance {
    NONE,
    MIGRATED_FROM_LAST_KNOWN_ONLY
}
