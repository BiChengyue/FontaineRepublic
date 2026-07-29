package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.server.playerdata.model.PlayerData;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-writer repository for authoritative player records.
 *
 * <p>This class is the sole production owner of the {@code "player-data"} NBT
 * namespace. No other production code may call
 * {@link DataManager#getModuleData(String)} or
 * {@link DataManager#putModuleData(String, CompoundTag)} for that key.</p>
 */
public final class PlayerDataRepository {
    /** Reserved module-data key for the player-data namespace. */
    public static final String MODULE_DATA_KEY = "player-data";

    private final PlayerDataStore store;
    private final PlayerDataNbtCodec codec;
    private final Thread ownerThread;
    private final LinkedHashMap<UUID, PlayerData> players = new LinkedHashMap<>();

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
        storeRevision = snapshot.storeRevision();
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

    public PlayerData create(PlayerData playerData) {
        requireOwnerThread();
        Objects.requireNonNull(playerData, "playerData");
        UUID playerId = playerData.identity().playerId();
        if (players.containsKey(playerId)) {
            throw new IllegalStateException("Player UUID is already registered: " + playerId);
        }

        LinkedHashMap<UUID, PlayerData> updated = new LinkedHashMap<>(players);
        updated.put(playerId, playerData);
        commit(updated);
        return playerData;
    }

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
        commit(updated);
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
                players
        );
    }

    private void commit(LinkedHashMap<UUID, PlayerData> updated) {
        if (storeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("player-data store revision overflow");
        }
        long nextStoreRevision = storeRevision + 1;
        PlayerDataStoreSnapshot next = new PlayerDataStoreSnapshot(
                PlayerDataStoreSnapshot.CURRENT_STORE_VERSION,
                nextStoreRevision,
                updated
        );

        store.save(codec.encode(next));
        players.clear();
        players.putAll(updated);
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
    }
}
