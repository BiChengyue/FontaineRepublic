package com.fontainerepublic.server.command;

import com.fontainerepublic.FontaineRepublic;
import com.fontainerepublic.server.command.registration.CommandContributionSpec;
import com.fontainerepublic.server.command.registration.CommandRegistrationException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fresh-tree compiler for the single FontaineRepublic command root.
 */
final class FRCommand {
    static final String ROOT_LITERAL = "fr";

    private FRCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver,
            List<CommandContributionSpec> specifications
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        Objects.requireNonNull(specifications, "specifications");

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT_LITERAL)
                .executes(context -> CommandFeedback.success(
                        context.getSource(),
                        "FontaineRepublic " + FontaineRepublic.MOD_VERSION
                                + " commands are available. Use /fr help."
                ));
        root.then(Commands.literal("help").executes(FRCommand::showHelp));
        root.then(FrameworkAdminCommand.create(runtimeResolver));

        for (CommandContributionSpec specification : specifications) {
            LiteralArgumentBuilder<CommandSourceStack> child;
            try {
                child = specification.treeFactory().create(
                        buildContext,
                        runtimeResolver
                );
            } catch (RuntimeException exception) {
                throw new CommandRegistrationException(
                        "Command tree factory failed for "
                                + specification.topLevelLiteral(),
                        exception
                );
            }
            if (child == null) {
                throw new CommandRegistrationException(
                        "Command tree factory returned null for "
                                + specification.topLevelLiteral()
                );
            }
            if (!specification.topLevelLiteral().equals(child.getLiteral())) {
                throw new CommandRegistrationException(
                        "Command tree literal mismatch: expected "
                                + specification.topLevelLiteral()
                                + " but factory returned " + child.getLiteral()
                );
            }
            root.then(child);
        }
        return root;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandNode<CommandSourceStack> root =
                context.getRootNode().getChild(ROOT_LITERAL);
        ArrayList<String> visible = new ArrayList<>();
        if (root != null) {
            root.getChildren().stream()
                    .filter(node -> node.canUse(context.getSource()))
                    .map(CommandNode::getName)
                    .sorted()
                    .forEach(visible::add);
        }
        String suffix = visible.isEmpty()
                ? "No subcommands are available."
                : "Available: " + String.join(", ", visible) + ".";
        return CommandFeedback.success(
                context.getSource(),
                "FontaineRepublic command help. " + suffix
        );
    }
}
