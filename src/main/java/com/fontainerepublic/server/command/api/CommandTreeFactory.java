package com.fontainerepublic.server.command.api;

import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * Creates a fresh top-level contribution builder for one command-tree rebuild.
 */
@FunctionalInterface
public interface CommandTreeFactory {
    LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    );
}
