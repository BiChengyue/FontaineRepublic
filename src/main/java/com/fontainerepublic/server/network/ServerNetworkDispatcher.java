package com.fontainerepublic.server.network;

import com.fontainerepublic.common.network.NetworkMessageSpec;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Direction, sender, main-thread, runtime, and rate enforcement.
 */
public final class ServerNetworkDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final NetworkRuntimeResolver runtimeResolver;

    public ServerNetworkDispatcher(NetworkRuntimeResolver runtimeResolver) {
        this.runtimeResolver = Objects.requireNonNull(runtimeResolver, "runtimeResolver");
    }

    public <MSG> void accept(
            NetworkMessageSpec<MSG> specification,
            MSG message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(contextSupplier, "contextSupplier");

        NetworkEvent.Context context = contextSupplier.get();
        try {
            if (context.getDirection() != specification.direction()) {
                LOGGER.warn(
                        "[Network] Rejected message ID {} class {}: direction {} != {}",
                        specification.id(),
                        specification.messageClass().getName(),
                        context.getDirection(),
                        specification.direction()
                );
                return;
            }

            if (specification.direction() == NetworkDirection.PLAY_TO_SERVER) {
                ServerPlayer sender = context.getSender();
                if (sender == null) {
                    LOGGER.warn(
                            "[Network] Rejected C2S message ID {} class {}: missing sender",
                            specification.id(),
                            specification.messageClass().getName()
                    );
                    return;
                }
                context.enqueueWork(
                        () -> dispatchServerMain(specification, message, context, sender)
                );
                return;
            }

            context.enqueueWork(() -> dispatchReceivingMain(specification, message, context));
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "[Network] Transport dispatch failed for message ID {} class {}: {}",
                    specification.id(),
                    specification.messageClass().getName(),
                    diagnostic(failure),
                    failure
            );
        } finally {
            context.setPacketHandled(true);
        }
    }

    private <MSG> void dispatchServerMain(
            NetworkMessageSpec<MSG> specification,
            MSG message,
            NetworkEvent.Context context,
            ServerPlayer sender
    ) {
        try {
            if (sender.connection.connection != context.getNetworkManager()
                    || !context.getNetworkManager().isConnected()) {
                LOGGER.warn(
                        "[Network] Rejected message ID {} for player {}: connection is no longer live",
                        specification.id(),
                        sender.getUUID()
                );
                return;
            }

            NetworkRuntimeService runtime = runtimeResolver.resolve().orElse(null);
            if (runtime == null) {
                LOGGER.warn(
                        "[Network] Rejected message ID {} for player {}: network runtime unavailable",
                        specification.id(),
                        sender.getUUID()
                );
                return;
            }

            PacketRateLimiter.RateLimitDecision decision = runtime.tryAcquire(
                    sender.getUUID(),
                    specification.id(),
                    specification.rateLimitPolicy().orElseThrow(
                            () -> new IllegalStateException(
                                    "Missing rate policy for C2S message ID "
                                            + specification.id()
                            )
                    )
            );
            if (decision != PacketRateLimiter.RateLimitDecision.ALLOWED) {
                LOGGER.warn(
                        "[Network] Rejected message ID {} for player {}: rate limit {}",
                        specification.id(),
                        sender.getUUID(),
                        decision
                );
                return;
            }

            specification.handler().handle(message, context);
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "[Network] Main-thread handler failed for message ID {} player {}: {}",
                    specification.id(),
                    sender.getUUID(),
                    diagnostic(failure),
                    failure
            );
        }
    }

    private <MSG> void dispatchReceivingMain(
            NetworkMessageSpec<MSG> specification,
            MSG message,
            NetworkEvent.Context context
    ) {
        try {
            specification.handler().handle(message, context);
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "[Network] Receiving-side handler failed for message ID {} class {}: {}",
                    specification.id(),
                    specification.messageClass().getName(),
                    diagnostic(failure),
                    failure
            );
        }
    }

    private String diagnostic(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return failure.getClass().getSimpleName() + ": " + message.trim();
    }
}
