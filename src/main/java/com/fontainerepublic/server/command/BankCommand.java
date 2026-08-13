package com.fontainerepublic.server.command;

import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
 * Central-bank official-duty command surface (FR-ECO-002-A §3): the public
 * treasury total and the on-site official duties deposit / withdraw /
 * freeze / unfreeze. Every official mutation is gated on a fresh
 * {@code ONSITE_OFFICIAL_DUTY} on-site context issued from a registered
 * central-bank terminal; the economy service revalidates the context at its
 * final mutation boundary. OP permission alone is never sufficient.
 *
 * <pre>
 * /fr bank balance
 * /fr bank deposit &lt;target&gt; &lt;amount&gt; &lt;terminalId&gt; [reason]
 * /fr bank withdraw &lt;target&gt; &lt;amount&gt; &lt;terminalId&gt; [reason]
 * /fr bank freeze &lt;target&gt; &lt;terminalId&gt; [reason]
 * /fr bank unfreeze &lt;target&gt; &lt;terminalId&gt; [reason]
 * </pre>
 *
 * The target accepts a canonical UUID, a registry number, or an exact game
 * name (all converging on one {@code SubjectId} through the shared
 * resolution in {@link MoneyCommand}). Deliberately absent: cash/ATM,
 * other-player balances, emergency issue/reclaim (FR-EMG), and GUI. Execution
 * resolves the current ACTIVE {@link EconomyService} per invocation through
 * the {@link CommandRuntimeResolver} and never caches services or state.
 */
public final class BankCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Official reason used when the operator omits the optional reason. */
    private static final String DEFAULT_REASON = "Central bank official duty";

    private BankCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("bank")
                .then(Commands.literal("balance")
                        .executes(context -> balance(
                                context.getSource(),
                                runtimeResolver
                        )))
                .then(Commands.literal("deposit")
                        .then(targetAmountTerminal(runtimeResolver, "deposit")))
                .then(Commands.literal("withdraw")
                        .then(targetAmountTerminal(runtimeResolver, "withdraw")))
                .then(Commands.literal("freeze")
                        .then(targetTerminal(runtimeResolver, "freeze")))
                .then(Commands.literal("unfreeze")
                        .then(targetTerminal(runtimeResolver, "unfreeze")));
    }

    /**
     * Shared subtree for deposit/withdraw:
     * {@code <target> <amount> <terminalId> [reason]}.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> targetAmountTerminal(
            CommandRuntimeResolver runtimeResolver,
            String action
    ) {
        return Commands.argument("target", StringArgumentType.string())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .then(Commands.argument("terminalId", StringArgumentType.string())
                                .executes(context -> amountDuty(
                                        context,
                                        runtimeResolver,
                                        action,
                                        null
                                ))
                                .then(Commands.argument(
                                                "reason",
                                                StringArgumentType.greedyString()
                                        )
                                        .executes(context -> amountDuty(
                                                context,
                                                runtimeResolver,
                                                action,
                                                StringArgumentType.getString(
                                                        context, "reason"
                                                )
                                        )))));
    }

    /**
     * Shared subtree for freeze/unfreeze:
     * {@code <target> <terminalId> [reason]}.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> targetTerminal(
            CommandRuntimeResolver runtimeResolver,
            String action
    ) {
        return Commands.argument("target", StringArgumentType.string())
                .then(Commands.argument("terminalId", StringArgumentType.string())
                        .executes(context -> stateDuty(
                                context,
                                runtimeResolver,
                                action,
                                null
                        ))
                        .then(Commands.argument(
                                        "reason",
                                        StringArgumentType.greedyString()
                                )
                                .executes(context -> stateDuty(
                                        context,
                                        runtimeResolver,
                                        action,
                                        StringArgumentType.getString(
                                                context, "reason"
                                        )
                                ))));
    }

    // ------------------------------------------------------------------
    // /fr bank balance (read-only, public)
    // ------------------------------------------------------------------

    private static int balance(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        Optional<EconomyService> economy = economy(runtimeResolver);
        if (economy.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Economy runtime is unavailable."
            );
        }
        try {
            long treasury = economy.get().getTreasuryBalance();
            return CommandFeedback.success(
                    source,
                    "National treasury: " + economy.get().formatBalance(treasury) + "."
            );
        } catch (RuntimeException failure) {
            return unexpected(source, "bank.balance", failure);
        }
    }

    // ------------------------------------------------------------------
    // official duties
    // ------------------------------------------------------------------

    private static int amountDuty(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String action,
            String reason
    ) {
        CommandSourceStack source = context.getSource();
        Optional<EconomyService> economy = economy(runtimeResolver);
        if (economy.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Economy runtime is unavailable."
            );
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        MoneyCommand.TargetResolution target = resolveTarget(
                source,
                runtimeResolver,
                StringArgumentType.getString(context, "target")
        );
        if (target == null) {
            return CommandFeedback.FAILURE;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        TerminalId terminalId = parseTerminalId(
                source, StringArgumentType.getString(context, "terminalId")
        );
        if (terminalId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> access =
                runtimeResolver.institutionAccessService();
        if (access.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic institution-access runtime is unavailable."
            );
        }
        try {
            OnSiteContext onSite = issueContext(
                    source,
                    access.get(),
                    actor,
                    terminalId,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY
            );
            EconomyTransaction transaction;
            if (action.equals("deposit")) {
                transaction = economy.get().deposit(
                        target.subjectId(),
                        amount,
                        reasonOrDefault(reason),
                        onSite
                );
            } else {
                transaction = economy.get().withdraw(
                        target.subjectId(),
                        amount,
                        reasonOrDefault(reason),
                        onSite
                );
            }
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    capitalize(action) + " committed (transaction #"
                            + transaction.transactionId() + "): "
                            + economy.get().formatBalance(amount) + " to subject "
                            + target.subjectId() + "."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, capitalize(action), failure);
        } catch (EconomyUnavailableException failure) {
            return reject(source, capitalize(action), failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "bank." + action, failure);
        }
    }

    private static int stateDuty(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String action,
            String reason
    ) {
        CommandSourceStack source = context.getSource();
        Optional<EconomyService> economy = economy(runtimeResolver);
        if (economy.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Economy runtime is unavailable."
            );
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        MoneyCommand.TargetResolution target = resolveTarget(
                source,
                runtimeResolver,
                StringArgumentType.getString(context, "target")
        );
        if (target == null) {
            return CommandFeedback.FAILURE;
        }
        TerminalId terminalId = parseTerminalId(
                source, StringArgumentType.getString(context, "terminalId")
        );
        if (terminalId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> access =
                runtimeResolver.institutionAccessService();
        if (access.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic institution-access runtime is unavailable."
            );
        }
        try {
            OnSiteContext onSite = issueContext(
                    source,
                    access.get(),
                    actor,
                    terminalId,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY
            );
            EconomyAccount updated;
            if (action.equals("freeze")) {
                updated = economy.get().freeze(
                        target.subjectId(),
                        reasonOrDefault(reason),
                        onSite
                );
            } else {
                updated = economy.get().unfreeze(
                        target.subjectId(),
                        reasonOrDefault(reason),
                        onSite
                );
            }
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    capitalize(action) + " committed: subject "
                            + target.subjectId() + " is now "
                            + (updated.frozen() ? "frozen" : "unfrozen") + "."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, capitalize(action), failure);
        } catch (EconomyUnavailableException failure) {
            return reject(source, capitalize(action), failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "bank." + action, failure);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Optional<EconomyService> economy(
            CommandRuntimeResolver runtimeResolver
    ) {
        return runtimeResolver.economyService();
    }

    private static MoneyCommand.TargetResolution resolveTarget(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver,
            String targetInput
    ) {
        MoneyCommand.TargetResolution target = MoneyCommand.resolveTarget(
                runtimeResolver.playerDirectoryService(),
                runtimeResolver.subjectRegistryService(),
                targetInput
        );
        if (target.subjectId() == null) {
            CommandFeedback.failure(source, target.failureMessage());
            return null;
        }
        return target;
    }

    private static String reasonOrDefault(String reason) {
        if (reason == null) {
            return DEFAULT_REASON;
        }
        return reason;
    }

    private static String capitalize(String action) {
        return Character.toUpperCase(action.charAt(0)) + action.substring(1);
    }

    private static UUID requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            CommandFeedback.failure(
                    source,
                    "Only a player can perform an official bank duty."
            );
            return null;
        }
        return player.getUUID();
    }

    private static TerminalId parseTerminalId(
            CommandSourceStack source,
            String input
    ) {
        UUID parsed = parseCanonicalUuid(source, input, "terminalId");
        if (parsed == null) {
            return null;
        }
        return TerminalId.of(parsed);
    }

    private static UUID parseCanonicalUuid(
            CommandSourceStack source,
            String input,
            String argument
    ) {
        try {
            UUID parsed = UUID.fromString(input);
            if (!parsed.toString().equals(input)) {
                CommandFeedback.failure(
                        source,
                        "Rejected: " + argument + " must be a canonical UUID."
                );
                return null;
            }
            return parsed;
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    "Rejected: " + argument + " must be a canonical UUID."
            );
            return null;
        }
    }

    /**
     * Issues a fresh {@code ONSITE_OFFICIAL_DUTY} context from the
     * authoritative server player position at the given terminal
     * (FR-INST-001-A §6.3). The economy service revalidates this context at
     * its final mutation boundary.
     */
    private static OnSiteContext issueContext(
            CommandSourceStack source,
            InstitutionAccessService access,
            UUID playerId,
            TerminalId terminalId,
            CapabilityClass capability
    ) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Only a player can perform an institutional action"
            );
        }
        return access.issueOnSiteContext(
                playerId,
                terminalId,
                capability,
                player.level().dimension().location().toString(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ()
        );
    }

    /**
     * Bounded rejection feedback. The stable failure code is surfaced without
     * leaking internal details; ordinary mistakes receive one explicit line.
     */
    private static int reject(
            CommandSourceStack source,
            String action,
            EconomyUnavailableException failure
    ) {
        String code = failure.failureCode();
        String detail = switch (code) {
            case EconomyUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                 EconomyUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                 EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE ->
                    "the target could not be resolved to a provisioned subject.";
            case EconomyUnavailableException.CODE_SUBJECT_MISSING ->
                    "the target subject does not exist.";
            case EconomyUnavailableException.CODE_SUBJECT_NOT_ACTIVE ->
                    "the target subject is not active.";
            case EconomyUnavailableException.CODE_NO_ACCOUNT ->
                    "the target has no economy account.";
            case EconomyUnavailableException.CODE_AMOUNT_INVALID ->
                    "the amount is invalid.";
            case EconomyUnavailableException.CODE_MEMO_INVALID ->
                    "the reason is invalid.";
            case EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS ->
                    "the source balance is insufficient.";
            case EconomyUnavailableException.CODE_TREASURY_INSUFFICIENT ->
                    "the national treasury does not hold the amount.";
            case EconomyUnavailableException.CODE_FROZEN ->
                    "the target account is frozen.";
            case EconomyUnavailableException.CODE_ON_SITE_CONTEXT_INVALID ->
                    "the on-site context is not valid.";
            case EconomyUnavailableException.CODE_STALE_REVISION ->
                    "the account state changed concurrently; retry the command.";
            case EconomyUnavailableException.CODE_OVERFLOW,
                 EconomyUnavailableException.CODE_CAPACITY_EXCEEDED ->
                    "a capacity or balance bound was exceeded.";
            case EconomyUnavailableException.CODE_STORE_FAILURE ->
                    "the durable store did not acknowledge the change.";
            default -> "the request failed validation.";
        };
        return CommandFeedback.failure(
                source,
                action + " rejected: " + detail
        );
    }

    private static int rejectOnSite(
            CommandSourceStack source,
            String action,
            InstitutionAccessUnavailableException failure
    ) {
        return CommandFeedback.failure(
                source,
                action + " rejected: no valid on-site context "
                        + "(code " + failure.failureCode() + ")."
        );
    }

    private static int unexpected(
            CommandSourceStack source,
            String commandId,
            RuntimeException failure
    ) {
        LOGGER.error(
                "[Command] Unexpected failure executing {} for source {}",
                commandId,
                source.getEntity() == null ? "non-player" : "player",
                failure
        );
        return CommandFeedback.failure(
                source,
                "FontaineRepublic could not complete the command."
        );
    }
}
