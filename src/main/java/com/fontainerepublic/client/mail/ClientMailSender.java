package com.fontainerepublic.client.mail;

import com.fontainerepublic.common.mail.MailBroadcastPacket;
import com.fontainerepublic.common.mail.MailDeletePacket;
import com.fontainerepublic.common.mail.MailListRequestPacket;
import com.fontainerepublic.common.mail.MailReadPacket;
import com.fontainerepublic.common.mail.MailSendPacket;
import com.fontainerepublic.common.network.NetworkBootstrap;

import java.util.List;

/**
 * Client-side C2S sender of the mail ledger (FR-MAIL-001-A §3, message ledger
 * IDs 16-19, 22). Thin transport helpers; the server re-runs every authority
 * rule. This class lives in {@code client/} and is never loaded by a dedicated
 * server.
 */
public final class ClientMailSender {

    private ClientMailSender() {
    }

    public static void send(
            String to,
            String subject,
            String body,
            long moneyAttachment,
            List<Integer> itemSlotIndices
    ) {
        NetworkBootstrap.instance().sendToServer(
                new MailSendPacket(to, subject, body, moneyAttachment, itemSlotIndices)
        );
    }

    public static void requestList() {
        NetworkBootstrap.instance().sendToServer(
                new MailListRequestPacket(MailListRequestPacket.MAX_LIMIT)
        );
    }

    public static void read(long mailId) {
        NetworkBootstrap.instance().sendToServer(new MailReadPacket(mailId));
    }

    public static void delete(long mailId) {
        NetworkBootstrap.instance().sendToServer(new MailDeletePacket(mailId));
    }

    public static void broadcast(String subject, String body) {
        NetworkBootstrap.instance().sendToServer(new MailBroadcastPacket(subject, body));
    }
}
