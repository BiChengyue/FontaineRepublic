package com.fontainerepublic.server.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Plain server-resolved feedback compatible with clients that do not have the Mod.
 */
public final class CommandFeedback {
    public static final int SUCCESS = 1;
    public static final int FAILURE = 0;
    private static final int MAX_MESSAGE_LENGTH = 240;

    private CommandFeedback() {
    }

    public static int success(CommandSourceStack source, String message) {
        String bounded = bounded(message);
        source.sendSuccess(() -> Component.literal(bounded), false);
        return SUCCESS;
    }

    public static int failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(bounded(message)));
        return FAILURE;
    }

    static String bounded(String message) {
        String normalized = Objects.requireNonNull(message, "message")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (normalized.length() <= MAX_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }
}
