package com.fontainerepublic.server.citizen.service;

import com.fontainerepublic.server.citizen.api.CitizenChangeKind;
import com.fontainerepublic.server.citizen.api.CitizenReceipt;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.api.SubjectDirectory;
import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.citizen.persistence.CitizenRepository;
import com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.SubjectRecord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link CitizenService} (FR-CIT-001-A §4).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link CitizenRepository}; it supplies the server clock and the ordered
 * {@code PlayerData -> subject -> citizen} provisioning chain. Reads are exact
 * and projection-only; writes publish only after the durable gate commits.</p>
 */
public final class DefaultCitizenService implements CitizenService {

    private final CitizenRepository repository;
    private final LongSupplier clock;
    private final PlayerPresence playerPresence;
    private final SubjectDirectory subjectDirectory;

    public DefaultCitizenService(
            CitizenRepository repository,
            LongSupplier clock,
            PlayerPresence playerPresence,
            SubjectDirectory subjectDirectory
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerPresence = Objects.requireNonNull(playerPresence, "playerPresence");
        this.subjectDirectory = Objects.requireNonNull(subjectDirectory, "subjectDirectory");
    }

    @Override
    public CitizenRecord ensureCitizen(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!playerPresence.isAvailable()) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                    "Player-data service is not available for citizen provisioning"
            );
        }
        if (!playerPresence.hasPlayerRecord(playerId)) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
        if (!subjectDirectory.isAvailable()) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE,
                    "Subject registry is not available for citizen provisioning"
            );
        }
        SubjectRecord subject = subjectDirectory.ensureSubject(playerId);
        return repository.ensure(playerId, subject.subjectId(), now());
    }

    @Override
    public Optional<CitizenRecord> getCitizen(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.findByPlayer(playerId);
    }

    @Override
    public CitizenReceipt setRank(UUID playerId, CitizenRank rank) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rank, "rank");
        CitizenRecord current = requireCitizen(playerId);
        requireBoundSubject(current);
        CitizenRecord updated = repository.updateRank(playerId, rank);
        return new CitizenReceipt(
                CitizenChangeKind.RANK,
                updated,
                !updated.equals(current),
                now()
        );
    }

    @Override
    public CitizenReceipt setStatus(UUID playerId, CitizenStatus status) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(status, "status");
        CitizenRecord current = requireCitizen(playerId);
        requireBoundSubject(current);
        CitizenRecord updated = repository.updateStatus(playerId, status);
        return new CitizenReceipt(
                CitizenChangeKind.STATUS,
                updated,
                !updated.equals(current),
                now()
        );
    }

    @Override
    public List<CitizenService.CitizenIdentity> activeCitizens(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        CitizenRepository repo = repository;
        if (repo == null) {
            return List.of();
        }
        List<CitizenService.CitizenIdentity> result = new java.util.ArrayList<>();
        for (CitizenRecord record : repo.snapshot().citizens().values()) {
            if (record.status() != CitizenStatus.CITIZEN) {
                continue;
            }
            result.add(new CitizenService.CitizenIdentity(record.playerId(), record.subjectId()));
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private CitizenRecord requireCitizen(UUID playerId) {
        return repository.findByPlayer(playerId).orElseThrow(
                () -> new CitizenUnavailableException(
                        CitizenUnavailableException.CODE_NO_CITIZEN_RECORD,
                        "No citizen record for player " + playerId
                )
        );
    }

    /**
     * Defensive consistency check at the mutation boundary: a citizen record
     * must always resolve to a live subject (FR-CIT-001-A §4.1). Rejects the
     * mutation fail-closed when the subject registry is unavailable or the
     * bound subject no longer exists.
     */
    private void requireBoundSubject(CitizenRecord record) {
        if (!subjectDirectory.isAvailable()
                || !subjectDirectory.hasSubject(record.subjectId())) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_SUBJECT_MISSING,
                    "Citizen " + record.playerId() + " is bound to a missing subject "
                            + record.subjectId()
            );
        }
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
