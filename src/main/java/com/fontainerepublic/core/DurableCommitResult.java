package com.fontainerepublic.core;

/**
 * Result of {@link DataManager#commitModuleData(String, net.minecraft.nbt.CompoundTag)}
 * (FR-CORE-002).
 *
 * @param status        commit outcome
 * @param moduleName    the namespace that was requested
 * @param bytesWritten  bytes actually written to the authoritative file; 0 on rejection
 * @param durationMillis wall-clock duration of the commit attempt
 * @param failureCode   stable, non-secret failure code; empty on success
 */
public record DurableCommitResult(
        DurableCommitStatus status,
        String moduleName,
        long bytesWritten,
        long durationMillis,
        String failureCode
) {
}
