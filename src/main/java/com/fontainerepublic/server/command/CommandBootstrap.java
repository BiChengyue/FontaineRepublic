package com.fontainerepublic.server.command;

import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.command.registration.CommandContributionSpec;
import com.fontainerepublic.server.command.registration.CommandRegistrationException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

/**
 * Forge event adapter that compiles and registers one fresh /fr tree per dispatcher.
 */
public final class CommandBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CommandContributionRegistry contributionRegistry;
    private final CommandRuntimeResolver runtimeResolver;

    public CommandBootstrap(
            CommandContributionRegistry contributionRegistry,
            CommandRuntimeResolver runtimeResolver
    ) {
        this.contributionRegistry =
                Objects.requireNonNull(contributionRegistry, "contributionRegistry");
        this.runtimeResolver = Objects.requireNonNull(runtimeResolver, "runtimeResolver");
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        Objects.requireNonNull(event, "event");
        register(
                event.getDispatcher(),
                event.getBuildContext(),
                event.getCommandSelection()
        );
    }

    public void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            Commands.CommandSelection commandSelection
    ) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(commandSelection, "commandSelection");

        if (dispatcher.getRoot().getChild(FRCommand.ROOT_LITERAL) != null) {
            CommandRegistrationException collision = new CommandRegistrationException(
                    "Command root collision: /" + FRCommand.ROOT_LITERAL
            );
            LOGGER.error("[Command] {}", collision.getMessage());
            throw collision;
        }

        List<CommandContributionSpec> specifications;
        try {
            specifications = contributionRegistry.requireFrozenSnapshot();
            LiteralArgumentBuilder<CommandSourceStack> root = FRCommand.create(
                    buildContext,
                    runtimeResolver,
                    specifications
            );
            dispatcher.register(root);
        } catch (CommandRegistrationException exception) {
            LOGGER.error("[Command] Command registration rejected: {}", exception.getMessage(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("[Command] Unexpected command-tree construction failure", exception);
            throw new CommandRegistrationException(
                    "Unexpected command-tree construction failure",
                    exception
            );
        }

        LOGGER.info(
                "[Command] Registered /{} for {} with {} contributions",
                FRCommand.ROOT_LITERAL,
                commandSelection,
                specifications.size()
        );
    }
}
