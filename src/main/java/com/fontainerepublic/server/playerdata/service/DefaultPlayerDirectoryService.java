package com.fontainerepublic.server.playerdata.service;

import com.fontainerepublic.server.playerdata.api.PlayerDirectoryService;
import com.fontainerepublic.server.playerdata.model.DirectoryEntry;
import com.fontainerepublic.server.playerdata.model.GameNameNormalizer;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolution;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default read-only directory service over the player-data repository
 * (FR-DATA-003-A §4.3 / §5.2).
 *
 * <p>The result is derived, never stored as independently mutable truth.
 * Impossible combinations are treated as store corruption and fail closed.</p>
 */
public final class DefaultPlayerDirectoryService implements PlayerDirectoryService {
    private final PlayerDataRepository repository;

    public DefaultPlayerDirectoryService(PlayerDataRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public PlayerNameResolution resolveExactGameName(String input) {
        Optional<String> key = GameNameNormalizer.normalize(input);
        if (key.isEmpty()) {
            return PlayerNameResolution.invalidInput();
        }

        Optional<DirectoryEntry> entry = repository.findDirectoryEntry(key.get());
        if (entry.isEmpty()) {
            return PlayerNameResolution.unknown();
        }

        DirectoryEntry directoryEntry = entry.get();
        if (directoryEntry.permanentlyAmbiguous()) {
            return PlayerNameResolution.ambiguous();
        }
        if (directoryEntry.currentOwners().size() > 1) {
            return PlayerNameResolution.ambiguous();
        }
        if (directoryEntry.currentOwners().size() == 1) {
            UUID owner = directoryEntry.currentOwners().iterator().next();
            if (directoryEntry.uniqueHistoricalOwner().isPresent()
                    && directoryEntry.uniqueHistoricalOwner().get().equals(owner)) {
                return PlayerNameResolution.uniqueCurrent(owner);
            }
            throw invariantViolation(key.get());
        }
        if (directoryEntry.uniqueHistoricalOwner().isPresent()) {
            return PlayerNameResolution.retired();
        }
        throw invariantViolation(key.get());
    }

    private IllegalStateException invariantViolation(String normalizedName) {
        return new IllegalStateException(
                "player-data directory invariant violated for entry " + normalizedName
        );
    }
}
