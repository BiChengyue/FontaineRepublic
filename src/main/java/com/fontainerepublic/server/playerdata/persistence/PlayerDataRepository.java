package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.playerdata.model.DirectoryEntry;
import com.fontainerepublic.server.playerdata.model.GameNameNormalizer;
import com.fontainerepublic.server.playerdata.model.MigrationProvenance;
import com.fontainerepublic.server.playerdata.model.PlayerData;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Single-writer repository for authoritative player records and the exact-name
 * directory (FR-DATA-003-A §7.5).
 *
 * <p>This class is the sole production owner of the {@code "player-data"} NBT
 * namespace. No other production code may call
 * {@link DataManager#getModuleData(String)} or
 * {@link DataManager#putModuleData(String, CompoundTag)} for that key.
 * Every mutation proposes a complete immutable snapshot containing Players and
 * Directory together, encodes and validates it, and publishes live state only
 * after the storage adapter reports {@link DurableCommitStatus#COMMITTED}. On
 * any failure the players, directory, revisions, and StoreRevision are
 * unchanged.</p>
 */
public final class PlayerDataRepository {
    /** Reserved module-data key for the player-data namespace. */
    public static final String MODULE_DATA_KEY = "player-data";

    private final PlayerDataStore store;
    private final PlayerDataNbtCodec codec;
    private final Thread ownerThread;
    private final LinkedHashMap<UUID, PlayerData> players = new LinkedHashMap<>();
    private final LinkedHashMap<String, DirectoryEntry> directory = new LinkedHashMap<>();
    private final MigrationProvenance migrationProvenance;

    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static PlayerDataRepository createProduction(PlayerDataNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new PlayerDataRepository(new DataManagerStore(), codec);
    }

    public PlayerDataRepository(PlayerDataStore store, PlayerDataNbtCodec codec) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.ownerThread = Thread.currentThread();

        PlayerDataStoreSnapshot snapshot = codec.decode(store.load().copy());
        players.putAll(snapshot.players());
        directory.putAll(snapshot.directory());
        storeRevision = snapshot.storeRevision();
        migrationProvenance = snapshot.migrationProvenance();
    }

    public Optional<PlayerData> find(UUID playerId) {
        requireOwnerThread();
        return Optional.ofNullable(players.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public PlayerData require(UUID playerId) {
        return find(playerId).orElseThrow(
                () -> new IllegalArgumentException("Unknown player UUID: " + playerId)
        );
    }

    /**
     * Read-only exact lookup of one directory entry by canonical normalized
     * name. No enumeration, prefix, fuzzy, or bulk access is exposed.
     */
    public Optional<DirectoryEntry> findDirectoryEntry(String normalizedName) {
        requireOwnerThread();
        Objects.requireNonNull(normalizedName, "normalizedName");
        if (GameNameNormalizer.normalize(normalizedName).isEmpty()
                || !normalizedName.equals(normalizedName.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "normalizedName must be a canonical lowercase game-name key"
            );
        }
        return Optional.ofNullable(directory.get(normalizedName));
    }

    /**
     * Registers a new player and observes its verified name as one alias
     * observation in the same proposed snapshot (FR-DATA-003-A §6.1).
     */
    public PlayerData create(PlayerData playerData) {
        requireOwnerThread();
        Objects.requireNonNull(playerData, "playerData");
        UUID playerId = playerData.identity().playerId();
        if (players.containsKey(playerId)) {
            throw new IllegalStateException("Player UUID is already registered: " + playerId);
        }

        LinkedHashMap<UUID, PlayerData> updated = new LinkedHashMap<>(players);
        updated.put(playerId, playerData);

        String name = playerData.identity().lastKnownGameName();
        String normalized = requireNormalized(name, playerId);
        long observedAt = playerData.identity().lastSeenAt();
        Map<String, DirectoryEntry> updatedDirectory = new LinkedHashMap<>(directory);
        DirectoryEntry existing = updatedDirectory.get(normalized);
        updatedDirectory.put(
                normalized,
                existing == null
                        ? newEntry(normalized, name, playerId, observedAt)
                        : addOwner(existing, playerId, observedAt)
        );

        commit(updated, updatedDirectory);
        return playerData;
    }

    /**
     * Replaces one player record; derives any directory change (case-only
     * spelling update or full rename) from the identity difference in the
     * same atomic snapshot (FR-DATA-003-A §6.2–§6.4).
     */
    public PlayerData replace(PlayerData playerData, long expectedRevision) {
        requireOwnerThread();
        Objects.requireNonNull(playerData, "playerData");
        UUID playerId = playerData.identity().playerId();
        PlayerData current = require(playerId);
        if (current.revision() != expectedRevision) {
            throw new StalePlayerDataRevisionException(
                    playerId,
                    expectedRevision,
                    current.revision()
            );
        }
        if (playerData.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException(
                    "Replacement revision must increment exactly once"
            );
        }

        LinkedHashMap<UUID, PlayerData> updated = new LinkedHashMap<>(players);
        updated.put(playerId, playerData);
        Map<String, DirectoryEntry> updatedDirectory = deriveDirectory(
                playerId,
                current,
                playerData,
                directory,
                playerData.identity().lastSeenAt()
        );

        commit(updated, updatedDirectory);
        return playerData;
    }

    public int size() {
        requireOwnerThread();
        return players.size();
    }

    public PlayerDataStoreSnapshot snapshot() {
        requireOwnerThread();
        return new PlayerDataStoreSnapshot(
                PlayerDataStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                players,
                directory,
                migrationProvenance
        );
    }

    // ------------------------------------------------------------------
    // directory derivation (FR-DATA-003-A §6)
    // ------------------------------------------------------------------

    private Map<String, DirectoryEntry> deriveDirectory(
            UUID playerId,
            PlayerData before,
            PlayerData after,
            Map<String, DirectoryEntry> currentDirectory,
            long observedAt
    ) {
        String beforeName = before.identity().lastKnownGameName();
        String afterName = after.identity().lastKnownGameName();
        String beforeKey = requireNormalized(beforeName, playerId);
        String afterKey = requireNormalized(afterName, playerId);

        Map<String, DirectoryEntry> next = new LinkedHashMap<>(currentDirectory);
        if (beforeKey.equals(afterKey)) {
            DirectoryEntry entry = next.get(beforeKey);
            if (entry == null) {
                next.put(beforeKey, newEntry(beforeKey, afterName, playerId, observedAt));
            } else if (!entry.lastVerifiedSpelling().equals(afterName)) {
                next.put(beforeKey, updateSpelling(entry, afterName, observedAt));
            }
            return next;
        }

        // Rename X -> Y: remove A from X, add A to Y.
        DirectoryEntry from = next.get(beforeKey);
        if (from != null) {
            next.put(beforeKey, removeOwner(from, playerId));
        }
        DirectoryEntry to = next.get(afterKey);
        next.put(
                afterKey,
                to == null
                        ? newEntry(afterKey, afterName, playerId, observedAt)
                        : addOwner(to, playerId, observedAt)
        );
        return next;
    }

    private DirectoryEntry addOwner(DirectoryEntry entry, UUID owner, long observedAt) {
        if (entry.currentOwners().contains(owner)) {
            return entry;
        }
        Set<UUID> owners = new TreeSet<>(entry.currentOwners());
        owners.add(owner);
        Optional<UUID> unique = entry.uniqueHistoricalOwner();
        boolean ambiguous = entry.permanentlyAmbiguous();
        if (!ambiguous && unique.isPresent() && !unique.get().equals(owner)) {
            // A different historical or current UUID uses this alias:
            // permanent ambiguity in the same commit (FR-DATA-003-A §5.3).
            ambiguous = true;
            unique = Optional.empty();
        }
        return new DirectoryEntry(
                entry.normalizedName(),
                entry.lastVerifiedSpelling(),
                ambiguous,
                unique,
                owners,
                entry.firstObservedAt(),
                observedAt,
                entry.entryRevision() + 1
        );
    }

    private DirectoryEntry removeOwner(DirectoryEntry entry, UUID owner) {
        if (!entry.currentOwners().contains(owner)) {
            return entry;
        }
        Set<UUID> owners = new TreeSet<>(entry.currentOwners());
        owners.remove(owner);
        return new DirectoryEntry(
                entry.normalizedName(),
                entry.lastVerifiedSpelling(),
                entry.permanentlyAmbiguous(),
                entry.uniqueHistoricalOwner(),
                owners,
                entry.firstObservedAt(),
                entry.lastObservedAt(),
                entry.entryRevision() + 1
        );
    }

    private DirectoryEntry updateSpelling(DirectoryEntry entry, String spelling, long observedAt) {
        return new DirectoryEntry(
                entry.normalizedName(),
                spelling,
                entry.permanentlyAmbiguous(),
                entry.uniqueHistoricalOwner(),
                entry.currentOwners(),
                entry.firstObservedAt(),
                observedAt,
                entry.entryRevision() + 1
        );
    }

    private DirectoryEntry newEntry(
            String normalizedName,
            String spelling,
            UUID owner,
            long observedAt
    ) {
        return new DirectoryEntry(
                normalizedName,
                spelling,
                false,
                Optional.of(owner),
                Set.of(owner),
                observedAt,
                observedAt,
                1L
        );
    }

    private String requireNormalized(String name, UUID playerId) {
        return GameNameNormalizer.normalize(name).orElseThrow(() ->
                new IllegalArgumentException(
                        "Invalid game name for " + playerId + ": " + name
                )
        );
    }

    // ------------------------------------------------------------------
    // commit (FR-DATA-003-A §7.5 / §13)
    // ------------------------------------------------------------------

    private void commit(
            LinkedHashMap<UUID, PlayerData> updated,
            Map<String, DirectoryEntry> updatedDirectory
    ) {
        if (storeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("player-data store revision overflow");
        }
        long nextStoreRevision = storeRevision + 1;
        PlayerDataStoreSnapshot next = new PlayerDataStoreSnapshot(
                PlayerDataStoreSnapshot.CURRENT_STORE_VERSION,
                nextStoreRevision,
                updated,
                updatedDirectory,
                migrationProvenance
        );

        DurableCommitResult result = store.commit(codec.encode(next));
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new PlayerDataCommitException(result.status(), result.failureCode());
        }

        players.clear();
        players.putAll(updated);
        directory.clear();
        directory.putAll(updatedDirectory);
        storeRevision = nextStoreRevision;
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "PlayerDataRepository may only be accessed from its owning server thread"
            );
        }
    }

    private static final class DataManagerStore implements PlayerDataStore {
        @Override
        public CompoundTag load() {
            return DataManager.getModuleData(MODULE_DATA_KEY).copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            DataManager.putModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            return DataManager.commitModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }
    }
}
