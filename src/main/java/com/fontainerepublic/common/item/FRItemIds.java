package com.fontainerepublic.common.item;

import net.minecraft.resources.ResourceLocation;

/**
 * Bootstrap-free communicator <em>carrier</em> identity constants
 * (FR-ITEM-002-A §4).
 *
 * <p>FR-ITEM-002-A removes the custom {@code fontainerepublic:communicator}
 * item registry entry (FR-ITEM-001-A §2.1) that made a Forge client without
 * FontaineRepublic unable to join the dedicated server. The Water Mirror is
 * now a {@linkplain #CARRIER_ITEM_ID vanilla {@code minecraft:clock} carrier}
 * recognized by a bounded, namespaced {@code FontaineRepublicDevice} NBT
 * compound plus an HMAC-SHA-256 {@code Signature}. A client marker, display
 * name, lore or {@code CustomModelData} alone is never an authoritative
 * device.</p>
 *
 * <p>These constants are kept dependency-free of the Forge item registry so
 * the plain-JVM foundation tests and the client UI gate can validate the
 * carrier contract without initializing Forge.</p>
 */
public final class FRItemIds {

    /** Resource path of the vanilla carrier item (a clock). */
    public static final String CARRIER_ITEM_ID = "clock";

    /** Full resource location of the vanilla carrier item. */
    public static final String CARRIER_ITEM_REGISTRY_NAME = "minecraft:" + CARRIER_ITEM_ID;

    /** {@link ResourceLocation} of the vanilla carrier item. */
    public static final ResourceLocation CARRIER_ITEM =
            ResourceLocation.fromNamespaceAndPath("minecraft", CARRIER_ITEM_ID);

    /** Top-level namespaced NBT compound key carrying the device fields. */
    public static final String DEVICE_TAG = "FontaineRepublicDevice";

    /** Displayed name of the issued Water Mirror (presentation only). */
    public static final String DISPLAY_NAME = "传讯水镜";

    /** Current device schema version written under {@code Schema}. */
    public static final int SCHEMA_VERSION = 1;

    /** Canonical device kind: the Message Water Mirror. */
    public static final String DEVICE_KIND = "message_water_mirror";

    /** Default {@code CustomModelData} used for the FR-client Water Mirror look. */
    public static final int CUSTOM_MODEL_DATA = 1;

    /** Maximum {@code KeyId} length in bytes (printable ASCII, 1..32). */
    public static final int MAX_KEY_ID_LENGTH = 32;

    /** Device {@code Signature} is exactly 32 raw HMAC-SHA-256 bytes. */
    public static final int SIGNATURE_LENGTH = 32;

    /** Maximum presentation name length in Unicode code points (64). */
    public static final int MAX_NAME_CODE_POINTS = 64;

    /** Maximum presentation lore line count (8). */
    public static final int MAX_LORE_LINES = 8;

    /** Maximum presentation lore line length in Unicode code points (128). */
    public static final int MAX_LORE_CODE_POINTS = 128;

    private FRItemIds() {
    }
}
