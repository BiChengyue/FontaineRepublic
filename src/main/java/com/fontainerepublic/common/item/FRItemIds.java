package com.fontainerepublic.common.item;

import net.minecraft.resources.ResourceLocation;

/**
 * Bootstrap-free communicator item identity constants (FR-ITEM-003).
 *
 * <p>Reverses FR-ITEM-002-A: the Message Water Mirror is again an independent
 * custom {@code fontainerepublic:communicator} item registered through a
 * common {@code DeferredRegister&lt;Item&gt;} (see {@link FRItems}). The
 * vanilla-clock HMAC carrier and its {@code FontaineRepublicDevice} NBT
 * contract are removed - the FR client is now required, and the Water Mirror
 * is an ordinary unstackable item with no signature or owner binding
 * (FR-ITEM-001-A &sect;2.1).</p>
 *
 * <p>These constants stay dependency-free of the Forge item registry so the
 * plain-JVM foundation tests can validate the item contract without
 * initializing Forge.</p>
 */
public final class FRItemIds {

    /** Resource path of the communicator item. */
    public static final String COMMUNICATOR_ID = "communicator";

    /** Full resource location of the communicator item. */
    public static final String COMMUNICATOR_REGISTRY_NAME =
            "fontainerepublic:" + COMMUNICATOR_ID;

    /** {@link ResourceLocation} of the communicator item. */
    public static final ResourceLocation COMMUNICATOR_ITEM =
            ResourceLocation.fromNamespaceAndPath("fontainerepublic", COMMUNICATOR_ID);

    /** Displayed name of the Water Mirror (lang files localize it). */
    public static final String DISPLAY_NAME = "传讯水镜";

    private FRItemIds() {
    }
}
