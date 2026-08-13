package com.fontainerepublic.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class ModSavedData extends SavedData {
    public static final String DATA_NAME = "fontainerepublic";

    /** Root-tag key holding the world identity (FR-CORE-002 §4.3). */
    public static final String WORLD_IDENTITY_KEY = "WorldIdentity";

    /** Root-tag key holding the root format version (FR-CORE-002). */
    public static final String FORMAT_VERSION_KEY = "FormatVersion";

    /** Current root format version; bumped only by an explicit migration. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    private final Map<String, CompoundTag> moduleData = new HashMap<>();
    private String worldIdentity = "";

    @Override
    public CompoundTag save(CompoundTag root) {
        CompoundTag modules = new CompoundTag();
        for (Map.Entry<String, CompoundTag> entry : moduleData.entrySet()) {
            modules.put(entry.getKey(), entry.getValue());
        }
        root.put("modules", modules);
        root.putString(WORLD_IDENTITY_KEY, worldIdentity);
        root.putInt(FORMAT_VERSION_KEY, CURRENT_FORMAT_VERSION);
        return root;
    }

    public static ModSavedData load(CompoundTag root) {
        ModSavedData data = new ModSavedData();
        CompoundTag modules = root.getCompound("modules");
        for (String key : modules.getAllKeys()) {
            data.moduleData.put(key, modules.getCompound(key));
        }
        data.worldIdentity = root.getString(WORLD_IDENTITY_KEY);
        return data;
    }

    public String getWorldIdentity() {
        return worldIdentity;
    }

    public void setWorldIdentity(String worldIdentity) {
        this.worldIdentity = worldIdentity == null ? "" : worldIdentity;
    }

    public CompoundTag getModuleData(String name) {
        return moduleData.getOrDefault(name, new CompoundTag());
    }

    public void putModuleData(String name, CompoundTag tag) {
        moduleData.put(name, tag);
        setDirty();
    }
}
