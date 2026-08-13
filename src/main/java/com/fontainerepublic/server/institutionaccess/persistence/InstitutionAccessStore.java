package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.core.DurableCommitResult;
import net.minecraft.nbt.CompoundTag;

/**
 * Injectable storage boundary of the {@code institution-access} namespace.
 * Production is backed by the FR-CORE-002 durable commit gate through
 * {@code DataManager.commitModuleData}; tests inject a SavedData-backed store.
 */
public interface InstitutionAccessStore {

    /** Loads the current namespace (empty tag when absent). */
    CompoundTag load();

    /** Commits one complete replacement snapshot; returns the gate result. */
    DurableCommitResult commit(CompoundTag snapshot);
}
