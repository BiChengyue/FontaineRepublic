package com.fontainerepublic.core;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;

public class DataManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ModSavedData savedData;

    public static void init(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        savedData = storage.computeIfAbsent(
                ModSavedData::load,
                ModSavedData::new,
                ModSavedData.DATA_NAME
        );
        LOGGER.info("[DataManager] Initialized");
    }

    public static void saveAll() {
        if (savedData != null) {
            savedData.setDirty();
            LOGGER.info("[DataManager] Saved");
        }
    }

    public static CompoundTag getModuleData(String name) {
        return savedData != null ? savedData.getModuleData(name) : new CompoundTag();
    }

    public static void putModuleData(String name, CompoundTag tag) {
        if (savedData != null) {
            savedData.putModuleData(name, tag);
        }
    }
}
