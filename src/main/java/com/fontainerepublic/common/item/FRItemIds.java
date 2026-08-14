package com.fontainerepublic.common.item;

import com.fontainerepublic.FontaineRepublic;

/**
 * Bootstrap-free communicator identity constants (FR-ITEM-001-A 搂2.1).
 *
 * <p>Kept separate from {@link FRItems} so the plain-JVM foundation test
 * and the client UI gate can validate the item contract without initializing
 * the Forge item registry (which requires the ModLauncher bootstrap).</p>
 */
public final class FRItemIds {

    /** Registry path of the communicator item. */
    public static final String ITEM_ID = "communicator";

    /** Full registry name, as used by {@code /give @s ...}. */
    public static final String ITEM_REGISTRY_NAME =
            FontaineRepublic.MOD_ID + ":" + ITEM_ID;

    /** Language key of the display name (vanilla item default lookup). */
    public static final String ITEM_LANG_KEY =
            "item." + FontaineRepublic.MOD_ID + "." + ITEM_ID;

    /** The communicator stacks to 1 (a personal device, not a stack). */
    public static final int MAX_STACK = 1;

    private FRItemIds() {
    }
}
