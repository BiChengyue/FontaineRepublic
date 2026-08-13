package com.fontainerepublic.core;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Injectable storage adapter for the durable commit gate (FR-CORE-002).
 *
 * <p>The implementation must serialize the complete root to a same-volume
 * temporary file, fsync it, and atomically replace {@code target}. A failure
 * at any step must leave {@code target} untouched and must discard the
 * temporary file.</p>
 */
public interface DurableStore {

    /**
     * Writes {@code root} to {@code target} durably.
     *
     * @param root   the complete root snapshot to persist
     * @param target the authoritative root file ({@code fontainerepublic.dat})
     * @return the write outcome; {@code ok() == true} only after fsync +
     *         replacement completed
     * @throws IOException only for unexpected failures that cannot be mapped
     *                     to a stable failure code
     */
    DurableStoreWrite writeAtomically(CompoundTag root, Path target) throws IOException;
}
