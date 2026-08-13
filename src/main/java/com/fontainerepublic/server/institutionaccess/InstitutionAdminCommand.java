package com.fontainerepublic.server.institutionaccess;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.institutionaccess.api.FacilityChangeKind;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.ZoneReceipt;
import com.fontainerepublic.server.institutionaccess.api.ZoneRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.land.model.ParcelId;
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
 * (FR-INST-002-B §5).
 *
 * <p>Attached under {@code /fr admin institution ...} with the Minecraft OP
 * level-2 early gate inherited from the admin tree. OP is only an early gate:
 * every mutation still runs the full service validation (parcel existence and
 * binding, region checks, small-size budget, state machine, capability sets,
 * actor resolution) and commits through the FR-CORE-002 durable gate.
 * Feedback is bounded and never enumerates the directory.</p>
 *
 * <pre>
 * /fr admin institution facility register &lt;institutionType&gt; &lt;parcelId&gt;
 * /fr admin institution facility suspend|activate|disable &lt;facilityId&gt;
 * /fr admin institution facility relocate &lt;facilityId&gt; &lt;parcelId&gt;
 * /fr admin institution zone add &lt;facilityId&gt; &lt;kind&gt; &lt;dimension&gt;
 *   &lt;minX&gt; &lt;minY&gt; &lt;minZ&gt; &lt;maxX&gt; &lt;maxY&gt; &lt;maxZ&gt; &lt;capabilities&gt;
 * /fr admin institution zone remove|suspend|activate &lt;zoneId&gt;
 * /fr admin institution zone resize &lt;zoneId&gt; &lt;dimension&gt;
 *   &lt;minX&gt; &lt;minY&gt; &lt;minZ&gt; &lt;maxX&gt; &lt;maxY&gt; &lt;maxZ&gt;
 * /fr admin institution zone set-kind &lt;zoneId&gt; &lt;kind&gt;
 * </pre>
 *
 * <p>Zone coordinates are never hard-coded: they come from the explicit
 * bounded sub-region supplied by the operator at command time (validated
 * against the facility's FR-LAND parcel).</p>
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
                .then(zoneNode(runtimeResolver));
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

    private static LiteralArgumentBuilder<CommandSourceStack> zoneNode(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("zone")
                .then(Commands.literal("add")
                        .then(Commands.argument("facilityId", StringArgumentType.string())
                                .then(Commands.argument("kind", StringArgumentType.string())
                                        .then(Commands.argument("dimension", StringArgumentType.string())
                                                .then(Commands.argument("minX", IntegerArgumentType.integer())
                                                        .then(Commands.argument("minY", IntegerArgumentType.integer())
                                                                .then(Commands.argument("minZ", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("maxX", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                                                                        .then(Commands.argument("maxZ", IntegerArgumentType.integer())
                                                                                                .then(Commands.argument("capabilities", StringArgumentType.string())
                                                                                                        .executes(context -> zoneAdd(context, runtimeResolver)))))))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .executes(context -> zoneRemove(context, runtimeResolver))))
                .then(Commands.literal("resize")
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .then(Commands.argument("dimension", StringArgumentType.string())
                                        .then(Commands.argument("minX", IntegerArgumentType.integer())
                                                .then(Commands.argument("minY", IntegerArgumentType.integer())
                                                        .then(Commands.argument("minZ", IntegerArgumentType.integer())
                                                                .then(Commands.argument("maxX", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("maxZ", IntegerArgumentType.integer())
                                                                                        .executes(context -> zoneResize(context, runtimeResolver)))))))))))
                .then(Commands.literal("set-kind")
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .then(Commands.argument("kind", StringArgumentType.string())
                                        .executes(context -> zoneSetKind(context, runtimeResolver)))))
                .then(Commands.literal("suspend")
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .executes(context -> zoneSuspend(context, runtimeResolver))))
                .then(Commands.literal("activate")
                        .then(Commands.argument("zoneId", StringArgumentType.string())
                                .executes(context -> zoneActivate(context, runtimeResolver))));
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
                    actor, facilityId, parcelId
            );
            return reportFacility(source, "relocated", receipt);
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
    // zone subcommands
    // ------------------------------------------------------------------

    private static int zoneAdd(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        FacilityId facilityId = parseFacilityId(source,
                StringArgumentType.getString(context, "facilityId"));
        if (facilityId == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneKind kind = parseZoneKind(source,
                StringArgumentType.getString(context, "kind"));
        if (kind == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneRegion region = parseZoneRegion(source, context, "zone add");
        if (region == null) {
            return CommandFeedback.FAILURE;
        }
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
        try {
            ZoneReceipt receipt = service.get().addZone(
                    actor,
                    new ZoneRegistrationRequest(
                            facilityId, kind, region, capabilities
                    )
            );
            return CommandFeedback.success(
                    source,
                    "Zone " + receipt.zone().zoneId() + " added to facility "
                            + facilityId + " (kind=" + receipt.zone().kind()
                            + ", state=" + receipt.zone().state()
                            + ", revision=" + receipt.zone().zoneRevision() + ")."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Zone add", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.zone.add", failure);
        }
    }

    private static int zoneRemove(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
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
            service.get().removeZone(actor, zoneId);
            return CommandFeedback.success(
                    source,
                    "Zone " + zoneId + " removed."
            );
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Zone removal", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.zone.remove", failure);
        }
    }

    private static int zoneResize(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneRegion region = parseZoneRegion(source, context, "zone resize");
        if (region == null) {
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
            ZoneReceipt receipt = service.get().resizeZone(actor, zoneId, region);
            if (!receipt.applied()) {
                return CommandFeedback.success(
                        source,
                        "Zone " + zoneId + " was already at that region; no change."
                );
            }
            return reportZone(source, "resized", receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Zone resize", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.zone.resize", failure);
        }
    }

    private static int zoneSetKind(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        ZoneKind kind = parseZoneKind(source,
                StringArgumentType.getString(context, "kind"));
        if (kind == null) {
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
            ZoneReceipt receipt = service.get().setZoneKind(actor, zoneId, kind);
            if (!receipt.applied()) {
                return CommandFeedback.success(
                        source,
                        "Zone " + zoneId + " was already " + kind + "; no change."
                );
            }
            return reportZone(source, "re-kinded", receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Zone kind change", failure);
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.institution.zone.set-kind", failure);
        }
    }

    private static int zoneSuspend(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        return zoneStateChange(source, runtimeResolver, zoneId,
                "admin.institution.zone.suspend", "suspended",
                (service, actor) -> service.suspendZone(actor, zoneId));
    }

    private static int zoneActivate(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        ZoneId zoneId = parseZoneId(source,
                StringArgumentType.getString(context, "zoneId"));
        if (zoneId == null) {
            return CommandFeedback.FAILURE;
        }
        return zoneStateChange(source, runtimeResolver, zoneId,
                "admin.institution.zone.activate", "activated",
                (service, actor) -> service.activateZone(actor, zoneId));
    }

    // ------------------------------------------------------------------
    // shared execution helpers
    // ------------------------------------------------------------------

    private interface FacilityMutation {
        FacilityReceipt apply(InstitutionAccessService service, UUID actor);
    }

    private interface ZoneMutation {
        ZoneReceipt apply(InstitutionAccessService service, UUID actor);
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

    private static int zoneStateChange(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver,
            ZoneId zoneId,
            String commandId,
            String verb,
            ZoneMutation mutation
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
            ZoneReceipt receipt = mutation.apply(service.get(), actor);
            if (!receipt.applied()) {
                return CommandFeedback.success(
                        source,
                        "Zone " + zoneId + " was already " + verb + "; no change."
                );
            }
            return reportZone(source, verb, receipt);
        } catch (InstitutionAccessUnavailableException failure) {
            return reject(source, "Zone " + verb, failure);
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

    private static int reportZone(
            CommandSourceStack source,
            String verb,
            ZoneReceipt receipt
    ) {
        return CommandFeedback.success(
                source,
                "Zone " + receipt.zone().zoneId() + " " + verb
                        + " (kind=" + receipt.zone().kind()
                        + ", state=" + receipt.zone().state()
                        + ", revision=" + receipt.zone().zoneRevision() + ")."
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

    private static ZoneId parseZoneId(CommandSourceStack source, String input) {
        UUID parsed = parseCanonicalUuid(input);
        if (parsed == null) {
            CommandFeedback.failure(
                    source,
                    "Rejected: zoneId must be a canonical UUID."
            );
            return null;
        }
        return ZoneId.of(parsed);
    }

    private static ZoneKind parseZoneKind(CommandSourceStack source, String input) {
        try {
            return ZoneKind.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    "Rejected: kind must be one of PUBLIC, OFFICIAL, SECURE."
            );
            return null;
        }
    }

    /**
     * Parses the explicit bounded sub-region of a zone from operator-supplied
     * coordinates (never hard-coded). The canonical dimension and the
     * inclusive min/max block coordinates come from the command arguments;
     * the service re-validates the region against the facility parcel and the
     * small-size budget.
     */
    private static ZoneRegion parseZoneRegion(
            CommandSourceStack source,
            CommandContext<CommandSourceStack> context,
            String action
    ) {
        String dimension = StringArgumentType.getString(context, "dimension");
        int minX = IntegerArgumentType.getInteger(context, "minX");
        int minY = IntegerArgumentType.getInteger(context, "minY");
        int minZ = IntegerArgumentType.getInteger(context, "minZ");
        int maxX = IntegerArgumentType.getInteger(context, "maxX");
        int maxY = IntegerArgumentType.getInteger(context, "maxY");
        int maxZ = IntegerArgumentType.getInteger(context, "maxZ");
        try {
            return new ZoneRegion(dimension, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (IllegalArgumentException invalid) {
            CommandFeedback.failure(
                    source,
                    action + " rejected: dimension must be a canonical resource "
                            + "location and min must not exceed max on any axis."
            );
            return null;
        }
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
            case InstitutionAccessUnavailableException.CODE_ZONE_NOT_FOUND ->
                    "the zone does not exist.";
            case InstitutionAccessUnavailableException.CODE_ZONE_NOT_ACTIVE ->
                    "the zone is not ACTIVE.";
            case InstitutionAccessUnavailableException.CODE_ZONE_OUTSIDE_REGION ->
                    "the zone region is outside the facility parcel region.";
            case InstitutionAccessUnavailableException.CODE_ZONE_SIZE_EXCEEDED ->
                    "the zone region exceeds the small-size budget.";
            case InstitutionAccessUnavailableException.CODE_INSTITUTION_MISMATCH ->
                    "the institution type does not match the facility.";
            case InstitutionAccessUnavailableException.CODE_KIND_CAPABILITY_MISMATCH ->
                    "the capability set does not match the zone kind.";
            case InstitutionAccessUnavailableException.CODE_CAPABILITY_NOT_ALLOWED ->
                    "the zone does not allow the requested capability.";
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
