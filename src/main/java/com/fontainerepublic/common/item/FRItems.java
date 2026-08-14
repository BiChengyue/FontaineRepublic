package com.fontainerepublic.common.item;

import com.fontainerepublic.FontaineRepublic;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * FR item registry (FR-ITEM-001-A 搂2.1).
 *
 * <p>The {@code communicator} is the in-world entry item of the FR client
 * visual UI: it is a plain {@link Item} (no block/entity), stacks to 1, and
 * uses a temporary vanilla {@code minecraft:item/clock} texture until a
 * custom Fontainian texture replaces it. The item is registered on the mod
 * event bus (both physical sides know it) so {@code /give @s
 * fontainerepublic:communicator} works on a dedicated server as well.</p>
 *
 * <p>The item is a UI entry only: it carries no authoritative side effect.
 * Every action still flows through the server command surface; a client
 * without the item (or without the FR client at all) keeps full
 * no-client parity (FR-ITEM-001-A 搂3). Identity constants live in the
 * bootstrap-free {@link FRItemIds} for plain-JVM testability.</p>
 */
public final class FRItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS,
            FontaineRepublic.MOD_ID
    );

    /** The portable communicator item (FR-ITEM-001-A 搂2.1). */
    public static final RegistryObject<Item> COMMUNICATOR = ITEMS.register(
            FRItemIds.ITEM_ID,
            () -> new Item(new Item.Properties().stacksTo(FRItemIds.MAX_STACK))
    );

    private FRItems() {
    }

    /** Attaches the deferred registry to the mod event bus (called from the
     * mod constructor before FMLCommonSetup). */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
