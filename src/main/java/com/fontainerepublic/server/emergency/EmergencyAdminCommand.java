package com.fontainerepublic.server.emergency;

import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.emergency.api.ConfigureResult;
import com.fontainerepublic.server.emergency.api.ConfirmResult;
import com.fontainerepublic.server.emergency.api.EmergencyInspection;
import com.fontainerepublic.server.emergency.api.EmergencyRequest;
import com.fontainerepublic.server.emergency.api.EmergencyService;
import com.fontainerepublic.server.emergency.api.EmergencyStatus;
import com.fontainerepublic.server.emergency.api.PreviewResult;
import com.fontainerepublic.server.emergency.model.EmergencyActorSource;
import com.fontainerepublic.server.emergency.model.EmergencyActorType;
import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencySourceClassification;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;
import com.fontainerepublic.server.emergency.persistence.EmergencyUnavailableException;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyService;
import com.fontainerepublic.server.emergency.service.EmergencyConsoleClassifier;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Foundation-owned emergency admin child adapter (FR-EMG-001-A §13,
 * implementation task §3) attached under the reserved {@code admin} literal.
 *
 * <p>This adapter contains syntax and a stable runtime resolver only: at every
 * execution it resolves the current ACTIVE {@link EmergencyService} through
 * {@link CommandRuntimeResolver#emergencyService()} and never captures a
 * per-server Service while building the tree. It never implements business
 * actions and never copies the token table, attempt/configuration journal, or
 * authority-configuration lifecycle — those remain owned by the FR-EMG-001
 * service.</p>
 *
 * <pre>
 * /fr admin emergency preview &lt;module&gt; &lt;action&gt; &lt;version&gt;
 *     &lt;targetType&gt; &lt;targetId&gt; &lt;category&gt; &lt;reason&gt; [&lt;key&gt; &lt;value&gt; ...]
 * /fr admin emergency confirm &lt;token&gt;
 * /fr admin emergency inspect &lt;attemptId&gt;
 * /fr admin emergency status
 * /fr admin emergency bootstrap &lt;uuid&gt; &lt;reason&gt;   (local console only)
 * /fr admin emergency stage &lt;uuid&gt; &lt;reason&gt;        (authorized Hydro Archon or console)
 * /fr admin emergency recover &lt;uuid&gt; &lt;reason&gt;      (local console only)
 * </pre>
 *
 * <p>Source classification follows {@link EmergencyConsoleClassifier}:
 * {@code LOCAL_CONSOLE} maps to a {@code SERVER_CONSOLE} actor source and
 * {@code PLAYER} maps to a {@code HYDRO_ARCHON} actor source carrying the
 * authenticated player UUID. RCON, command blocks, functions, integrated
 * hosts, and other entity-less sources cannot be represented as an actor
 * source and fail closed at the command boundary; the service additionally
 * revalidates the trusted actor at its final mutation boundary. The plaintext
 * confirmation token is presented exactly once and never written to logs or
 * chat history. All feedback is bounded and goes through
 * {@link CommandFeedback}.</p>
 */
public final class EmergencyAdminCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 200;

    private EmergencyAdminCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("emergency")
                .then(previewNode(runtimeResolver))
                .then(Commands.literal("confirm")
                        .then(Commands.argument(
                                        "token",
                                        StringArgumentType.string()
                                )
                                .executes(context -> confirm(
                                        context,
                                        runtimeResolver
                                ))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(
                                        "attemptId",
                                        LongArgumentType.longArg(1L)
                                )
                                .executes(context -> inspect(
                                        context,
                                        runtimeResolver
                                ))))
                .then(Commands.literal("status")
                        .executes(context -> status(
                                context.getSource(),
                                runtimeResolver
                        )))
                .then(authorityNode("bootstrap", runtimeResolver))
                .then(authorityNode("stage", runtimeResolver))
                .then(authorityNode("recover", runtimeResolver));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> previewNode(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("preview")
                .then(Commands.argument("module", StringArgumentType.string())
                        .then(Commands.argument("action", StringArgumentType.string())
                                .then(Commands.argument("version", StringArgumentType.string())
                                        .then(Commands.argument("targetType", StringArgumentType.string())
                                                .then(Commands.argument("targetId", StringArgumentType.string())
                                                        .then(Commands.argument("category", StringArgumentType.string())
                                                                .then(Commands.argument("reason", StringArgumentType.string())
                                                                        .executes(context -> preview(
                                                                                context,
                                                                                runtimeResolver
                                                                        ))
                                                                        .then(Commands.argument(
                                                                                        "kv",
                                                                                        StringArgumentType.greedyString()
                                                                                )
                                                                                .executes(context -> preview(
                                                                                        context,
                                                                                        runtimeResolver
                                                                                ))))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> authorityNode(
            String operation,
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal(operation)
                .then(Commands.argument(
                                "uuid",
                                StringArgumentType.string()
                        )
                        .then(Commands.argument(
                                        "reason",
                                        StringArgumentType.greedyString()
                                )
                                .executes(context -> authority(
                                        context,
                                        operation,
                                        runtimeResolver
                                ))));
    }

    // ------------------------------------------------------------------
    // preview
    // ------------------------------------------------------------------

    private static int preview(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        EmergencyRequest request;
        try {
            request = parseRequest(context);
        } catch (IllegalArgumentException invalid) {
            return CommandFeedback.failure(
                    source,
                    "Emergency preview rejected: " + invalid.getMessage() + "."
            );
        }
        Optional<EmergencyService> service = emergencyService(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        EmergencyActorSource actor = resolveActor(source);
        if (actor == null) {
            return rejectedSource(source, "preview");
        }
        try {
            PreviewResult result = service.get().preview(request, actor);
            if (result.accepted()) {
                // The plaintext token is presented exactly once and is never
                // written to logs or persisted by this adapter.
                return CommandFeedback.success(
                        source,
                        "Emergency preview accepted: attempt=" + result.attemptId()
                                + "; token=" + result.token()
                                + "; expiresAt=" + result.expiresAt()
                                + ". " + result.summary()
                );
            }
            return CommandFeedback.failure(
                    source,
                    "Emergency preview rejected: " + describe(result.failureCode())
                            + ". " + result.summary()
            );
        } catch (EmergencyUnavailableException failure) {
            return reject(source, "preview", failure.failureCode());
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.emergency.preview", failure);
        }
    }

    /** Bounded request construction; throws on invalid input (fail closed). */
    static EmergencyRequest parseRequest(CommandContext<CommandSourceStack> context) {
        String module = StringArgumentType.getString(context, "module");
        String action = StringArgumentType.getString(context, "action");
        String version = StringArgumentType.getString(context, "version");
        EmergencyTargetType targetType = parseTargetType(
                StringArgumentType.getString(context, "targetType")
        );
        String targetId = StringArgumentType.getString(context, "targetId");
        EmergencyCategory category = parseCategory(
                StringArgumentType.getString(context, "category")
        );
        String reason = StringArgumentType.getString(context, "reason");
        Map<String, String> parameters = parseKeyValues(optionalString(context, "kv"));
        return new EmergencyRequest(
                module,
                action,
                version,
                targetType,
                targetId,
                category,
                reason,
                parameters
        );
    }

    static EmergencyTargetType parseTargetType(String input) {
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        try {
            return EmergencyTargetType.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "targetType must be one of "
                            + java.util.EnumSet.allOf(EmergencyTargetType.class)
            );
        }
    }

    static EmergencyCategory parseCategory(String input) {
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        try {
            return EmergencyCategory.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "category must be one of "
                            + java.util.EnumSet.allOf(EmergencyCategory.class)
            );
        }
    }

    /**
     * Parses the trailing {@code key value ...} pairs (bounded: at most
     * {@link EmergencyRequest#MAX_PARAMETERS} pairs, keys <= 32, values <= 200).
     */
    static Map<String, String> parseKeyValues(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "parameter pairs must be even (key value ...)"
            );
        }
        if (tokens.length / 2 > EmergencyRequest.MAX_PARAMETERS) {
            throw new IllegalArgumentException(
                    "parameters exceed the maximum of "
                            + EmergencyRequest.MAX_PARAMETERS
            );
        }
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        for (int index = 0; index < tokens.length; index += 2) {
            String key = tokens[index].trim();
            String value = tokens[index + 1].trim();
            if (key.isEmpty() || key.length() > 32) {
                throw new IllegalArgumentException(
                        "parameter keys are bounded to 32 characters"
                );
            }
            if (value.length() > 200) {
                throw new IllegalArgumentException(
                        "parameter values are bounded to 200 characters"
                );
            }
            parameters.put(key, value);
        }
        return parameters;
    }

    private static String optionalString(
            CommandContext<CommandSourceStack> context,
            String name
    ) {
        boolean present = context.getNodes().stream()
                .anyMatch(node -> node.getNode().getName().equals(name));
        return present ? StringArgumentType.getString(context, name) : "";
    }

    // ------------------------------------------------------------------
    // confirm
    // ------------------------------------------------------------------

    private static int confirm(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        String token = StringArgumentType.getString(context, "token").trim();
        if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return CommandFeedback.failure(
                    source,
                    "Emergency confirm rejected: the token is invalid (bounded at "
                            + MAX_TOKEN_LENGTH + " characters)."
            );
        }
        Optional<EmergencyService> service = emergencyService(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        EmergencyActorSource actor = resolveActor(source);
        if (actor == null) {
            return rejectedSource(source, "confirm");
        }
        try {
            ConfirmResult result = service.get().confirm(token, actor);
            if (result.success()) {
                return CommandFeedback.success(
                        source,
                        "Emergency action confirmed: attempt=" + result.attemptId()
                                + ". " + result.summary()
                );
            }
            return CommandFeedback.failure(
                    source,
                    "Emergency confirm rejected: " + describe(result.failureCode())
                            + ". " + result.summary()
            );
        } catch (EmergencyUnavailableException failure) {
            return reject(source, "confirm", failure.failureCode());
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.emergency.confirm", failure);
        }
    }

    // ------------------------------------------------------------------
    // inspect / status
    // ------------------------------------------------------------------

    private static int inspect(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        long attemptId = LongArgumentType.getLong(context, "attemptId");
        Optional<EmergencyService> service = emergencyService(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        try {
            Optional<EmergencyInspection> inspection = service.get().inspect(attemptId);
            if (inspection.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "Emergency journal: no record for attempt " + attemptId + "."
                );
            }
            EmergencyInspection record = inspection.get();
            return CommandFeedback.success(
                    source,
                    "Emergency attempt " + attemptId + ": at=" + record.at()
                            + "; actor=" + record.actorType()
                            + "; kind=" + record.kind()
                            + "; result=" + record.resultName()
                            + "; digest=" + record.requestDigest() + "."
            );
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.emergency.inspect", failure);
        }
    }

    private static int status(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        Optional<EmergencyService> service = emergencyService(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        try {
            EmergencyStatus status = service.get().status();
            String active = status.activeUuidDigest() == null
                    ? "none" : status.activeUuidDigest();
            String head = status.journalHeadDigest() == null
                    ? "none" : status.journalHeadDigest();
            return CommandFeedback.success(
                    source,
                    "Emergency authority: phase=" + status.phase()
                            + "; revision=" + status.configRevision()
                            + "; drift=" + status.driftDetected()
                            + "; records=" + status.recordCount()
                            + "; active=" + active
                            + "; head=" + head
                            + "; providers=" + status.providerWatermarks().size()
                            + "."
            );
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.emergency.status", failure);
        }
    }

    // ------------------------------------------------------------------
    // authority configuration lifecycle (bootstrap / stage / recover)
    // ------------------------------------------------------------------

    private static int authority(
            CommandContext<CommandSourceStack> context,
            String operation,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        String uuidInput = StringArgumentType.getString(context, "uuid");
        UUID targetUuid;
        try {
            targetUuid = parseCanonicalUuid(uuidInput);
        } catch (IllegalArgumentException invalid) {
            return CommandFeedback.failure(
                    source,
                    "Emergency " + operation + " rejected: invalid target UUID '"
                            + uuidInput + "' (canonical UUID required)."
            );
        }
        String reason = StringArgumentType.getString(context, "reason");
        if (reason == null || reason.isBlank()) {
            return CommandFeedback.failure(
                    source,
                    "Emergency " + operation
                            + " rejected: a non-empty reason is required."
            );
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return CommandFeedback.failure(
                    source,
                    "Emergency " + operation + " rejected: reason exceeds "
                            + MAX_REASON_LENGTH + " characters."
            );
        }
        Optional<EmergencyService> service = emergencyService(runtimeResolver);
        if (service.isEmpty()) {
            return unavailableRuntime(source);
        }
        EmergencyActorSource actor = resolveActor(source);
        if (actor == null) {
            return rejectedSource(source, operation);
        }
        try {
            ConfigureResult result = switch (operation) {
                case "bootstrap" -> service.get().bootstrapAuthority(
                        targetUuid, reason, actor
                );
                case "stage" -> service.get().stageAuthority(
                        targetUuid, reason, actor
                );
                default -> service.get().recoverAuthority(
                        targetUuid, reason, actor
                );
            };
            if (result.accepted()) {
                return CommandFeedback.success(
                        source,
                        "Emergency authority " + operation + " accepted: revision="
                                + result.configRevision() + ". " + result.summary()
                );
            }
            return CommandFeedback.failure(
                    source,
                    "Emergency authority " + operation + " rejected: "
                            + describe(result.failureCode()) + ". " + result.summary()
            );
        } catch (EmergencyUnavailableException failure) {
            return reject(source, operation, failure.failureCode());
        } catch (RuntimeException failure) {
            return unexpected(source, "admin.emergency." + operation, failure);
        }
    }

    // ------------------------------------------------------------------
    // source classification and actor mapping (FR-EMG-001-A §4 / §13)
    // ------------------------------------------------------------------

    /**
     * Maps a strict source classification to a service actor source.
     * {@code LOCAL_CONSOLE} becomes {@code SERVER_CONSOLE}; {@code PLAYER}
     * becomes {@code HYDRO_ARCHON} carrying the authenticated player UUID.
     * RCON, command blocks, functions, integrated hosts, and other sources
     * return {@code null} (fail closed at the command boundary; the service
     * revalidates the trusted actor at its final mutation boundary).
     */
    static EmergencyActorSource mapSource(
            EmergencySourceClassification classification,
            UUID playerUuid
    ) {
        return switch (classification) {
            case LOCAL_CONSOLE -> new EmergencyActorSource(
                    EmergencyActorType.SERVER_CONSOLE,
                    null,
                    EmergencySourceClassification.LOCAL_CONSOLE
            );
            case PLAYER -> playerUuid == null ? null : new EmergencyActorSource(
                    EmergencyActorType.HYDRO_ARCHON,
                    playerUuid,
                    EmergencySourceClassification.PLAYER
            );
            default -> null;
        };
    }

    private static EmergencyActorSource resolveActor(CommandSourceStack source) {
        EmergencySourceClassification classification;
        try {
            classification = EmergencyConsoleClassifier.classify(source);
        } catch (RuntimeException failure) {
            return null;
        }
        UUID playerUuid = source.getEntity() instanceof ServerPlayer player
                ? player.getUUID() : null;
        return mapSource(classification, playerUuid);
    }

    // ------------------------------------------------------------------
    // feedback helpers
    // ------------------------------------------------------------------

    private static Optional<EmergencyService> emergencyService(
            CommandRuntimeResolver runtimeResolver
    ) {
        return runtimeResolver.emergencyService();
    }

    private static int unavailableRuntime(CommandSourceStack source) {
        LOGGER.warn("[Command] Emergency runtime is unavailable");
        return CommandFeedback.failure(
                source,
                "FontaineRepublic emergency runtime is unavailable."
        );
    }

    private static int rejectedSource(CommandSourceStack source, String action) {
        return CommandFeedback.failure(
                source,
                "Emergency " + action + " rejected: only the real local server "
                        + "console or an authorized Hydro Archon player may run "
                        + "emergency commands."
        );
    }

    private static int reject(
            CommandSourceStack source,
            String action,
            String failureCode
    ) {
        return CommandFeedback.failure(
                source,
                "Emergency " + action + " rejected: " + describe(failureCode) + "."
        );
    }

    /** Stable bounded description of a service failure code (no internal leak). */
    static String describe(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return "the request failed";
        }
        return switch (failureCode) {
            case DefaultEmergencyService.CODE_REJECTED_SOURCE ->
                    "the source is not authorized for this action";
            case DefaultEmergencyService.CODE_UNKNOWN_ACTION ->
                    "the action is unknown";
            case DefaultEmergencyService.CODE_INVALID_INPUT ->
                    "the request failed validation";
            case DefaultEmergencyService.CODE_ACTION_UNAVAILABLE ->
                    "the action is not available";
            case DefaultEmergencyService.CODE_REJECTED_TOKEN ->
                    "the token is invalid, expired, or already used";
            case DefaultEmergencyService.CODE_REJECTED_REVISION ->
                    "the target revision changed; preview again";
            case DefaultEmergencyService.CODE_PROVIDER_FAILURE ->
                    "the business provider failed";
            case DefaultEmergencyService.CODE_COMMIT_FAILURE ->
                    "the durable commit was not acknowledged";
            case DefaultEmergencyService.CODE_STOPPING ->
                    "the server is stopping";
            case DefaultEmergencyService.CODE_UNAVAILABLE ->
                    "the emergency journal could not record the attempt";
            case DefaultEmergencyService.CODE_DRIFT ->
                    "authority configuration drift is present";
            case DefaultEmergencyService.CODE_CONFIG_REJECTED ->
                    "the configuration change was rejected";
            case EmergencyUnavailableException.CODE_STORE_FAILURE ->
                    "the durable store did not acknowledge the change";
            case EmergencyUnavailableException.CODE_CAPACITY_EXCEEDED ->
                    "a capacity budget was exceeded";
            case EmergencyUnavailableException.CODE_CORRUPTION ->
                    "the emergency namespace is corrupt";
            case EmergencyUnavailableException.CODE_NOT_ACTIVE ->
                    "the emergency runtime is not active";
            default -> "the request failed (" + failureCode + ")";
        };
    }

    private static UUID parseCanonicalUuid(String input) {
        UUID parsed = UUID.fromString(input);
        if (!parsed.toString().equals(input)) {
            throw new IllegalArgumentException("UUID must be canonical");
        }
        return parsed;
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
