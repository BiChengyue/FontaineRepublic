package com.fontainerepublic.server.citizen.api;

import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the citizen module (FR-CIT-001-A §4).
 *
 * <p>The service owns citizenship status and political rank. It exposes no
 * bulk citizen list, no repositories, no NBT, no balance, land, case, office,
 * registry-number, or permission fields, and no rank-to-permission mapping.
 * All mutations run on the logical server owner thread and publish only after
 * the FR-CORE-002 durable gate reports {@code COMMITTED}.</p>
 *
 * <p>Provisioning follows the ordered chain {@code PlayerData -> FR-ID subject
 * -> citizen record}; a citizen record without a subject is invalid.</p>
 */
public interface CitizenService {

    /**
     * Lazy, idempotent, owner-threaded provisioning of the citizen record for
     * a verified player UUID (FR-CIT-001-A §4.1).
     *
     * <p>Requires the UUID to have an authoritative PlayerData record and a
     * provisioned FR-ID subject; the subject is created idempotently through
     * the subject registry before the citizen record is ensured. Repeated
     * calls return the same persisted record unchanged.</p>
     *
     * @throws com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException
     *         with a stable code when player-data or the subject registry is
     *         unavailable, the player is not provisioned, capacity is
     *         exhausted, or the durable store rejected the snapshot
     */
    CitizenRecord ensureCitizen(UUID playerId);

    /** Exact lookup: the citizen record of a player UUID, if any. */
    Optional<CitizenRecord> getCitizen(UUID playerId);

    /**
     * Authoritative rank replacement: one complete replacement snapshot,
     * record revision and store revision +1 exactly once, published only
     * after the durable gate commits. Same-rank requests are idempotent
     * no-ops. Rank never grants technical permission.
     *
     * @throws com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException
     *         when there is no citizen record, the bound subject is missing,
     *         or the durable store rejected the snapshot
     */
    CitizenReceipt setRank(UUID playerId, CitizenRank rank);

    /**
     * Authoritative status replacement: one complete replacement snapshot,
     * record revision and store revision +1 exactly once, published only
     * after the durable gate commits. Same-status requests are idempotent
     * no-ops.
     *
     * @throws com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException
     *         when there is no citizen record, the bound subject is missing,
     *         or the durable store rejected the snapshot
     */
    CitizenReceipt setStatus(UUID playerId, CitizenStatus status);
}
