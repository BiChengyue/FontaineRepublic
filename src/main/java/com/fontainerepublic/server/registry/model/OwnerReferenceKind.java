package com.fontainerepublic.server.registry.model;

/**
 * Kind of an {@link OwnerReference} (FR-ID-001-A §3.4).
 *
 * <p>Future owner types (enterprise, institution, city, ...) must contribute a
 * closed codec and Service integration; they do not give FR-ID direct access
 * to their repositories or NBT.</p>
 */
public enum OwnerReferenceKind {

    /** Authenticated Minecraft player UUID obtained from PlayerData. */
    PLAYER_UUID("PLAYER_UUID"),

    /** The permanent Hydro Archon office itself (constant, not its holder). */
    OFFICE_ID("OFFICE_ID");

    private final String keyPrefix;

    OwnerReferenceKind(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /** Reversible canonical index key prefix of this owner kind. */
    public String keyPrefix() {
        return keyPrefix;
    }
}
