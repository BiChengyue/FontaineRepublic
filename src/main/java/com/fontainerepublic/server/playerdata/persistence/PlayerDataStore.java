package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * Persistence boundary for the authoritative player-data namespace
 * (FR-DATA-003-A §7.5).
 *
 * <p>Mutations go through {@link #commit(CompoundTag)}: the repository
 * encodes a complete proposed snapshot, publishes live state only after the
 * store reports {@link DurableCommitStatus#COMMITTED}, and leaves everything
 * unchanged otherwise. The default implementation falls back to the legacy
 * non-acknowledged {@link #save(CompoundTag)} path so existing injected test
 * stores keep working; the production adapter commits through the FR-CORE-002
 * durable gate.</p>
 */
public interface PlayerDataStore {
    CompoundTag load();

    void save(CompoundTag snapshot);

    /**
     * Acknowledged atomic replacement of the namespace. The default
     * implementation keeps the legacy save-then-acknowledge behavior for
     * non-gated stores.
     */
    default DurableCommitResult commit(CompoundTag snapshot) {
        save(Objects.requireNonNull(snapshot, "snapshot"));
        return new DurableCommitResult(
                DurableCommitStatus.COMMITTED,
                PlayerDataRepository.MODULE_DATA_KEY,
                0L,
                0L,
                ""
        );
    }
}
