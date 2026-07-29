package com.fontainerepublic.server.playerdata.persistence;

import net.minecraft.nbt.CompoundTag;

/**
 * Persistence boundary for the authoritative player-data namespace.
 */
public interface PlayerDataStore {
    CompoundTag load();

    void save(CompoundTag snapshot);
}
