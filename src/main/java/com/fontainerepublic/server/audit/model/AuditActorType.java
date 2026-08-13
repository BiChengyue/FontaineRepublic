package com.fontainerepublic.server.audit.model;

/**
 * Closed actor classification for audit entries (FR-AUD-001-A §3.1).
 *
 * <p>{@link #PLAYER} actors are addressed by canonical UUID; the other types
 * use a bounded canonical string id. No fabricated identity is ever created:
 * when the actor is unknown the entry is recorded with type plus bounded id
 * only (FR-AUD-001-A §7).</p>
 */
public enum AuditActorType {
    PLAYER,
    SERVER_CONSOLE,
    SYSTEM
}
