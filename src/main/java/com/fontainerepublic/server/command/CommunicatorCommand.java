package com.fontainerepublic.server.command;

import com.fontainerepublic.common.item.CommunicatorAuthenticator;
import com.fontainerepublic.common.item.CommunicatorIssuer;
import com.fontainerepublic.server.communicator.CommunicatorAuthority;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Scoped Water Mirror issuance adapter (FR-ITEM-002-A §5).
 *
 * <p>{@code /fr communicator issue <target>} issues one signed vanilla
 * {@code minecraft:clock} Water Mirror carrier to a target player. The target
 * is a canonical UUID or the exact game name of an online player. Issuance is
 * restricted to the real local dedicated-server console or the configured
 * Hydro Archon player ({@link CommunicatorAuthority#isIssuanceAuthorized});
 * the {@code /give ... fontainerepublic:communicator} path is retired.</p>
 *
 * <p>The command generates a fresh {@code DeviceId}, signs the canonical
 * fields with the server-only key, writes presentation NBT and creates one
 * carrier. The signing key material is never exposed; only an SHA-256 key
 * digest is logged for diagnostics (FR-ITEM-002-A §10).</p>
 */
public final class CommunicatorCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CommunicatorCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("communicator")
                .then(Commands.literal("issue")
                        .then(Commands.argument("target", StringArgumentType.string())
                                .executes(context -> issue(context.getSource(), context))));
    }

    private static int issue(CommandSourceStack source, CommandContext<CommandSourceStack> context) {
        if (!CommunicatorAuthority.isIssuanceAuthorized(source)) {
            return CommandFeedback.failure(
                    source,
                    "Water Mirror issuance rejected: only the local dedicated-server "
                            + "console or the configured Hydro Archon may issue one."
            );
        }
        CommunicatorAuthenticator authenticator = CommunicatorAuthority.authenticator();
        if (authenticator == null) {
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic Water Mirror issuance is unavailable."
            );
        }
        String target = StringArgumentType.getString(context, "target");
        ServerPlayer targetPlayer = resolveTarget(source, target);
        if (targetPlayer == null) {
            return CommandFeedback.failure(
                    source,
                    "Water Mirror issuance rejected: target '" + target
                            + "' is not a canonical UUID or an online player."
            );
        }
        try {
            ItemStack carrier = CommunicatorIssuer.issue(authenticator, targetPlayer.getUUID());
            if (carrier.isEmpty()) {
                return CommandFeedback.failure(source, "Water Mirror issuance failed.");
            }
            if (!targetPlayer.getInventory().add(carrier)) {
                return CommandFeedback.failure(
                        source,
                        "Water Mirror issuance failed: " + targetPlayer.getName().getString()
                                + " has no inventory space."
                );
            }
            LOGGER.info(
                    "[Communicator] Water Mirror issued to {} ({}) by {}; keyDigest={}",
                    targetPlayer.getName().getString(),
                    targetPlayer.getUUID(),
                    source.getTextName(),
                    authenticator.activeKeyDigest()
            );
            return CommandFeedback.success(
                    source,
                    "Issued one Message Water Mirror (传讯水镜) to "
                            + targetPlayer.getName().getString() + "."
            );
        } catch (RuntimeException failure) {
            LOGGER.error("[Communicator] Water Mirror issuance failed", failure);
            return CommandFeedback.failure(
                    source,
                    "Water Mirror issuance failed; nothing was issued."
            );
        }
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, String target) {
        // Canonical UUID first.
        UUID uuid = null;
        try {
            UUID parsed = UUID.fromString(target);
            if (parsed.toString().equals(target)) {
                uuid = parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // not a canonical UUID; try name next
        }
        if (uuid != null) {
            ServerPlayer byId = source.getServer().getPlayerList().getPlayer(uuid);
            if (byId != null) {
                return byId;
            }
        }
        return source.getServer().getPlayerList().getPlayerByName(target);
    }
}
