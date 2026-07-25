package com.fontainerepublic.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class ModSavedData extends SavedData {
    public static final String DATA_NAME = "fontainerepublic";

    private final Map<String, CompoundTag> moduleData = new HashMap<>();

    @Override
    public CompoundTag save(CompoundTag root) {
        CompoundTag modules = new CompoundTag();
        for (Map.Entry<String, CompoundTag> entry : moduleData.entrySet()) {
            modules.put(entry.getKey(), entry.getValue());
        }
        root.put("modules", modules);
        return root;
    }

    public static ModSavedData load(CompoundTag root) {
        ModSavedData data = new ModSavedData();
        CompoundTag modules = root.getCompound("modules");
        for (String key : modules.getAllKeys()) {
            data.moduleData.put(key, modules.getCompound(key));
        }
        return data;
    }

    public CompoundTag getModuleData(String name) {
        return moduleData.getOrDefault(name, new CompoundTag());
    }

    public void putModuleData(String name, CompoundTag tag) {
        moduleData.put(name, tag);
        setDirty();
    }
}
