package com.fontainerepublic.common.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Common item registry for the Message Water Mirror (FR-ITEM-003).
 *
 * <p>Reversed FR-ITEM-002-A: the Water Mirror is again an independent custom
 * item {@code fontainerepublic:communicator}, registered through a common
 * {@link DeferredRegister} so both physical sides know the item. The FR client
 * is now required, so the registry-equality concern that motivated the
 * vanilla-clock carrier no longer applies. It is an ordinary unstackable
 * {@link Item} with no block/entity form and no signature or owner binding -
 * the item is a UX gate only; all authority stays server-side
 * (FR-ITEM-001-A &sect;3).</p>
 *
 * <p>This class holds no link to any {@code client/} code. Register it on the
 * common mod event bus before {@code FMLCommonSetupEvent}.</p>
 */
public final class FRItems {

    /** The mod item registry (register mod-event-bus before common setup). */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "fontainerepublic");

    /** The Message Water Mirror (传讯水镜). Unstackable, no signature. */
    public static final RegistryObject<Item> COMMUNICATOR =
            ITEMS.register(FRItemIds.COMMUNICATOR_ID,
                    () -> new Item(new Item.Properties().stacksTo(1)));

    private FRItems() {
    }
}
