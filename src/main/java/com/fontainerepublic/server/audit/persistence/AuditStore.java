package com.fontainerepublic.server.audit.persistence;

import com.fontainerepublic.core.DurableCommitResult;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistence boundary of the authoritative {@code "audit"} namespace
 * (FR-AUD-001-A §3.3 / §4).
 *
 * <p>The production implementation delegates to {@code DataManager}: {@link #load}
 * and {@link #save} use the non-acknowledged module-data path, {@link #commit}
 * uses the FR-CORE-002 durable commit gate and returns its result.</p>
 */
public interface AuditStore {
    CompoundTag load();

    void save(CompoundTag snapshot);

    /**
     * Acknowledged durable commit of the complete namespace snapshot; returns
     * {@link com.fontainerepublic.core.DurableCommitStatus#COMMITTED} only
     * after durability is assured.
     */
    DurableCommitResult commit(CompoundTag snapshot);
}
