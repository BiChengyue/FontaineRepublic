package com.fontainerepublic.server.parliament.service;

import java.util.UUID;

/**
 * Citizen/actor resolution boundary of the parliament module (FR-PAR-001-A
 * §2/§4).
 *
 * <p>Proposers and voters are resolved through the PlayerData and FR-CIT
 * services (never raw NBT, never a game name): a UUID must have an
 * authoritative PlayerData record and an active {@code CITIZEN} status to act
 * in the legislative pipeline. The production binding is wired by the
 * parliament module after runtime start; until bound, every query reports
 * unavailable and every mutation fails closed.</p>
 *
 * <p>This interface deliberately exposes no citizen enumeration: the module
 * maintains its own passive citizen roster (registered citizens who
 * interacted with the parliament), frozen per ballot at vote open
 * (FR-BL-005 §2), because FR-CIT exposes no bulk citizen list and this
 * module must not enumerate other modules' storage.</p>
 */
public interface ParliamentCitizenDirectory {

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
