package com.fontainerepublic.server.registry.api;

import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the unified digital subject registry
 * (FR-ID-001-A §7).
 *
 * <p>The service returns immutable, purpose-bounded views. It exposes no
 * all-subject iteration, number-prefix lookup, owner-prefix lookup,
 * repositories, NBT, mutable indexes, balances, profiles, citizenship, roles,
 * or permissions. All mutations run on the logical server owner thread and
 * publish only after the FR-CORE-002 durable gate reports {@code COMMITTED}.</p>
 *
 * <p>Consumer note: knowing or paying a public number grants no debit, balance
 * lookup, impersonation, office, or FR-EMG authority. Consumers must re-resolve
 * and revalidate status at their final mutation boundary.</p>
 */
public interface SubjectRegistryService {

    /**
     * Lazy, idempotent, owner-threaded provisioning of the natural-person
     * subject for a verified player UUID (FR-ID-001-A §8).
     *
     * <p>Requires the UUID to have an authoritative PlayerData record.
     * Repeated calls for the same UUID return the same persisted subject
     * unchanged.</p>
     *
     * @throws com.fontainerepublic.server.registry.persistence.SubjectRegistryUnavailableException
     *         with a stable code when player-data is unavailable, the player is
     *         not provisioned, allocation is exhausted, or the durable store
     *         rejected the snapshot
     */
    SubjectRecord ensurePlayerSubject(UUID playerId);

    /** Exact owner lookup: the subject of a player UUID, if any. */
    Optional<SubjectRecord> findSubjectForPlayer(UUID playerId);

    /** Exact internal lookup by {@link SubjectId}. */
    Optional<SubjectRecord> findBySubjectId(SubjectId subjectId);

    /**
     * Exact, bounded, non-enumerating public lookup of one fully formed
     * number (FR-ID-001-A §13). The number must already be parsed/validated
     * by the caller.
     */
    PublicRoutingResult resolveExactRegistryNumber(RegistryNumber number);

    /** Current infrastructure status of a subject, if known. */
    Optional<SubjectStatus> status(SubjectId subjectId);

    /**
     * Status replacement for an existing subject: same identity and number,
     * record and store revision +1 exactly once. Same-status requests are
     * idempotent no-ops. The authority and grounds for status transitions are
     * intentionally not decided by this module (FR-ID-001-A §3.5).
     */
    SubjectRecord updateStatus(SubjectId subjectId, SubjectStatus status);
}
