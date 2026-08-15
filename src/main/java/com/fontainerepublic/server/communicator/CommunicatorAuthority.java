package com.fontainerepublic.server.communicator;

import com.fontainerepublic.core.ConfigManager;
import net.minecraft.commands.CommandSourceStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-side console/Hydro-Archon issuance authority boundary (FR-ITEM-003).
 *
 * <p>The Water Mirror is once more an ordinary custom item; the FR-ITEM-002-A
 * server-only HMAC key map, {@code loadOrCreateKey} persistence and the
 * carrier authenticator holder are removed. Only the issuance boundary
 * remains: who may hand out a Message Water Mirror.</p>
 *
 * <p>Issuance is restricted to:</p>
 * <ul>
 *   <li>the real local dedicated-server console ({@code sourceName} "Server",
 *       entity-less, dedicated), or</li>
 *   <li>the configured Hydro Archon player, after server-side verification.</li>
 * </ul>
 */
public final class CommunicatorAuthority {

    private CommunicatorAuthority() {
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