package com.fontainerepublic.server.command;

import com.fontainerepublic.server.command.CommandRuntimeResolver.ModuleDiagnostic;
import com.fontainerepublic.server.command.CommandRuntimeResolver.RuntimeSnapshot;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;

/**
 * Read-only framework diagnostics behind the temporary Minecraft OP level-2 gate.
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
                        )));
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
