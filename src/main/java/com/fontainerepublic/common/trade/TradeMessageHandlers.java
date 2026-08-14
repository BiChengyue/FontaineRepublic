package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkMessageHandler;
import com.fontainerepublic.server.trade.TradeRuntime;
import com.fontainerepublic.server.trade.api.TradeService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Receiving-side handlers of the C2S trade ledger (FR-TRADE-001-A §3,
 * message ledger IDs 9-14).
 *
 * <p>The handlers live in the common package and never hold state: the
 * active {@link TradeService} is resolved per invocation through the static
 * {@link TradeRuntime} locator (bound by the trade module at runtime start,
 * unbound at shutdown, following the module service-routing precedent of the
 * project). The server dispatcher already enforced direction, connection
 * liveness, per-message rate policy and main-thread execution before the
 * handler runs; the sender UUID is taken from the connection context, never
 * from the payload. A missing runtime (shutdown window) is a silent
 * best-effort no-op with a warn log.</p>
 */
public final class TradeMessageHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TradeMessageHandlers() {
    }

    public static NetworkMessageHandler<TradeRequestPacket> request() {
        return (message, context) -> service(context).ifPresent(
                service -> service.request(sender(context), message.target())
        );
    }

    public static NetworkMessageHandler<TradeRespondPacket> respond() {
        return (message, context) -> service(context).ifPresent(
                service -> service.respond(
                        sender(context),
                        message.sessionId(),
                        message.accept()
                )
        );
    }

    public static NetworkMessageHandler<TradeOfferMoneyPacket> offerMoney() {
        return (message, context) -> service(context).ifPresent(
                service -> service.offerMoney(
                        sender(context),
                        message.sessionId(),
                        message.amount()
                )
        );
    }

    public static NetworkMessageHandler<TradeOfferItemPacket> offerItem() {
        return (message, context) -> service(context).ifPresent(
                service -> service.offerItem(
                        sender(context),
                        message.sessionId(),
                        message.slot(),
                        message.inventoryIndex()
                )
        );
    }

    public static NetworkMessageHandler<TradeOfferXpPacket> offerXp() {
        return (message, context) -> service(context).ifPresent(
                service -> service.offerXp(
                        sender(context),
                        message.sessionId(),
                        message.xpPoints()
                )
        );
    }

    public static NetworkMessageHandler<TradeAgreePacket> agree() {
        return (message, context) -> service(context).ifPresent(
                service -> service.agree(
                        sender(context),
                        message.sessionId(),
                        message.agree()
                )
        );
    }

    public static NetworkMessageHandler<TradeCancelPacket> cancel() {
        return (message, context) -> service(context).ifPresent(
                service -> service.cancel(sender(context), message.sessionId())
        );
    }

    private static java.util.UUID sender(net.minecraftforge.network.NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            throw new IllegalStateException("Missing C2S sender for trade message");
        }
        return sender.getUUID();
    }

    private static java.util.Optional<TradeService> service(
            net.minecraftforge.network.NetworkEvent.Context context
    ) {
        java.util.Optional<TradeService> resolved = TradeRuntime.resolve();
        if (resolved.isEmpty()) {
            LOGGER.warn(
                    "[Trade] Rejected C2S message for player {}: trade runtime unavailable",
                    context.getSender() == null ? "?" : context.getSender().getUUID()
            );
        }
        return resolved;
    }
}
