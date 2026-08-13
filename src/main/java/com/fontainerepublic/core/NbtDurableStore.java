package com.fontainerepublic.core;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Production {@link DurableStore}: NBT whole-root write to {@code *.dat.tmp},
 * fsync, then atomic replacement of the authoritative root (FR-CORE-002).
 *
 * <p>Failure codes are stable: {@code IO_WRITE}, {@code IO_FSYNC},
 * {@code IO_RENAME}, and {@code NON_ATOMIC_REPLACE} (successful fallback when
 * the platform does not support atomic moves). On any failure the temporary
 * file is discarded (best effort) and the target is never touched.</p>
 */
public final class NbtDurableStore implements DurableStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String CODE_WRITE_FAILED = "IO_WRITE";
    public static final String CODE_FSYNC_FAILED = "IO_FSYNC";
    public static final String CODE_RENAME_FAILED = "IO_RENAME";
    public static final String CODE_NON_ATOMIC = "NON_ATOMIC_REPLACE";

    public static final NbtDurableStore INSTANCE = new NbtDurableStore();

    private NbtDurableStore() {
    }

    @Override
    public DurableStoreWrite writeAtomically(CompoundTag root, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

        try {
            NbtIo.writeCompressed(root, tmp.toFile());
        } catch (IOException failure) {
            deleteQuietly(tmp);
            LOGGER.error(
                    "[DurableStore] Failed to write staging file {}: {}",
                    tmp,
                    failure.getMessage()
            );
            return new DurableStoreWrite(false, 0L, CODE_WRITE_FAILED);
        }

        long bytes;
        try {
            bytes = Files.size(tmp);
        } catch (IOException failure) {
            deleteQuietly(tmp);
            return new DurableStoreWrite(false, 0L, CODE_WRITE_FAILED);
        }

        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            channel.force(true);
        } catch (IOException failure) {
            deleteQuietly(tmp);
            LOGGER.error(
                    "[DurableStore] fsync failed for {}: {}",
                    tmp,
                    failure.getMessage()
            );
            return new DurableStoreWrite(false, 0L, CODE_FSYNC_FAILED);
        }

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(target.getParent());
            return new DurableStoreWrite(true, bytes, "");
        } catch (AtomicMoveNotSupportedException unsupported) {
            LOGGER.warn(
                    "[DurableStore] Atomic move unsupported for {}, falling back to non-atomic replace",
                    target
            );
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                forceDirectory(target.getParent());
                return new DurableStoreWrite(true, bytes, CODE_NON_ATOMIC);
            } catch (IOException failure) {
                deleteQuietly(tmp);
                LOGGER.error(
                        "[DurableStore] Non-atomic replace failed for {}: {}",
                        target,
                        failure.getMessage()
                );
                return new DurableStoreWrite(false, 0L, CODE_RENAME_FAILED);
            }
        } catch (IOException failure) {
            deleteQuietly(tmp);
            LOGGER.error(
                    "[DurableStore] Atomic replace failed for {}: {}",
                    target,
                    failure.getMessage()
            );
            return new DurableStoreWrite(false, 0L, CODE_RENAME_FAILED);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort: the orphan *.dat.tmp is cleaned up at next startup
        }
    }

    /**
     * Best-effort fsync of the parent directory so the rename itself is
     * durable. Not supported on all platforms (e.g. Windows); failures are
     * ignored by contract.
     */
    private static void forceDirectory(Path dir) {
        if (dir == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // platform does not support opening a directory for fsync
        }
    }
}
