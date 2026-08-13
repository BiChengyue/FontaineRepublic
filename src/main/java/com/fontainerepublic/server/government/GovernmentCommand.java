package com.fontainerepublic.server.government;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.government.api.AppointmentReceipt;
import com.fontainerepublic.server.government.api.CreatePositionRequest;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.government.api.MinistryDraft;
import com.fontainerepublic.server.government.api.MinistryProjection;
import com.fontainerepublic.server.government.api.MinistryReceipt;
import com.fontainerepublic.server.government.api.PositionProjection;
import com.fontainerepublic.server.government.api.PositionReceipt;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.persistence.GovernmentUnavailableException;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.registry.model.OwnerReference;
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
 * Government command surface (FR-GOV-001-A §5): bounded ministry/position
 * administration, on-site-gated appointment/dismissal, and exact office
 * lookup. Never enumerates the store; output is bounded. Appoint/dismiss
 * issue a fresh {@code ONSITE_OFFICIAL_DUTY} on-site context from the
 * authoritative player position at the given zone, and the service
 * revalidates that context at its final mutation boundary. Execution resolves
 * the current ACTIVE {@link GovernmentService} per invocation through the
 * {@link CommandRuntimeResolver} and never caches services or state.
 *
 * <pre>
 * /fr government ministry create &lt;name&gt;
 * /fr government ministry list
 * /fr government position create &lt;ministryId&gt; &lt;title&gt;
 * /fr government position list &lt;ministryId&gt;
 * /fr government appoint &lt;positionId&gt; &lt;holderUuid&gt; &lt;zoneId&gt;
 * /fr government dismiss &lt;positionId&gt; &lt;reason&gt; &lt;zoneId&gt;
 * /fr government office &lt;positionId&gt;
 * </pre>
 */
public final class GovernmentCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GovernmentCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("government")
                .then(Commands.literal("ministry")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> ministryCreate(
                                                context, runtimeResolver
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> ministryList(
                                        context, runtimeResolver
                                ))))
                .then(Commands.literal("position")
                        .then(Commands.literal("create")
                                .then(Commands.argument(
                                                "ministryId",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "title",
                                                        StringArgumentType.string()
                                                )
                                                .executes(context -> positionCreate(
                                                        context, runtimeResolver
                                                )))))
                        .then(Commands.literal("list")
                                .then(Commands.argument(
                                                "ministryId",
                                                StringArgumentType.string()
                                        )
                                        .executes(context -> positionList(
                                                context, runtimeResolver
                                        )))))
                .then(Commands.literal("appoint")
                        .then(Commands.argument(
                                        "positionId",
                                        StringArgumentType.string()
                                )
                                .then(Commands.argument(
                                                "holderUuid",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "zoneId",
                                                        StringArgumentType.string()
                                                )
                                                .executes(context -> appoint(
                                                        context, runtimeResolver
                                                ))))))
                .then(Commands.literal("dismiss")
                        .then(Commands.argument(
                                        "positionId",
                                        StringArgumentType.string()
                                )
                                .then(Commands.argument(
                                                "reason",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "zoneId",
                                                        StringArgumentType.string()
                                                )
                                                .executes(context -> dismiss(
                                                        context, runtimeResolver
                                                ))))))
                .then(Commands.literal("office")
                        .then(Commands.argument(
                                        "positionId",
                                        StringArgumentType.string()
                                )
                                .executes(context -> office(
                                        context, runtimeResolver
                                ))));
    }

    // ------------------------------------------------------------------
    // /fr government ministry create|list
    // ------------------------------------------------------------------

    private static int ministryCreate(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        String name = StringArgumentType.getString(context, "name");
        try {
            MinistryReceipt receipt = service.get().createMinistry(
                    actor,
                    new MinistryDraft(name)
            );
            return CommandFeedback.success(
                    source,
                    "Ministry " + receipt.ministry().ministryId()
                            + " created (" + receipt.ministry().name() + ")."
            );
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Ministry creation", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.ministry.create", failure);
        }
    }

    private static int ministryList(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        try {
            List<MinistryProjection> ministries = service.get().ministries();
            if (ministries.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "No ministries are registered."
                );
            }
            StringBuilder builder = new StringBuilder();
            int shown = 0;
            for (MinistryProjection ministry : ministries) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(ministry.name()).append(" (")
                        .append(ministry.ministryId()).append(")");
                shown++;
            }
            return CommandFeedback.success(
                    source,
                    "Ministries (" + shown + "): " + builder + "."
            );
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Ministry list", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.ministry.list", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr government position create|list
    // ------------------------------------------------------------------

    private static int positionCreate(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        MinistryId ministryId = parseMinistryId(source,
                StringArgumentType.getString(context, "ministryId"));
        if (ministryId == null) {
            return CommandFeedback.FAILURE;
        }
        String title = StringArgumentType.getString(context, "title");
        try {
            PositionReceipt receipt = service.get().createPosition(
                    actor,
                    new CreatePositionRequest(
                            ministryId, title
                    )
            );
            return CommandFeedback.success(
                    source,
                    "Position " + receipt.position().positionId()
                            + " created (" + receipt.position().title()
                            + ", ministry " + ministryId + ")."
            );
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Position creation", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.position.create", failure);
        }
    }

    private static int positionList(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        MinistryId ministryId = parseMinistryId(source,
                StringArgumentType.getString(context, "ministryId"));
        if (ministryId == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            List<PositionProjection> positions =
                    service.get().positionsByMinistry(ministryId);
            if (positions.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "Ministry " + ministryId + " has no positions."
                );
            }
            StringBuilder builder = new StringBuilder();
            int shown = 0;
            for (PositionProjection position : positions) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(position.title()).append(" (")
                        .append(position.positionId()).append(", ")
                        .append(position.state());
                position.holderRef().ifPresent(holder ->
                        builder.append(", holder ").append(holder.key()));
                builder.append(")");
                shown++;
            }
            return CommandFeedback.success(
                    source,
                    "Positions of " + ministryId + " (" + shown + "): "
                            + builder + "."
            );
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Position list", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.position.list", failure);
        }
    }

    // ------------------------------------------------------------------
    // /fr government appoint|dismiss (on-site gated)
    // ------------------------------------------------------------------

    private static int appoint(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        PositionId positionId = parsePositionId(source,
                StringArgumentType.getString(context, "positionId"));
        if (positionId == null) {
            return CommandFeedback.FAILURE;
        }
        UUID holderUuid = parseCanonicalUuid(source,
                StringArgumentType.getString(context, "holderUuid"),
                "holderUuid");
        if (holderUuid == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> access = runtimeResolver
                .institutionAccessService();
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
            AppointmentReceipt receipt = service.get().appoint(
                    actor, positionId, OwnerReference.forPlayer(holderUuid), onSite
            );
            access.get().consume(onSite);
            return CommandFeedback.success(
                    source,
                    "Holder " + receipt.position().holderRef()
                            .map(OwnerReference::key)
                            .orElse("<none>")
                            + " appointed to position " + positionId
                            + " (office " + receipt.office()
                            .map(Office::officeId).map(UUID::toString).orElse("<none>")
                            + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Appointment", failure);
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Appointment", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.appoint", failure);
        }
    }

    private static int dismiss(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        PositionId positionId = parsePositionId(source,
                StringArgumentType.getString(context, "positionId"));
        if (positionId == null) {
            return CommandFeedback.FAILURE;
        }
        String reason = StringArgumentType.getString(context, "reason");
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> access = runtimeResolver
                .institutionAccessService();
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
            AppointmentReceipt receipt = service.get().dismiss(
                    actor, positionId, reason, onSite
            );
            access.get().consume(onSite);
            if (!receipt.applied()) {
                return CommandFeedback.success(
                        source,
                        "Position " + positionId
                                + " was already vacant; no change."
                );
            }
            return CommandFeedback.success(
                    source,
                    "Position " + positionId + " vacated (" + reason + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return rejectOnSite(source, "Dismissal", failure);
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Dismissal", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.dismiss", failure);
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

    // ------------------------------------------------------------------
    // /fr government office <positionId> (exact read)
    // ------------------------------------------------------------------

    private static int office(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        Optional<GovernmentService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        PositionId positionId = parsePositionId(source,
                StringArgumentType.getString(context, "positionId"));
        if (positionId == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            Optional<Office> office = service.get().currentOffice(positionId);
            if (office.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "Position " + positionId + " has no current office."
                );
            }
            Office current = office.get();
            return CommandFeedback.success(
                    source,
                    "Position " + positionId + " is held by "
                            + current.holderRef().key() + " since "
                            + current.assignedAt() + " (office "
                            + current.officeId() + ")."
            );
        } catch (GovernmentUnavailableException failure) {
            return reject(source, "Office lookup", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "government.office", failure);
        }
    }

    // ------------------------------------------------------------------
    // shared execution helpers
    // ------------------------------------------------------------------

    private static Optional<GovernmentService> service(
            CommandRuntimeResolver runtimeResolver
    ) {
        return runtimeResolver.governmentService();
    }

    private static int unavailableRuntime(CommandSourceStack source) {
        LOGGER.warn("[Command] Government runtime is unavailable");
        return CommandFeedback.failure(
                source,
                "FontaineRepublic government runtime is unavailable."
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

    private static MinistryId parseMinistryId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "ministryId");
        if (parsed == null) {
            return null;
        }
        return MinistryId.of(parsed);
    }

    private static PositionId parsePositionId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(source, input, "positionId");
        if (parsed == null) {
            return null;
        }
        return PositionId.of(parsed);
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
     * Bounded rejection feedback. The stable failure code is surfaced without
     * leaking internal details; ordinary mistakes receive one explicit line.
     */
    private static int reject(
            CommandSourceStack source,
            String action,
            GovernmentUnavailableException failure
    ) {
        String code = failure.failureCode();
        String detail = switch (code) {
            case GovernmentUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                 GovernmentUnavailableException.CODE_INVALID_HOLDER,
                 GovernmentUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE ->
                    "the actor or holder could not be resolved to an active subject.";
            case GovernmentUnavailableException.CODE_UNSUPPORTED_HOLDER_KIND ->
                    "only PLAYER_UUID holders are supported in Alpha.";
            case GovernmentUnavailableException.CODE_MINISTRY_NOT_FOUND ->
                    "the ministry does not exist.";
            case GovernmentUnavailableException.CODE_POSITION_NOT_FOUND ->
                    "the position does not exist.";
            case GovernmentUnavailableException.CODE_POSITION_FILLED ->
                    "the position is already filled.";
            case GovernmentUnavailableException.CODE_POSITION_SUSPENDED ->
                    "the position is suspended.";
            case GovernmentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID ->
                    "the on-site official-duty context is not valid.";
            case GovernmentUnavailableException.CODE_INVALID_REQUEST ->
                    "the request failed validation.";
            case GovernmentUnavailableException.CODE_CAPACITY_EXCEEDED ->
                    "a capacity budget was exceeded.";
            case GovernmentUnavailableException.CODE_STORE_FAILURE ->
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
