package com.fontainerepublic.server.emergency.persistence;

import com.fontainerepublic.core.DurableCommitResult;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistence boundary of the authoritative {@code "emergency"} namespace
 * (FR-EMG-001-A §10.3 / implementation task §3.2).
 *
 * <p>The production implementation delegates to {@code DataManager}: {@link #load}
 * uses the module-data path, {@link #commit} uses the FR-CORE-002 durable
 * commit gate and returns its result. Journal writes and configuration events
 * always run through the acknowledged durable path.</p>
 */
public interface EmergencyStore {
    CompoundTag load();

    /**
     * Acknowledged durable commit of the complete namespace snapshot; returns
     * {@link com.fontainerepublic.core.DurableCommitStatus#COMMITTED} only
     * after durability is assured.
     */
    DurableCommitResult commit(CompoundTag snapshot);
}
