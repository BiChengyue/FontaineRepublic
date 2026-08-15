package com.fontainerepublic.common.trade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.function.BiConsumer;

/**
 * Trade sound events (FR-TRADE-003-B container presentation).
 *
 * <p>Adapted from Secure Trade's {@code SecureTradeSounds} (MIT, Copyright
 * (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}). Registered under the
 * {@code fontainerepublic} namespace via the mod's
 * {@code DeferredRegister&lt;SoundEvent&gt;}; the registration is authorized by
 * FR-TRADE-003-A &sect;3 (Human decision 2026-08-15 to allow a custom
 * {@code MenuType} + {@code SoundEvent} so the 54-slot container UI can be
 * reused). The event identity + the {@code register} hook mirror upstream;
 * the namespace is the only change.</p>
 */
public final class TradeSounds {

    public static final ResourceLocation TRADE_ITEM_ADD_ID = id("trade_item_add");
    public static final ResourceLocation TRADE_ITEM_BLOCKED_ID = id("trade_item_blocked");
    public static final ResourceLocation TRADE_CANCEL_ID = id("trade_cancel");
    public static final ResourceLocation TRADE_COUNTDOWN_TICK_ID = id("trade_countdown_tick");
    public static final ResourceLocation TRADE_REQUEST_SENT_ID = id("trade_request_sent");
    public static final ResourceLocation TRADE_SUCCESS_ID = id("trade_success");

    public static final SoundEvent TRADE_ITEM_ADD = SoundEvent.createVariableRangeEvent(TRADE_ITEM_ADD_ID);
    public static final SoundEvent TRADE_ITEM_BLOCKED = SoundEvent.createVariableRangeEvent(TRADE_ITEM_BLOCKED_ID);
    public static final SoundEvent TRADE_CANCEL = SoundEvent.createVariableRangeEvent(TRADE_CANCEL_ID);
    public static final SoundEvent TRADE_COUNTDOWN_TICK = SoundEvent.createVariableRangeEvent(TRADE_COUNTDOWN_TICK_ID);
    public static final SoundEvent TRADE_REQUEST_SENT = SoundEvent.createVariableRangeEvent(TRADE_REQUEST_SENT_ID);
    public static final SoundEvent TRADE_SUCCESS = SoundEvent.createVariableRangeEvent(TRADE_SUCCESS_ID);

    private TradeSounds() {
    }

    public static void register(BiConsumer<ResourceLocation, SoundEvent> registrar) {
        registrar.accept(TRADE_ITEM_ADD_ID, TRADE_ITEM_ADD);
        registrar.accept(TRADE_ITEM_BLOCKED_ID, TRADE_ITEM_BLOCKED);
        registrar.accept(TRADE_CANCEL_ID, TRADE_CANCEL);
        registrar.accept(TRADE_COUNTDOWN_TICK_ID, TRADE_COUNTDOWN_TICK);
        registrar.accept(TRADE_REQUEST_SENT_ID, TRADE_REQUEST_SENT);
        registrar.accept(TRADE_SUCCESS_ID, TRADE_SUCCESS);
    }

    @SuppressWarnings("removal") // Forge 1.20.1 API surface (deprecated for removal on newer JDKs)
    private static ResourceLocation id(String path) {
        return new ResourceLocation("fontainerepublic", path);
    }
}
