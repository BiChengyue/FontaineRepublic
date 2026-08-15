package com.fontainerepublic.trade.securetrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.fontainerepublic.trade.securetrade.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeHistoryManager} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}).
 */
public class TradeHistoryManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class TradeEntry {
        public long timestamp;
        public String senderName;
        public String senderUuid;
        public String targetName;
        public String targetUuid;
        public List<ItemInfo> senderItems;
        public List<ItemInfo> targetItems;
        public long senderXP;
        public long targetXP;
        public long senderMoney;
        public long targetMoney;
    }

    public static class ItemInfo {
        public String id;
        public int count;
        public String displayName;
        public String stackData;

        public ItemInfo(String id, int count, String displayName, String stackData) {
            this.id = id;
            this.count = count;
            this.displayName = displayName;
            this.stackData = stackData;
        }
    }

    public static void recordTrade(ServerPlayer p1, ServerPlayer p2, net.minecraft.world.SimpleContainer inv1, net.minecraft.world.SimpleContainer inv2, long p1XP, long p2XP, long p1Money, long p2Money) {
        try {
            MinecraftServer server = p1.getServer();
            if (server == null) return;

            Path historyFile = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-history.json");
            List<TradeEntry> history = loadHistory(historyFile);

            TradeEntry entry = new TradeEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.senderName = p1.getScoreboardName();
            entry.senderUuid = p1.getUUID().toString();
            entry.targetName = p2.getScoreboardName();
            entry.targetUuid = p2.getUUID().toString();
            entry.senderItems = getItemsList(inv1, server.registryAccess());
            entry.targetItems = getItemsList(inv2, server.registryAccess());
            entry.senderXP = p1XP;
            entry.targetXP = p2XP;
            entry.senderMoney = p1Money;
            entry.targetMoney = p2Money;

            history.add(0, entry);

            int limit = Math.max(100, Services.PLATFORM.getMaxHistoryEntries() * 10);
            while (history.size() > limit) {
                history.remove(history.size() - 1);
            }

            saveHistory(historyFile, history);
        } catch (Exception e) {
            TradeLogger.log("Failed to record trade history: " + e.getMessage());
        }
    }

    private static List<ItemInfo> getItemsList(net.minecraft.world.SimpleContainer container, HolderLookup.Provider registries) {
        List<ItemInfo> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int count = stack.getCount();
                String displayName = stack.getHoverName().getString();
                ItemStack stackCopy = stack.copy();
                stackCopy.setCount(1);
                String stackData = stackCopy.save(new CompoundTag()).toString();
                list.add(new ItemInfo(id, count, displayName, stackData));
            }
        }
        return list;
    }

    private static List<TradeEntry> loadHistory(Path path) {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            List<TradeEntry> list = GSON.fromJson(reader, new TypeToken<ArrayList<TradeEntry>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            TradeLogger.log("Failed to load trade history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void saveHistory(Path path, List<TradeEntry> history) {
        try {
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(history, writer);
            }
        } catch (Exception e) {
            TradeLogger.log("Failed to save trade history: " + e.getMessage());
        }
    }

    public static void showHistory(ServerPlayer player) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Path historyFile = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-history.json");
            List<TradeEntry> history = loadHistory(historyFile);

            String playerUuid = player.getUUID().toString();
            List<TradeEntry> playerHistory = new ArrayList<>();
            for (TradeEntry entry : history) {
                if (playerUuid.equals(entry.senderUuid) || playerUuid.equals(entry.targetUuid)) {
                    playerHistory.add(entry);
                }
            }

            int maxEntries = Services.PLATFORM.getMaxHistoryEntries();
            int toShow = Math.min(maxEntries, playerHistory.size());

            if (toShow == 0) {
                player.sendSystemMessage(Component.translatable("securetrade.history.empty").withStyle(ChatFormatting.GRAY));
                return;
            }

            player.sendSystemMessage(Component.translatable("securetrade.history.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (int i = 0; i < toShow; i++) {
                TradeEntry entry = playerHistory.get(i);

                String otherName = playerUuid.equals(entry.senderUuid) ? entry.targetName : entry.senderName;

                Component otherNameComponent = Component.literal(otherName).withStyle(ChatFormatting.AQUA);
                player.sendSystemMessage(Component.translatable("securetrade.history.entry", i + 1, otherNameComponent).withStyle(ChatFormatting.GRAY));

                List<ItemInfo> gaveItems = playerUuid.equals(entry.senderUuid) ? entry.senderItems : entry.targetItems;
                List<ItemInfo> receivedItems = playerUuid.equals(entry.senderUuid) ? entry.targetItems : entry.senderItems;
                long gaveXP = playerUuid.equals(entry.senderUuid) ? entry.senderXP : entry.targetXP;
                long receivedXP = playerUuid.equals(entry.senderUuid) ? entry.targetXP : entry.senderXP;
                long gaveMoney = playerUuid.equals(entry.senderUuid) ? entry.senderMoney : entry.targetMoney;
                long receivedMoney = playerUuid.equals(entry.senderUuid) ? entry.targetMoney : entry.senderMoney;

                Component gaveComponent = formatItemsXpMoney(gaveItems, gaveXP, gaveMoney, server.registryAccess());
                player.sendSystemMessage(Component.translatable("securetrade.history.gave", gaveComponent).withStyle(ChatFormatting.RED));

                Component receivedComponent = formatItemsXpMoney(receivedItems, receivedXP, receivedMoney, server.registryAccess());
                player.sendSystemMessage(Component.translatable("securetrade.history.received", receivedComponent).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("securetrade.history.error", e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private static Component formatItemsXpMoney(List<ItemInfo> items, long xp, long money, HolderLookup.Provider registries) {
        boolean hasItems = items != null && !items.isEmpty();
        if (!hasItems && xp <= 0 && money <= 0) {
            return Component.translatable("securetrade.history.nothing").withStyle(ChatFormatting.GRAY);
        }

        MutableComponent result = Component.empty();
        boolean hasContent = false;

        if (hasItems) {
            for (ItemInfo item : aggregateItems(items)) {
                if (hasContent) {
                    result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                result.append(formatItem(item, registries));
                hasContent = true;
            }
        }

        if (xp > 0) {
            if (hasContent) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            result.append(Component.translatable("securetrade.history.xp_amount", xp).withStyle(ChatFormatting.AQUA));
            hasContent = true;
        }

        if (money > 0) {
            if (hasContent) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            result.append(Component.translatable("securetrade.history.money_amount", money).withStyle(ChatFormatting.GOLD));
        }

        return result;
    }

    private static List<ItemInfo> aggregateItems(List<ItemInfo> items) {
        Map<String, ItemInfo> aggregated = new LinkedHashMap<>();
        for (ItemInfo item : items) {
            if (item == null || item.id == null) {
                continue;
            }

            String key = getAggregationKey(item);
            ItemInfo existing = aggregated.get(key);
            if (existing == null) {
                aggregated.put(key, new ItemInfo(item.id, item.count, item.displayName, item.stackData));
            } else {
                existing.count = (int) Math.min(Integer.MAX_VALUE, (long) existing.count + item.count);
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    private static String getAggregationKey(ItemInfo item) {
        if (item.stackData != null && !item.stackData.isBlank()) {
            return item.stackData;
        }
        return item.id + "\u0000" + (item.displayName == null ? "" : item.displayName);
    }

    private static Component formatItem(ItemInfo item, HolderLookup.Provider registries) {
        ItemStack hoverStack = resolveItemStack(item, registries);
        MutableComponent itemName = (hoverStack.isEmpty() ? resolveItemName(item) : hoverStack.getHoverName().copy())
                .withStyle(ChatFormatting.YELLOW);
        MutableComponent result = Component.empty()
                .append(Component.literal(item.count + "x ").withStyle(ChatFormatting.GRAY))
                .append(itemName);
        if (!hoverStack.isEmpty()) {
            return result.withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(hoverStack))
            ));
        }

        Component hoverText = Component.literal(item.id + "\n" + item.count + "x").withStyle(ChatFormatting.GRAY);
        return result.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }

    private static MutableComponent resolveItemName(ItemInfo item) {
        if (item == null || item.id == null || item.id.isBlank()) {
            return Component.translatable("securetrade.history.unknown_item");
        }

        try {
            ResourceLocation id = new ResourceLocation(item.id);
            Item resolvedItem = BuiltInRegistries.ITEM.get(id);
            if (resolvedItem != Items.AIR || "minecraft:air".equals(item.id)) {
                return Component.translatable(resolvedItem.getDescriptionId());
            }
        } catch (Exception ignored) {
            // Fall back to the stored legacy display name below.
        }

        if (item.displayName != null && !item.displayName.isBlank()) {
            return Component.literal(item.displayName);
        }

        return Component.literal(item.id);
    }

    private static ItemStack resolveItemStack(ItemInfo item, HolderLookup.Provider registries) {
        if (item == null || item.id == null || item.id.isBlank()) {
            return ItemStack.EMPTY;
        }

        if (item.stackData != null && !item.stackData.isBlank()) {
            try {
                return ItemStack.of(TagParser.parseTag(item.stackData));
            } catch (Exception ignored) {
            }
        }

        try {
            ResourceLocation id = new ResourceLocation(item.id);
            Item resolvedItem = BuiltInRegistries.ITEM.get(id);
            if (resolvedItem != Items.AIR || "minecraft:air".equals(item.id)) {
                return new ItemStack(resolvedItem);
            }
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }

}
