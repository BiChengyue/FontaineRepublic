package com.fontainerepublic.server.command;

import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Approved personal money command surface (FR-ECO-001-A §6.3, scoped by
 * FR-CMD-USER-001): own balance, UUID-target ordinary payment with bounded
 * memo, and bounded own history. Deliberately absent: top / bank / treasury /
 * other-player balance / freeze / cash / issuance. Execution resolves the
 * current ACTIVE {@link EconomyService} per invocation through the
 * {@link CommandRuntimeResolver} and never caches services or state.
 */
public final class MoneyCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bounded history page argument (1-based); guards the cursor loop. */
    private static final int MAX_HISTORY_PAGE = 1_000;

    private MoneyCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("money")
                .then(Commands.literal("balance")
                        .executes(context -> balance(
                                context.getSource(),
                                runtimeResolver
                        )))
                .then(Commands.literal("pay")
                        .then(Commands.argument("uuid", StringArgumentType.string())
                                .then(Commands.argument(
                                                "amount",
                                                LongArgumentType.longArg(1)
                                        )
                                        .executes(context -> pay(
                                                context,
                                                runtimeResolver,
                                                null
                                        ))
                                        .then(Commands.argument(
                                                        "memo",
                                                        StringArgumentType.greedyString()
                                                )
                                                .executes(context -> pay(
                                                        context,
                                                        runtimeResolver,
                                                        StringArgumentType.getString(
                                                                context,
                                                                "memo"
                                                        )
                                                ))))))
                .then(Commands.literal("history")
                        .executes(context -> history(
                                context.getSource(),
                                runtimeResolver,
                                1
                        ))
                        .then(Commands.argument(
                                        "page",
                                        IntegerArgumentType.integer(1)
                                )
                                .executes(context -> history(
                                        context.getSource(),
                                        runtimeResolver,
                                        IntegerArgumentType.getInteger(context, "page")
                                ))));
    }

    // ------------------------------------------------------------------
    // /fr money balance
    // ------------------------------------------------------------------

    private static int balance(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        Optional<EconomyService> service = runtimeResolver.economyService();
        if (service.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Economy runtime is unavailable."
            );
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandFeedback.failure(
                    source,
                    "Only a player can check their balance."
            );
        }
        try {
            EconomyAccount account = service.get()
                    .ensureAccountForPlayer(player.getUUID());
            return CommandFeedback.success(
                    source,
                    "Your balance: "
                            + service.get().formatBalance(account.balance()) + "."
            );
        } catch (EconomyUnavailableException exception) {
            return CommandFeedback.failure(source, economyMessage(exception));
        } catch (RuntimeException exception) {
            return unexpected(source, "money.balance", exception);
        }
    }

    // ------------------------------------------------------------------
    // /fr money pay <uuid> <amount> [memo]
    // ------------------------------------------------------------------

    private static int pay(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String rawMemo
    ) {
        CommandSourceStack source = context.getSource();
        String uuidInput = StringArgumentType.getString(context, "uuid");
        UUID target;
        try {
            target = parseCanonicalUuid(uuidInput);
        } catch (IllegalArgumentException invalid) {
            return CommandFeedback.failure(
                    source,
                    "Payment rejected: invalid target UUID '" + uuidInput
                            + "' (canonical UUID required)."
            );
        }
        String memo = normalizeMemo(rawMemo);
        if (memo != null && memo.length() > EconomyLimits.DEFAULT.maxMemoLength()) {
            return CommandFeedback.failure(
                    source,
                    "Payment rejected: memo must be at most "
                            + EconomyLimits.DEFAULT.maxMemoLength()
                            + " characters."
            );
        }

        Optional<EconomyService> service = runtimeResolver.economyService();
        if (service.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Economy runtime is unavailable."
            );
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandFeedback.failure(
                    source,
                    "Only a player can send a payment."
            );
        }
        try {
            TransferReceipt receipt = service.get().transferByPlayer(
                    player.getUUID(),
                    target,
                    LongArgumentType.getLong(context, "amount"),
                    memo
            );
            return CommandFeedback.success(
                    source,
                    "Payment sent: "
                            + service.get().formatBalance(receipt.amount())
                            + " to " + receipt.to()
                            + " (transaction #" + receipt.transactionId() + ")."
            );
        } catch (EconomyUnavailableException exception) {
            return CommandFeedback.failure(source, economyMessage(exception));
        } catch (RuntimeException exception) {
            return unexpected(source, "money.pay", exception);
        }
    }

    // ------------------------------------------------------------------
    // /fr money history [page]
    // ------------------------------------------------------------------

    private static int history(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver,
            int page
    ) {
        Optional<EconomyService> service = runtimeResolver.economyService();
        if (service.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Economy runtime is unavailable."
            );
        }
        if (page > MAX_HISTORY_PAGE) {
            return CommandFeedback.failure(
                    source,
                    "History page must be at most " + MAX_HISTORY_PAGE + "."
            );
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandFeedback.failure(
                    source,
                    "Only a player can view their transaction history."
            );
        }
        try {
            EconomyAccount account = service.get()
                    .ensureAccountForPlayer(player.getUUID());
            int limit = EconomyLimits.DEFAULT.maxPageSize();
            EconomyPage<EconomyTransaction> current = service.get()
                    .getRecentTransactions(account.subjectId(), 0L, limit);
            for (int hop = 1; hop < page && current.hasMore(); hop++) {
                current = service.get().getRecentTransactions(
                        account.subjectId(),
                        current.nextAfterId(),
                        limit
                );
            }
            if (current.items().isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "No transactions found on page " + page + "."
                );
            }
            for (EconomyTransaction transaction : current.items()) {
                String direction = transaction.from().equals(account.subjectId())
                        ? "sent"
                        : "received";
                String memo = transaction.memo() == null
                        ? ""
                        : " memo=" + transaction.memo();
                CommandFeedback.success(
                        source,
                        "#" + transaction.transactionId() + " @" + transaction.timestamp()
                                + " " + direction + " "
                                + service.get().formatBalance(transaction.amount())
                                + " from " + transaction.from()
                                + " to " + transaction.to() + memo + "."
                );
            }
            String footer = "Page " + page + " of your history"
                    + (current.hasMore() ? " (more available)." : ".");
            return CommandFeedback.success(source, footer);
        } catch (EconomyUnavailableException exception) {
            return CommandFeedback.failure(source, economyMessage(exception));
        } catch (RuntimeException exception) {
            return unexpected(source, "money.history", exception);
        }
    }

    // ------------------------------------------------------------------
    // shared helpers
    // ------------------------------------------------------------------

    /**
     * Display-only memo projection (FR-ECO-001-C §5.1): absent when null or
     * blank; otherwise trimmed and bounded by the service contract.
     */
    private static String normalizeMemo(String rawMemo) {
        if (rawMemo == null) {
            return null;
        }
        String trimmed = rawMemo.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UUID parseCanonicalUuid(String input) {
        UUID parsed = UUID.fromString(input);
        if (!parsed.toString().equals(input)) {
            throw new IllegalArgumentException("UUID must be canonical");
        }
        return parsed;
    }

    private static String economyMessage(EconomyUnavailableException exception) {
        return switch (exception.failureCode()) {
            case EconomyUnavailableException.CODE_PLAYER_NOT_PROVISIONED ->
                    "Your player record is not provisioned yet; try again shortly.";
            case EconomyUnavailableException.CODE_SUBJECT_MISSING,
                    EconomyUnavailableException.CODE_SUBJECT_NOT_ACTIVE,
                    EconomyUnavailableException.CODE_NO_ACCOUNT ->
                    "Your account is not available.";
            case EconomyUnavailableException.CODE_AMOUNT_INVALID ->
                    "Invalid amount.";
            case EconomyUnavailableException.CODE_MEMO_INVALID ->
                    "Payment rejected: memo is invalid.";
            case EconomyUnavailableException.CODE_SELF_TRANSFER ->
                    "You cannot pay yourself.";
            case EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS ->
                    "You do not have enough funds.";
            case EconomyUnavailableException.CODE_OVERFLOW ->
                    "Transaction would exceed the maximum balance.";
            case EconomyUnavailableException.CODE_COOLDOWN ->
                    "Please wait before transferring again.";
            case EconomyUnavailableException.CODE_STALE_REVISION ->
                    "Transaction conflict; please retry.";
            case EconomyUnavailableException.CODE_CAPACITY_EXCEEDED,
                    EconomyUnavailableException.CODE_STORE_FAILURE,
                    EconomyUnavailableException.CODE_ECONOMY_UNAVAILABLE,
                    EconomyUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                    EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE ->
                    "Economy is currently unavailable.";
            default -> "Economy is currently unavailable.";
        };
    }

    private static int unexpected(
            CommandSourceStack source,
            String commandId,
            RuntimeException exception
    ) {
        LOGGER.error(
                "[Command] Unexpected failure executing {} for source {}",
                commandId,
                source.getEntity() == null ? "non-player" : "player",
                exception
        );
        return CommandFeedback.failure(
                source,
                "FontaineRepublic could not complete the command."
        );
    }
}
