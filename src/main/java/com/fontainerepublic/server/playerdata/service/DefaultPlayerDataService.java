package com.fontainerepublic.server.playerdata.service;

import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.playerdata.model.PlayerData;
import com.fontainerepublic.server.playerdata.model.PlayerIdentity;
import com.fontainerepublic.server.playerdata.model.PlayerProfile;
import com.fontainerepublic.server.playerdata.model.PlayerProfileUpdate;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataRepository;
import com.fontainerepublic.server.playerdata.persistence.StalePlayerDataRevisionException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Default logical-server implementation of the player-data service.
 */
public final class DefaultPlayerDataService implements PlayerDataService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PlayerDataRepository repository;
    private final LongSupplier clock;

    public DefaultPlayerDataService(
            PlayerDataRepository repository,
            LongSupplier clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlayerData ensurePlayer(UUID playerId, String verifiedGameName) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(verifiedGameName, "verifiedGameName");

        Optional<PlayerData> existing = repository.find(playerId);
        if (existing.isPresent()) {
            return existing.get();
        }

        long observedAt = now();
        PlayerIdentity identity = new PlayerIdentity(
                playerId,
                verifiedGameName,
                observedAt,
                observedAt
        );
        return repository.create(
                new PlayerData(
                        PlayerData.CURRENT_SCHEMA_VERSION,
                        1,
                        identity,
                        PlayerProfile.empty(observedAt)
                )
        );
    }

    @Override
    public PlayerData recordLogin(UUID playerId, String verifiedGameName) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(verifiedGameName, "verifiedGameName");
        long observedAt = now();

        Optional<PlayerData> existing = repository.find(playerId);
        if (existing.isEmpty()) {
            PlayerIdentity identity = new PlayerIdentity(
                    playerId,
                    verifiedGameName,
                    observedAt,
                    observedAt
            );
            return repository.create(
                    new PlayerData(
                            PlayerData.CURRENT_SCHEMA_VERSION,
                            1,
                            identity,
                            PlayerProfile.empty(observedAt)
                    )
            );
        }

        PlayerData current = existing.get();
        PlayerIdentity observedIdentity = current.identity()
                .observedAs(verifiedGameName, observedAt);
        if (observedIdentity.equals(current.identity())) {
            return current;
        }
        PlayerData replacement = current.nextRevision(
                observedIdentity,
                current.profile()
        );
        try {
            return repository.replace(replacement, current.revision());
        } catch (RuntimeException failed) {
            LOGGER.error(
                    "[PlayerData] Login update failed for {} (revision {} -> {}): {}",
                    playerId,
                    current.revision(),
                    replacement.revision(),
                    failed.getMessage(),
                    failed
            );
            throw failed;
        }
    }

    @Override
    public Optional<PlayerData> find(UUID playerId) {
        return repository.find(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public PlayerData require(UUID playerId) {
        return repository.require(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public PlayerData updateProfile(
            UUID playerId,
            long expectedRevision,
            PlayerProfileUpdate update
    ) {
        Objects.requireNonNull(update, "update");
        PlayerData current = repository.require(Objects.requireNonNull(playerId, "playerId"));
        if (current.revision() != expectedRevision) {
            throw new StalePlayerDataRevisionException(
                    playerId,
                    expectedRevision,
                    current.revision()
            );
        }

        PlayerProfile profile = update.toProfile(now());
        PlayerData replacement = current.nextRevision(current.identity(), profile);
        return repository.replace(replacement, expectedRevision);
    }

    @Override
    public void recordLogout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        repository.find(playerId).ifPresent(current -> {
            PlayerIdentity observedIdentity = current.identity()
                    .observedAs(current.identity().lastKnownGameName(), now());
            if (!observedIdentity.equals(current.identity())) {
                try {
                    repository.replace(
                            current.nextRevision(observedIdentity, current.profile()),
                            current.revision()
                    );
                } catch (RuntimeException failed) {
                    LOGGER.error(
                            "[PlayerData] Logout update failed for {} (revision {}): {}",
                            playerId,
                            current.revision(),
                            failed.getMessage(),
                            failed
                    );
                }
            }
        });
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
