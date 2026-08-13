package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.server.playerdata.model.DirectoryEntry;
import com.fontainerepublic.server.playerdata.model.GameNameNormalizer;
import com.fontainerepublic.server.playerdata.model.MigrationProvenance;
import com.fontainerepublic.server.playerdata.model.PlayerData;
import com.fontainerepublic.server.playerdata.model.PlayerIdentity;
import com.fontainerepublic.server.playerdata.model.PlayerProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned NBT codec for the player-data namespace (FR-DATA-003-A
 * §7). Decodes v1 stores through an isolated deterministic migration and v2
 * stores strictly; encodes deterministically (players and entries ordered by
 * canonical key, owners ordered by canonical UUID string, optional fields
 * omitted rather than represented by magic values).
 */
public final class PlayerDataNbtCodec {
    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String PLAYERS = "Players";
    private static final String DIRECTORY = "Directory";

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

    private static final String DIRECTORY_VERSION = "DirectoryVersion";
    private static final String MIGRATION_PROVENANCE = "MigrationProvenance";
    private static final String ENTRIES = "Entries";

    private static final String ENTRY_VERSION = "EntryVersion";
    private static final String ENTRY_REVISION = "EntryRevision";
    private static final String LAST_VERIFIED_SPELLING = "LastVerifiedSpelling";
    private static final String PERMANENTLY_AMBIGUOUS = "PermanentlyAmbiguous";
    private static final String UNIQUE_HISTORICAL_OWNER = "UniqueHistoricalOwner";
    private static final String CURRENT_OWNERS = "CurrentOwners";
    private static final String FIRST_OBSERVED_AT = "FirstObservedAt";
    private static final String LAST_OBSERVED_AT = "LastObservedAt";

    private static final int MAX_PLAYER_RECORDS = 10_000;
    private static final int MAX_DIRECTORY_ENTRIES = 10_000;

    private static final Set<String> V1_STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            PLAYERS
    );
    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            PLAYERS,
            DIRECTORY
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
    private static final Set<String> DIRECTORY_KEYS = Set.of(
            DIRECTORY_VERSION,
            MIGRATION_PROVENANCE,
            ENTRIES
    );
    private static final Set<String> ENTRY_KEYS = Set.of(
            ENTRY_VERSION,
            ENTRY_REVISION,
            LAST_VERIFIED_SPELLING,
            PERMANENTLY_AMBIGUOUS,
            UNIQUE_HISTORICAL_OWNER,
            CURRENT_OWNERS,
            FIRST_OBSERVED_AT,
            LAST_OBSERVED_AT
    );

    public PlayerDataStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            return PlayerDataStoreSnapshot.empty();
        }

        requireType(root, STORE_VERSION, Tag.TAG_INT, "player-data");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "player-data");
        requireType(root, PLAYERS, Tag.TAG_COMPOUND, "player-data");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion == 1) {
            return decodeV1AndMigrate(root);
        }
        if (storeVersion != PlayerDataStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported store version: " + storeVersion);
        }
        return decodeV2(root);
    }

    public CompoundTag encode(PlayerDataStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());
        root.put(PLAYERS, encodePlayers(snapshot.players()));
        root.put(DIRECTORY, encodeDirectory(snapshot.directory(), snapshot.migrationProvenance()));
        return root;
    }

    // ------------------------------------------------------------------
    // v1 migration (FR-DATA-003-A §8)
    // ------------------------------------------------------------------

    /**
     * Decodes a v1 store and derives the initial directory deterministically
     * from {@code lastKnownGameName}: groups with one UUID become unique
     * current entries, groups with multiple UUIDs become permanently
     * ambiguous entries. Player records and their revisions are preserved;
     * StoreRevision advances exactly once; provenance records the migration.
     */
    private PlayerDataStoreSnapshot decodeV1AndMigrate(CompoundTag root) {
        requireOnlyKeys(root, V1_STORE_KEYS, "player-data v1");

        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }
        if (storeRevision == Long.MAX_VALUE) {
            throw invalid("StoreRevision overflow during v1 migration");
        }

        Map<UUID, PlayerData> players = decodePlayers(root.getCompound(PLAYERS));

        Map<String, DirectoryEntry> directory = new TreeMap<>();
        Map<String, Set<UUID>> groups = new TreeMap<>();
        for (PlayerData player : players.values()) {
            String key = GameNameNormalizer.normalize(
                    player.identity().lastKnownGameName()
            ).orElseThrow(() -> invalid(
                    "v1 migration: invalid lastKnownGameName for "
                            + player.identity().playerId()
            ));
            groups.computeIfAbsent(key, ignored -> new java.util.TreeSet<>())
                    .add(player.identity().playerId());
        }
        for (Map.Entry<String, Set<UUID>> group : groups.entrySet()) {
            String key = group.getKey();
            Set<UUID> owners = group.getValue();
            if (owners.size() == 1) {
                UUID owner = owners.iterator().next();
                directory.put(key, new DirectoryEntry(
                        key,
                        players.get(owner).identity().lastKnownGameName(),
                        false,
                        Optional.of(owner),
                        owners,
                        0L,
                        0L,
                        1L
                ));
            } else {
                directory.put(key, new DirectoryEntry(
                        key,
                        players.get(owners.iterator().next())
                                .identity().lastKnownGameName(),
                        true,
                        Optional.empty(),
                        owners,
                        0L,
                        0L,
                        1L
                ));
            }
        }

        try {
            return new PlayerDataStoreSnapshot(
                    PlayerDataStoreSnapshot.CURRENT_STORE_VERSION,
                    storeRevision + 1,
                    players,
                    directory,
                    MigrationProvenance.MIGRATED_FROM_LAST_KNOWN_ONLY
            );
        } catch (IllegalArgumentException failure) {
            throw invalid("v1 migration produced an invalid snapshot", failure);
        }
    }

    // ------------------------------------------------------------------
    // v2 decode
    // ------------------------------------------------------------------

    private PlayerDataStoreSnapshot decodeV2(CompoundTag root) {
        requireOnlyKeys(root, STORE_KEYS, "player-data");

        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<UUID, PlayerData> players = decodePlayers(root.getCompound(PLAYERS));

        requireType(root, DIRECTORY, Tag.TAG_COMPOUND, "player-data");
        Directory decodeResult = decodeDirectory(root.getCompound(DIRECTORY), players);

        try {
            return new PlayerDataStoreSnapshot(
                    PlayerDataStoreSnapshot.CURRENT_STORE_VERSION,
                    storeRevision,
                    players,
                    decodeResult.entries(),
                    decodeResult.provenance()
            );
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid player-data snapshot", failure);
        }
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    private Map<UUID, PlayerData> decodePlayers(CompoundTag playersTag) {
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
        return players;
    }

    private CompoundTag encodePlayers(Map<UUID, PlayerData> players) {
        CompoundTag playersTag = new CompoundTag();
        TreeMap<String, PlayerData> orderedPlayers = new TreeMap<>();
        players.forEach(
                (playerId, playerData) -> orderedPlayers.put(playerId.toString(), playerData)
        );
        orderedPlayers.forEach(
                (playerId, playerData) -> playersTag.put(playerId, encodePlayer(playerData))
        );
        return playersTag;
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

    // ------------------------------------------------------------------
    // Directory
    // ------------------------------------------------------------------

    private record Directory(
            Map<String, DirectoryEntry> entries,
            MigrationProvenance provenance
    ) {
    }

    private Directory decodeDirectory(CompoundTag tag, Map<UUID, PlayerData> players) {
        requireOnlyKeys(tag, DIRECTORY_KEYS, "Directory");
        requireType(tag, DIRECTORY_VERSION, Tag.TAG_INT, "Directory");
        requireType(tag, MIGRATION_PROVENANCE, Tag.TAG_STRING, "Directory");
        requireType(tag, ENTRIES, Tag.TAG_COMPOUND, "Directory");

        int directoryVersion = tag.getInt(DIRECTORY_VERSION);
        if (directoryVersion != PlayerDataStoreSnapshot.CURRENT_DIRECTORY_VERSION) {
            throw invalid("Unsupported directory version: " + directoryVersion);
        }

        MigrationProvenance provenance = parseProvenance(tag.getString(MIGRATION_PROVENANCE));

        CompoundTag entriesTag = tag.getCompound(ENTRIES);
        if (entriesTag.getAllKeys().size() > MAX_DIRECTORY_ENTRIES) {
            throw invalid("Directory entry count exceeds " + MAX_DIRECTORY_ENTRIES);
        }

        Map<String, DirectoryEntry> entries = new LinkedHashMap<>();
        for (String key : entriesTag.getAllKeys().stream().sorted().toList()) {
            requireType(entriesTag, key, Tag.TAG_COMPOUND, "Entries");
            DirectoryEntry entry = decodeEntry(entriesTag.getCompound(key), key);
            if (entries.put(key, entry) != null) {
                throw invalid("Duplicate directory key: " + key);
            }
        }
        return new Directory(entries, provenance);
    }

    private DirectoryEntry decodeEntry(CompoundTag tag, String expectedKey) {
        requireOnlyKeys(tag, ENTRY_KEYS, "entry " + expectedKey);
        requireType(tag, ENTRY_VERSION, Tag.TAG_INT, "entry " + expectedKey);
        requireType(tag, ENTRY_REVISION, Tag.TAG_LONG, "entry " + expectedKey);
        requireType(tag, LAST_VERIFIED_SPELLING, Tag.TAG_STRING, "entry " + expectedKey);
        requireType(tag, PERMANENTLY_AMBIGUOUS, Tag.TAG_BYTE, "entry " + expectedKey);
        requireType(tag, CURRENT_OWNERS, Tag.TAG_LIST, "entry " + expectedKey);
        requireType(tag, FIRST_OBSERVED_AT, Tag.TAG_LONG, "entry " + expectedKey);
        requireType(tag, LAST_OBSERVED_AT, Tag.TAG_LONG, "entry " + expectedKey);

        int entryVersion = tag.getInt(ENTRY_VERSION);
        if (entryVersion != 1) {
            throw invalid("Unsupported entry version: " + entryVersion);
        }

        String normalizedKey = requireNormalizedKey(expectedKey);
        boolean ambiguous = tag.getByte(PERMANENTLY_AMBIGUOUS) != 0;

        Optional<UUID> uniqueOwner = Optional.empty();
        if (tag.contains(UNIQUE_HISTORICAL_OWNER)) {
            if (!tag.hasUUID(UNIQUE_HISTORICAL_OWNER)) {
                throw invalid("entry " + expectedKey
                        + " field UniqueHistoricalOwner has the wrong NBT type");
            }
            uniqueOwner = Optional.of(tag.getUUID(UNIQUE_HISTORICAL_OWNER));
        } else if (!ambiguous) {
            throw invalid("non-ambiguous entry " + expectedKey
                    + " is missing UniqueHistoricalOwner");
        }

        ListTag ownersTag = tag.getList(CURRENT_OWNERS, Tag.TAG_INT_ARRAY);
        Set<UUID> owners = new java.util.TreeSet<>();
        for (Tag ownerTag : ownersTag) {
            UUID owner = NbtUtils.loadUUID(ownerTag);
            if (!owners.add(owner)) {
                throw invalid("entry " + expectedKey
                        + " contains duplicate current owner " + owner);
            }
        }

        try {
            return new DirectoryEntry(
                    normalizedKey,
                    tag.getString(LAST_VERIFIED_SPELLING),
                    ambiguous,
                    uniqueOwner,
                    owners,
                    tag.getLong(FIRST_OBSERVED_AT),
                    tag.getLong(LAST_OBSERVED_AT),
                    tag.getLong(ENTRY_REVISION)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid directory entry " + expectedKey, exception);
        }
    }

    private CompoundTag encodeDirectory(
            Map<String, DirectoryEntry> directory,
            MigrationProvenance provenance
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DIRECTORY_VERSION, PlayerDataStoreSnapshot.CURRENT_DIRECTORY_VERSION);
        tag.putString(MIGRATION_PROVENANCE, provenance.name());

        CompoundTag entriesTag = new CompoundTag();
        TreeMap<String, DirectoryEntry> orderedEntries = new TreeMap<>(directory);
        orderedEntries.forEach(
                (key, entry) -> entriesTag.put(key, encodeEntry(entry))
        );
        tag.put(ENTRIES, entriesTag);
        return tag;
    }

    private CompoundTag encodeEntry(DirectoryEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ENTRY_VERSION, 1);
        tag.putLong(ENTRY_REVISION, entry.entryRevision());
        tag.putString(LAST_VERIFIED_SPELLING, entry.lastVerifiedSpelling());
        tag.putByte(PERMANENTLY_AMBIGUOUS, (byte) (entry.permanentlyAmbiguous() ? 1 : 0));
        entry.uniqueHistoricalOwner().ifPresent(
                owner -> tag.putUUID(UNIQUE_HISTORICAL_OWNER, owner)
        );
        ListTag owners = new ListTag();
        TreeMap<String, UUID> orderedOwners = new TreeMap<>();
        entry.currentOwners().forEach(owner -> orderedOwners.put(owner.toString(), owner));
        orderedOwners.values().forEach(owner -> owners.add(NbtUtils.createUUID(owner)));
        tag.put(CURRENT_OWNERS, owners);
        tag.putLong(FIRST_OBSERVED_AT, entry.firstObservedAt());
        tag.putLong(LAST_OBSERVED_AT, entry.lastObservedAt());
        return tag;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String requireNormalizedKey(String value) {
        if (GameNameNormalizer.normalize(value).isEmpty()
                || !value.equals(value.toLowerCase(java.util.Locale.ROOT))) {
            throw invalid("Directory key is not a canonical normalized name: " + value);
        }
        return value;
    }

    private MigrationProvenance parseProvenance(String value) {
        try {
            return MigrationProvenance.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("Unknown migration provenance: " + value);
        }
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
