package com.fontainerepublic.server.command;

import com.fontainerepublic.server.landclaim.api.ClaimReceipt;
import com.fontainerepublic.server.landclaim.api.InspectResult;
import com.fontainerepublic.server.landclaim.api.LandClaimService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * No-client peer of the communicator land claim (FR-LAND-CLAIM-001-A §3.4):
 * {@code /fr land inspect <x> <y> <z>} and {@code /fr land claim <x> <y> <z>}.
 *
 * <p>Both commands are player-only; the dimension is always taken from the
 * executing player's current world (never from the caller). They call the very
 * same {@link LandClaimService} as the C2S handlers, so the held-communicator
 * gate, loading, reach, current-dimension and durable-commit rules cannot be
 * bypassed, and the command is not a backdoor (OP gains no bypass). Feedback is
 * rendered as server chat — no client packet is required.</p>
 */
public final class LandCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LandCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    ) {
        Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        return Commands.literal("land")
                .then(Commands.literal("inspect")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument(
                                                        "z",
                                                        IntegerArgumentType.integer()
                                                )
                                                .executes(context -> inspect(
                                                        context.getSource(),
                                                        runtimeResolver,
                                                        IntegerArgumentType.getInteger(
                                                                context, "x"),
                                                        IntegerArgumentType.getInteger(
                                                                context, "y"),
                                                        IntegerArgumentType.getInteger(
                                                                context, "z")
                                                ))))))
                .then(Commands.literal("claim")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument(
                                                        "z",
                                                        IntegerArgumentType.integer()
                                                )
                                                .executes(context -> claim(
                                                        context.getSource(),
                                                        runtimeResolver,
                                                        IntegerArgumentType.getInteger(
                                                                context, "x"),
                                                        IntegerArgumentType.getInteger(
                                                                context, "y"),
                                                        IntegerArgumentType.getInteger(
                                                                context, "z")
                                                ))))));
    }

    // ------------------------------------------------------------------
    // /fr land inspect <x> <y> <z>
    // ------------------------------------------------------------------

    /**
     * Brigadier callback for {@code /fr land inspect}. Package-visible for the
     * behavioral command test: it resolves the player-only gate, the shared
     * ACTIVE {@link LandClaimService} and the executing player's current
     * dimension, then delegates the shared-service call and feedback selection
     * to the dependency-free {@link #executeInspect} so the command can be
     * outcome-tested without a live Minecraft server.
     */
    static int inspect(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver,
            int x,
            int y,
            int z
    ) {
        ServerPlayer player = playerOnly(source);
        if (player == null) {
            return CommandFeedback.failure(source, "Only a player can inspect land.");
        }
        Optional<LandClaimService> service = runtimeResolver.landClaimService();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Land claim runtime is unavailable.");
        }
        String dimension = player.level().dimension().location().toString();
        CommandOutcome outcome = executeInspect(
                service.get(), dimension, player.getUUID(), x, y, z);
        if (outcome.success()) {
            return CommandFeedback.success(source, outcome.message());
        }
        return CommandFeedback.failure(source, outcome.message());
    }

    /**
     * Dependency-free core of {@code /fr land inspect}: calls the very same
     * {@link LandClaimService} used by the C2S handlers (never a raw
     * {@code LandService} mutation), sourcing the dimension supplied by the
     * caller, and selects the exact player-facing feedback for the claimable /
     * owned / gate-failure cases. Returned as a {@link CommandOutcome} so a
     * plain JVM can assert every branch through executable production logic.
     */
    public static CommandOutcome executeInspect(
            LandClaimService service,
            String dimension,
            UUID actor,
            int x,
            int y,
            int z
    ) {
        InspectResult result = service.inspect(actor, dimension, x, y, z);
        if (result.claimable()) {
            return CommandOutcome.success(
                    "Land at " + x + " " + y + " " + z
                            + " is unowned and claimable."
            );
        }
        if (result.parcelId() != null) {
            return CommandOutcome.success(
                    "Land at " + x + " " + y + " " + z
                            + " is owned by parcel "
                            + result.parcelId() + " (zone "
                            + (result.zoneType() == null ? "?" : result.zoneType())
                            + ", access "
                            + (result.access() == null ? "?" : result.access()) + ")."
            );
        }
        return CommandOutcome.failure(
                "Inspect rejected: " + result.code() + "."
        );
    }

    // ------------------------------------------------------------------
    // /fr land claim <x> <y> <z>
    // ------------------------------------------------------------------

    /**
     * Brigadier callback for {@code /fr land claim}. Package-visible for the
     * behavioral command test; mirrors {@link #inspect}.
     */
    static int claim(
            CommandSourceStack source,
            CommandRuntimeResolver runtimeResolver,
            int x,
            int y,
            int z
    ) {
        ServerPlayer player = playerOnly(source);
        if (player == null) {
            return CommandFeedback.failure(source, "Only a player can claim land.");
        }
        Optional<LandClaimService> service = runtimeResolver.landClaimService();
        if (service.isEmpty()) {
            return CommandFeedback.failure(source, "Land claim runtime is unavailable.");
        }
        String dimension = player.level().dimension().location().toString();
        CommandOutcome outcome = executeClaim(
                service.get(), dimension, player.getUUID(), x, y, z);
        if (outcome.success()) {
            return CommandFeedback.success(source, outcome.message());
        }
        return CommandFeedback.failure(source, outcome.message());
    }

    /**
     * Dependency-free core of {@code /fr land claim}: calls the very same
     * {@link LandClaimService} used by the C2S handlers (never a raw
     * {@code LandService} mutation), so the held-communicator, dimension,
     * loading, reach and atomic durable-commit rules cannot be bypassed by a
     * command (and OP gains no bypass). Selects success/failure feedback and
     * returns it as a {@link CommandOutcome} for plain-JVM assertion.
     */
    public static CommandOutcome executeClaim(
            LandClaimService service,
            String dimension,
            UUID actor,
            int x,
            int y,
            int z
    ) {
        ClaimReceipt receipt = service.claim(actor, dimension, x, y, z);
        if (receipt.success()) {
            return CommandOutcome.success(
                    "Parcel created: " + receipt.parcelId() + " (zone "
                            + receipt.zoneType() + ", access " + receipt.access()
                            + "). Hold the communicator to build."
            );
        }
        return CommandOutcome.failure("Claim rejected: " + receipt.code() + ".");
    }

    /** Fails closed when the command source is not a player. */
    private static ServerPlayer playerOnly(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    /**
     * Closed outcome of a land command action: exactly one of a success
     * message or a failure message, chosen by the shared-service execution
     * {@link #executeInspect}/{@link #executeClaim} and rendered by the
     * Brigadier callback. Dependency-free and immutable so a plain JVM can
     * assert every feedback branch.
     */
    public record CommandOutcome(boolean success, String message) {
        public CommandOutcome {
            message = Objects.requireNonNull(message, "message");
            if (message.isEmpty()) {
                throw new IllegalArgumentException("Command feedback must not be empty");
            }
        }

        private static CommandOutcome success(String message) {
            return new CommandOutcome(true, message);
        }

        private static CommandOutcome failure(String message) {
            return new CommandOutcome(false, message);
        }
    }
}
