package com.fontainerepublic.core;

import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Core persistence facade (FR-CORE-002 durable commit gate).
 *
 * <p>Two mutation paths share the in-memory {@link ModSavedData} root as the
 * single source of truth:</p>
 * <ul>
 *   <li>{@link #putModuleData} — non-acknowledged path, deferred to
 *       Minecraft's autosave lifecycle (unchanged).</li>
 *   <li>{@link #commitModuleData} — acknowledged path: whole-root temporary
 *       write + fsync + atomic replacement; memory is swapped only after the
 *       file is durably authoritative.</li>
 * </ul>
 *
 * <p>Server authority and owner-thread discipline are enforced; all persistent
 * state stays in SavedData/NBT under the single {@code fontainerepublic.dat}
 * root. New acknowledged writes publish the canonical Minecraft SavedData
 * envelope: the FontaineRepublic payload (comprising {@code FormatVersion},
 * {@code WorldIdentity}, and {@code modules}) lives under a top-level compound
 * named {@code data} with the usual current {@code DataVersion} on the outer
 * envelope — the same shape Minecraft's {@code DimensionDataStorage} publishes
 * on autosave. The reader unwraps that envelope before validating
 * {@code FormatVersion} and {@code WorldIdentity}, and also accepts the
 * previously published legitimate raw payload (compatibility only; new writes
 * are always enveloped). A root whose {@code WorldIdentity} does not match the
 * current world is rejected (fail closed); an outer {@code data} key that is
 * present but not a compound tag fails closed rather than being treated as a
 * legacy raw payload; a corrupt root disables persistence until the file is
 * repaired or removed — never auto-repaired.</p>
 */
public class DataManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Top-level envelope key under which the FontaineRepublic payload is
     * persisted, matching Minecraft's {@code SavedData} envelope: the payload
     * is the whole root under a compound named {@code data}, with the normal
     * current {@code DataVersion} on the outer envelope
     * (FR-CORE-002 envelope fix). Mirrors {@code NbtUtils.SNBT_DATA_TAG}.
     */
    public static final String ENVELOPE_DATA_KEY = "data";

    // Stable failure codes (FR-CORE-002 implementation task §3.6).
    public static final String CODE_THREAD_VIOLATION = "THREAD_VIOLATION";
    public static final String CODE_UNINITIALIZED = "UNINITIALIZED";
    public static final String CODE_STOPPING = "STOPPING";
    public static final String CODE_WORLD_IDENTITY = "WORLD_IDENTITY";
    public static final String CODE_BOUNDS_EXCEEDED = "BOUNDS_EXCEEDED";
    public static final String CODE_RATE_GUARD = "RATE_GUARD";
    public static final String CODE_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String CODE_LOAD_FAILED = "LOAD_FAILED";

    private static volatile ModSavedData savedData;
    private static volatile DurableStore store;
    private static volatile String worldIdentity;
    private static volatile Path dataDir;
    private static volatile boolean stopping;
    private static volatile String loadFailureCode;
    private static volatile BooleanSupplier ownerThreadCheck;
    private static volatile LongSupplier clock;
    private static volatile DurableCommitPolicy commitPolicy;
    private static volatile long lastCommitMillis;

    private DataManager() {
    }

    /**
     * Initializes persistence for a logical server. Called from
     * {@code ServerStartingEvent} on the server owner thread.
     */
    public static void init(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        Path resolvedDataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        String identity = computeWorldIdentity(server, resolvedDataDir);
        initialize(
                resolvedDataDir,
                identity,
                NbtDurableStore.INSTANCE,
                server::isSameThread,
                System::currentTimeMillis,
                readPolicyFromConfig()
        );
        if (loadFailureCode == null) {
            storage.set(ModSavedData.DATA_NAME, savedData);
            LOGGER.info("[DataManager] Initialized for world {}", identity);
        } else {
            LOGGER.error(
                    "[DataManager] Initialized with persistence unavailable (code={})",
                    loadFailureCode
            );
        }
    }

    public static void saveAll() {
        if (savedData != null) {
            savedData.setDirty();
            LOGGER.info("[DataManager] Saved");
        }
    }

    /**
     * Marks the shutdown window. Idempotent; while set, new durable commits
     * are rejected with {@link DurableCommitStatus#STOPPING}. Pending state is
     * flushed afterwards by the existing {@link #saveAll()} path.
     */
    public static void beginShutdown() {
        stopping = true;
    }

    public static CompoundTag getModuleData(String name) {
        return savedData != null ? savedData.getModuleData(name) : new CompoundTag();
    }

    public static void putModuleData(String name, CompoundTag tag) {
        if (savedData != null) {
            savedData.putModuleData(name, tag);
        }
    }

    /**
     * Acknowledged durable commit of one module namespace (FR-CORE-002 §4).
     *
     * <p>Writes the complete root (current modules with {@code name} replaced
     * by {@code snapshot}) in the canonical Minecraft SavedData envelope to
     * {@code fontainerepublic.dat} via temporary file + fsync + atomic rename.
     * Returns {@link DurableCommitStatus#COMMITTED} only after the file is
     * durably authoritative; only then is the in-memory module map swapped and
     * any downstream effect visible. On any failure the memory, revisions, and
     * the previously published file remain unchanged.</p>
     *
     * <p>The caller's tag is copied in; it is never mutated nor retained by
     * reference.</p>
     *
     * @param name     registered module namespace
     * @param snapshot complete immutable replacement for that namespace
     * @return the commit result
     */
    public static DurableCommitResult commitModuleData(String name, CompoundTag snapshot) {
        long startedNanos = System.nanoTime();

        if (loadFailureCode != null) {
            return result(DurableCommitStatus.FAILED, name, 0L, startedNanos, loadFailureCode);
        }
        if (savedData == null) {
            return result(DurableCommitStatus.UNINITIALIZED, name, 0L, startedNanos, CODE_UNINITIALIZED);
        }
        if (stopping) {
            return result(DurableCommitStatus.STOPPING, name, 0L, startedNanos, CODE_STOPPING);
        }
        if (!isOwnerThread()) {
            return result(DurableCommitStatus.FAILED, name, 0L, startedNanos, CODE_THREAD_VIOLATION);
        }
        if (name == null || name.isEmpty() || snapshot == null) {
            return result(DurableCommitStatus.FAILED, name, 0L, startedNanos, CODE_INVALID_ARGUMENT);
        }

        DurableCommitPolicy policy = commitPolicy;
        if (policy == null) {
            policy = DurableCommitPolicy.DEFAULT;
        }

        int snapshotBytes = encodedBytes(snapshot);
        if (snapshotBytes > policy.maxBytesPerNamespace()) {
            return result(
                    DurableCommitStatus.FAILED,
                    name,
                    0L,
                    startedNanos,
                    CODE_BOUNDS_EXCEEDED
            );
        }

        long now = clock != null ? clock.getAsLong() : System.currentTimeMillis();
        if (now - lastCommitMillis < policy.minIntervalMillis()) {
            return result(DurableCommitStatus.FAILED, name, 0L, startedNanos, CODE_RATE_GUARD);
        }

        CompoundTag payload = buildRootWith(name, snapshot);
        if (!payload.getString(ModSavedData.WORLD_IDENTITY_KEY).equals(worldIdentity)) {
            return result(DurableCommitStatus.FAILED, name, 0L, startedNanos, CODE_WORLD_IDENTITY);
        }
        CompoundTag envelope = buildEnvelope(payload);

        Path rootFile = dataDir.resolve(ModSavedData.DATA_NAME + ".dat");
        DurableStoreWrite write;
        try {
            write = store.writeAtomically(envelope, rootFile);
        } catch (IOException unexpected) {
            LOGGER.error(
                    "[DataManager] Unexpected storage failure during commit of {}: {}",
                    name,
                    unexpected.getMessage()
            );
            return result(
                    DurableCommitStatus.FAILED,
                    name,
                    0L,
                    startedNanos,
                    NbtDurableStore.CODE_WRITE_FAILED
            );
        }
        if (!write.ok()) {
            LOGGER.error(
                    "[DataManager] Commit of {} rejected by store (code={})",
                    name,
                    write.failureCode()
            );
            return result(
                    DurableCommitStatus.FAILED,
                    name,
                    write.bytesWritten(),
                    startedNanos,
                    write.failureCode()
            );
        }

        savedData.putModuleData(name, snapshot.copy());
        lastCommitMillis = now;
        return result(DurableCommitStatus.COMMITTED, name, write.bytesWritten(), startedNanos, "");
    }

    // ------------------------------------------------------------------
    // initialization internals
    // ------------------------------------------------------------------

    private static void initialize(
            Path resolvedDataDir,
            String identity,
            DurableStore resolvedStore,
            BooleanSupplier resolvedOwnerThreadCheck,
            LongSupplier resolvedClock,
            DurableCommitPolicy resolvedPolicy
    ) {
        resetRuntimeState();
        dataDir = resolvedDataDir;
        worldIdentity = identity;
        store = resolvedStore;
        ownerThreadCheck = resolvedOwnerThreadCheck;
        clock = resolvedClock;
        commitPolicy = resolvedPolicy;
        lastCommitMillis = 0L;

        try {
            Files.createDirectories(dataDir);
            cleanupOrphanTempFiles(dataDir);
            Path rootFile = dataDir.resolve(ModSavedData.DATA_NAME + ".dat");
            if (Files.exists(rootFile)) {
                CompoundTag root = readRootChecked(rootFile);
                if (loadFailureCode != null) {
                    return;
                }
                String storedIdentity = root.getString(ModSavedData.WORLD_IDENTITY_KEY);
                if (storedIdentity.isEmpty() || !storedIdentity.equals(identity)) {
                    failClosed(
                            CODE_WORLD_IDENTITY,
                            "root identity " + (storedIdentity.isEmpty() ? "<missing>"
                                    : storedIdentity) + " does not match world " + identity
                    );
                    return;
                }
                savedData = ModSavedData.load(root);
            } else {
                savedData = new ModSavedData();
                savedData.setWorldIdentity(identity);
                LOGGER.info("[DataManager] Creating new root for world {}", identity);
            }
        } catch (IOException failure) {
            failClosed(CODE_LOAD_FAILED, "root load failed: " + failure.getMessage());
        }
    }

    /**
     * Reads, unwraps, and validates the persisted root, returning the
     * FontaineRepublic payload compound.
     *
     * <p>The canonical on-disk form is the Minecraft SavedData envelope: the
     * payload under a top-level {@code data} compound (with a {@code
     * DataVersion} on the outer envelope). For backward compatibility the
     * previously published legitimate raw payload (no {@code data} key) is
     * also accepted as the payload itself. An outer {@code data} key that is
     * present but is not a compound tag is ambiguous and fails closed rather
     * than being mis-read as a legacy raw payload. The returned payload is
     * validated for the FontaineRepublic {@code FormatVersion}; world-identity
     * validation happens in the caller.</p>
     */
    private static CompoundTag readRootChecked(Path rootFile) {
        try {
            CompoundTag outer = NbtIo.readCompressed(rootFile.toFile());
            CompoundTag payload;
            if (outer.contains(ENVELOPE_DATA_KEY)) {
                // Canonical envelope. A 'data' key of the wrong NBT type must
                // not silently fall back to treating the outer tag as a
                // legacy raw payload (fail closed, FR-CORE-002 envelope fix).
                if (!outer.contains(ENVELOPE_DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    failClosed(
                            CODE_LOAD_FAILED,
                            "root '" + ENVELOPE_DATA_KEY + "' is present but is not a compound tag"
                    );
                    return null;
                }
                payload = outer.getCompound(ENVELOPE_DATA_KEY);
            } else {
                // Backward compatibility: previously published legitimate raw
                // FontaineRepublic root format. Compatibility only; all new
                // production acknowledged writes use the canonical envelope.
                payload = outer;
            }
            if (payload == null || payload.getInt(ModSavedData.FORMAT_VERSION_KEY)
                    != ModSavedData.CURRENT_FORMAT_VERSION) {
                failClosed(
                        CODE_LOAD_FAILED,
                        "root format version "
                                + (payload == null ? "<missing>"
                                : payload.getInt(ModSavedData.FORMAT_VERSION_KEY))
                                + " is not supported (expected "
                                + ModSavedData.CURRENT_FORMAT_VERSION + ")"
                );
                return null;
            }
            return payload;
        } catch (IOException failure) {
            failClosed(CODE_LOAD_FAILED, "corrupt or unreadable root: " + failure.getMessage());
            return null;
        }
    }

    private static void failClosed(String code, String reason) {
        loadFailureCode = code;
        savedData = null;
        LOGGER.error("[DataManager] Persistence unavailable (code={}): {}", code, reason);
    }

    /**
     * Deletes orphan {@code fontainerepublic.dat.tmp} files left by a crash
     * before or during the atomic rename (crash windows, FR-CORE-002 §4.2).
     */
    private static void cleanupOrphanTempFiles(Path resolvedDataDir) throws IOException {
        Path tmp = resolvedDataDir.resolve(ModSavedData.DATA_NAME + ".dat.tmp");
        if (Files.exists(tmp)) {
            Files.delete(tmp);
            LOGGER.warn("[DataManager] Removed orphan staging file {}", tmp);
        }
    }

    private static String computeWorldIdentity(MinecraftServer server, Path resolvedDataDir) {
        String levelName = server.getWorldData().getLevelName();
        String canonicalDir;
        try {
            canonicalDir = resolvedDataDir.toRealPath().toString();
        } catch (IOException failure) {
            canonicalDir = resolvedDataDir.toAbsolutePath().normalize().toString();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (levelName + "|" + canonicalDir).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static DurableCommitPolicy readPolicyFromConfig() {
        try {
            return new DurableCommitPolicy(
                    ConfigManager.commitMinIntervalMillis(),
                    ConfigManager.commitMaxBytesPerNamespace()
            );
        } catch (RuntimeException unavailable) {
            LOGGER.warn(
                    "[DataManager] Commit bounds config unavailable, using defaults: {}",
                    unavailable.getMessage()
            );
            return DurableCommitPolicy.DEFAULT;
        }
    }

    // ------------------------------------------------------------------
    // commit internals
    // ------------------------------------------------------------------

    private static CompoundTag buildRootWith(String name, CompoundTag snapshot) {
        CompoundTag root = savedData.save(new CompoundTag());
        root.getCompound("modules").put(name, snapshot.copy());
        return root;
    }

    /**
     * Wraps a FontaineRepublic payload in the canonical Minecraft SavedData
     * envelope: the payload under a top-level {@code data} compound plus the
     * normal current {@code DataVersion}. This mirrors exactly what Minecraft's
     * {@code SavedData.save(File)} publishes for the autosave path, so the
     * acknowledged-commit bytes and the autosave bytes agree.
     *
     * <p>Package-visible so the validation entry point can build the same
     * autosave-view of in-memory state for its deterministic-encoding check.</p>
     */
    static CompoundTag buildEnvelope(CompoundTag payload) {
        CompoundTag envelope = new CompoundTag();
        envelope.put(ENVELOPE_DATA_KEY, payload);
        addCurrentDataVersion(envelope);
        return envelope;
    }

    /**
     * Adds the normal current Minecraft {@code DataVersion} to the outer
     * envelope (matching vanilla {@code SavedData} serialization). Uses the
     * real current version — never a custom value. In a minimal bootstrap-free
     * runtime there may be no detectable current version; that is treated as
     * "not practical to include" and the metadata is skipped, since the reader
     * forwards on the internal payload format version rather than {@code
     * DataVersion}.
     */
    private static void addCurrentDataVersion(CompoundTag envelope) {
        try {
            SharedConstants.tryDetectVersion();
            NbtUtils.addCurrentDataVersion(envelope);
        } catch (RuntimeException unavailable) {
            LOGGER.warn(
                    "[DataManager] Could not resolve current Minecraft DataVersion; "
                            + "skipping envelope metadata: {}",
                    unavailable.getMessage()
            );
        }
    }

    private static int encodedBytes(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Uncompressed NBT size: gzip would allow compression to evade the
            // byte budget, so the bound is enforced on the real payload size.
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean isOwnerThread() {
        return ownerThreadCheck == null || ownerThreadCheck.getAsBoolean();
    }

    private static DurableCommitResult result(
            DurableCommitStatus status,
            String name,
            long bytes,
            long startedNanos,
            String code
    ) {
        long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return new DurableCommitResult(status, name, bytes, durationMillis, code);
    }

    // ------------------------------------------------------------------
    // test hooks (package-private; production never calls these)
    // ------------------------------------------------------------------

    /**
     * Test-only initialization that exercises the same load/validate path as
     * {@link #init(MinecraftServer)} against a plain directory.
     */
    static void initForTest(
            Path resolvedDataDir,
            String identity,
            DurableStore resolvedStore,
            BooleanSupplier resolvedOwnerThreadCheck,
            LongSupplier resolvedClock,
            DurableCommitPolicy resolvedPolicy
    ) {
        initialize(
                resolvedDataDir,
                identity,
                resolvedStore,
                resolvedOwnerThreadCheck,
                resolvedClock,
                resolvedPolicy
        );
    }

    /** Test-only reset of all static state. */
    static void resetForTest() {
        resetRuntimeState();
    }

    private static void resetRuntimeState() {
        savedData = null;
        store = null;
        worldIdentity = null;
        dataDir = null;
        stopping = false;
        loadFailureCode = null;
        ownerThreadCheck = null;
        clock = null;
        commitPolicy = null;
        lastCommitMillis = 0L;
    }
}
