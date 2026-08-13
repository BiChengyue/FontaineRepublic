package com.fontainerepublic.server.justice.service;

import java.util.UUID;

/**
 * Citizen/actor resolution boundary of the justice module (FR-JUS-001-A
 * §2/§4).
 *
 * <p>Filing parties, judges, and reviewers are resolved through the
 * PlayerData and FR-CIT services (never raw NBT, never a game name): a UUID
 * must have an authoritative PlayerData record and an active {@code CITIZEN}
 * status to act in the judicial pipeline (standing). The production binding
 * is wired by the justice module after runtime start; until bound, every
 * query reports unavailable and every mutation fails closed.</p>
 *
 * <p>This interface deliberately exposes no citizen enumeration: the module
 * must not enumerate other modules' storage.</p>
 */
public interface JusticeCitizenDirectory {

    /** Whether the underlying PlayerData/citizen services are bound. */
    boolean isAvailable();

    /** Whether the UUID has an authoritative PlayerData record. */
    boolean hasPlayerRecord(UUID playerId);

    /**
     * Whether the UUID is an active citizen (FR-CIT status {@code CITIZEN}).
     * Returns {@code false} on unavailable services or a missing record
     * (fail closed).
     */
    boolean isActiveCitizen(UUID playerId);
}
