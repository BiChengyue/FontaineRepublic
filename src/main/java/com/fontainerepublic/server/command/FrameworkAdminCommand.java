package com.fontainerepublic.server.command;

import com.fontainerepublic.server.command.CommandRuntimeResolver.ModuleDiagnostic;
import com.fontainerepublic.server.command.CommandRuntimeResolver.RuntimeSnapshot;
import com.fontainerepublic.server.registry.model.BootstrapAttemptResult;
import com.fontainerepublic.server.registry.model.BootstrapDigests;
import com.fontainerepublic.server.registry.model.BootstrapPhase;
import com.fontainerepublic.server.registry.model.BootstrapSourceClassification;
import com.fontainerepublic.server.registry.model.BootstrapStatus;
import com.fontainerepublic.server.registry.service.BootstrapConsoleClassifier;
import com.fontainerepublic.server.registry.service.SubjectBootstrapService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only framework diagnostics plus the foundation-owned bootstrap admin
 * child adapter behind the temporary Minecraft OP level-2 gate.
 */
final class FrameworkAdminCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FrameworkAdminCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandRuntimeResolver runtimeResolver
    ) {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(context -> status(
                                context.getSource(),
                                runtimeResolver
                        )))
                .then(Commands.literal("modules")
                        .executes(context -> modules(
                                context.getSource(),
                                runtimeResolver
                        )))
                .then(Commands.literal("bootstrap")
                        .then(Commands.literal("subject-hydro")
                                .then(Commands.argument(
                                                "uuid",
                                                StringArgumentType.string()
                                        )
                                        .then(Commands.argument(
                                                        "reason",
                                                        StringArgumentType.greedyString()
                                                )
                                                .executes(context -> bootstrapSubjectHydro(
                                                        context,
                                                        runtimeResolver
                                                )))))
                        .then(Commands.literal("status")
                                .executes(context -> bootstrapStatus(
                                        context.getSource(),
                                        runtimeResolver
                                ))));
    }

    // ------------------------------------------------------------------
    // bootstrap admin child adapter (FR-ID-BOOTSTRAP-001-A §3):
    // /fr admin bootstrap subject-hydro <uuid> <reason>
    // /fr admin bootstrap status
    // ------------------------------------------------------------------

    private static int bootstrapSubjectHydro(
            CommandContext<CommandSourceStack> context,
            CommandRuntimeResolver runtimeResolver
    ) {
        CommandSourceStack source = context.getSource();
        String uuidInput = StringArgumentType.getString(context, "uuid");
        String reason = StringArgumentType.getString(context, "reason");

        UUID targetUuid;
        try {
            targetUuid = parseCanonicalUuid(uuidInput);
        } catch (IllegalArgumentException invalid) {
            return CommandFeedback.failure(
                    source,
                    "Bootstrap rejected: invalid target UUID '" + uuidInput
                            + "' (canonical UUID required)."
            );
        }
        if (reason == null || reason.isBlank()) {
            return CommandFeedback.failure(
                    source,
                    "Bootstrap rejected: a non-empty reason is required."
            );
        }
        if (reason.length() > com.fontainerepublic.server.registry.service
                .DefaultSubjectBootstrapService.MAX_REASON_LENGTH) {
            return CommandFeedback.failure(
                    source,
                    "Bootstrap rejected: reason exceeds "
                            + com.fontainerepublic.server.registry.service
                            .DefaultSubjectBootstrapService.MAX_REASON_LENGTH
                            + " characters."
            );
        }

        Optional<SubjectBootstrapService> service = runtimeResolver.subjectBootstrapService();
        if (service.isEmpty()) {
            LOGGER.warn("[Command] Subject bootstrap service is unavailable");
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic subject-registry runtime is unavailable."
            );
        }

        BootstrapSourceClassification classification;
        try {
            classification = BootstrapConsoleClassifier.classify(source);
        } catch (RuntimeException exception) {
            LOGGER.error("[Command] Bootstrap source classification failed", exception);
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic could not classify the bootstrap source."
            );
        }

        try {
            BootstrapAttemptResult result = service.get().bootstrapOriginalPerson(
                    targetUuid,
                    reason,
                    classification
            );
            return reportBootstrapResult(source, result);
        } catch (RuntimeException exception) {
            return unexpected(source, "admin.bootstrap.subject-hydro", exception);
        }
    }

    private static int bootstrapStatus(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        try {
            Optional<SubjectBootstrapService> service =
                    runtimeResolver.subjectBootstrapService();
            if (service.isEmpty()) {
                return CommandFeedback.failure(
                        source,
                        "FontaineRepublic subject-registry runtime is unavailable."
                );
            }
            BootstrapStatus status = service.get().status();
            String bound = status.phase() == BootstrapPhase.BOUND
                    ? "bound; digest=" + BootstrapDigests.toHex(status.boundUuidDigest())
                            + "; at=" + status.boundAt()
                    : "unbound";
            String last = status.lastResult()
                    .map(result -> result.name())
                    .orElse("none");
            return CommandFeedback.success(
                    source,
                    "Original-person bootstrap: phase=" + status.phase()
                            + "; attempts=" + status.attemptCount()
                            + "; last=" + last
                            + "; trailHead="
                            + (status.trailHeadDigest() == null
                            ? "none"
                            : BootstrapDigests.toHex(status.trailHeadDigest()))
                            + "; " + bound + "."
            );
        } catch (RuntimeException exception) {
            return unexpected(source, "admin.bootstrap.status", exception);
        }
    }

    private static int reportBootstrapResult(
            CommandSourceStack source,
            BootstrapAttemptResult result
    ) {
        String message = switch (result) {
            case SUCCESS -> "Bootstrap committed: the original Hydro Archon "
                    + "personal subject (10-000001-61) is bound.";
            case IDEMPOTENT_NOOP -> "Bootstrap no-op: the target UUID is already "
                    + "the bound original person.";
            case PLAYER_NOT_PROVISIONED -> "Bootstrap incomplete: the target UUID "
                    + "has no authoritative PlayerData record yet (retry later).";
            case PERSISTENCE_FAILURE -> "Bootstrap failed: the durable commit was "
                    + "not acknowledged; nothing was published (retry allowed).";
            case REJECTED_SOURCE -> "Bootstrap rejected: only the local dedicated "
                    + "server console may bind the original person.";
            case REJECTED_INPUT -> "Bootstrap rejected: invalid input.";
            case REJECTED -> "Bootstrap rejected: the binding is committed and "
                    + "immutable; a different target cannot be bound.";
            case PENDING -> "Bootstrap recorded as pending.";
        };
        return CommandFeedback.success(source, message);
    }

    private static UUID parseCanonicalUuid(String input) {
        UUID parsed = UUID.fromString(input);
        if (!parsed.toString().equals(input)) {
            throw new IllegalArgumentException("UUID must be canonical");
        }
        return parsed;
    }

    private static int status(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        try {
            RuntimeSnapshot snapshot = runtimeResolver.snapshot();
            if (!snapshot.runtimeAvailable()) {
                LOGGER.warn("[Command] Runtime unavailable for /fr admin status");
                return CommandFeedback.failure(
                        source,
                        "FontaineRepublic runtime is unavailable."
                );
            }
            String environment = source.getServer().isDedicatedServer()
                    ? "Dedicated"
                    : "Integrated";
            return CommandFeedback.success(
                    source,
                    "FontaineRepublic runtime: available; dependencies: "
                            + (snapshot.dependencyResolutionComplete() ? "complete" : "incomplete")
                            + "; modules: " + snapshot.modules().size()
                            + "; active: " + snapshot.activeModules()
                            + "; unavailable: " + snapshot.unavailableModules()
                            + "; environment: " + environment + "."
            );
        } catch (RuntimeException exception) {
            return unexpected(source, "admin.status", exception);
        }
    }

    private static int modules(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        try {
            RuntimeSnapshot snapshot = runtimeResolver.snapshot();
            if (!snapshot.runtimeAvailable()) {
                LOGGER.warn("[Command] Runtime unavailable for /fr admin modules");
                return CommandFeedback.failure(
                        source,
                        "FontaineRepublic runtime is unavailable."
                );
            }
            CommandFeedback.success(
                    source,
                    "FontaineRepublic modules: " + snapshot.modules().size() + "."
            );
            for (ModuleDiagnostic module : snapshot.modules()) {
                String availability = module.available()
                        ? "available"
                        : "unavailable:" + module.failureCategory();
                CommandFeedback.success(
                        source,
                        module.moduleId() + " - " + module.state() + " - " + availability
                );
            }
            return CommandFeedback.SUCCESS;
        } catch (RuntimeException exception) {
            return unexpected(source, "admin.modules", exception);
        }
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
