package com.fontainerepublic.trade.securetrade.network;

import com.fontainerepublic.trade.securetrade.menu.TradeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeNetwork} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors), extended by FR-TRADE-004 with
 * the money-change C2S packet.
 *
 * <p>The escrow network lives on its own sub-channel
 * ({@code fontainerepublic:trade}) with its own protocol version, so the
 * verbatim Secure Trade handlers (which operate directly on the server-side
 * {@link TradeMenu}/{@code TradeSession}) stay self-contained. The FR main
 * {@code fontainerepublic:main} ledger and its rate-limit machinery are
 * untouched, so the FR protocol version does not change for this port.</p>
 */
public class TradeNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("fontainerepublic", "trade"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(TradeLockPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TradeLockPacket::new)
                .encoder(TradeLockPacket::write)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null && player.containerMenu instanceof TradeMenu tradeMenu) {
                        tradeMenu.setLocked(player, msg.locked());
                    }
                })
                .add();

        CHANNEL.messageBuilder(TradeXPChangePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TradeXPChangePacket::new)
                .encoder(TradeXPChangePacket::write)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null && player.containerMenu instanceof TradeMenu tradeMenu) {
                        tradeMenu.setOfferedXP(player, msg.xpPoints());
                    }
                })
                .add();

        CHANNEL.messageBuilder(TradeMoneyChangePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TradeMoneyChangePacket::new)
                .encoder(TradeMoneyChangePacket::write)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null && player.containerMenu instanceof TradeMenu tradeMenu) {
                        tradeMenu.setOfferedMoney(player, msg.moneyAmount());
                    }
                })
                .add();

        CHANNEL.messageBuilder(TradeStateSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TradeStateSyncPacket::new)
                .encoder(TradeStateSyncPacket::write)
                .consumerMainThread((msg, ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.containerMenu instanceof TradeMenu tradeMenu) {
                        tradeMenu.updateFields(msg.myLock(), msg.otherLock(), msg.countdownSeconds(), msg.myXP(), msg.otherXP(), msg.otherTotalXP(), msg.partnerName(), msg.myMoney(), msg.otherMoney());
                    }
                })
                .add();

        CHANNEL.messageBuilder(TradeBlacklistWarningPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TradeBlacklistWarningPacket::new)
                .encoder(TradeBlacklistWarningPacket::write)
                .consumerMainThread((msg, ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.containerMenu instanceof TradeMenu tradeMenu) {
                        tradeMenu.showBlacklistWarning();
                    }
                })
                .add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
