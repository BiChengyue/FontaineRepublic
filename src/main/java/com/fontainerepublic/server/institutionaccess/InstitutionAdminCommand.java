package com.fontainerepublic.server.institutionaccess;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.institutionaccess.api.FacilityChangeKind;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.TerminalReceipt;
import com.fontainerepublic.server.institutionaccess.api.TerminalRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.TerminalPosition;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.land.model.ParcelId;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Admin command surface of the shared institution access boundary
 * (FR-INST-002-A §7, FR-INST-002 implementation task §3.3).
 *
 * <p>Attached under {@code /fr admin institution ...} with the Minecraft OP
 * level-2 early gate inherited from the admin tree. OP is only an early gate:
 * every mutation still runs the full service validation (parcel existence and
 * binding, region checks, state machine, capability sets, actor resolution)
 * and commits through the FR-CORE-002 durable gate. Feedback is bounded and
 * never enumerates the directory.</p>
 *
 * <pre>
 * /fr admin institution facility register &lt;institutionType&gt; &lt;parcelId&gt;
 * /fr admin institution facility suspend|activate|disable &lt;facilityId&gt;
 * /fr admin institution facility relocate &lt;facilityId&gt; &lt;parcelId&gt;
 * /fr admin institution terminal register &lt;facilityId&gt; &lt;dimension&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;capabilities&gt; [secure]
 * /fr admin institution terminal suspend|disable &lt;terminalId&gt;
 * </pre>
 */
public final class InstitutionAdminCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private InstitutionAdminCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("institution")
                .then(facilityNode(runtimeResolver))
                .then(terminalNode(runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> facilityNode(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("facility")
                .then(Commands.literal("register")
                        .then(Commands.argument(
                                        "institutionType",
                                        StringArgumentType.string()
                                )
                                .then(Commands.argument(
                                                "parcelId",
                                                StringArgumentType.string()
                                        )
                                        .executes(context -> facilityRegister(
                                                context,
                                                runtimeResolver
                                        )))))
                .then(Commands.literal("suspend")
                        .then(Commands.argument(
                                        "facilityId",
                                        StringArgumentType.string()
                                )
                                .executes(context -> facilitySuspend(
                                        context,
                                        runtimeResolver
                                ))))
                .then(Commands.literal("activate")
                        .then(Commands.argument(
                                        "facilityId",
                                        StringArgumentType.string()
                                )
                                .executes(context -> facilityActivate(
                                        context,
                                        runtimeResolver
                                ))))
                .then(Commands.literal("relocate")
                        .then(Commands.argument(
                                        "facilityId",
                                        StringArgumentType.string()
                                )
                                .then(Commands.argument(
                                                "parcelId",
                                                StringArgumentType.string()
                                        )
                                        .executes(context -> facilityRelocate(
                                                context,
                                                runtimeResolver
                                        )))))
                .then(Commands.literal("disable")
                        .then(Commands.argument(
                                        "facilityId",
                                        StringArgumentType.string()
                                )
                                .executes(context -> facilityDisable(
                                        context,
                                        runtimeResolver
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> terminalNode(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("terminal")
                .then(terminalRegisterNode(runtimeResolver))
                .then(Commands.literal("suspend")
                        .then(Commands.argument(
                                        "terminalId",
                                        StringArgumentType.string()
                                )
                                .executes(context -> terminalSuspend(
                                        context,
                                        runtimeResolver
                                ))))
                .then(Commands.literal("disable")
                        .then(Commands.argument(
                                        "terminalId",
                                        StringArgumentType.string()
                                )
                                .executes(context -> terminalDisable(
                                        context,
                                        runtimeResolver
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> terminalRegisterNode(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("register")
                .then(Commands.argument(
                                "facilityId",
                                StringArgumentType.string()
                        )
                        .then(Commands.argument(
                                        "dimension",
                                        StringArgumentType.string()
                                )
                                .then(Commands.argument(
                                                "x",
                                                IntegerArgumentType.integer()
                                        )
                                        .then(Commands.argument(
                                                        "y",
                                                        IntegerArgumentType.integer()
                                                )
                                                .then(Commands.argument(
                                                                "z",
                                                                IntegerArgumentType.integer()
                                                        )
                                                        .then(Commands.argument(
                                                                        "capabilities",
                                                                        StringArgumentType.string()
                                                                )
                                                                .executes(context -> terminalRegister(
                                                                        context,
                                                                        runtimeResolver,
                                                                        false
                                                                ))
                                                                .then(Commands.argument(
                                                                                "secure",
                                                                                BoolArgumentType.bool()
                                                                        )
                                                                        .executes(context -> terminalRegister(
                                                                                context,
                                                                                runtimeResolver,
                                                                                BoolArgumentType
                                                                                        .getBool(
                                                                                                context,
                                                                                                "secure"
                                                                                        )
                                                                        )))))))));
    }

    // ------------------------------------------------------------------
    // facility subcommands
    // ------------------------------------------------------------------

    private static int facilityRegister(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        String typeInput = StringArgumentType.getString(context, "institutionType");
        String parcelInput = StringArgumentType.getString(context, "parcelId");

        InstitutionType type;
        try {
            type = InstitutionType.valueOf(
                    typeInput.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException invalid) {
            return CommandFeedback.failure(
                    source,
                    "Facility registration rejected: institutionType must be one "
                            + "of PARLIAMENT, GOVERNMENT, COURT, CENTRAL_BANK."
            );
        }
        ParcelId parcelId = parseParcelId(source, parcelInput);
        if (parcelId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            FacilityReceipt receipt = service.get().registerFacility(
                    actor,
                    new FacilityRegistrationRequest(type, parcelId)
            );
            return reportFacility(source, "registered", receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Facility registration", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.facility.register", failure);
        }
    }

    private static int facilitySuspend(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        FacilityId facilityId = parseFacilityId(source,
                StringArgumentType.getString(context, "facilityId"));
        if (facilityId == null) {
            return CommandFeedback.FAILURE;
        }
        return facilityStateChange(source, runtimeResolver, facilityId,
                "admin.institution.facility.suspend", "suspended",
                FacilityChangeKind.FACILITY_SUSPENDED,
                (service, actor) -> service.suspendFacility(actor, facilityId));
    }

    private static int facilityActivate(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        FacilityId facilityId = parseFacilityId(source,
                StringArgumentType.getString(context, "facilityId"));
        if (facilityId == null) {
            return CommandFeedback.FAILURE;
        }
        return facilityStateChange(source, runtimeResolver, facilityId,
                "admin.institution.facility.activate", "activated",
                FacilityChangeKind.FACILITY_ACTIVATED,
                (service, actor) -> service.activateFacility(actor, facilityId));
    }

    private static int facilityRelocate(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        FacilityId facilityId = parseFacilityId(source,
                StringArgumentType.getString(context, "facilityId"));
        if (facilityId == null) {
            return CommandFeedback.FAILURE;
        }
        ParcelId parcelId = parseParcelId(source,
                StringArgumentType.getString(context, "parcelId"));
        if (parcelId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            FacilityReceipt receipt = service.get().relocateFacility(
                    actor,
                    facilityId,
                    parcelId
            );
            return reportFacility(source, "relocated onto parcel "
                    + receipt.facility().parcelId(), receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Facility relocation", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.facility.relocate", failure);
        }
    }

    private static int facilityDisable(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        FacilityId facilityId = parseFacilityId(source,
                StringArgumentType.getString(context, "facilityId"));
        if (facilityId == null) {
            return CommandFeedback.FAILURE;
        }
        return facilityStateChange(source, runtimeResolver, facilityId,
                "admin.institution.facility.disable", "disabled",
                FacilityChangeKind.FACILITY_DISABLED,
                (service, actor) -> service.disableFacility(actor, facilityId));
    }

    // ------------------------------------------------------------------
    // terminal subcommands
    // ------------------------------------------------------------------

    private static int terminalRegister(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver,
            boolean secure
    ) {
        CommandSourceStack source = context.getSource();
        FacilityId facilityId = parseFacilityId(source,
                StringArgumentType.getString(context, "facilityId"));
        if (facilityId == null) {
            return CommandFeedback.FAILURE;
        }
        String dimension = StringArgumentType.getString(context, "dimension");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        Set<CapabilityClass> capabilities = parseCapabilities(
                source,
                StringArgumentType.getString(context, "capabilities")
        );
        if (capabilities == null) {
            return CommandFeedback.FAILURE;
        }

        Optional<InstitutionAccessService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        TerminalPosition position;
        try {
            position = new TerminalPosition(dimension, x, y, z);
        } catch (IllegalArgumentException invalid) {
            return CommandFeedback.failure(
                    source,
                    "Terminal registration rejected: dimension must be a canonical "
                            + "resource location."
            );
        }
        try {
            TerminalReceipt receipt = service.get().registerTerminal(
                    actor,
                    new TerminalRegistrationRequest(
                            facilityId,
                            position,
                            capabilities,
                            secure
                    )
            );
            return CommandFeedback.success(
                    source,
                    "Terminal " + receipt.terminal().terminalId()
                            + " registered on facility " + facilityId
                            + " (state=" + receipt.terminal().state()
                            + ", revision=" + receipt.terminal().terminalRevision()
                            + ", secure=" + receipt.terminal().secure() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Terminal registration", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.terminal.register", failure);
        }
    }

    private static int terminalSuspend(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        TerminalId terminalId = parseTerminalId(source,
                StringArgumentType.getString(context, "terminalId"));
        if (terminalId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            TerminalReceipt receipt = service.get().suspendTerminal(actor, terminalId);
            return reportTerminal(source, "suspended", receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Terminal suspension", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.terminal.suspend", failure);
        }
    }

    private static int terminalDisable(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        TerminalId terminalId = parseTerminalId(source,
                StringArgumentType.getString(context, "terminalId"));
        if (terminalId == null) {
            return CommandFeedback.FAILURE;
        }
        Optional<InstitutionAccessService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            TerminalReceipt receipt = service.get().disableTerminal(actor, terminalId);
            return reportTerminal(source, "disabled", receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Terminal disable", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.terminal.disable", failure);
        }
    }

    // ------------------------------------------------------------------
    // shared execution helpers
    // ------------------------------------------------------------------

    private interface FacilityMutation {
        FacilityReceipt apply(InstitutionAccessService service, UUID actor);
    }

    private static int facilityStateChange(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver,
            FacilityId facilityId,
            String commandId,
            String verb,
            FacilityChangeKind kind,
            FacilityMutation mutation
    ) {
        Optional<InstitutionAccessService> service = service(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        UUID actor = requirePlayer(source);
        if (actor == null) {
            return CommandFeedback.FAILURE;
        }
        try {
            FacilityReceipt receipt = mutation.apply(service.get(), actor);
            if (!receipt.applied()) {
                return CommandFeedback.success(
                        source,
                        "Facility " + facilityId + " was already " + verb + "; no change."
                );
            }
            return reportFacility(source, verb, receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Facility " + verb, failure);
        } catch (RuntimeException failure) {
            return unexpected(source, commandId, failure);
        }
    }

    private static int reportFacility(
            CommandSourceStack source,
            String verb,
            FacilityReceipt receipt
    ) {
        return CommandFeedback.success(
                source,
                "Facility " + receipt.facility().facilityId() + " " + verb
                        + " (state=" + receipt.facility().state()
                        + ", revision=" + receipt.facility().facilityRevision()
                        + ", parcel=" + receipt.facility().parcelId() + ")."
        );
    }

    private static int reportTerminal(
            CommandSourceStack source,
            String verb,
            TerminalReceipt receipt
    ) {
        return CommandFeedback.success(
                source,
                "Terminal " + receipt.terminal().terminalId() + " " + verb
                        + " (state=" + receipt.terminal().state()
                        + ", revision=" + receipt.terminal().terminalRevision() + ")."
        );
    }

    private static Optional<InstitutionAccessService> service(
            CommandRuntimeResolver runtimeResolver
    ) {
        return runtimeResolver.institutionAccessService();
    }

    private static int unavailableRuntime(CommandSourceStack source) {
        LOGGER.warn("[Command] Institution-access runtime is unavailable");
        return CommandFeedback.failure(
                source,
                "FontaineRepublic institution-access runtime is unavailable."
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

    private static ParcelId parseParcelId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(input);
        if (parsed == null) {
            CommandFeedback.failure(
                    source,
                    "Rejected: parcelId must be a canonical UUID."
            );
            return null;
        }
        return ParcelId.of(parsed);
    }

    private static FacilityId parseFacilityId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(input);
        if (parsed == null) {
            CommandFeedback.failure(
                    source,
                    "Rejected: facilityId must be a canonical UUID."
            );
            return null;
        }
        return FacilityId.of(parsed);
    }

    private static TerminalId parseTerminalId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(input);
        if (parsed == null) {
            CommandFeedback.failure(
                    source,
                    "Rejected: terminalId must be a canonical UUID."
            );
            return null;
        }
        return TerminalId.of(parsed);
    }

    private static UUID parseCanonicalUuid(String input) {
        try {
            UUID parsed = UUID.fromString(input);
            if (!parsed.toString().equals(input)) {
                return null;
            }
            return parsed;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Set<CapabilityClass> parseCapabilities(
            CommandSourceStack source,
            String input
    ) {
        LinkedHashSet<CapabilityClass> capabilities = new LinkedHashSet<>();
        for (String token : input.split(",")) {
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                capabilities.add(CapabilityClass.valueOf(normalized));
            } catch (IllegalArgumentException invalid) {
                CommandFeedback.failure(
                        source,
                        "Rejected: unknown capability '" + token.trim()
                                + "' (comma-separated list of "
                                + "REMOTE_INFORMATION, REMOTE_PERSONAL_SERVICE, "
                                + "REMOTE_PREPARATION, ONSITE_PUBLIC_SERVICE, "
                                + "ONSITE_OFFICIAL_DUTY, EMERGENCY_RECOVERY)."
                );
                return null;
            }
        }
        if (capabilities.isEmpty()) {
            CommandFeedback.failure(
                    source,
                    "Rejected: at least one capability is required."
            );
            return null;
        }
        return capabilities;
    }

    /**
     * Bounded rejection feedback. The stable failure code is surfaced without
     * leaking internal details; ordinary mistakes receive one explicit line.
     */
    private static int reject(
            CommandSourceStack source,
            String action,
            InstitutionAccessUnavailableException failure
    ) {
        String code = failure.failureCode();
        String detail = switch (code) {
            case InstitutionAccessUnavailableException.CODE_PARCEL_NOT_FOUND ->
                    "the FR-LAND parcel does not exist.";
            case InstitutionAccessUnavailableException.CODE_PARCEL_ALREADY_BOUND ->
                    "the parcel is already bound to a facility.";
            case InstitutionAccessUnavailableException.CODE_FACILITY_NOT_FOUND ->
                    "the facility does not exist.";
            case InstitutionAccessUnavailableException.CODE_FACILITY_NOT_ACTIVE ->
                    "the facility is not ACTIVE.";
            case InstitutionAccessUnavailableException.CODE_INVALID_STATE_TRANSITION ->
                    "the requested state change is not permitted.";
            case InstitutionAccessUnavailableException.CODE_TERMINAL_NOT_FOUND ->
                    "the terminal does not exist.";
            case InstitutionAccessUnavailableException.CODE_TERMINAL_NOT_ACTIVE ->
                    "the terminal is not ACTIVE.";
            case InstitutionAccessUnavailableException.CODE_TERMINAL_OUTSIDE_REGION ->
                    "the terminal position is outside the facility parcel region.";
            case InstitutionAccessUnavailableException.CODE_INSTITUTION_MISMATCH ->
                    "the institution type does not match the facility.";
            case InstitutionAccessUnavailableException.CODE_CAPABILITY_NOT_ALLOWED ->
                    "the terminal does not allow the requested capability.";
            case InstitutionAccessUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                 InstitutionAccessUnavailableException.CODE_INVALID_HOLDER,
                 InstitutionAccessUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE ->
                    "the actor could not be resolved to an active subject.";
            case InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED ->
                    "a capacity budget was exceeded.";
            case InstitutionAccessUnavailableException.CODE_STORE_FAILURE ->
                    "the durable store did not acknowledge the change.";
            default -> "the request failed validation.";
        };
        return CommandFeedback.failure(
                source,
                action + " rejected: " + detail
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
