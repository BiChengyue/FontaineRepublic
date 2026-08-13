package com.fontainerepublic.server.parliament;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.parliament.api.BillReceipt;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.api.ProposalDraft;
import com.fontainerepublic.server.parliament.api.ProposalProjection;
import com.fontainerepublic.server.parliament.api.ProposalReceipt;
import com.fontainerepublic.server.parliament.api.VoteReceipt;
import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.VoteChoice;
import com.fontainerepublic.server.parliament.model.VoteId;
import com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Parliament command surface (FR-PAR-001-A §6; FR-PAR-001 task §3.3): bounded
 * proposal submission/listing, on-site-gated vote open/cast/close, and exact
 * bill lookup. Never enumerates the store; output is bounded. Every
 * authoritative mutation issues a fresh {@code ONSITE_OFFICIAL_DUTY}
 * on-site context from the authoritative player position at the given
 * terminal, and the service revalidates that context at its final mutation
 * boundary. Execution resolves the current ACTIVE {@link ParliamentService}
 * per invocation through the {@link CommandRuntimeResolver} and never caches
 * services or state.
 *
 * <pre>
 * /fr parliament proposal submit &lt;title&gt; &lt;normLevel&gt; &lt;terminalId&gt; &lt;fullText&gt;
 * /fr parliament proposal list [afterSeq] [limit]
 * /fr parliament vote open &lt;proposalId&gt; &lt;terminalId&gt;
 * /fr parliament vote cast &lt;voteId&gt; &lt;choice&gt; &lt;terminalId&gt;
 * /fr parliament vote close &lt;voteId&gt; &lt;terminalId&gt;
 * /fr parliament bill show &lt;billId&gt;
 * </pre>
 */
public final class ParliamentCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ParliamentCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("parliament")
                .then(Commands.literal("proposal")
                        .then(Commands.literal("submit")
                                .then(Commands.argument(
                                                "title",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "normLevel",
                                                        StringArgumentType.string()
                                                )
                                                .then(Commands.argument(
                                                                "terminalId",
                                                                StringArgumentType.string()
                                                        )
                                                        .then(Commands.argument(
                                                                        "fullText",
                                                                        StringArgumentType.greedyString()
                                                                )
                                                                .executes(context ->
                                                                        proposalSubmit(
                                                                                context,
                                                                                runtimeResolver
                                                                        )))))))
                        .then(Commands.literal("list")
                                .executes(context -> proposalList(
                                        context, runtimeResolver, 0, 10
                                ))
                                .then(Commands.argument(
                                                "afterSeq",
                                                IntegerArgumentType.integer(0)
                                        )
                                        .executes(context -> proposalList(
                                                context, runtimeResolver,
                                                IntegerArgumentType.getInteger(
                                                        context, "afterSeq"
                                                ),
                                                10
                                        ))
                                        .then(Commands.argument(
                                                        "limit",
                                                        IntegerArgumentType.integer(1)
                                                )
                                                .executes(context -> proposalList(
                                                        context, runtimeResolver,
                                                        IntegerArgumentType.getInteger(
                                                                context, "afterSeq"
                                                        ),
                                                        IntegerArgumentType.getInteger(
                                                                context, "limit"
                                                        )
                                                ))))))
                .then(Commands.literal("vote")
                        .then(Commands.literal("open")
                                .then(Commands.argument(
                                                "proposalId",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "terminalId",
                                                        StringArgumentType.string()
                                                )
                                                .executes(context -> voteOpen(
                                                        context, runtimeResolver
                                                )))))
                        .then(Commands.literal("cast")
                                .then(Commands.argument(
                                                "voteId",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "choice",
                                                        StringArgumentType.string()
                                                )
                                                .then(Commands.argument(
                                                                "terminalId",
                                                                StringArgumentType.string()
                                                        )
                                                        .executes(context -> voteCast(
                                                                context, runtimeResolver
                                                        ))))))
                        .then(Commands.literal("close")
                                .then(Commands.argument(
                                                "voteId",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "terminalId",
                                                        StringArgumentType.string()
                                                )
                                                .executes(context -> voteClose(
                                                        context, runtimeResolver
                                                ))))))
                .then(Commands.literal("bill")
                        .then(Commands.literal("show")
                                .then(Commands.argument(
                                                "billId",
                                                StringArgumentType.string()
                                        )
                                        .executes(context -> billShow(
                                                context, runtimeResolver
                                        )))));
    }

    // ------------------------------------------------------------------
    // /fr parliament proposal submit|list
    // ------------------------------------------------------------------

    private static int proposalSubmit(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        String title = StringArgumentType.getString(context, "title");
        NormLevel level = parseNormLevel(
                source, StringArgumentType.getString(context, "normLevel")
        );
        if (level == null) {
            return CommandFeedback.FAILURE;
        }
        TerminalId terminalId = parseTerminalId(
                source, StringArgumentType.getString(context, "terminalId")
        );
        if (terminalId == null) {
            return CommandFeedback.FAILURE;
        }
        String fullText = StringArgumentType.getString(context, "fullText");
        Optional<InstitutionAccessService> access =
                runtimeResolver.institutionAccessService();
        if (access.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic institution-access runtime is unavailable."
            );
        }
        try {
            OnSiteContext onSite = issueOfficialContext(
                    source, access.get(), actor, terminalId
            );
            ProposalReceipt receipt = service.get().submitProposal(
                    new ProposalDraft(title, level, fullText),
                    onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Proposal " + receipt.proposal().proposalId()
                            + " submitted (" + receipt.proposal().normLevel()
                            + ", " + receipt.proposal().title() + ", state "
                            + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Proposal submission", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Proposal submission", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.proposal.submit", failure);
        }
    }

    private static int proposalList(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            long afterSeq,
            int limit
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        try {
            List<ProposalProjection> proposals = service.get().proposals(
                    afterSeq, Math.max(1, Math.min(limit, 50))
            );
            if (proposals.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "No proposals after sequence " + afterSeq + "."
                );
            }
            StringBuilder builder = new StringBuilder();
            int shown = 0;
            for (ProposalProjection proposal : proposals) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append("#").append(proposal.proposalSeq())
                        .append(" ").append(proposal.title())
                        .append(" (").append(proposal.proposalId())
                        .append(", ").append(proposal.normLevel())
                        .append(", ").append(proposal.state()).append(")");
                shown++;
            }
            return CommandFeedback.success(
                    source,
                    "Proposals (" + shown + "): " + builder + "."
            );
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Proposal list", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.proposal.list", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr parliament vote open|cast|close (on-site gated)
    // ------------------------------------------------------------------

    private static int voteOpen(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        ProposalId proposalId = parseProposalId(
                source, StringArgumentType.getString(context, "proposalId")
        );
        if (proposalId == null) {
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
            OnSiteContext onSite = issueOfficialContext(
                    source, access.get(), actor, terminalId
            );
            VoteReceipt receipt = service.get().openVote(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Ballot " + receipt.vote().voteId() + " opened for proposal "
                            + proposalId + " (threshold "
                            + receipt.vote().requiredThreshold() + " of "
                            + receipt.vote().frozenRosterCount() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Vote open", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Vote open", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.vote.open", failure);
        }
    }

    private static int voteCast(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        VoteId voteId = parseVoteId(
                source, StringArgumentType.getString(context, "voteId")
        );
        if (voteId == null) {
            return CommandFeedback.FAILURE;
        }
        VoteChoice choice = parseChoice(
                source, StringArgumentType.getString(context, "choice")
        );
        if (choice == null) {
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
            OnSiteContext onSite = issueOfficialContext(
                    source, access.get(), actor, terminalId
            );
            VoteReceipt receipt = service.get().castVote(
                    actor, voteId, choice, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Vote recorded on ballot " + voteId + ": " + choice + "."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Vote cast", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Vote cast", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.vote.cast", failure);
        }
    }

    private static int voteClose(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        VoteId voteId = parseVoteId(
                source, StringArgumentType.getString(context, "voteId")
        );
        if (voteId == null) {
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
            OnSiteContext onSite = issueOfficialContext(
                    source, access.get(), actor, terminalId
            );
            BillReceipt receipt = service.get().closeVoteAndAdvance(
                    actor, voteId, onSite
            );
            access.get().consume(onSite);
            if (!receipt.applied()) {
                return CommandFeedback.success(
                        source,
                        "Ballot " + voteId + " was already closed; no change."
                );
            }
            if (receipt.passed()) {
                return CommandFeedback.success(
                        source,
                        "Ballot " + voteId + " passed; bill "
                                + receipt.bill().map(Bill::billId)
                                .map(BillId::toString).orElse("<none>")
                                + " created."
                );
            }
            return CommandFeedback.success(
                    source,
                    "Ballot " + voteId + " was rejected (threshold not met)."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Vote close", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Vote close", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.vote.close", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr parliament bill show <billId> (exact read)
    // ------------------------------------------------------------------

    private static int billShow(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        BillId billId = parseBillId(
                source, StringArgumentType.getString(context, "billId")
        );
        if (billId == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            Optional<Bill> bill = service.get().bill(billId);
            if (bill.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "Bill " + billId + " does not exist."
                );
            }
            Bill current = bill.get();
            return CommandFeedback.success(
                    source,
                    "Bill " + billId + " for proposal "
                            + current.proposalId() + " (state "
                            + current.state() + ", passed at "
                            + current.passedAt() + ")."
            );
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Bill lookup", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.bill.show", failure);
        }
    }

    // ------------------------------------------------------------------
    // shared execution helpers
    // ------------------------------------------------------------------

    private static Optional<ParliamentService> service(
            CommandRuntimeResolver runtimeResolver
    ) {
        return runtimeResolver.parliamentService();
    }

    private static int unavailableRuntime(CommandSourceStack source) {
        LOGGER.warn("[Command] Parliament runtime is unavailable");
        return CommandFeedback.failure(
                source,
                "FontaineRepublic parliament runtime is unavailable."
        );
    }

    private static UUID requirePlayer(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            CommandFeedback.failure(
                    source,
                    "This command must be run by a player."
            );
            return null;
        }
        return player.getUUID();
    }

    private static NormLevel parseNormLevel(CommandSourceStack source, String input) {
        try {
            return NormLevel.valueOf(input.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    "Rejected: normLevel must be one of CONSTITUTION_BASIC, "
                            + "ORGANIC, ORDINARY, ADMINISTRATIVE."
            );
            return null;
        }
    }

    private static VoteChoice parseChoice(CommandSourceStack source, String input) {
        try {
            return VoteChoice.valueOf(input.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    "Rejected: choice must be one of FOR, AGAINST, ABSTAIN."
            );
            return null;
        }
    }

    private static ProposalId parseProposalId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "proposalId");
        if (parsed == null) {
            return null;
        }
        return ProposalId.of(parsed);
    }

    private static VoteId parseVoteId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "voteId");
        if (parsed == null) {
            return null;
        }
        return VoteId.of(parsed);
    }

    private static BillId parseBillId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "billId");
        if (parsed == null) {
            return null;
        }
        return BillId.of(parsed);
    }

    private static TerminalId parseTerminalId(CommandSourceStack source, String input) {
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
     * authoritative server player position at the given terminal (FR-INST-001-A
     * §6.3). The service revalidates this context at its final mutation
     * boundary.
     */
    private static OnSiteContext issueOfficialContext(
            CommandSourceStack source,
            InstitutionAccessService access,
            UUID playerId,
            TerminalId terminalId
    ) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Only a player can perform an official duty"
            );
        }
        return access.issueOnSiteContext(
                playerId,
                terminalId,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
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
            ParliamentUnavailableException failure
    ) {
        String code = failure.failureCode();
        String detail = switch (code) {
            case ParliamentUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                 ParliamentUnavailableException.CODE_CITIZEN_DIRECTORY_UNAVAILABLE ->
                    "the actor could not be resolved to a provisioned player.";
            case ParliamentUnavailableException.CODE_NOT_CITIZEN ->
                    "the actor is not an active citizen (or not on the ballot's frozen roster).";
            case ParliamentUnavailableException.CODE_ROSTER_UNAVAILABLE ->
                    "no citizens are registered; a ballot cannot be opened.";
            case ParliamentUnavailableException.CODE_PROPOSAL_NOT_FOUND ->
                    "the proposal does not exist.";
            case ParliamentUnavailableException.CODE_VOTE_NOT_FOUND ->
                    "the ballot does not exist.";
            case ParliamentUnavailableException.CODE_BILL_NOT_FOUND ->
                    "the bill does not exist.";
            case ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION ->
                    "the state machine does not allow this transition.";
            case ParliamentUnavailableException.CODE_BALLOT_NOT_OPEN ->
                    "the ballot is not open for voting.";
            case ParliamentUnavailableException.CODE_ALREADY_VOTED ->
                    "this citizen already voted (one vote per citizen).";
            case ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID ->
                    "the on-site official-duty context is not valid.";
            case ParliamentUnavailableException.CODE_INVALID_REQUEST ->
                    "the request failed validation.";
            case ParliamentUnavailableException.CODE_CAPACITY_EXCEEDED ->
                    "a capacity budget was exceeded.";
            case ParliamentUnavailableException.CODE_STORE_FAILURE ->
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
                action + " rejected: no valid on-site official-duty context "
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
