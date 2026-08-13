package com.fontainerepublic.server.justice;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.justice.api.CaseDraft;
import com.fontainerepublic.server.justice.api.CaseProjection;
import com.fontainerepublic.server.justice.api.CaseReceipt;
import com.fontainerepublic.server.justice.api.EvidenceDraft;
import com.fontainerepublic.server.justice.api.EvidenceProjection;
import com.fontainerepublic.server.justice.api.EvidenceReceipt;
import com.fontainerepublic.server.justice.api.JusticeService;
import com.fontainerepublic.server.justice.api.VerdictDraft;
import com.fontainerepublic.server.justice.api.VerdictReceipt;
import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.CaseState;
import com.fontainerepublic.server.justice.model.CaseType;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;
import com.fontainerepublic.server.justice.model.VerdictOutcome;
import com.fontainerepublic.server.justice.persistence.JusticeUnavailableException;
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
 * Justice command surface (FR-JUS-001-A §6; FR-JUS-001 task §3.3): bounded
 * case filing/listing, on-site evidence submission, on-site official-duty
 * verdict issue, and the review path. Never enumerates the store; output is
 * bounded. Public-service mutations issue a fresh {@code ONSITE_PUBLIC_SERVICE}
 * on-site context; official duties issue {@code ONSITE_OFFICIAL_DUTY}; the
 * service revalidates each context at its final mutation boundary. Execution
 * resolves the current ACTIVE {@link JusticeService} per invocation through
 * the {@link CommandRuntimeResolver} and never caches services or state.
 *
 * <pre>
 * /fr court case file &lt;caseType&gt; &lt;title&gt; &lt;zoneId&gt; &lt;description&gt;
 * /fr court case list [afterSeq] [limit]
 * /fr court case show &lt;caseId&gt;
 * /fr court evidence submit &lt;caseId&gt; &lt;description&gt; &lt;zoneId&gt;
 * /fr court evidence list &lt;caseId&gt; [afterSeq] [limit]
 * /fr court verdict issue &lt;caseId&gt; &lt;outcome&gt; &lt;zoneId&gt; &lt;reasoning&gt;
 * /fr court verdict show &lt;verdictId&gt;
 * /fr court review request &lt;caseId&gt; &lt;zoneId&gt;
 * /fr court review decide &lt;caseId&gt; &lt;zoneId&gt;
 * </pre>
 */
public final class CourtCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CourtCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("court")
                .then(caseSubtree(runtimeResolver))
                .then(evidenceSubtree(runtimeResolver))
                .then(verdictSubtree(runtimeResolver))
                .then(reviewSubtree(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> caseSubtree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("case")
                .then(caseFileCommand(runtimeResolver))
                .then(caseListCommand(runtimeResolver))
                .then(caseShowCommand(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> caseFileCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("file")
                .then(Commands.argument("caseType", StringArgumentType.string())
                        .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                                .executes(context -> caseFile(
                                                        context, runtimeResolver
                                                ))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> caseListCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("list")
                .executes(context -> caseList(
                        context, runtimeResolver, 0, 10
                ))
                .then(Commands.argument("afterSeq", IntegerArgumentType.integer(0))
                        .executes(context -> caseList(
                                context, runtimeResolver,
                                IntegerArgumentType.getInteger(
                                        context, "afterSeq"
                                ),
                                10
                        ))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                .executes(context -> caseList(
                                        context, runtimeResolver,
                                        IntegerArgumentType.getInteger(
                                                context, "afterSeq"
                                        ),
                                        IntegerArgumentType.getInteger(
                                                context, "limit"
                                        )
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> caseShowCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("show")
                .then(Commands.argument("caseId", StringArgumentType.string())
                        .executes(context -> caseShow(
                                context, runtimeResolver
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> evidenceSubtree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("evidence")
                .then(evidenceSubmitCommand(runtimeResolver))
                .then(evidenceListCommand(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> evidenceSubmitCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("submit")
                .then(Commands.argument("caseId", StringArgumentType.string())
                        .then(Commands.argument("description", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .executes(context -> evidenceSubmit(
                                                context, runtimeResolver
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> evidenceListCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("list")
                .then(Commands.argument("caseId", StringArgumentType.string())
                        .executes(context -> evidenceList(
                                context, runtimeResolver, 0, 10
                        ))
                        .then(Commands.argument("afterSeq", IntegerArgumentType.integer(0))
                                .executes(context -> evidenceList(
                                        context, runtimeResolver,
                                        IntegerArgumentType.getInteger(
                                                context, "afterSeq"
                                        ),
                                        10
                                ))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                        .executes(context -> evidenceList(
                                                context, runtimeResolver,
                                                IntegerArgumentType.getInteger(
                                                        context, "afterSeq"
                                                ),
                                                IntegerArgumentType.getInteger(
                                                        context, "limit"
                                                )
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> verdictSubtree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("verdict")
                .then(verdictIssueCommand(runtimeResolver))
                .then(verdictShowCommand(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> verdictIssueCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("issue")
                .then(Commands.argument("caseId", StringArgumentType.string())
                        .then(Commands.argument("outcome", StringArgumentType.string())
                                .then(Commands.argument("zoneId", StringArgumentType.string())
                                        .then(Commands.argument("reasoning", StringArgumentType.greedyString())
                                                .executes(context -> verdictIssue(
                                                        context, runtimeResolver
                                                ))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> verdictShowCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("show")
                .then(Commands.argument("verdictId", StringArgumentType.string())
                        .executes(context -> verdictShow(
                                context, runtimeResolver
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reviewSubtree(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("review")
                .then(reviewRequestCommand(runtimeResolver))
                .then(reviewDecideCommand(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reviewRequestCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("request")
                .then(Commands.argument("caseId", StringArgumentType.string())
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .executes(context -> reviewRequest(
                                        context, runtimeResolver
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reviewDecideCommand(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("decide")
                .then(Commands.argument("caseId", StringArgumentType.string())
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .executes(context -> reviewDecide(
                                        context, runtimeResolver
                                ))));
    }

    // ------------------------------------------------------------------
    // /fr court case file|list|show
    // ------------------------------------------------------------------

    private static int caseFile(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        CaseType caseType = parseCaseType(
                source, StringArgumentType.getString(context, "caseType")
        );
        if (caseType == null) {
            return CommandFeedback.FAILURE;
        }
        String title = StringArgumentType.getString(context, "title");
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        String description = StringArgumentType.getString(context, "description");
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
                    zoneId,
                    CapabilityClass.ONSITE_PUBLIC_SERVICE
            );
            CaseReceipt receipt = service.get().fileCase(
                    new CaseDraft(
                            caseType,
                            title,
                            description,
                            Optional.empty()
                    ),
                    onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Case " + receipt.aCase().caseId() + " filed ("
                            + receipt.aCase().caseType() + ", "
                            + receipt.aCase().title() + ", state "
                            + receipt.aCase().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Case filing", failure);
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Case filing", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.case.file", failure);
        }
    }

    private static int caseList(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            long afterSeq,
            int limit
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        try {
            List<CaseProjection> cases = service.get().cases(
                    afterSeq, Math.max(1, Math.min(limit, 50))
            );
            if (cases.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "No cases after sequence " + afterSeq + "."
                );
            }
            StringBuilder builder = new StringBuilder();
            int shown = 0;
            for (CaseProjection projection : cases) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append("#").append(projection.caseSeq())
                        .append(" ").append(projection.title())
                        .append(" (").append(projection.caseId())
                        .append(", ").append(projection.caseType())
                        .append(", ").append(projection.state()).append(")");
                shown++;
            }
            return CommandFeedback.success(
                    source,
                    "Cases (" + shown + "): " + builder + "."
            );
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Case list", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.case.list", failure);
        }
    }

    private static int caseShow(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        CaseId caseId = parseCaseId(
                source, StringArgumentType.getString(context, "caseId")
        );
        if (caseId == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            Optional<Case> aCase = service.get().caseById(caseId);
            if (aCase.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "Case " + caseId + " does not exist."
                );
            }
            Case current = aCase.get();
            return CommandFeedback.success(
                    source,
                    "Case " + caseId + " (" + current.caseType() + ", "
                            + current.title() + ", state " + current.state()
                            + ", plaintiff " + current.plaintiffRef() + ")."
            );
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Case lookup", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.case.show", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr court evidence submit|list
    // ------------------------------------------------------------------

    private static int evidenceSubmit(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        CaseId caseId = parseCaseId(
                source, StringArgumentType.getString(context, "caseId")
        );
        if (caseId == null) {
            return CommandFeedback.FAILURE;
        }
        String description = StringArgumentType.getString(context, "description");
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
            OnSiteContext onSite = issueContext(
                    source,
                    access.get(),
                    actor,
                    zoneId,
                    CapabilityClass.ONSITE_PUBLIC_SERVICE
            );
            EvidenceReceipt receipt = service.get().submitEvidence(
                    new EvidenceDraft(caseId, description, Optional.empty()),
                    onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Evidence " + receipt.evidence().evidenceId()
                            + " submitted for case " + caseId + "."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Evidence submission", failure);
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Evidence submission", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.evidence.submit", failure);
        }
    }

    private static int evidenceList(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            long afterSeq,
            int limit
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        CaseId caseId = parseCaseId(
                source, StringArgumentType.getString(context, "caseId")
        );
        if (caseId == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            List<EvidenceProjection> items = service.get().evidenceFor(
                    caseId, afterSeq, Math.max(1, Math.min(limit, 50))
            );
            if (items.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "No evidence for case " + caseId + " after sequence "
                                + afterSeq + "."
                );
            }
            StringBuilder builder = new StringBuilder();
            int shown = 0;
            for (EvidenceProjection item : items) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append("#").append(item.evidenceSeq())
                        .append(" ").append(item.description())
                        .append(" (").append(item.evidenceId())
                        .append(", ").append(item.state()).append(")");
                shown++;
            }
            return CommandFeedback.success(
                    source,
                    "Evidence of " + caseId + " (" + shown + "): " + builder + "."
            );
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Evidence list", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.evidence.list", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr court verdict issue|show
    // ------------------------------------------------------------------

    private static int verdictIssue(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        CaseId caseId = parseCaseId(
                source, StringArgumentType.getString(context, "caseId")
        );
        if (caseId == null) {
            return CommandFeedback.FAILURE;
        }
        VerdictOutcome outcome = parseOutcome(
                source, StringArgumentType.getString(context, "outcome")
        );
        if (outcome == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneId zoneId = parseZoneId(
                source, StringArgumentType.getString(context, "zoneId")
        );
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        String reasoning = StringArgumentType.getString(context, "reasoning");
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
                    zoneId,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY
            );
            VerdictReceipt receipt = service.get().issueVerdict(
                    actor,
                    caseId,
                    new VerdictDraft(caseId, outcome, reasoning),
                    onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Verdict " + receipt.verdict().verdictId() + " issued for "
                            + "case " + caseId + " (" + outcome + ", case state "
                            + receipt.outcomeState() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Verdict issue", failure);
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Verdict issue", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.verdict.issue", failure);
        }
    }

    private static int verdictShow(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        VerdictId verdictId = parseVerdictId(
                source, StringArgumentType.getString(context, "verdictId")
        );
        if (verdictId == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            Optional<Verdict> verdict = service.get().verdictById(verdictId);
            if (verdict.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "Verdict " + verdictId + " does not exist."
                );
            }
            Verdict current = verdict.get();
            return CommandFeedback.success(
                    source,
                    "Verdict " + verdictId + " for case " + current.caseId()
                            + " (" + current.outcome() + ", level "
                            + current.level() + ", issued at "
                            + current.issuedAt() + ")."
            );
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Verdict lookup", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.verdict.show", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr court review request|decide
    // ------------------------------------------------------------------

    private static int reviewRequest(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        CaseId caseId = parseCaseId(
                source, StringArgumentType.getString(context, "caseId")
        );
        if (caseId == null) {
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
            OnSiteContext onSite = issueContext(
                    source,
                    access.get(),
                    actor,
                    zoneId,
                    CapabilityClass.ONSITE_PUBLIC_SERVICE
            );
            CaseReceipt receipt = service.get().requestReview(
                    actor, caseId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Review requested for case " + caseId + " (state "
                            + receipt.aCase().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Review request", failure);
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Review request", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.review.request", failure);
        }
    }

    private static int reviewDecide(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<JusticeService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        CaseId caseId = parseCaseId(
                source, StringArgumentType.getString(context, "caseId")
        );
        if (caseId == null) {
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
            OnSiteContext onSite = issueContext(
                    source,
                    access.get(),
                    actor,
                    zoneId,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY
            );
            CaseReceipt receipt = service.get().decideReview(
                    actor, caseId, onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Review of case " + caseId + " completed (state "
                            + receipt.aCase().state() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Review decision", failure);
        } catch (JusticeUnavailableException failure) {
            return reject(source, "Review decision", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "court.review.decide", failure);
        }
    }

    // ------------------------------------------------------------------
    // shared execution helpers
    // ------------------------------------------------------------------

    private static Optional<JusticeService> service(
            CommandRuntimeResolver runtimeResolver
    ) {
        return runtimeResolver.justiceService();
    }

    private static int unavailableRuntime(CommandSourceStack source) {
        LOGGER.warn("[Command] Justice runtime is unavailable");
        return CommandFeedback.failure(
                source,
                "FontaineRepublic justice runtime is unavailable."
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

    private static CaseType parseCaseType(CommandSourceStack source, String input) {
        try {
            return CaseType.valueOf(input.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    "Rejected: caseType must be one of CIVIL, CRIMINAL, "
                            + "ADMINISTRATIVE, LAND."
            );
            return null;
        }
    }

    private static VerdictOutcome parseOutcome(CommandSourceStack source, String input) {
        try {
            return VerdictOutcome.valueOf(input.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    "Rejected: outcome must be one of GUILTY, NOT_GUILTY, DISMISSED."
            );
            return null;
        }
    }

    private static CaseId parseCaseId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "caseId");
        if (parsed == null) {
            return null;
        }
        return CaseId.of(parsed);
    }

    private static VerdictId parseVerdictId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "verdictId");
        if (parsed == null) {
            return null;
        }
        return VerdictId.of(parsed);
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
     * Issues a fresh on-site context of the given capability from the
     * authoritative server player position at the given zone (FR-INST-001-A
     * §6.3). The service revalidates this context at its final mutation
     * boundary.
     */
    private static OnSiteContext issueContext(
            CommandSourceStack source,
            InstitutionAccessService access,
            UUID playerId,
            ZoneId zoneId,
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
                zoneId,
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
            JusticeUnavailableException failure
    ) {
        String code = failure.failureCode();
        String detail = switch (code) {
            case JusticeUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                 JusticeUnavailableException.CODE_CITIZEN_DIRECTORY_UNAVAILABLE ->
                    "the actor could not be resolved to a provisioned player.";
            case JusticeUnavailableException.CODE_NOT_CITIZEN ->
                    "the actor is not an active citizen.";
            case JusticeUnavailableException.CODE_CASE_NOT_FOUND ->
                    "the case does not exist.";
            case JusticeUnavailableException.CODE_EVIDENCE_NOT_FOUND ->
                    "the evidence does not exist.";
            case JusticeUnavailableException.CODE_VERDICT_NOT_FOUND ->
                    "the verdict does not exist.";
            case JusticeUnavailableException.CODE_ILLEGAL_TRANSITION ->
                    "the pipeline does not allow this transition.";
            case JusticeUnavailableException.CODE_EVIDENCE_NOT_ADMISSIBLE ->
                    "the evidence is not admissible (unruled evidence or no admitted evidence).";
            case JusticeUnavailableException.CODE_REPORT_ALREADY_FILED ->
                    "the land violation report was already filed (one case per report).";
            case JusticeUnavailableException.CODE_EVIDENCE_ALREADY_RULED ->
                    "the evidence was already ruled; a ruling is final.";
            case JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID ->
                    "the on-site context is not valid.";
            case JusticeUnavailableException.CODE_INVALID_REQUEST ->
                    "the request failed validation.";
            case JusticeUnavailableException.CODE_CAPACITY_EXCEEDED ->
                    "a capacity budget was exceeded.";
            case JusticeUnavailableException.CODE_STORE_FAILURE ->
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
