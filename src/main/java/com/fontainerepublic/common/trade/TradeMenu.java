package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.display.TradeStateSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared 54-slot trade container (FR-TRADE-003-B).
 *
 * <p>Adapted from Secure Trade's {@code TradeMenu} (MIT, Copyright (c) 2026
 * Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}). The screen layout (two
 * 27-slot offer regions + 36 player-inventory slots = 90 total slot indices)
 * is preserved; the authority model is FR's intent model, not upstream's
 * escrow model.</p>
 *
 * <p><b>Authority (the key adaptation):</b> upstream's 27-slot offer region is
 * an escrow {@code SimpleContainer} into which items physically move. FR
 * forbids that: offered items never leave the owner's inventory before the
 * atomic settlement (the intent model), and every offer / agreement / money /
 * XP decision is owned by {@code com.fontainerepublic.server.trade}.
 * Therefore this menu's two offer regions are <em>phantom display
 * containers</em>: the server repopulates them from the authoritative
 * {@link TradeStateSyncPacket} projection after every state change, and they
 * are read-only to both parties (placement and pickup are rejected). Offers
 * are submitted by clicking the player's own main-inventory region in the
 * attached screen, which sends the bounded {@code TradeOfferItemPacket}
 * (C2S); the server re-validates the source slot, updates the session, and
 * pushes a fresh snapshot that repaints this container. Nothing is ever
 * removed from a player's real inventory outside the atomic settlement.</p>
 *
 * <p>The FR intent model carries exactly
 * {@link TradeOfferItemPacket#SLOT_COUNT} (4) offer slots per side, while the
 * Secure Trade layout draws 27. To keep the 54-slot layout visually intact
 * while remaining faithful to the 4-slot authority, the first 4 cells of each
 * offer region are live (bound to the FR offers) and the remaining 23 are
 * inert (empty, always rejected). This is a deliberate, documented bridge; a
 * future expansion of the offer slot count can light up the rest of the
 * region without a layout change.</p>
 */
public class TradeMenu extends AbstractContainerMenu {

    public static final int TRADE_SLOTS_COUNT = 27; // 9x3, Secure Trade layout

    public static final int MY_SLOTS_START = 0;
    public static final int OTHER_SLOTS_START = MY_SLOTS_START + TRADE_SLOTS_COUNT; // 27
    public static final int INV_SLOTS_START = OTHER_SLOTS_START + TRADE_SLOTS_COUNT; // 54
    public static final int HOTBAR_SLOTS_START = INV_SLOTS_START + 27; // 81

    /** Number of live FR intent offer slots exposed per region. */
    public static final int LIVE_OFFER_SLOTS = TradeOfferItemPacket.SLOT_COUNT;

    // Per-viewer display state, last set by the S2C snapshot.
    private volatile int phase = TradeStateSyncPacket.PHASE_REQUESTED;
    private volatile long ownMoney;
    private volatile long otherMoney;
    private volatile long ownXp;
    private volatile long otherXp;
    private volatile boolean ownAgree;
    private volatile boolean otherAgree;
    private volatile int countdownSeconds;
    private volatile String partnerName = "";

    private final Container myRegion;
    private final Container otherRegion;

    public TradeMenu(int containerId, Inventory playerInventory) {
        super(TradeMenuType.get(), containerId);
        this.myRegion = new SimpleContainer(TRADE_SLOTS_COUNT);
        this.otherRegion = new SimpleContainer(TRADE_SLOTS_COUNT);
        setupSlots(playerInventory);
    }

    private void setupSlots(Inventory playerInventory) {
        // Own offer region (rows 0..2): phantom display; only the first
        // LIVE_OFFER_SLOTS are ever populated by the server snapshot.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new ReadOnlyOfferSlot(
                        myRegion, col + row * 9, 8 + col * 18, 17 + row * 18
                ));
            }
        }
        // Counterparty offer region (rows 0..2): always read-only.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new ReadOnlyOfferSlot(
                        otherRegion, col + row * 9, 188 + col * 18, 17 + row * 18
                ));
            }
        }
        // Player main inventory (rows 0..2) + hotbar (row 3): the real
        // inventory; clicking these cells submits an item offer intent.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 98 + col * 18, 126 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 98 + col * 18, 184));
        }
    }

    /** Rejects placement and pickup: phantom server-owned display region. */
    private static final class ReadOnlyOfferSlot extends Slot {
        ReadOnlyOfferSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player playerIn) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // per-viewer snapshot projection (driven by the S2C TradeStateSyncPacket)
    // ------------------------------------------------------------------

    /**
     * Repaints the two phantom offer regions from the authoritative snapshot.
     * Called by the client-bound sync handler (and immediately at open time)
     * so the container always mirrors the server session exactly.
     */
    public void applySnapshot(TradeStateSyncPacket snapshot) {
        this.phase = snapshot.phase();
        this.ownMoney = snapshot.ownMoney();
        this.otherMoney = snapshot.otherMoney();
        this.ownXp = snapshot.ownXp();
        this.otherXp = snapshot.otherXp();
        this.ownAgree = snapshot.ownAgree();
        this.otherAgree = snapshot.otherAgree();
        this.countdownSeconds = snapshot.countdownSeconds();
        writeOfferRegion(myRegion, snapshot.ownItems());
        writeOfferRegion(otherRegion, snapshot.otherItems());
    }

    private static void writeOfferRegion(Container region, List<ItemStack> items) {
        for (int i = 0; i < TRADE_SLOTS_COUNT; i++) {
            ItemStack stack = (i < items.size()) ? items.get(i).copy() : ItemStack.EMPTY;
            region.setItem(i, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    // Per-viewer getters used by the attached screen.
    public int phase() { return phase; }
    public long ownMoney() { return ownMoney; }
    public long otherMoney() { return otherMoney; }
    public long ownXp() { return ownXp; }
    public long otherXp() { return otherXp; }
    public boolean ownAgree() { return ownAgree; }
    public boolean otherAgree() { return otherAgree; }
    public int countdownSeconds() { return countdownSeconds; }
    public String partnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The offer regions are phantom; quick-move flows back out of the
        // offer region into the player inventory and otherwise is a no-op.
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();
            if (index < OTHER_SLOTS_START) {
                // own offer -> player inventory (display refresh only).
                if (!this.moveItemStackTo(slotStack, HOTBAR_SLOTS_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < INV_SLOTS_START) {
                // other offer: read-only, move nothing.
                return ItemStack.EMPTY;
            } else {
                // player inventory -> no phantom destination: no-op.
                return ItemStack.EMPTY;
            }
            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    /**
     * Opens the shared container for both parties of an OPEN/REQUESTED
     * session. Each player gets their own menu/view over the same
     * authoritative session; the per-viewer offer content is supplied by the
     * server snapshot, never by container mutation.
     */
    public static void openForPlayers(
            ServerPlayer initiator,
            ServerPlayer partner,
            int initiatorSlotBias,
            int partnerSlotBias
    ) {
        // Both players open the same MenuType; each menu is per-viewer and is
        // populated by the TradeStateSyncPacket pushed right after open.
        initiator.openMenu(new TradeMenuProvider(partner.getScoreboardName()));
        partner.openMenu(new TradeMenuProvider(initiator.getScoreboardName()));
    }

    private static final class TradeMenuProvider
            implements net.minecraft.world.MenuProvider {
        private final String otherName;

        TradeMenuProvider(String otherName) {
            this.otherName = otherName;
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("fontainerepublic.gui.trade_with", otherName);
        }

        @Override
        public AbstractContainerMenu createMenu(
                int id, Inventory inv, Player player
        ) {
            TradeMenu menu = new TradeMenu(id, inv);
            menu.setPartnerName(otherName);
            return menu;
        }
    }
}
