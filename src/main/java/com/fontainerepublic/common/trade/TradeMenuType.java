package com.fontainerepublic.common.trade;

import net.minecraft.world.inventory.MenuType;

/**
 * Holder for the custom trade container type (FR-TRADE-003-B).
 *
 * <p>Adapted from Secure Trade's {@code TradeMenuType} (MIT, Copyright (c)
 * 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}). The mod class builds the
 * {@link MenuType} through a {@code DeferredRegister&lt;MenuType&lt;?&gt;&gt;}
 * (authorized by FR-TRADE-003-A &sect;3) and installs it here so the
 * common-package {@link TradeMenu} constructor can resolve it without
 * importing a registry bootstrap class. Guards against double registration
 * mirror upstream.</p>
 */
public final class TradeMenuType {

    private static MenuType<TradeMenu> tradeMenu;

    private TradeMenuType() {
    }

    public static synchronized void set(MenuType<TradeMenu> menuType) {
        if (menuType == null) {
            throw new IllegalArgumentException("Trade menu type cannot be null");
        }
        if (tradeMenu != null) {
            throw new IllegalStateException("Trade menu type is already registered");
        }
        tradeMenu = menuType;
    }

    public static MenuType<TradeMenu> get() {
        if (tradeMenu == null) {
            throw new IllegalStateException("Trade menu type has not been registered yet");
        }
        return tradeMenu;
    }

    /** True once the mod class installed the menu type. */
    public static synchronized boolean isRegistered() {
        return tradeMenu != null;
    }
}
