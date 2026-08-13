package com.fontainerepublic.server.parliament;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.parliament.api.AmendmentDraft;
import com.fontainerepublic.server.parliament.api.BillReceipt;
import com.fontainerepublic.server.parliament.api.CourtReceipt;
import com.fontainerepublic.server.parliament.api.GuardianReceipt;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.api.ProposalDraft;
import com.fontainerepublic.server.parliament.api.ProposalProjection;
import com.fontainerepublic.server.parliament.api.ProposalReceipt;
import com.fontainerepublic.server.parliament.api.ReferendumReceipt;
import com.fontainerepublic.server.parliament.api.VoteReceipt;
import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.GuardianChannel;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.VoteChoice;
import com.fontainerepublic.server.parliament.model.VoteId;
import com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException;
import com.fontainerepublic.server.parliament.service.DefaultParliamentService;
import com.fontainerepublic.server.registry.service.BootstrapConsoleClassifier;
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
 * zone, and the service revalidates that context at its final mutation
 * boundary. Execution resolves the current ACTIVE {@link ParliamentService}
 * per invocation through the {@link CommandRuntimeResolver} and never caches
 * services or state.
 *
 * <pre>
 * /fr parliament proposal submit &lt;title&gt; &lt;normLevel&gt; &lt;zoneId&gt; &lt;fullText&gt;
 * /fr parliament proposal list [afterSeq] [limit]
 * /fr parliament vote open &lt;proposalId&gt; &lt;zoneId&gt;
 * /fr parliament vote cast &lt;voteId&gt; &lt;choice&gt; &lt;zoneId&gt;
 * /fr parliament vote close &lt;voteId&gt; &lt;zoneId&gt;
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
                .then(proposalTree(buildContext, runtimeResolver))
                .then(voteTree(runtimeResolver))
                .then(billTree(runtimeResolver))
                .then(guardianTree(runtimeResolver))
                .then(courtTree(runtimeResolver))
                .then(amendmentTree(runtimeResolver))
                .then(referendumTree(runtimeResolver))
                .then(consentTree(runtimeResolver))
                .then(publishTree(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> proposalTree(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("proposal")
                .then(Commands.literal("submit")
                        .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("normLevel", StringArgumentType.string())
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .then(Commands.argument("fullText", StringArgumentType.greedyString())
                                                        .executes(context -> proposalSubmit(context, runtimeResolver)))))))
                .then(Commands.literal("list")
                        .executes(context -> proposalList(context, runtimeResolver, 0, 10))
                        .then(Commands.argument("afterSeq", IntegerArgumentType.integer(0))
                                .executes(context -> proposalList(
                                        context, runtimeResolver,
                                        IntegerArgumentType.getInteger(context, "afterSeq"),
                                        10))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                        .executes(context -> proposalList(
                                                context, runtimeResolver,
                                                IntegerArgumentType.getInteger(context, "afterSeq"),
                                                IntegerArgumentType.getInteger(context, "limit"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> voteTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("vote")
                .then(Commands.literal("open")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> voteOpen(context, runtimeResolver)))))
                .then(Commands.literal("cast")
                        .then(Commands.argument("voteId", StringArgumentType.string())
                                .then(Commands.argument("choice", StringArgumentType.string())
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .executes(context -> voteCast(context, runtimeResolver))))))
                .then(Commands.literal("close")
                        .then(Commands.argument("voteId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> voteClose(context, runtimeResolver)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> billTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("bill")
                .then(Commands.literal("show")
                        .then(Commands.argument("billId", StringArgumentType.string())
                                .executes(context -> billShow(context, runtimeResolver))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> guardianTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("guardian")
                .then(Commands.literal("submit")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> guardianSubmit(context, runtimeResolver)))))
                .then(Commands.literal("approve")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .executes(context -> guardianApprove(context, runtimeResolver))
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> guardianApprove(context, runtimeResolver)))))
                .then(Commands.literal("return")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("basis", StringArgumentType.string())
                                        .executes(context -> guardianReturn(context, runtimeResolver))
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .executes(context -> guardianReturn(context, runtimeResolver))))))
                .then(Commands.literal("recuse")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .executes(context -> guardianRecuse(context, runtimeResolver))
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> guardianRecuse(context, runtimeResolver)))))
                .then(Commands.literal("timeout")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .executes(context -> guardianTimeout(context, runtimeResolver))
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> guardianTimeout(context, runtimeResolver)))))
                .then(Commands.literal("override")
                        .then(Commands.literal("open")
                                .then(Commands.argument("proposalId", StringArgumentType.string())
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .executes(context -> overrideOpen(context, runtimeResolver))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> courtTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("court")
                .then(Commands.literal("submit")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> courtSubmit(context, runtimeResolver)))))
                .then(Commands.literal("pass")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> courtPass(context, runtimeResolver)))))
                .then(Commands.literal("return")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> courtReturn(context, runtimeResolver)))))
                .then(Commands.literal("extend")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> courtExtend(context, runtimeResolver)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> amendmentTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("amendment")
                .then(Commands.literal("submit")
                        .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .then(Commands.argument("fullText", StringArgumentType.greedyString())
                                                .executes(context -> amendmentSubmit(context, runtimeResolver))))))
                .then(Commands.literal("vote")
                        .then(Commands.literal("open")
                                .then(Commands.argument("proposalId", StringArgumentType.string())
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .executes(context -> parliamentVoteOpen(context, runtimeResolver)))))
                        .then(Commands.literal("close")
                                .then(Commands.argument("voteId", StringArgumentType.string())
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .executes(context -> parliamentVoteClose(context, runtimeResolver))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> referendumTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("referendum")
                .then(Commands.literal("open")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> referendumOpen(context, runtimeResolver)))))
                .then(Commands.literal("vote")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("choice", StringArgumentType.string())
                                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                                .executes(context -> referendumVote(context, runtimeResolver))))))
                .then(Commands.literal("close")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> referendumClose(context, runtimeResolver)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> consentTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("consent")
                .then(Commands.literal("approve")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .executes(context -> consentApprove(context, runtimeResolver))
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> consentApprove(context, runtimeResolver)))))
                .then(Commands.literal("reject")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .executes(context -> consentReject(context, runtimeResolver))
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> consentReject(context, runtimeResolver)))))
                .then(Commands.literal("timeout")
                        .then(Commands.argument("proposalId", StringArgumentType.string())
                                .executes(context -> consentTimeout(context, runtimeResolver))
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> consentTimeout(context, runtimeResolver)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> publishTree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("publish")
                .then(Commands.argument("proposalId", StringArgumentType.string())
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .executes(context -> amendmentPublish(context, runtimeResolver))));
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
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
    // /fr parliament guardian submit|approve|return|recuse|timeout|override
    // ------------------------------------------------------------------

    private static int guardianSubmit(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            GuardianReceipt receipt = service.get().submitForGuardianReview(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Proposal " + proposalId + " submitted to the guardian "
                            + "review (state " + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Guardian submission", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Guardian submission", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.guardian.submit", failure);
        }
    }

    private static int guardianApprove(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return guardianAction(context, runtimeResolver, "approve");
    }

    private static int guardianReturn(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        ProposalId proposalId = parseProposalId(
                source, StringArgumentType.getString(context, "proposalId")
        );
        if (proposalId == null) {
            return CommandFeedback.FAILURE;
        }
        String basis = StringArgumentType.getString(context, "basis");
        Optional<InstitutionAccessService> access =
                runtimeResolver.institutionAccessService();
        if (access.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic institution-access runtime is unavailable."
            );
        }
        try {
            GuardianInvocation invocation =
                    resolveGuardianInvocation(source, access.get(), context);
            GuardianReceipt receipt = service.get().guardianReturn(
                    invocation.actor(),
                    proposalId,
                    invocation.channel(),
                    basis,
                    invocation.context()
            );
            invocation.consume();
            return CommandFeedback.success(
                    source,
                    "Proposal " + proposalId + " returned by the guardian (state "
                            + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Guardian return", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Guardian return", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.guardian.return", failure);
        }
    }

    private static int guardianRecuse(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return guardianAction(context, runtimeResolver, "recuse");
    }

    private static int guardianTimeout(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return guardianAction(context, runtimeResolver, "timeout");
    }

    /**
     * Dispatches a guardian action by name (approve/recuse/timeout) after
     * resolving the guardian channel: the real local console, or the Hydro
     * Archon player on-site at the given parliament zone.
     */
    private static int guardianAction(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String action
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        ProposalId proposalId = parseProposalId(
                source, StringArgumentType.getString(context, "proposalId")
        );
        if (proposalId == null) {
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
            GuardianInvocation invocation =
                    resolveGuardianInvocation(source, access.get(), context);
            GuardianReceipt receipt = switch (action) {
                case "approve" -> service.get().guardianApprove(
                        invocation.actor(), proposalId, invocation.channel(),
                        invocation.context()
                );
                case "recuse" -> service.get().guardianRecuse(
                        invocation.actor(), proposalId, invocation.channel(),
                        invocation.context()
                );
                case "timeout" -> service.get().guardianTimeoutAdvance(
                        invocation.actor(), proposalId, invocation.channel(),
                        invocation.context()
                );
                default -> throw new IllegalStateException(
                        "Unknown guardian action: " + action
                );
            };
            invocation.consume();
            return CommandFeedback.success(
                    source,
                    "Guardian " + action + " applied to proposal " + proposalId
                            + " (state " + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Guardian " + action, failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Guardian " + action, failure);
        } catch (RuntimeException failure) {
            return unexpected(
                    source, "parliament.guardian." + action, failure
            );
        }
    }

    private static int overrideOpen(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            VoteReceipt receipt = service.get().openOverrideVote(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Override ballot " + receipt.vote().voteId()
                            + " opened for proposal " + proposalId
                            + " (threshold " + receipt.vote().requiredThreshold()
                            + " of " + receipt.vote().frozenRosterCount() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Override vote open", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Override vote open", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.guardian.override.open", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr parliament court submit|pass|return|extend
    // ------------------------------------------------------------------

    private static int courtSubmit(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            CourtReceipt receipt = service.get().submitForCourtReview(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Proposal " + proposalId + " submitted to the supreme-court "
                            + "review (state " + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Court submission", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Court submission", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.court.submit", failure);
        }
    }

    private static int courtPass(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return courtAction(context, runtimeResolver, "pass");
    }

    private static int courtReturn(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return courtAction(context, runtimeResolver, "return");
    }

    private static int courtExtend(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return courtAction(context, runtimeResolver, "extend");
    }

    private static int courtAction(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String action
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            CourtReceipt receipt = switch (action) {
                case "pass" -> service.get().courtReviewPassed(
                        actor, proposalId, onSite
                );
                case "return" -> service.get().courtReviewReturned(
                        actor, proposalId, onSite
                );
                case "extend" -> service.get().extendCourtReview(
                        actor, proposalId, onSite
                );
                default -> throw new IllegalStateException(
                        "Unknown court action: " + action
                );
            };
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Court review " + action + " applied to proposal "
                            + proposalId + " (state "
                            + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Court " + action, failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Court " + action, failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.court." + action, failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr parliament amendment submit|vote|publish
    // ------------------------------------------------------------------

    private static int amendmentSubmit(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            ProposalReceipt receipt = service.get().submitAmendment(
                    new AmendmentDraft(title, fullText),
                    onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Constitutional amendment " + receipt.proposal().proposalId()
                            + " submitted (" + receipt.proposal().title()
                            + ", state " + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Amendment submission", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Amendment submission", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.amendment.submit", failure);
        }
    }

    private static int parliamentVoteOpen(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            VoteReceipt receipt = service.get().openParliamentVote(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Parliament ballot " + receipt.vote().voteId()
                            + " opened for amendment " + proposalId
                            + " (threshold " + receipt.vote().requiredThreshold()
                            + " of " + receipt.vote().frozenRosterCount() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Parliament vote open", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Parliament vote open", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.amendment.vote.open", failure);
        }
    }

    private static int parliamentVoteClose(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            BillReceipt receipt = service.get().closeParliamentVoteAndAdvance(
                    actor, voteId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    receipt.passed()
                            ? "Parliament ballot " + voteId
                            + " passed; the amendment enters the referendum."
                            : "Parliament ballot " + voteId
                            + " was rejected (threshold not met)."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Parliament vote close", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Parliament vote close", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.amendment.vote.close", failure);
        }
    }

    private static int amendmentPublish(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            GuardianReceipt receipt = service.get().publishAmendment(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Amendment " + proposalId + " published (state "
                            + receipt.proposal().state()
                            + "; constitutional changes execute manually)."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Amendment publication", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Amendment publication", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.amendment.publish", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr parliament referendum open|vote|close
    // ------------------------------------------------------------------

    private static int referendumOpen(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            ReferendumReceipt receipt = service.get().openReferendum(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Referendum opened for amendment " + proposalId
                            + " (frozen roster " + receipt.referendum().frozenRosterCount()
                            + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Referendum open", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Referendum open", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.referendum.open", failure);
        }
    }

    private static int referendumVote(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID voter = requirePlayer(source);
        if (voter == null) {
            return CommandFeedback.FAILURE;
        }
        ProposalId proposalId = parseProposalId(
                source, StringArgumentType.getString(context, "proposalId")
        );
        if (proposalId == null) {
            return CommandFeedback.FAILURE;
        }
        VoteChoice choice = parseChoice(
                source, StringArgumentType.getString(context, "choice")
        );
        if (choice == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
            OnSiteContext onSite = issuePublicContext(
                    source, access.get(), voter, zoneId
            );
            ReferendumReceipt receipt = service.get().castReferendumVote(
                    voter, proposalId, choice, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Referendum vote recorded for amendment " + proposalId
                            + ": " + choice + "."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Referendum vote", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Referendum vote", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.referendum.vote", failure);
        }
    }

    private static int referendumClose(
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
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
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
                    source, access.get(), actor, zoneId
            );
            ReferendumReceipt receipt = service.get().closeReferendum(
                    actor, proposalId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    receipt.passed()
                            ? "Referendum of amendment " + proposalId
                            + " passed; awaiting the water-god constitutional consent."
                            : "Referendum of amendment " + proposalId
                            + " failed (participation/approval threshold not met)."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Referendum close", failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Referendum close", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "parliament.referendum.close", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr parliament consent approve|reject|timeout
    // ------------------------------------------------------------------

    private static int consentApprove(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return consentAction(context, runtimeResolver, "approve");
    }

    private static int consentReject(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return consentAction(context, runtimeResolver, "reject");
    }

    private static int consentTimeout(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        return consentAction(context, runtimeResolver, "timeout");
    }

    private static int consentAction(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            String action
    ) {
        CommandSourceStack source = context.getSource();
        Optional<ParliamentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        ProposalId proposalId = parseProposalId(
                source, StringArgumentType.getString(context, "proposalId")
        );
        if (proposalId == null) {
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
            GuardianInvocation invocation =
                    resolveGuardianInvocation(source, access.get(), context);
            GuardianReceipt receipt = switch (action) {
                case "approve" -> service.get().guardianConsentApprove(
                        invocation.actor(), proposalId, invocation.channel(),
                        invocation.context()
                );
                case "reject" -> service.get().guardianConsentReject(
                        invocation.actor(), proposalId, invocation.channel(),
                        invocation.context()
                );
                case "timeout" -> service.get().guardianConsentTimeout(
                        invocation.actor(), proposalId, invocation.channel(),
                        invocation.context()
                );
                default -> throw new IllegalStateException(
                        "Unknown consent action: " + action
                );
            };
            invocation.consume();
            return CommandFeedback.success(
                    source,
                    "Constitutional consent " + action + " applied to amendment "
                            + proposalId + " (state "
                            + receipt.proposal().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Consent " + action, failure);
        } catch (ParliamentUnavailableException failure) {
            return reject(source, "Consent " + action, failure);
        } catch (RuntimeException failure) {
            return unexpected(
                    source, "parliament.consent." + action, failure
            );
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

    private static ZoneId parseZoneId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "zoneId");
        if (parsed == null) {
            return null;
        }
        return ZoneId.of(parsed);
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
     * authoritative server player position at the given zone (FR-INST-001-A
     * §6.3). The service revalidates this context at its final mutation
     * boundary.
     */
    private static OnSiteContext issueOfficialContext(
            CommandSourceStack source,
            InstitutionAccessService access,
            UUID playerId,
            ZoneId zoneId
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
                zoneId,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                player.level().dimension().location().toString(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ()
        );
    }

    /**
     * Issues a fresh {@code ONSITE_PUBLIC_SERVICE} context for referendum
     * voting in the parliament public zone (FR-PAR-002-A §5).
     */
    private static OnSiteContext issuePublicContext(
            CommandSourceStack source,
            InstitutionAccessService access,
            UUID playerId,
            ZoneId zoneId
    ) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Only a player can vote in a referendum"
            );
        }
        return access.issueOnSiteContext(
                playerId,
                zoneId,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                player.level().dimension().location().toString(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ()
        );
    }

    /**
     * Resolves the guardian channel of a command (FR-PAR-002 task §3.3): the
     * real local Dedicated Server console (classified with the bootstrap
     * classifier pattern) acts as the local-console guardian without an
     * on-site context; any player must be the Hydro Archon on-site at a
     * parliament zone (a {@code zoneId} argument is mandatory).
     */
    private static GuardianInvocation resolveGuardianInvocation(
            CommandSourceStack source,
            InstitutionAccessService access,
            CommandContext<CommandSourceStack> context
    ) {
        if (isLocalConsole(source)) {
            return new GuardianInvocation(
                    GuardianChannel.LOCAL_CONSOLE,
                    DefaultParliamentService.CONSOLE_ACTOR,
                    null,
                    access
            );
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Only the Hydro Archon player or the local console may act"
            );
        }
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_INVALID_REQUEST,
                    "A parliament zone is required for the Hydro Archon channel"
            );
        }
        OnSiteContext onSite = issueOfficialContext(
                source, access, actor, zoneId
        );
        return new GuardianInvocation(
                GuardianChannel.HYDRO_ARCHON_PLAYER,
                actor,
                onSite,
                access
        );
    }

    /** Whether the command source is the real local Dedicated Server console. */
    private static boolean isLocalConsole(CommandSourceStack source) {
        return BootstrapConsoleClassifier.isLocalConsole(
                BootstrapConsoleClassifier.classify(source)
        );
    }

    /** Resolved guardian channel of one command invocation. */
    private record GuardianInvocation(
            GuardianChannel channel,
            UUID actor,
            OnSiteContext context,
            InstitutionAccessService access
    ) {
        /** Consumes the issued on-site context when one was issued. */
        private void consume() {
            if (context != null) {
                access.consume(context);
            }
        }
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
            case ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED ->
                    "the acting channel is not the Hydro Archon or the local console.";
            case ParliamentUnavailableException.CODE_GUARDIAN_NO_VETO ->
                    "the guardian has no veto over organic laws.";
            case ParliamentUnavailableException.CODE_DEADLINE_NOT_REACHED ->
                    "the stage deadline has not been reached yet.";
            case ParliamentUnavailableException.CODE_REFERENDUM_NOT_FOUND ->
                    "no referendum exists for this proposal.";
            case ParliamentUnavailableException.CODE_COURT_EXTENSION_LIMIT ->
                    "the court review was already extended once.";
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
