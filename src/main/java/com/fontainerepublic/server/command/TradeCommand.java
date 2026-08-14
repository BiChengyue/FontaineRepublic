package com.fontainerepublic.server.command;

import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import com.fontainerepublic.server.trade.TradeRuntime;
import com.fontainerepublic.server.trade.api.TradeService;
import com.fontainerepublic.server.trade.model.TradePhase;
import com.fontainerepublic.server.trade.model.TradeSession;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * No-client command surface of the trade module (FR-TRADE-002-A §5).
 *
 * <p>The interface model replaced the old FR-TRADE-001 escrow UI; this
 * command is the canonical chat/console path and stays available to a Forge
 * client that does not install FontaineRepublic. Every action resolves the
 * active {@link TradeService} per invocation through {@link TradeRuntime}
 * (never cached), resolves the acting player's {@link ServerPlayer} and the
 * target from the live player list, and delegates authority to the service —
 * the command never decides trade state. The optional C2S ledger and client
 * screen remain presentation-only.</p>
 *
 * <p>Surface:</p>
 * <pre>
 *   /fr trade &lt;player&gt;
 *   /fr trade accept | deny | cancel
 *   /fr trade offer money &lt;amount&gt;
 *   /fr trade ready
 *   /fr trade history
 * </pre>
 */
public final class TradeCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TradeCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("trade")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> request(context.getSource(), context)))
                .then(Commands.literal("accept")
                        .executes(context -> respond(context.getSource(), true)))
                .then(Commands.literal("deny")
                        .executes(context -> respond(context.getSource(), false)))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context.getSource())))
                .then(Commands.literal("offer")
                        .then(Commands.literal("money")
                                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                        .executes(context -> offerMoney(context.getSource(), context)))))
                .then(Commands.literal("ready")
                        .executes(context -> agree(context.getSource())))
                .then(Commands.literal("history")
                        .executes(context -> history(context.getSource())));
    }

    // ------------------------------------------------------------------
    // /fr trade <player>
    // ------------------------------------------------------------------

    private static int request(CommandSourceStack source, CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can start a trade.");
        }
        Optional<TradeService> service = TradeRuntime.resolve();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Trade runtime is unavailable.");
        }
        String targetInput = StringArgumentType.getString(context, "player");
        ServerPlayer target = resolveOnlinePlayer(source, targetInput);
        if (target == null) {
            return CommandFeedback.failure(
                    source, "Trade request rejected: no online player matches '" + targetInput + "'.");
        }
        service.get().request(actor.getUUID(), target.getUUID());
        return CommandFeedback.SUCCESS;
    }

    // ------------------------------------------------------------------
    // accept / deny / cancel / offer money / ready
    // ------------------------------------------------------------------

    private static int respond(CommandSourceStack source, boolean accept) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can respond to a trade.");
        }
        Optional<TradeService> service = TradeRuntime.resolve();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Trade runtime is unavailable.");
        }
        Optional<TradeSession> session = service.get().sessionOf(actor.getUUID());
        if (session.isEmpty() || session.get().phase() != TradePhase.REQUESTED) {
            return CommandFeedback.failure(source, "You have no pending trade request.");
        }
        service.get().respond(actor.getUUID(), session.get().sessionId(), accept);
        return CommandFeedback.SUCCESS;
    }

    private static int cancel(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can cancel a trade.");
        }
        Optional<TradeService> service = TradeRuntime.resolve();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Trade runtime is unavailable.");
        }
        Optional<TradeSession> session = service.get().sessionOf(actor.getUUID());
        if (session.isEmpty()) {
            return CommandFeedback.failure(source, "You have no active trade session.");
        }
        service.get().cancel(actor.getUUID(), session.get().sessionId());
        return CommandFeedback.SUCCESS;
    }

    private static int offerMoney(CommandSourceStack source, CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can offer money.");
        }
        Optional<TradeService> service = TradeRuntime.resolve();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Trade runtime is unavailable.");
        }
        Optional<TradeSession> session = service.get().sessionOf(actor.getUUID());
        if (session.isEmpty()) {
            return CommandFeedback.failure(source, "You have no open trade session.");
        }
        long amount = LongArgumentType.getLong(context, "amount");
        if (amount > TradeOfferMoneyPacket.MAX_OFFER_AMOUNT) {
            return CommandFeedback.failure(source, "Offer amount is out of range.");
        }
        service.get().offerMoney(actor.getUUID(), session.get().sessionId(), amount);
        return CommandFeedback.SUCCESS;
    }

    private static int agree(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can mark ready.");
        }
        Optional<TradeService> service = TradeRuntime.resolve();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Trade runtime is unavailable.");
        }
        Optional<TradeSession> session = service.get().sessionOf(actor.getUUID());
        if (session.isEmpty()) {
            return CommandFeedback.failure(source, "You have no open trade session.");
        }
        if (session.get().phase() != TradePhase.OPEN && session.get().phase() != TradePhase.LOCKED) {
            return CommandFeedback.failure(source, "The trade is not ready to confirm.");
        }
        service.get().agree(actor.getUUID(), session.get().sessionId(), true);
        return CommandFeedback.SUCCESS;
    }

    // ------------------------------------------------------------------
    // history (bounded, server-side only — no durable journal in Stage A/C)
    // ------------------------------------------------------------------

    private static int history(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            return CommandFeedback.failure(source, "Only a player can view trade history.");
        }
        // FR-TRADE-002-A Stage D (durable TradeJournal) is not yet implemented;
        // history is bounded feedback until the journal lands.
        return CommandFeedback.success(
                source,
                "Trade history is not yet available (durable journal ships with FR-TRADE-002-A Stage D)."
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

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

    static {
        LOGGER.debug("[Trade] TradeCommand loaded");
    }
}
