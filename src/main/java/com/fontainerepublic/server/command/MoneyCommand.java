package com.fontainerepublic.server.command;

import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.playerdata.api.PlayerDirectoryService;
import com.fontainerepublic.server.playerdata.model.GameNameNormalizer;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolution;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolutionKind;
import com.fontainerepublic.server.registry.api.PublicRoutingResult;
import com.fontainerepublic.server.registry.api.RoutingStatus;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
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
 * FR-CMD-USER-001 and FR-CMD-USER-002): own balance, ordinary payment with
 * bounded memo, and bounded own history. The {@code pay} target accepts a
 * canonical UUID, an exact game name, or a public registry number
 * ({@code TT-NNNNNN-CC}); all three routes converge on the same
 * {@link SubjectId} through PlayerData/FR-ID services before any mutation
 * (FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.3). Deliberately absent: top / bank /
 * treasury / other-player balance / freeze / cash / issuance / fuzzy or
 * enumerated name input. Execution resolves the current ACTIVE
 * {@link EconomyService} per invocation through the
 * {@link CommandRuntimeResolver} and never caches services or state.
 */
public final class MoneyCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bounded history page argument (1-based); guards the cursor loop. */
    private static final int MAX_HISTORY_PAGE = 1_000;

    /** Bounded syntax hint for malformed target input (UUID / number / name). */
    private static final String INVALID_TARGET_SYNTAX_MESSAGE =
            "Payment rejected: target must be a canonical UUID, registry number "
                    + "(TT-NNNNNN-CC), or valid game name.";

    /** Bounded generic feedback for unresolvable targets; no classification detail. */
    private static final String TARGET_UNRESOLVABLE_MESSAGE =
            "Payment rejected: target cannot be resolved.";

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
                        .then(Commands.argument("target", StringArgumentType.string())
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
    // /fr money pay <target> <amount> [memo]
    // ------------------------------------------------------------------

    private static int pay(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String rawMemo
    ) {
        CommandSourceStack source = context.getSource();
        String targetInput = StringArgumentType.getString(context, "target");

        // Syntax layer first (no service dependency): the target must be a
        // canonical UUID, a registry number (10 digits or TT-NNNNNN-CC), or a
        // valid game name before anything else is resolved.
        if (classifyTarget(targetInput).isEmpty()) {
            return CommandFeedback.failure(source, INVALID_TARGET_SYNTAX_MESSAGE);
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
            PayOutcome outcome = executePay(
                    service.get(),
                    runtimeResolver.playerDirectoryService(),
                    runtimeResolver.subjectRegistryService(),
                    player.getUUID(),
                    targetInput,
                    LongArgumentType.getLong(context, "amount"),
                    memo
            );
            if (outcome.receipt() == null) {
                return CommandFeedback.failure(source, outcome.failureMessage());
            }
            TransferReceipt receipt = outcome.receipt();
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

    /**
     * Pay core without a command-source binding (testable): re-resolves the
     * exact target at execution time through the server services (suggestions
     * are never authoritative), re-resolves the actor's own subject, and only
     * then performs the ordinary {@link SubjectId}-keyed transfer. A failed
     * target resolution never reaches the economy mutation.
     */
    static PayOutcome executePay(
            EconomyService economy,
            Optional<PlayerDirectoryService> directory,
            Optional<SubjectRegistryService> registry,
            UUID fromPlayerId,
            String targetInput,
            long amount,
            String memo
    ) {
        TargetResolution target = resolveTarget(directory, registry, targetInput);
        if (!target.resolved()) {
            return PayOutcome.failed(target.failureMessage());
        }
        if (registry.isEmpty()) {
            return PayOutcome.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        Optional<SubjectId> self = registry.get()
                .findSubjectForPlayer(fromPlayerId)
                .map(SubjectRecord::subjectId);
        if (self.isEmpty()) {
            return PayOutcome.failed("Your account is not available.");
        }
        TransferReceipt receipt = economy.transfer(
                self.get(),
                target.subjectId(),
                amount,
                memo
        );
        return PayOutcome.success(receipt);
    }

    /** Syntax classification of the target input (no service dependency). */
    private static Optional<TargetKind> classifyTarget(String input) {
        if (isCanonicalUuid(input)) {
            return Optional.of(TargetKind.UUID);
        }
        if (isRegistryNumberSyntax(input)) {
            return Optional.of(TargetKind.REGISTRY_NUMBER);
        }
        if (GameNameNormalizer.normalize(input).isPresent()) {
            return Optional.of(TargetKind.GAME_NAME);
        }
        return Optional.empty();
    }

    private static boolean isCanonicalUuid(String input) {
        try {
            parseCanonicalUuid(input);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * Accepts exactly the canonical ten-digit form or the display form
     * {@code TT-NNNNNN-CC} at the syntax layer; the MOD 97 checksum and type
     * rules are enforced by {@link RegistryNumber#parse} during resolution.
     */
    private static boolean isRegistryNumberSyntax(String input) {
        if (input.length() == RegistryNumber.CANONICAL_LENGTH) {
            return allAsciiDigits(input);
        }
        if (input.length() == RegistryNumber.DISPLAY_LENGTH
                && input.charAt(2) == '-'
                && input.charAt(9) == '-') {
            return allAsciiDigits(
                    input.substring(0, 2)
                            + input.substring(3, 9)
                            + input.substring(10, 12)
            );
        }
        return false;
    }

    private static boolean allAsciiDigits(String input) {
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current < '0' || current > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Execution-time target resolution: every input kind converges on one
     * {@link SubjectId}. Failures are bounded and never expose directory or
     * registry classification details.
     */
    static TargetResolution resolveTarget(
            Optional<PlayerDirectoryService> directory,
            Optional<SubjectRegistryService> registry,
            String targetInput
    ) {
        Optional<TargetKind> kind = classifyTarget(targetInput);
        if (kind.isEmpty()) {
            return TargetResolution.failed(INVALID_TARGET_SYNTAX_MESSAGE);
        }
        return switch (kind.get()) {
            case UUID -> resolveUuidTarget(registry, targetInput);
            case REGISTRY_NUMBER -> resolveNumberTarget(registry, targetInput);
            case GAME_NAME -> resolveNameTarget(directory, registry, targetInput);
        };
    }

    private static TargetResolution resolveUuidTarget(
            Optional<SubjectRegistryService> registry,
            String targetInput
    ) {
        if (registry.isEmpty()) {
            return TargetResolution.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        UUID targetUuid = parseCanonicalUuid(targetInput);
        Optional<SubjectId> subject = registry.get()
                .findSubjectForPlayer(targetUuid)
                .map(SubjectRecord::subjectId);
        if (subject.isEmpty()) {
            return TargetResolution.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        return TargetResolution.resolved(subject.get());
    }

    private static TargetResolution resolveNumberTarget(
            Optional<SubjectRegistryService> registry,
            String targetInput
    ) {
        RegistryNumber number;
        try {
            number = RegistryNumber.parse(targetInput);
        } catch (IllegalArgumentException invalid) {
            return TargetResolution.failed(INVALID_TARGET_SYNTAX_MESSAGE);
        }
        if (registry.isEmpty()) {
            return TargetResolution.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        PublicRoutingResult result = registry.get().resolveExactRegistryNumber(number);
        if (result.status() != RoutingStatus.ROUTABLE_ACTIVE) {
            return TargetResolution.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        return TargetResolution.resolved(
                result.subject().orElseThrow().subjectId()
        );
    }

    private static TargetResolution resolveNameTarget(
            Optional<PlayerDirectoryService> directory,
            Optional<SubjectRegistryService> registry,
            String targetInput
    ) {
        if (directory.isEmpty() || registry.isEmpty()) {
            return TargetResolution.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        PlayerNameResolution resolution = directory.get()
                .resolveExactGameName(targetInput);
        if (resolution.kind() != PlayerNameResolutionKind.UNIQUE_CURRENT) {
            // UNKNOWN / RETIRED / AMBIGUOUS share one bounded message;
            // INVALID_INPUT is its own bounded syntax hint.
            return TargetResolution.failed(resolution.publicMessage());
        }
        Optional<SubjectId> subject = registry.get()
                .findSubjectForPlayer(resolution.playerId().orElseThrow())
                .map(SubjectRecord::subjectId);
        if (subject.isEmpty()) {
            return TargetResolution.failed(TARGET_UNRESOLVABLE_MESSAGE);
        }
        return TargetResolution.resolved(subject.get());
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

    /** Syntax classification of one {@code pay} target input. */
    private enum TargetKind {
        UUID,
        REGISTRY_NUMBER,
        GAME_NAME
    }

    /**
     * Closed result of target resolution: exactly one of a resolved
     * {@link SubjectId} or a bounded failure message.
     */
    record TargetResolution(SubjectId subjectId, String failureMessage) {

        private static TargetResolution resolved(SubjectId subjectId) {
            return new TargetResolution(
                    Objects.requireNonNull(subjectId, "subjectId"),
                    null
            );
        }

        private static TargetResolution failed(String failureMessage) {
            return new TargetResolution(
                    null,
                    Objects.requireNonNull(failureMessage, "failureMessage")
            );
        }

        private boolean resolved() {
            return subjectId != null;
        }
    }

    /**
     * Closed outcome of {@link #executePay}: exactly one of a successful
     * {@link TransferReceipt} or a bounded failure message.
     */
    record PayOutcome(TransferReceipt receipt, String failureMessage) {

        private static PayOutcome success(TransferReceipt receipt) {
            return new PayOutcome(
                    Objects.requireNonNull(receipt, "receipt"),
                    null
            );
        }

        private static PayOutcome failed(String failureMessage) {
            return new PayOutcome(
                    null,
                    Objects.requireNonNull(failureMessage, "failureMessage")
            );
        }
    }
}
