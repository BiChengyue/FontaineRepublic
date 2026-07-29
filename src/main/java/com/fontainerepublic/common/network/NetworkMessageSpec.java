package com.fontainerepublic.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Immutable compiled transport contract for one message.
 */
public record NetworkMessageSpec<MSG>(
        int id,
        Class<MSG> messageClass,
        NetworkDirection direction,
        BiConsumer<MSG, FriendlyByteBuf> encoder,
        Function<FriendlyByteBuf, MSG> decoder,
        NetworkMessageHandler<MSG> handler,
        Optional<RateLimitPolicy> rateLimitPolicy
) {
    public NetworkMessageSpec {
        messageClass = Objects.requireNonNull(messageClass, "messageClass");
        encoder = Objects.requireNonNull(encoder, "encoder");
        decoder = Objects.requireNonNull(decoder, "decoder");
        handler = Objects.requireNonNull(handler, "handler");
        rateLimitPolicy = Objects.requireNonNull(rateLimitPolicy, "rateLimitPolicy");
    }
}
