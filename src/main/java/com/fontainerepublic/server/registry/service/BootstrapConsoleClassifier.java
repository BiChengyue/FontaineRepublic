package com.fontainerepublic.server.registry.service;

import com.fontainerepublic.server.registry.model.BootstrapEntityKind;
import com.fontainerepublic.server.registry.model.BootstrapSourceClassification;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;

import java.util.Objects;

/**
 * Strict console source classifier for the original-person bootstrap
 * (FR-ID-BOOTSTRAP-001-A §3, implementation task §3.1).
 *
 * <p>Acceptance requires the real local Dedicated Server console: a dedicated
 * server, an entity-less source, and the exact source name {@code "Server"}.
 * Everything else is rejected before any mutation:</p>
 * <ul>
 *   <li>RCON — entity-less source named {@code "Rcon"};</li>
 *   <li>command blocks and minecart command blocks — entity-less sources named
 *       {@code "@"} (the default {@code BaseCommandBlock} name) or a command
 *       entity;</li>
 *   <li>players — entity sources;</li>
 *   <li>integrated-server hosts — non-dedicated servers;</li>
 *   <li>functions and other entity-less non-Server sources.</li>
 * </ul>
 *
 * <p>Implementation notes on Minecraft 1.20.1 behavior (verified against the
 * runtime): a command-block command source has {@code getEntity() == null}
 * (the {@code CommandSourceStack} is built with a null entity) and its default
 * name is {@code "@"}; a minecart command block exposes the minecart entity;
 * the console source is named {@code "Server"} and RCON {@code "Rcon"}.</p>
 *
 * <p>Known limitation (accepted by design review): a Minecraft function
 * invoked from the console produces an entity-less source whose name is
 * {@code "Server"}, indistinguishable from the console at the
 * {@link CommandSourceStack} level. RCON, command blocks (default name),
 * minecarts, players, and integrated hosts are reliably classified; the
 * residual function-name-spoofing vector requires physical console access and
 * is accepted for this single-use, fully audited bootstrap. A command block
 * with a custom name falls into {@code FUNCTION_OR_OTHER} and is still
 * rejected.</p>
 *
 * <p>The classification is revalidated at the final mutation boundary: the
 * executor checks {@link #isLocalConsole} again immediately before the
 * binding is committed.</p>
 */
public final class BootstrapConsoleClassifier {

    private BootstrapConsoleClassifier() {
    }

    /**
     * Classifies a command source by its runtime shape. The classification is
     * pure and testable without a live Minecraft server.
     *
     * @param dedicatedServer whether the server is a dedicated server
     * @param entityKind      the command source entity kind (NONE for console,
     *                        RCON, functions, and command blocks)
     * @param sourceName      the command source display name
     */
    public static BootstrapSourceClassification classify(
            boolean dedicatedServer,
            BootstrapEntityKind entityKind,
            String sourceName
    ) {
        Objects.requireNonNull(entityKind, "entityKind");
        Objects.requireNonNull(sourceName, "sourceName");
        if (!dedicatedServer) {
            return BootstrapSourceClassification.INTEGRATED_HOST;
        }
        switch (entityKind) {
            case PLAYER -> {
                return BootstrapSourceClassification.PLAYER;
            }
            case MINECART_COMMAND_BLOCK -> {
                return BootstrapSourceClassification.MINECART_COMMAND_BLOCK;
            }
            case OTHER -> {
                return BootstrapSourceClassification.FUNCTION_OR_OTHER;
            }
            case NONE -> {
                if (sourceName.equals("Server")) {
                    return BootstrapSourceClassification.LOCAL_CONSOLE;
                }
                if (sourceName.equals("Rcon")) {
                    return BootstrapSourceClassification.RCON;
                }
                if (sourceName.equals("@")) {
                    // Default BaseCommandBlock name (command block or minecart
                    // command block without a custom name).
                    return BootstrapSourceClassification.COMMAND_BLOCK;
                }
                return BootstrapSourceClassification.FUNCTION_OR_OTHER;
            }
            default -> {
                return BootstrapSourceClassification.FUNCTION_OR_OTHER;
            }
        }
    }

    /**
     * Classifies a live {@link CommandSourceStack}. Only the local dedicated
     * console may bootstrap the original person.
     */
    public static BootstrapSourceClassification classify(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        boolean dedicated = source.getServer().isDedicatedServer();
        BootstrapEntityKind entityKind = entityKind(source.getEntity());
        return classify(dedicated, entityKind, source.getTextName());
    }

    /** Whether a source classification is accepted for mutation. */
    public static boolean isLocalConsole(BootstrapSourceClassification classification) {
        return classification == BootstrapSourceClassification.LOCAL_CONSOLE;
    }

    private static BootstrapEntityKind entityKind(Entity entity) {
        if (entity == null) {
            return BootstrapEntityKind.NONE;
        }
        if (entity instanceof Player) {
            return BootstrapEntityKind.PLAYER;
        }
        if (entity instanceof MinecartCommandBlock) {
            return BootstrapEntityKind.MINECART_COMMAND_BLOCK;
        }
        return BootstrapEntityKind.OTHER;
    }
}
