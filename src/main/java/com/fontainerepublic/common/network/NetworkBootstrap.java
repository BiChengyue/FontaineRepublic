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

    private static volatile NetworkBootstrap instance;

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
        instance = this;
    }

    /** The mod-lifetime bootstrap, available after the mod constructor. */
    public static NetworkBootstrap instance() {
        NetworkBootstrap current = instance;
        if (current == null) {
            throw new IllegalStateException("NetworkBootstrap is not constructed yet");
        }
        return current;
    }

    public NetworkMessageRegistration registrationSurface() {
        return registrar;
    }

    /**
     * Freezes the compiled production ledger (FR-CLIENT-001-A §4.1: the
     * FR-NET-001 empty-table placeholder is replaced by an exact-count
     * validation against the expected ledger).
     */
    public void registerProductionMessagesAndFreeze() {
        NetworkProductionMessageTable.registerAll(registrar);
        int messageCount = registrar.freeze();
        if (messageCount != NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT) {
            throw new IllegalStateException(
                    "Production message table must hold exactly "
                            + NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT
                            + " messages for protocol " + NetworkProtocol.VERSION
                            + ": " + messageCount
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

    /**
     * Client-side C2S send surface (FR-TRADE-001-A §3: the C2S ledger entries
     * are the first client-to-server messages of the mod). Available only
     * after the message-table freeze; sending on a dedicated server is a
     * no-op (no local player, Forge drops it).
     */
    public void sendToServer(Object message) {
        Objects.requireNonNull(message, "message");
        if (!registrar.isFrozen()) {
            throw new IllegalStateException(
                    "C2S send is unavailable before message-table freeze"
            );
        }
        channel.sendToServer(message);
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
