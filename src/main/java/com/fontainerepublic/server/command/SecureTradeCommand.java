package com.fontainerepublic.server.command;

import com.fontainerepublic.trade.securetrade.command.TradeCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * FR-TRADE-004 command-parity surface: {@code /fr trade} delegates to the
 * copied Navielon/SecureTrade (MIT) escrow command logic so a Forge client
 * without the FR GUI can still request / accept / deny / review history.
 *
 * <p>The Secure Trade graphical UI (54-slot escrow screen) is the primary
 * surface for FR clients; this command is the no-client parity path. Every
 * action delegates authority to {@link TradeCommand}'s public facade (the
 * server remains authoritative — offers/locks/settlement are owned by the
 * escrow session on the server).</p>
 */
public final class SecureTradeCommand {

    private SecureTradeCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("trade")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> request(context.getSource(), context)))
                .then(Commands.literal("accept")
                        .executes(context -> accept(context.getSource())))
                .then(Commands.literal("deny")
                        .executes(context -> deny(context.getSource())))
                .then(Commands.literal("history")
                        .executes(context -> history(context.getSource())));
    }

    private static int request(CommandSourceStack source, CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can start a trade.");
        }
        String targetInput = StringArgumentType.getString(context, "player");
        ServerPlayer target = resolveOnlinePlayer(source, targetInput);
        if (target == null) {
            return CommandFeedback.failure(source, "No online player matches '" + targetInput + "'.");
        }
        return TradeCommand.requestTradeCommand(source, target);
    }

    private static int accept(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can accept a trade.");
        }
        return TradeCommand.acceptTradeCommand(source);
    }

    private static int deny(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can deny a trade.");
        }
        return TradeCommand.denyTradeCommand(source);
    }

    private static int history(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can view trade history.");
        }
        return TradeCommand.showHistoryCommand(source);
    }

    private static ServerPlayer resolveOnlinePlayer(CommandSourceStack source, String input) {
        UUID uuid = null;
        try {
            UUID parsed = UUID.fromString(input);
            if (parsed.toString().equals(input)) {
                uuid = parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // not a canonical UUID; try exact name
        }
        if (uuid != null) {
            return source.getServer().getPlayerList().getPlayer(uuid);
        }
        return source.getServer().getPlayerList().getPlayerByName(input);
    }
}
