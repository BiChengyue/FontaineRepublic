package com.fontainerepublic.server.communicator;

import com.fontainerepublic.common.item.CommunicatorAuthenticator;
import com.fontainerepublic.core.ConfigManager;
import net.minecraft.commands.CommandSourceStack;

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

    private CommunicatorAuthority() {
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
