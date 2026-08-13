package com.fontainerepublic.server.command;

import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.persistence.CitizenUnavailableException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Approved read-only citizen command surface (FR-CIT-001-A §5): own status
 * and rank only, bounded and non-enumerating. No bulk list, no rank/status
 * mutations, no permission surface. Execution resolves the current ACTIVE
 * {@link CitizenService} per invocation through the
 * {@link CommandRuntimeResolver} and never caches services or state.
 */
public final class CitizenCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CitizenCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("citizen")
                .then(Commands.literal("info")
                        .executes(context -> info(
                                context.getSource(),
                                runtimeResolver
                        )));
    }

    // ------------------------------------------------------------------
    // /fr citizen info
    // ------------------------------------------------------------------

    private static int info(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver
    ) {
        Optional<CitizenService> service = runtimeResolver.citizenService();
        if (service.isEmpty()) {
            return CommandFeedback.failure(
                    source,
                    "Citizen runtime is unavailable."
            );
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandFeedback.failure(
                    source,
                    "Only a player can view their citizen status."
            );
        }
        try {
            Optional<CitizenRecord> record = service.get()
                    .getCitizen(player.getUUID());
            if (record.isEmpty()) {
                return CommandFeedback.success(
                        source,
                        "You are not a citizen yet."
                );
            }
            CitizenRecord citizen = record.get();
            return CommandFeedback.success(
                    source,
                    "Citizen status: " + citizen.status()
                            + "; rank: " + citizen.rank() + "."
            );
        } catch (CitizenUnavailableException exception) {
            return CommandFeedback.failure(source, citizenMessage(exception));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "[Command] Unexpected failure executing citizen.info for source {}",
                    source.getEntity() == null ? "non-player" : "player",
                    exception
            );
            return CommandFeedback.failure(
                    source,
                    "FontaineRepublic could not complete the command."
            );
        }
    }

    private static String citizenMessage(CitizenUnavailableException exception) {
        return switch (exception.failureCode()) {
            case CitizenUnavailableException.CODE_PLAYER_NOT_PROVISIONED ->
                    "Your player record is not provisioned yet; try again shortly.";
            case CitizenUnavailableException.CODE_SUBJECT_MISSING,
                    CitizenUnavailableException.CODE_NO_CITIZEN_RECORD ->
                    "You are not a citizen yet.";
            default -> "Citizen service is currently unavailable.";
        };
    }
}
