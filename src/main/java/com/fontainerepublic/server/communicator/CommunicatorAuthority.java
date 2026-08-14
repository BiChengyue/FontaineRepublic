package com.fontainerepublic.server.communicator;

import com.fontainerepublic.common.item.CommunicatorAuthenticator;
import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side holder of the single Water Mirror authenticator plus the
 * console/Hydro-Archon issuance authority boundary (FR-ITEM-002-A §5).
 *
 * <p>The authenticator owns the server-only HMAC key map. Exactly one active
 * key is used for issuance; older keys may remain verification-only. The raw
 * key material never leaves this class and is never logged (only a SHA-256
 * digest is exposed for diagnostics).</p>
 *
 * <p>Issuance is restricted to:</p>
 * <ul>
 *   <li>the real local dedicated-server console ({@code sourceName} "Server",
 *       entity-less, dedicated), or</li>
 *   <li>the configured Hydro Archon player, after server-side verification.</li>
 * </ul>
 */
public final class CommunicatorAuthority {

    private static volatile CommunicatorAuthenticator authenticator;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Module-data namespace persisting the server-only HMAC key material. */
    private static final String MODULE_DATA_KEY = "communicator";

    /** NBT key holding the raw HMAC key bytes under {@link #MODULE_DATA_KEY}. */
    private static final String NBT_KEY = "HmacKey";

    /** Raw HMAC-SHA-256 key length in bytes. */
    private static final int KEY_BYTES = 32;

    private CommunicatorAuthority() {
    }

    /**
     * Loads the persisted server-only HMAC key material, or creates and
     * durably persists a fresh one on first run (FR-ITEM-002-A §4.2 key
     * persistence). Returning the same key across restarts keeps previously
     * issued Water Mirrors valid. A failed persistence attempt still returns a
     * usable session key but logs the degraded mode.
     */
    public static byte[] loadOrCreateKey() {
        CompoundTag tag = DataManager.getModuleData(MODULE_DATA_KEY);
        if (tag != null && tag.contains(NBT_KEY, Tag.TAG_BYTE_ARRAY)) {
            byte[] existing = tag.getByteArray(NBT_KEY);
            if (existing.length == KEY_BYTES) {
                return existing;
            }
        }
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        CompoundTag toSave = new CompoundTag();
        toSave.putByteArray(NBT_KEY, key);
        DurableCommitResult result = DataManager.commitModuleData(MODULE_DATA_KEY, toSave);
        if (result.status() != DurableCommitStatus.COMMITTED) {
            LOGGER.error(
                    "[Communicator] Failed to persist HMAC key (code={}); "
                            + "issued Water Mirrors will not survive restart",
                    result.failureCode()
            );
        }
        return key;
    }

    /** Initializes the server authenticator (idempotent; first call wins). */
    public static CommunicatorAuthenticator authenticator(byte[] seed) {
        CommunicatorAuthenticator current = authenticator;
        if (current == null) {
            synchronized (CommunicatorAuthority.class) {
                current = authenticator;
                if (current == null) {
                    current = new CommunicatorAuthenticator(seed);
                    authenticator = current;
                }
            }
        }
        return current;
    }

    /** The current authenticator, or {@code null} before server start. */
    public static CommunicatorAuthenticator authenticator() {
        return authenticator;
    }

    /**
     * Whether the command source may issue a Water Mirror: the real local
     * dedicated console, or the configured Hydro Archon player.
     */
    public static boolean isIssuanceAuthorized(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        if (!source.getServer().isDedicatedServer()) {
            return false;
        }
        net.minecraft.world.entity.Entity entity = source.getEntity();
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            UUID hydroArchon = parseHydroArchon();
            return hydroArchon != null && hydroArchon.equals(player.getUUID());
        }
        if (entity == null) {
            // Real local dedicated-server console is sourceName "Server";
            // RCON is "Rcon", command blocks are "@".
            return "Server".equals(source.getTextName());
        }
        return false;
    }

    private static UUID parseHydroArchon() {
        String raw = ConfigManager.emergencyHydroArchonUuid();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            UUID parsed = UUID.fromString(raw);
            return parsed.toString().equals(raw) ? parsed : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
