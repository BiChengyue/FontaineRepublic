package com.fontainerepublic.server.emergency.service;

import com.fontainerepublic.server.emergency.model.EmergencyEntityKind;
import com.fontainerepublic.server.emergency.model.EmergencySourceClassification;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;

import java.util.Objects;

/**
 * Strict console source classifier for the shared emergency authority
 * (FR-EMG-001-A §4), aligned with the FR-ID-BOOTSTRAP console classifier.
 *
 * <p>Acceptance requires the real local Dedicated Server console: a dedicated
 * server, an entity-less source, and the exact source name {@code "Server"}.
 * Everything else is rejected before any mutation: RCON (entity-less source
 * named {@code "Rcon"}), command blocks and minecart command blocks
 * (entity-less {@code "@"} or a command entity), players, integrated-server
 * hosts, and functions/other entity-less sources.</p>
 *
 * <p>The classification is revalidated at the final mutation boundary: the
 * service checks the classification again immediately before any provider
 * invocation or configuration commit.</p>
 */
public final class EmergencyConsoleClassifier {

    private EmergencyConsoleClassifier() {
    }

    /**
     * Classifies a command source by its runtime shape. Pure and testable
     * without a live Minecraft server.
     */
    public static EmergencySourceClassification classify(
            boolean dedicatedServer,
            EmergencyEntityKind entityKind,
            String sourceName
    ) {
        Objects.requireNonNull(entityKind, "entityKind");
        Objects.requireNonNull(sourceName, "sourceName");
        if (!dedicatedServer) {
            return EmergencySourceClassification.INTEGRATED_HOST;
        }
        switch (entityKind) {
            case PLAYER -> {
                return EmergencySourceClassification.PLAYER;
            }
            case MINECART_COMMAND_BLOCK -> {
                return EmergencySourceClassification.MINECART_COMMAND_BLOCK;
            }
            case OTHER -> {
                return EmergencySourceClassification.FUNCTION_OR_OTHER;
            }
            case NONE -> {
                if (sourceName.equals("Server")) {
                    return EmergencySourceClassification.LOCAL_CONSOLE;
                }
                if (sourceName.equals("Rcon")) {
                    return EmergencySourceClassification.RCON;
                }
                if (sourceName.equals("@")) {
                    return EmergencySourceClassification.COMMAND_BLOCK;
                }
                return EmergencySourceClassification.FUNCTION_OR_OTHER;
            }
            default -> {
                return EmergencySourceClassification.FUNCTION_OR_OTHER;
            }
        }
    }

    /** Classifies a live {@link CommandSourceStack}. */
    public static EmergencySourceClassification classify(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        boolean dedicated = source.getServer().isDedicatedServer();
        return classify(dedicated, entityKind(source.getEntity()), source.getTextName());
    }

    /** Whether a classification is the accepted local console source. */
    public static boolean isLocalConsole(EmergencySourceClassification classification) {
        return classification == EmergencySourceClassification.LOCAL_CONSOLE;
    }

    private static EmergencyEntityKind entityKind(Entity entity) {
        if (entity == null) {
            return EmergencyEntityKind.NONE;
        }
        if (entity instanceof Player) {
            return EmergencyEntityKind.PLAYER;
        }
        if (entity instanceof MinecartCommandBlock) {
            return EmergencyEntityKind.MINECART_COMMAND_BLOCK;
        }
        return EmergencyEntityKind.OTHER;
    }
}
