package com.fontainerepublic.server.economy.persistence;

import com.fontainerepublic.core.DurableCommitResult;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistence boundary of the authoritative {@code "economy"} namespace
 * (FR-ECO-001-A §5.1, FR-CORE-002 acknowledged gate).
 *
 * <p>The production implementation delegates to {@code DataManager}: {@link #load}
 * reads the module namespace, {@link #commit} uses the FR-CORE-002 durable
 * commit gate and returns its result. Every mutation of the economy store
 * goes through the acknowledged path — an ordinary non-acknowledged save does
 * not exist here because monetary state must never be published before
 * durability success.</p>
 */
public interface EconomyStore {

    /** Loads the current namespace snapshot (empty compound when absent). */
    CompoundTag load();

    /**
     * Acknowledged durable commit of the complete namespace snapshot; returns
     * {@link com.fontainerepublic.core.DurableCommitStatus#COMMITTED} only
     * after durability is assured. On any non-COMMITTED result the caller
     * must not publish anything.
     */
    DurableCommitResult commit(CompoundTag snapshot);
}
