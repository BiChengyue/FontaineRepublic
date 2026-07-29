package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.server.playerdata.model.PlayerData;
import com.fontainerepublic.server.playerdata.model.PlayerIdentity;
import com.fontainerepublic.server.playerdata.model.PlayerProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned NBT codec for the player-data namespace.
 */
public final class PlayerDataNbtCodec {
    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String PLAYERS = "Players";

    private static final String RECORD_VERSION = "RecordVersion";
    private static final String REVISION = "Revision";
    private static final String IDENTITY = "Identity";
    private static final String PROFILE = "Profile";

    private static final String PLAYER_ID = "PlayerId";
    private static final String LAST_KNOWN_GAME_NAME = "LastKnownGameName";
    private static final String FIRST_SEEN_AT = "FirstSeenAt";
    private static final String LAST_SEEN_AT = "LastSeenAt";

    private static final String DISPLAY_NAME = "DisplayName";
    private static final String LOCALE = "Locale";
    private static final String DESCRIPTION = "Description";
    private static final String UPDATED_AT = "UpdatedAt";

    private static final int MAX_PLAYER_RECORDS = 10_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            PLAYERS
    );
    private static final Set<String> RECORD_KEYS = Set.of(
            RECORD_VERSION,
            REVISION,
            IDENTITY,
            PROFILE
    );
    private static final Set<String> IDENTITY_KEYS = Set.of(
            PLAYER_ID,
            LAST_KNOWN_GAME_NAME,
            FIRST_SEEN_AT,
            LAST_SEEN_AT
    );
    private static final Set<String> PROFILE_KEYS = Set.of(
            DISPLAY_NAME,
            LOCALE,
            DESCRIPTION,
            UPDATED_AT
    );

    public PlayerDataStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return PlayerDataStoreSnapshot.empty();
        }

        requireOnlyKeys(root, STORE_KEYS, "player-data");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "player-data");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "player-data");
        requireType(root, PLAYERS, Tag.TAG_COMPOUND, "player-data");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != PlayerDataStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported store version: " + storeVersion);
        }

        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        CompoundTag playersTag = root.getCompound(PLAYERS);
        if (playersTag.getAllKeys().size() > MAX_PLAYER_RECORDS) {
            throw invalid("Player record count exceeds " + MAX_PLAYER_RECORDS);
        }

        Map<UUID, PlayerData> players = new LinkedHashMap<>();
        for (String key : playersTag.getAllKeys().stream().sorted().toList()) {
            UUID playerId = parseCanonicalUuid(key);
            requireType(playersTag, key, Tag.TAG_COMPOUND, "Players");
            PlayerData playerData = decodePlayer(playersTag.getCompound(key), playerId);
            if (players.put(playerId, playerData) != null) {
                throw invalid("Duplicate player UUID: " + playerId);
            }
        }

        return new PlayerDataStoreSnapshot(storeVersion, storeRevision, players);
    }

    public CompoundTag encode(PlayerDataStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag playersTag = new CompoundTag();
        TreeMap<String, PlayerData> orderedPlayers = new TreeMap<>();
        snapshot.players().forEach(
                (playerId, playerData) -> orderedPlayers.put(playerId.toString(), playerData)
        );
        orderedPlayers.forEach(
                (playerId, playerData) -> playersTag.put(playerId, encodePlayer(playerData))
        );
        root.put(PLAYERS, playersTag);
        return root;
    }

    private PlayerData decodePlayer(CompoundTag tag, UUID expectedPlayerId) {
        requireOnlyKeys(tag, RECORD_KEYS, "player " + expectedPlayerId);
        requireType(tag, RECORD_VERSION, Tag.TAG_INT, "player " + expectedPlayerId);
        requireType(tag, REVISION, Tag.TAG_LONG, "player " + expectedPlayerId);
        requireType(tag, IDENTITY, Tag.TAG_COMPOUND, "player " + expectedPlayerId);
        requireType(tag, PROFILE, Tag.TAG_COMPOUND, "player " + expectedPlayerId);

        int recordVersion = tag.getInt(RECORD_VERSION);
        if (recordVersion != PlayerData.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported record version for " + expectedPlayerId + ": " + recordVersion
            );
        }

        PlayerIdentity identity = decodeIdentity(
                tag.getCompound(IDENTITY),
                expectedPlayerId
        );
        PlayerProfile profile = decodeProfile(tag.getCompound(PROFILE), expectedPlayerId);
        try {
            return new PlayerData(
                    recordVersion,
                    tag.getLong(REVISION),
                    identity,
                    profile
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid player record " + expectedPlayerId, exception);
        }
    }

    private CompoundTag encodePlayer(PlayerData playerData) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(RECORD_VERSION, playerData.schemaVersion());
        tag.putLong(REVISION, playerData.revision());
        tag.put(IDENTITY, encodeIdentity(playerData.identity()));
        tag.put(PROFILE, encodeProfile(playerData.profile()));
        return tag;
    }

    private PlayerIdentity decodeIdentity(CompoundTag tag, UUID expectedPlayerId) {
        requireOnlyKeys(tag, IDENTITY_KEYS, "identity " + expectedPlayerId);
        if (!tag.hasUUID(PLAYER_ID)) {
            throw invalid("Missing or invalid PlayerId for " + expectedPlayerId);
        }
        requireType(tag, LAST_KNOWN_GAME_NAME, Tag.TAG_STRING, "identity " + expectedPlayerId);
        requireType(tag, FIRST_SEEN_AT, Tag.TAG_LONG, "identity " + expectedPlayerId);
        requireType(tag, LAST_SEEN_AT, Tag.TAG_LONG, "identity " + expectedPlayerId);

        UUID playerId = tag.getUUID(PLAYER_ID);
        if (!expectedPlayerId.equals(playerId)) {
            throw invalid(
                    "Player key " + expectedPlayerId + " does not match identity " + playerId
            );
        }
        try {
            return new PlayerIdentity(
                    playerId,
                    tag.getString(LAST_KNOWN_GAME_NAME),
                    tag.getLong(FIRST_SEEN_AT),
                    tag.getLong(LAST_SEEN_AT)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid identity for " + expectedPlayerId, exception);
        }
    }

    private CompoundTag encodeIdentity(PlayerIdentity identity) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(PLAYER_ID, identity.playerId());
        tag.putString(LAST_KNOWN_GAME_NAME, identity.lastKnownGameName());
        tag.putLong(FIRST_SEEN_AT, identity.firstSeenAt());
        tag.putLong(LAST_SEEN_AT, identity.lastSeenAt());
        return tag;
    }

    private PlayerProfile decodeProfile(CompoundTag tag, UUID playerId) {
        requireOnlyKeys(tag, PROFILE_KEYS, "profile " + playerId);
        requireOptionalString(tag, DISPLAY_NAME, "profile " + playerId);
        requireOptionalString(tag, LOCALE, "profile " + playerId);
        requireOptionalString(tag, DESCRIPTION, "profile " + playerId);
        requireType(tag, UPDATED_AT, Tag.TAG_LONG, "profile " + playerId);

        try {
            return new PlayerProfile(
                    optionalString(tag, DISPLAY_NAME),
                    optionalString(tag, LOCALE),
                    optionalString(tag, DESCRIPTION),
                    tag.getLong(UPDATED_AT)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid profile for " + playerId, exception);
        }
    }

    private CompoundTag encodeProfile(PlayerProfile profile) {
        CompoundTag tag = new CompoundTag();
        profile.displayName().ifPresent(value -> tag.putString(DISPLAY_NAME, value));
        profile.locale().ifPresent(value -> tag.putString(LOCALE, value));
        profile.description().ifPresent(value -> tag.putString(DESCRIPTION, value));
        tag.putLong(UPDATED_AT, profile.updatedAt());
        return tag;
    }

    private Optional<String> optionalString(CompoundTag tag, String key) {
        return tag.contains(key) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    private void requireOptionalString(CompoundTag tag, String key, String path) {
        if (tag.contains(key) && !tag.contains(key, Tag.TAG_STRING)) {
            throw invalid(path + " field " + key + " has the wrong NBT type");
        }
    }

    private void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(path + " is missing required field " + key + " or has the wrong type");
        }
    }

    private void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private UUID parseCanonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid("Player UUID key is not canonical: " + value);
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            if (exception instanceof PlayerDataNbtException playerDataException) {
                throw playerDataException;
            }
            throw invalid("Invalid player UUID key: " + value, exception);
        }
    }

    private PlayerDataNbtException invalid(String message) {
        return new PlayerDataNbtException(message);
    }

    private PlayerDataNbtException invalid(String message, Throwable cause) {
        return new PlayerDataNbtException(message, cause);
    }
}
