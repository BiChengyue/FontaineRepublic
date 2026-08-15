package com.fontainerepublic.trade.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeStateSyncPacket} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors), extended by FR-TRADE-004 with
 * the two-sided money fields ({@link #myMoney}/{@link #otherMoney}).
 */
public class TradeStateSyncPacket {
    private final boolean myLock;
    private final boolean otherLock;
    private final int countdownSeconds;
    private final long myXP;
    private final long otherXP;
    private final long otherTotalXP;
    private final String partnerName;
    private final long myMoney;
    private final long otherMoney;

    public TradeStateSyncPacket(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName, long myMoney, long otherMoney) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
        this.otherTotalXP = otherTotalXP;
        this.partnerName = partnerName;
        this.myMoney = myMoney;
        this.otherMoney = otherMoney;
    }

    public TradeStateSyncPacket(FriendlyByteBuf buf) {
        this.myLock = buf.readBoolean();
        this.otherLock = buf.readBoolean();
        this.countdownSeconds = buf.readVarInt();
        this.myXP = buf.readVarLong();
        this.otherXP = buf.readVarLong();
        this.otherTotalXP = buf.readVarLong();
        this.partnerName = buf.readUtf();
        this.myMoney = buf.readVarLong();
        this.otherMoney = buf.readVarLong();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.myLock);
        buf.writeBoolean(this.otherLock);
        buf.writeVarInt(this.countdownSeconds);
        buf.writeVarLong(this.myXP);
        buf.writeVarLong(this.otherXP);
        buf.writeVarLong(this.otherTotalXP);
        buf.writeUtf(this.partnerName);
        buf.writeVarLong(this.myMoney);
        buf.writeVarLong(this.otherMoney);
    }

    public boolean myLock() { return myLock; }
    public boolean otherLock() { return otherLock; }
    public int countdownSeconds() { return countdownSeconds; }
    public long myXP() { return myXP; }
    public long otherXP() { return otherXP; }
    public long otherTotalXP() { return otherTotalXP; }
    public String partnerName() { return partnerName; }
    public long myMoney() { return myMoney; }
    public long otherMoney() { return otherMoney; }
}
