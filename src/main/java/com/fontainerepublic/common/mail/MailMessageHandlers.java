package com.fontainerepublic.common.mail;

import com.fontainerepublic.common.network.NetworkMessageHandler;
import com.fontainerepublic.server.mail.MailRuntime;
import com.fontainerepublic.server.mail.api.MailService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Receiving-side handlers of the C2S mail ledger (FR-MAIL-001-A §3,
 * message ledger IDs 16-19, 22). The active {@link MailService} is resolved per
 * invocation through the static {@link MailRuntime} locator (bound at runtime
 * start, unbound at shutdown). The sender is taken from the connection context;
 * a missing runtime is a silent best-effort no-op.
 */
public final class MailMessageHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MailMessageHandlers() {
    }

    public static NetworkMessageHandler<MailSendPacket> send() {
        return (message, context) -> service(context).ifPresent(service ->
                service.send(
                        sender(context),
                        message.to(),
                        message.subject(),
                        message.body(),
                        message.moneyAttachment(),
                        message.itemSlotIndices()
                ));
    }

    public static NetworkMessageHandler<MailListRequestPacket> list() {
        return (message, context) -> service(context).ifPresent(service ->
                service.requestSync(sender(context)));
    }

    public static NetworkMessageHandler<MailReadPacket> read() {
        return (message, context) -> service(context).ifPresent(service ->
                service.read(sender(context), message.mailId()));
    }

    public static NetworkMessageHandler<MailDeletePacket> delete() {
        return (message, context) -> service(context).ifPresent(service ->
                service.delete(sender(context), message.mailId()));
    }

    public static NetworkMessageHandler<MailBroadcastPacket> broadcast() {
        return (message, context) -> service(context).ifPresent(service ->
                service.broadcast(sender(context), message.subject(), message.body()));
    }

    private static java.util.UUID sender(net.minecraftforge.network.NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            throw new IllegalStateException("Missing C2S sender for mail message");
        }
        return sender.getUUID();
    }

    private static java.util.Optional<MailService> service(
            net.minecraftforge.network.NetworkEvent.Context context
    ) {
        java.util.Optional<MailService> resolved = MailRuntime.resolve();
        if (resolved.isEmpty()) {
            LOGGER.warn(
                    "[Mail] Rejected C2S message for player {}: mail runtime unavailable",
                    context.getSender() == null ? "?" : context.getSender().getUUID()
            );
        }
        return resolved;
    }
}
