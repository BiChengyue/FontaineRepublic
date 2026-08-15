package com.fontainerepublic.trade.securetrade.platform;

import java.util.Objects;

/**
 * Platform-helper locator (FR-TRADE-004).
 *
 * <p>Upstream Navielon/SecureTrade loads {@code IPlatformHelper} through
 * {@link java.util.ServiceLoader} (see upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}) and exposes it as the
 * public static field {@code Services.PLATFORM}. FR is a single-module Forge
 * build with no multi-loader split, so the helper is bound directly by the FR
 * mod bootstrap ({@code ForgePlatformHelper}) instead of via a
 * {@code META-INF/services} provider file. This is a documented, minimal
 * deviation: the {@code PLATFORM} field contract is otherwise unchanged (MIT
 * attribution retained).
 */
public final class Services {

    public static volatile IPlatformHelper PLATFORM;

    private Services() {
    }

    public static void bind(IPlatformHelper helper) {
        PLATFORM = Objects.requireNonNull(helper, "helper");
    }
}
