package com.fontainerepublic.common.network;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.server.network.NetworkRuntimeResolver;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.network.ServerNetworkDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Mod-lifetime owner of the single channel and its one-way message table.
 */
public final class NetworkBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SimpleChannel channel;
    private final NetworkMessageRegistrar registrar;
    private final ServerNetworkDispatcher dispatcher;
    private final NetworkSendService sendService;

    public NetworkBootstrap(CoreManager coreManager) {
        Objects.requireNonNull(coreManager, "coreManager");
        channel = NetworkProtocol.createChannel();
        dispatcher = new ServerNetworkDispatcher(new NetworkRuntimeResolver(coreManager));
        registrar = new NetworkMessageRegistrar(this::bindUnchecked);
        sendService = new NetworkSendService(
                channel::isRemotePresent,
                (message, connection) ->
                        channel.sendTo(
                                message,
                                connection,
                                NetworkDirection.PLAY_TO_CLIENT
                        )
        );
    }

    public NetworkMessageRegistration registrationSurface() {
        return registrar;
    }

    /**
     * Freezes the compiled production ledger. FR-NET-001 intentionally has no messages.
     */
    public void registerProductionMessagesAndFreeze() {
        NetworkProductionMessageTable.registerAll(registrar);
        int messageCount = registrar.freeze();
        if (messageCount != 0) {
            throw new IllegalStateException(
                    "FR-NET-001 production message table must be empty: " + messageCount
            );
        }
        LOGGER.info(
                "[Network] Channel {} protocol {} frozen with {} production messages",
                NetworkProtocol.CHANNEL_NAME,
                NetworkProtocol.VERSION,
                messageCount
        );
    }

    public int productionMessageCount() {
        return registrar.size();
    }

    public boolean isFrozen() {
        return registrar.isFrozen();
    }

    public NetworkSendService sendService() {
        if (!registrar.isFrozen()) {
            throw new IllegalStateException(
                    "Network send service is unavailable before message-table freeze"
            );
        }
        return sendService;
    }

    @SuppressWarnings("unchecked")
    private void bindUnchecked(NetworkMessageSpec<?> specification) {
        bind((NetworkMessageSpec<Object>) specification);
    }

    private <MSG> void bind(NetworkMessageSpec<MSG> specification) {
        BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer =
                (message, contextSupplier) ->
                        dispatcher.accept(specification, message, contextSupplier);
        channel.messageBuilder(
                        specification.messageClass(),
                        specification.id(),
                        specification.direction()
                )
                .encoder((message, buffer) -> encode(specification, message, buffer))
                .decoder(buffer -> decode(specification, buffer))
                .consumerNetworkThread(consumer)
                .add();
    }

    private <MSG> void encode(
            NetworkMessageSpec<MSG> specification,
            MSG message,
            FriendlyByteBuf buffer
    ) {
        int startIndex = buffer.writerIndex();
        specification.encoder().accept(message, buffer);
        NetworkPayloadLimits.validateEncodedLength(buffer.writerIndex() - startIndex);
    }

    private <MSG> MSG decode(
            NetworkMessageSpec<MSG> specification,
            FriendlyByteBuf buffer
    ) {
        NetworkPayloadLimits.validateIncomingPayload(buffer);
        MSG message = Objects.requireNonNull(
                specification.decoder().apply(buffer),
                "Decoded message"
        );
        if (buffer.readableBytes() != 0) {
            throw new NetworkPayloadException(
                    "Trailing payload bytes for message ID "
                            + specification.id() + ": " + buffer.readableBytes()
            );
        }
        return message;
    }
}
