package com.fontainerepublic.server.economy.api;

import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Identity resolution boundary of the economy module
 * (FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.3).
 *
 * <p>Every input route (authenticated self UUID, explicit target UUID) is
 * resolved to the natural-person {@link SubjectId} through the PlayerData and
 * FR-ID subject services before any Economy operation; the player name route
 * stays disabled until FR-DATA-003. The production binding is wired by the
 * economy module after runtime start; until bound, every query reports
 * unavailable and mutations fail closed. Economy never scans PlayerData NBT
 * or the registry.</p>
 */
public interface SubjectDirectory {

    /** Whether the underlying PlayerData/subject services are bound. */
    boolean isAvailable();

    /**
     * Lazy, idempotent provisioning of the natural-person subject for a
     * verified player UUID (requires an authoritative PlayerData record).
     */
    SubjectRecord ensureSubject(UUID playerId);

    /** Whether a subject with the exact id exists in the registry. */
    boolean hasSubject(SubjectId subjectId);

    /**
     * Current infrastructure status of a subject, if known. Empty when the
     * registry is unavailable or the subject does not exist (fail closed).
     */
    Optional<SubjectStatus> status(SubjectId subjectId);
}
