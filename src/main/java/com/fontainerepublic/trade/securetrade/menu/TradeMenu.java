package com.fontainerepublic.trade.securetrade.menu;

import com.fontainerepublic.trade.securetrade.platform.Services;
import com.fontainerepublic.trade.securetrade.TradeItemValidator;
import com.fontainerepublic.trade.securetrade.SecureTradeSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeMenu} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors), extended by FR-TRADE-004 with
 * per-viewer money offer fields ({@link #myMoney}/{@link #otherMoney}) and the
 * {@link #setOfferedMoney} C2S hop.
 */
public class TradeMenu extends AbstractContainerMenu {
    public final TradeSession session;
    public final boolean isPlayer1;
    public boolean myLock = false;
    public boolean otherLock = false;
    public int countdownSeconds = -1;
    public long myXP = 0;
    public long otherXP = 0;
    public long otherTotalXP = 0;
    public long myMoney = 0;
    public long otherMoney = 0;
    public String partnerName = "";
    private long blacklistWarningUntilMillis = 0L;

    private final Container myContainer;
    private final Container otherContainer;
    private long lastBlacklistNotificationMillis = 0L;
    private long lastLocalItemAddSoundMillis = 0L;
    private long lastLocalBlacklistSoundMillis = 0L;

    public static final int TRADE_SLOTS_COUNT = 27; // 9x3

    public static final int MY_SLOTS_START = 0;
    public static final int OTHER_SLOTS_START = MY_SLOTS_START + TRADE_SLOTS_COUNT; // 27
    public static final int INV_SLOTS_START = OTHER_SLOTS_START + TRADE_SLOTS_COUNT; // 54
    public static final int HOTBAR_SLOTS_START = INV_SLOTS_START + 27; // 81

    public TradeMenu(int containerId, Inventory playerInventory) {
        super(TradeMenuType.get(), containerId);
        this.session = null;
        this.isPlayer1 = true;
        this.myContainer = new SimpleContainer(TRADE_SLOTS_COUNT);
        this.otherContainer = new SimpleContainer(TRADE_SLOTS_COUNT);
        setupSlots(playerInventory);
    }

    public TradeMenu(int containerId, Inventory playerInventory, TradeSession session, boolean isPlayer1) {
        super(TradeMenuType.get(), containerId);
        this.session = session;
        this.isPlayer1 = isPlayer1;
        this.myContainer = isPlayer1 ? session.inventory1 : session.inventory2;
        this.otherContainer = isPlayer1 ? session.inventory2 : session.inventory1;
        setupSlots(playerInventory);
    }

    private void setupSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(myContainer, col + row * 9, 8 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !stack.isEmpty() && !isBlacklisted(stack);
                    }
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        if (session != null) session.onItemsChanged();
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(otherContainer, col + row * 9, 188 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                    @Override
                    public boolean mayPickup(Player playerIn) {
                        return false;
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 98 + col * 18, 126 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 98 + col * 18, 184));
        }
    }

    private boolean isBlacklisted(ItemStack stack) {
        return TradeItemValidator.containsBlacklistedItem(stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= MY_SLOTS_START && slotId < OTHER_SLOTS_START) {
            ItemStack attemptedStack = getAttemptedPlacementStack(button, clickType, player);
            if (!attemptedStack.isEmpty() && isBlacklisted(attemptedStack)) {
                notifyBlacklisted(player);
                return;
            }
            if (shouldPlayLocalItemAddSound(slotId, attemptedStack, clickType, player)) {
                playLocalItemAddSound(player);
            }
            super.clicked(slotId, button, clickType, player);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private ItemStack getAttemptedPlacementStack(int button, ClickType clickType, Player player) {
        if (clickType == ClickType.SWAP && button >= 0 && button < player.getInventory().getContainerSize()) {
            return player.getInventory().getItem(button);
        }
        return getCarried();
    }

    private void notifyBlacklisted(Player player) {
        if (player.level().isClientSide()) {
            showBlacklistWarning();
            playLocalBlacklistSound(player);
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBlacklistNotificationMillis < 250L) {
            return;
        }
        lastBlacklistNotificationMillis = now;
        serverPlayer.playNotifySound(SecureTradeSounds.TRADE_ITEM_BLOCKED, SoundSource.MASTER, 0.9f, 1.0f);
        Services.PLATFORM.sendBlacklistWarning(serverPlayer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (index >= OTHER_SLOTS_START && index < INV_SLOTS_START) {
            return ItemStack.EMPTY;
        }

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < OTHER_SLOTS_START) {
                if (!this.moveItemStackTo(itemstack1, INV_SLOTS_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }
            else {
                if (isBlacklisted(itemstack1)) {
                    notifyBlacklisted(player);
                    return ItemStack.EMPTY;
                }
                if (!this.moveItemStackTo(itemstack1, MY_SLOTS_START, OTHER_SLOTS_START, false)) {
                    return ItemStack.EMPTY;
                }
                playLocalItemAddSound(player);
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }


    @Override
    public void removed(Player player) {
        super.removed(player);
        if (session != null && !player.level().isClientSide()) {
            session.cancelTrade();
        }
    }

    public void setLocked(Player player, boolean locked) {
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            session.setLocked(serverPlayer, locked);
        }
    }

    public void setOfferedXP(Player player, long xp) {
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            session.setOfferedXP(serverPlayer, xp);
        }
    }

    public void setOfferedMoney(Player player, long money) {
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            session.setOfferedMoney(serverPlayer, money);
        }
    }

    public void updateFields(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName, long myMoney, long otherMoney) {
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

    public void syncToClient(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName, long myMoney, long otherMoney) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
        this.otherTotalXP = otherTotalXP;
        this.partnerName = partnerName;
        this.myMoney = myMoney;
        this.otherMoney = otherMoney;
        if (session != null) {
            ServerPlayer myPlayer = isPlayer1 ? session.player1 : session.player2;
            Services.PLATFORM.sendStateSync(myPlayer, myLock, otherLock, countdownSeconds, myXP, otherXP, otherTotalXP, partnerName, myMoney, otherMoney);
        }
    }

    public void showBlacklistWarning() {
        this.blacklistWarningUntilMillis = System.currentTimeMillis() + 2200L;
    }

    private boolean shouldPlayLocalItemAddSound(int slotId, ItemStack attemptedStack, ClickType clickType, Player player) {
        if (!player.level().isClientSide() || attemptedStack.isEmpty()) {
            return false;
        }
        if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_CRAFT && clickType != ClickType.SWAP) {
            return false;
        }
        Slot slot = this.slots.get(slotId);
        if (!slot.mayPlace(attemptedStack)) {
            return false;
        }

        ItemStack current = slot.getItem();
        return current.isEmpty()
                || !ItemStack.isSameItemSameTags(current, attemptedStack)
                || current.getCount() < Math.min(current.getMaxStackSize(), slot.getMaxStackSize());
    }

    private void playLocalItemAddSound(Player player) {
        if (!player.level().isClientSide()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLocalItemAddSoundMillis < 45L) {
            return;
        }
        lastLocalItemAddSoundMillis = now;
        player.playSound(SecureTradeSounds.TRADE_ITEM_ADD, 0.45f, 1.0f);
    }

    private void playLocalBlacklistSound(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastLocalBlacklistSoundMillis < 120L) {
            return;
        }
        lastLocalBlacklistSoundMillis = now;
        player.playSound(SecureTradeSounds.TRADE_ITEM_BLOCKED, 0.85f, 1.0f);
    }

    public long getBlacklistWarningRemainingMillis() {
        return Math.max(0L, this.blacklistWarningUntilMillis - System.currentTimeMillis());
    }

    public static void openTrade(ServerPlayer player1, ServerPlayer player2) {
        TradeSession session = new TradeSession(player1, player2);

        player1.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("securetrade.gui.trade_with", player2.getScoreboardName());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new TradeMenu(id, inv, session, true);
            }
        });

        player2.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("securetrade.gui.trade_with", player1.getScoreboardName());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new TradeMenu(id, inv, session, false);
            }
        });

        session.syncState();
    }
}
