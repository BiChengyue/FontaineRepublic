package com.fontainerepublic.server.registry.service;

import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.BootstrapAttemptRecord;
import com.fontainerepublic.server.registry.model.BootstrapAttemptResult;
import com.fontainerepublic.server.registry.model.BootstrapDigests;
import com.fontainerepublic.server.registry.model.BootstrapPhase;
import com.fontainerepublic.server.registry.model.BootstrapSourceClassification;
import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.BootstrapStatus;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryRepository;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryUnavailableException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link SubjectBootstrapService}
 * (FR-ID-BOOTSTRAP-001-A §5).
 *
 * <p>Execution is owner-thread-scoped through the single-writer
 * {@link SubjectRegistryRepository}. Every attempt first appends a PENDING
 * trail record through the durable gate, then a terminal record; the
 * successful binding appends the SUCCESS terminal record in the same
 * replacement snapshot that materializes the subject and flips
 * {@link BootstrapState} to BOUND. The console classification is revalidated
 * at this final mutation boundary. A binding is immutable once committed.</p>
 */
public final class DefaultSubjectBootstrapService implements SubjectBootstrapService {

    /** Upper bound of the operator reason (bounded input contract). */
    public static final int MAX_REASON_LENGTH = 200;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SubjectRegistryRepository repository;
    private final LongSupplier clock;
    private final PlayerPresence playerPresence;

    public DefaultSubjectBootstrapService(
            SubjectRegistryRepository repository,
            LongSupplier clock,
            PlayerPresence playerPresence
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerPresence = Objects.requireNonNull(playerPresence, "playerPresence");
    }

    @Override
    public BootstrapAttemptResult bootstrapOriginalPerson(
            UUID playerUuid,
            String reason,
            BootstrapSourceClassification source
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(source, "source");

        // 1. Syntax/input validation (failures still leave a durable trail).
        if (!isCanonical(playerUuid) || !isValidReason(reason)) {
            return recordAttempt(
                    playerUuid, reason, source, BootstrapAttemptResult.REJECTED_INPUT
            );
        }

        // 2. Console source classification — revalidated at this boundary.
        if (!BootstrapConsoleClassifier.isLocalConsole(source)) {
            return recordAttempt(
                    playerUuid, reason, source, BootstrapAttemptResult.REJECTED_SOURCE
            );
        }

        // 3. Immutability: a committed binding accepts only idempotent replay.
        BootstrapState state = repository.bootstrapState();
        if (state.phase() == BootstrapPhase.BOUND) {
            boolean sameKey = Arrays.equals(
                    state.boundUuidDigest(),
                    BootstrapDigests.uuidDigest(playerUuid)
            );
            return recordAttempt(
                    playerUuid,
                    reason,
                    source,
                    sameKey ? BootstrapAttemptResult.IDEMPOTENT_NOOP
                            : BootstrapAttemptResult.REJECTED
            );
        }

        // 4. PENDING is durably recorded before any further resolution.
        long now = now();
        repository.appendBootstrapPending(playerUuid, reason, source, now);

        // 5. PlayerData precondition (retryable with the same idempotency key).
        if (!playerPresence.isAvailable() || !playerPresence.hasPlayerRecord(playerUuid)) {
            return recordTerminal(
                    playerUuid,
                    reason,
                    source,
                    BootstrapAttemptResult.PLAYER_NOT_PROVISIONED,
                    now
            );
        }

        // 6. One replacement snapshot: subject + indexes + BOUND + SUCCESS.
        try {
            repository.applyOriginalPersonBinding(playerUuid, reason, source, now);
            return BootstrapAttemptResult.SUCCESS;
        } catch (SubjectRegistryUnavailableException failure) {
            // Nothing was published; record the persistence failure so the
            // incomplete attempt is recoverable on retry.
            recordTerminal(
                    playerUuid,
                    reason,
                    source,
                    BootstrapAttemptResult.PERSISTENCE_FAILURE,
                    now()
            );
            LOGGER.warn(
                    "[Bootstrap] Original-person binding commit failed for {}: {}",
                    playerUuid,
                    failure.getMessage()
            );
            return BootstrapAttemptResult.PERSISTENCE_FAILURE;
        }
    }

    @Override
    public BootstrapStatus status() {
        BootstrapState state = repository.bootstrapState();
        Optional<BootstrapAttemptRecord> last = repository.lastBootstrapAttempt();
        return new BootstrapStatus(
                state.phase(),
                state.boundUuidDigest(),
                state.boundAt(),
                state.trailHeadDigest(),
                repository.bootstrapAttempts().size(),
                last.map(BootstrapAttemptRecord::resultCode)
        );
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /**
     * Records a fully rejected/idempotent attempt: PENDING then the terminal
     * result (same idempotency key as the target UUID). Store failures are
     * logged and swallowed so the command still reports the stable result.
     */
    private BootstrapAttemptResult recordAttempt(
            UUID playerUuid,
            String reason,
            BootstrapSourceClassification source,
            BootstrapAttemptResult result
    ) {
        long timestamp = now();
        try {
            repository.appendBootstrapPending(playerUuid, reason, source, timestamp);
            repository.appendBootstrapTerminal(
                    playerUuid, reason, source, result, timestamp
            );
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[Bootstrap] Attempt trail could not record {} for {}: {}",
                    result,
                    playerUuid,
                    failure.getMessage()
            );
        }
        return result;
    }

    /** Appends only the terminal record after an already-recorded PENDING. */
    private BootstrapAttemptResult recordTerminal(
            UUID playerUuid,
            String reason,
            BootstrapSourceClassification source,
            BootstrapAttemptResult result,
            long timestamp
    ) {
        try {
            repository.appendBootstrapTerminal(
                    playerUuid, reason, source, result, timestamp
            );
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[Bootstrap] Terminal trail record {} could not be written for {}: {}",
                    result,
                    playerUuid,
                    failure.getMessage()
            );
        }
        return result;
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }

    private static boolean isCanonical(UUID uuid) {
        return uuid.toString().equals(uuid.toString().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isValidReason(String reason) {
        return !reason.isBlank() && reason.length() <= MAX_REASON_LENGTH;
    }
}
