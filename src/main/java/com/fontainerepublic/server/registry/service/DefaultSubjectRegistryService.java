package com.fontainerepublic.server.registry.service;

import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.api.PublicRoutingResult;
import com.fontainerepublic.server.registry.api.SubjectProjection;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryRepository;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryUnavailableException;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link SubjectRegistryService} (FR-ID-001-A §8).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link SubjectRegistryRepository}; it supplies the server clock and the
 * authoritative PlayerData precondition. Reads are exact and projection-only;
 * writes publish only after the durable gate commits.</p>
 */
public final class DefaultSubjectRegistryService implements SubjectRegistryService {

    private final SubjectRegistryRepository repository;
    private final LongSupplier clock;
    private final PlayerPresence playerPresence;

    public DefaultSubjectRegistryService(
            SubjectRegistryRepository repository,
            LongSupplier clock,
            PlayerPresence playerPresence
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerPresence = Objects.requireNonNull(playerPresence, "playerPresence");
    }

    @Override
    public SubjectRecord ensurePlayerSubject(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!playerPresence.isAvailable()) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                    "Player-data service is not available for subject provisioning"
            );
        }
        if (!playerPresence.hasPlayerRecord(playerId)) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }

        OwnerReference owner = OwnerReference.forPlayer(playerId);
        Optional<SubjectRecord> existing = repository.findByOwner(owner);
        if (existing.isPresent()) {
            SubjectRecord record = existing.get();
            if (record.subjectType() != SubjectType.NATURAL_PERSON) {
                throw new IllegalStateException(
                        "Owner " + owner.key() + " maps to a non-natural subject"
                );
            }
            return record;
        }
        return repository.provision(owner, SubjectType.NATURAL_PERSON, now());
    }

    @Override
    public Optional<SubjectRecord> findSubjectForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.findByOwner(OwnerReference.forPlayer(playerId));
    }

    @Override
    public Optional<SubjectRecord> findBySubjectId(SubjectId subjectId) {
        return repository.findBySubjectId(subjectId);
    }

    @Override
    public PublicRoutingResult resolveExactRegistryNumber(RegistryNumber number) {
        Objects.requireNonNull(number, "number");
        Optional<SubjectRecord> record = repository.findByNumber(number);
        if (record.isEmpty()) {
            return PublicRoutingResult.unknownOrInvalid();
        }
        SubjectRecord subject = record.get();
        if (subject.status() == SubjectStatus.ACTIVE) {
            return PublicRoutingResult.routable(
                    new SubjectProjection(
                            subject.subjectId(),
                            subject.registryNumber(),
                            subject.subjectType(),
                            subject.status()
                    )
            );
        }
        return PublicRoutingResult.knownNonActive();
    }

    @Override
    public Optional<SubjectStatus> status(SubjectId subjectId) {
        return repository.findBySubjectId(subjectId).map(SubjectRecord::status);
    }

    @Override
    public SubjectRecord updateStatus(SubjectId subjectId, SubjectStatus status) {
        return repository.updateStatus(subjectId, status, now());
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
