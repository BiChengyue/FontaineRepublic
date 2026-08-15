package com.fontainerepublic.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-CORE-002 (durable commit
 * gate). Exercises the FR-CORE-002-A §8 acceptance matrix with an injectable
 * storage adapter and real temporary-directory integration tests.
 */
public final class DurableCommitTestMain {
    private static final String IDENTITY_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String IDENTITY_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private DurableCommitTestMain() {
    }

    public static void main(String[] args) throws IOException {
        testUninitialized();
        testCommitSuccess();
        testFailureInjection(NbtDurableStore.CODE_WRITE_FAILED);
        testFailureInjection(NbtDurableStore.CODE_FSYNC_FAILED);
        testFailureInjection(NbtDurableStore.CODE_RENAME_FAILED);
        testStopping();
        testOffThread();
        testCrossWorldRoot();
        testCorruptRoot();
        testOrphanTempCleanup();
        testDeterministicEncodingAndAutosaveAgreement();
        testRateGuard();
        testBoundsExceeded();
        testCrashBeforeRename();
        testCrashAfterRename();
        testCanonicalEnvelopeReload();
        testLegacyRawReloadAndRewriteCanonical();
        testForgeAutosaveEnvelopeReload();
        testWrongFormatVersionFailsClosed();
        testEnvelopeWrongWorldIdentityFailsClosed();
        testEnvelopeDataWrongTypeFailsClosed();
        System.out.println("[FR-CORE-002] Durable commit validation passed");
    }

    private static void testUninitialized() {
        DataManager.resetForTest();
        DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
        require(
                result.status() == DurableCommitStatus.UNINITIALIZED,
                "uninitialized call returns UNINITIALIZED"
        );
        require(
                result.failureCode().equals(DataManager.CODE_UNINITIALIZED),
                "uninitialized call carries the stable code"
        );
        require(result.bytesWritten() == 0L, "uninitialized call writes nothing");
    }

    private static void testCommitSuccess() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            CompoundTag payload = snapshot("value");
            DurableCommitResult result = DataManager.commitModuleData("ns", payload);
            require(
                    result.status() == DurableCommitStatus.COMMITTED,
                    "commit succeeds on a fresh world"
            );
            require(result.failureCode().isEmpty(), "success carries an empty failure code");
            require(result.moduleName().equals("ns"), "result echoes the module name");
            require(result.bytesWritten() > 0L, "commit reports written bytes");

            Path rootFile = dir.resolve("fontainerepublic.dat");
            require(Files.exists(rootFile), "committed root file exists");
            require(
                    DataManager.getModuleData("ns").equals(payload),
                    "in-memory module matches the committed snapshot"
            );

            CompoundTag loaded = NbtIo.readCompressed(rootFile.toFile());
            require(
                    loaded.contains(DataManager.ENVELOPE_DATA_KEY),
                    "fresh commit writes the canonical data envelope"
            );
            require(
                    loaded.contains(DataManager.ENVELOPE_DATA_KEY, 10),
                    "the data envelope key is a compound tag"
            );
            require(
                    loaded.contains("DataVersion"),
                    "canonical envelope carries the normal DataVersion metadata"
            );
            CompoundTag envelopePayload = loaded.getCompound(DataManager.ENVELOPE_DATA_KEY);
            require(
                    envelopePayload.getString("WorldIdentity").equals(IDENTITY_A),
                    "envelope payload carries the world identity"
            );
            require(
                    envelopePayload.getInt("FormatVersion")
                            == ModSavedData.CURRENT_FORMAT_VERSION,
                    "envelope payload carries the format version"
            );
            require(
                    envelopePayload.getCompound("modules").getCompound("ns").equals(payload),
                    "envelope payload contains the committed namespace"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testFailureInjection(String failureCode) throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            FaultInjectingStore store = new FaultInjectingStore();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    store,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult first = DataManager.commitModuleData("keep", snapshot("v1"));
            require(
                    first.status() == DurableCommitStatus.COMMITTED,
                    "baseline commit succeeds [" + failureCode + "]"
            );
            byte[] publishedBefore = Files.readAllBytes(dir.resolve("fontainerepublic.dat"));

            store.setFailureCode(failureCode);
            DurableCommitResult failed = DataManager.commitModuleData("rejected", snapshot("v2"));
            require(
                    failed.status() == DurableCommitStatus.FAILED,
                    "injected " + failureCode + " fails the commit"
            );
            require(
                    failed.failureCode().equals(failureCode),
                    "injected failure surfaces its stable code"
            );
            require(
                    DataManager.getModuleData("rejected").getAllKeys().isEmpty(),
                    "failed commit leaves memory unchanged [" + failureCode + "]"
            );
            require(
                    Arrays.equals(
                            Files.readAllBytes(dir.resolve("fontainerepublic.dat")),
                            publishedBefore
                    ),
                    "failed commit leaves the published file authoritative [" + failureCode + "]"
            );

            store.setFailureCode(null);
            DurableCommitResult retried = DataManager.commitModuleData("rejected", snapshot("v2"));
            require(
                    retried.status() == DurableCommitStatus.COMMITTED,
                    "commit recovers after the failure is lifted [" + failureCode + "]"
            );
            require(
                    DataManager.getModuleData("rejected").getString("k").equals("v2"),
                    "retried commit publishes the namespace [" + failureCode + "]"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testStopping() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            DataManager.beginShutdown();
            DataManager.beginShutdown(); // idempotent

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.STOPPING,
                    "commit during shutdown window returns STOPPING"
            );
            require(
                    result.failureCode().equals(DataManager.CODE_STOPPING),
                    "stopping carries the stable code"
            );
            require(
                    DataManager.getModuleData("ns").getAllKeys().isEmpty(),
                    "stopping rejects without mutating memory"
            );
            require(
                    !Files.exists(dir.resolve("fontainerepublic.dat")),
                    "stopping writes nothing"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testOffThread() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> false,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.FAILED,
                    "off-thread commit is rejected"
            );
            require(
                    result.failureCode().equals(DataManager.CODE_THREAD_VIOLATION),
                    "off-thread commit reports THREAD_VIOLATION"
            );
            require(
                    DataManager.getModuleData("ns").getAllKeys().isEmpty(),
                    "off-thread commit never mutates memory"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testCrossWorldRoot() throws IOException {
        Path dir = newTempDir();
        try {
            writeRootFile(dir, IDENTITY_B, Map.of("legacy", snapshot("foreign")));

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.FAILED,
                    "cross-world root is rejected (fail closed)"
            );
            require(
                    result.failureCode().equals(DataManager.CODE_WORLD_IDENTITY),
                    "cross-world root reports WORLD_IDENTITY"
            );
            require(
                    DataManager.getModuleData("legacy").getAllKeys().isEmpty(),
                    "cross-world root publishes no module state"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testCorruptRoot() throws IOException {
        Path dir = newTempDir();
        try {
            Path rootFile = dir.resolve("fontainerepublic.dat");
            Files.write(rootFile, new byte[]{0x1f, (byte) 0x8b, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.FAILED,
                    "corrupt root fails closed"
            );
            require(
                    result.failureCode().equals(DataManager.CODE_LOAD_FAILED),
                    "corrupt root reports LOAD_FAILED"
            );

            DataManager.putModuleData("ns", snapshot("v"));
            require(
                    DataManager.getModuleData("ns").getAllKeys().isEmpty(),
                    "corrupt root keeps dependent module writes ineffective"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testOrphanTempCleanup() throws IOException {
        Path dir = newTempDir();
        try {
            writeRootFile(dir, IDENTITY_A, Map.of("keep", snapshot("v1")));
            Path tmp = dir.resolve("fontainerepublic.dat.tmp");
            Files.write(tmp, new byte[]{1, 2, 3});

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            require(!Files.exists(tmp), "orphan *.dat.tmp is removed at startup");
            require(
                    DataManager.getModuleData("keep").getString("k").equals("v1"),
                    "valid root still loads after orphan cleanup"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testDeterministicEncodingAndAutosaveAgreement() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            CompoundTag payload = snapshot("deterministic");
            DataManager.commitModuleData("ns", payload);

            // Deterministic encoding: re-encoding the same in-memory root
            // (single source of truth) yields byte-identical output, so the
            // autosave path and the commit path cannot diverge. Both paths wrap
            // the payload in the canonical data envelope.
            CompoundTag rebuilt = rebuildRootFromMemory(IDENTITY_A);
            Path other = dir.resolve("reencoded.dat");
            NbtDurableStore.INSTANCE.writeAtomically(rebuilt, other);

            byte[] committed = Files.readAllBytes(dir.resolve("fontainerepublic.dat"));
            byte[] reencoded = Files.readAllBytes(other);
            require(
                    Arrays.equals(committed, reencoded),
                    "same snapshot encodes to identical file bytes (commit == autosave content)"
            );

            CompoundTag autosaveRead = NbtIo.readCompressed(other.toFile());
            require(
                    autosaveRead.contains(DataManager.ENVELOPE_DATA_KEY),
                    "autosave-path content exposes the canonical 'data' envelope key"
            );
            require(
                    autosaveRead.contains(DataManager.ENVELOPE_DATA_KEY, 10),
                    "autosave-path 'data' envelope key is a compound tag"
            );
            require(
                    autosaveRead.getCompound(DataManager.ENVELOPE_DATA_KEY)
                            .getCompound("modules").getCompound("ns").equals(payload),
                    "autosave-path '/data/modules/ns' payload matches the committed namespace"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testRateGuard() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            MutableClock clock = new MutableClock(1_000);
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    clock,
                    new DurableCommitPolicy(1_000, 8 * 1024 * 1024)
            );

            require(
                    DataManager.commitModuleData("a", snapshot("v1")).status()
                            == DurableCommitStatus.COMMITTED,
                    "first commit within interval passes"
            );
            // FR-CORE-002 per-namespace rate guard: a DIFFERENT namespace may
            // commit back-to-back (e.g. login provisioning writing
            // subject-registry, then citizen, then economy).
            require(
                    DataManager.commitModuleData("b", snapshot("v2")).status()
                            == DurableCommitStatus.COMMITTED,
                    "a different namespace may commit within the interval"
            );
            // The SAME namespace is still rate-guarded within the interval.
            DurableCommitResult tooSoon = DataManager.commitModuleData("a", snapshot("v3"));
            require(
                    tooSoon.status() == DurableCommitStatus.FAILED
                            && tooSoon.failureCode().equals(DataManager.CODE_RATE_GUARD),
                    "same-namespace commit within the interval fails with RATE_GUARD"
            );
            require(
                    "v1".equals(DataManager.getModuleData("a").getString("k")),
                    "rate-guarded commit has no side effects"
            );

            clock.advance(1_000);
            require(
                    DataManager.commitModuleData("a", snapshot("v3")).status()
                            == DurableCommitStatus.COMMITTED,
                    "same-namespace commit passes after the minimum interval elapses"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testBoundsExceeded() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 1_024)
            );

            CompoundTag oversized = new CompoundTag();
            oversized.putString("blob", "x".repeat(4_096));
            DurableCommitResult result = DataManager.commitModuleData("ns", oversized);
            require(
                    result.status() == DurableCommitStatus.FAILED
                            && result.failureCode().equals(DataManager.CODE_BOUNDS_EXCEEDED),
                    "oversized snapshot fails with BOUNDS_EXCEEDED"
            );
            require(
                    DataManager.getModuleData("ns").getAllKeys().isEmpty(),
                    "bound-rejected commit has no side effects"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testCrashBeforeRename() throws IOException {
        Path dir = newTempDir();
        try {
            writeRootFile(dir, IDENTITY_A, Map.of("old", snapshot("v1")));

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            require(
                    DataManager.getModuleData("old").getString("k").equals("v1"),
                    "pre-crash root loads"
            );

            // Simulated crash window: a durable but never-renamed staging file.
            Path tmp = dir.resolve("fontainerepublic.dat.tmp");
            Files.write(tmp, new byte[]{9, 9, 9});

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(2_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            require(!Files.exists(tmp), "crash-before-rename staging file is cleaned up");
            require(
                    DataManager.getModuleData("old").getString("k").equals("v1"),
                    "restart after crash-before-rename loads the old authoritative root"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testCrashAfterRename() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            CompoundTag payload = snapshot("new-state");
            require(
                    DataManager.commitModuleData("ns", payload).status()
                            == DurableCommitStatus.COMMITTED,
                    "commit succeeds"
            );

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(2_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            require(
                    DataManager.getModuleData("ns").equals(payload),
                    "restart after crash-after-rename loads the new authoritative root"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testCanonicalEnvelopeReload() throws IOException {
        Path dir = newTempDir();
        try {
            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            CompoundTag payload = snapshot("enveloped");
            require(
                    DataManager.commitModuleData("ns", payload).status()
                            == DurableCommitStatus.COMMITTED,
                    "canonical commit succeeds"
            );

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(2_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            require(
                    DataManager.getModuleData("ns").equals(payload),
                    "canonical enveloped root reloads and preserves module data"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testLegacyRawReloadAndRewriteCanonical() throws IOException {
        Path dir = newTempDir();
        try {
            // A previously published legitimate raw root format (no envelope).
            writeRootFile(dir, IDENTITY_A, Map.of("legacy", snapshot("old")));

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            require(
                    DataManager.getModuleData("legacy").getString("k").equals("old"),
                    "legacy raw root reloads through the backward-compatible reader"
            );

            // The next acknowledged commit must rewrite the file canonically.
            require(
                    DataManager.commitModuleData("ns", snapshot("v2")).status()
                            == DurableCommitStatus.COMMITTED,
                    "commit after legacy load succeeds"
            );
            CompoundTag rewritten = NbtIo.readCompressed(
                    dir.resolve("fontainerepublic.dat").toFile()
            );
            require(
                    rewritten.contains(DataManager.ENVELOPE_DATA_KEY, 10)
                            && !rewritten.contains("modules"),
                    "legacy raw root is rewritten as a canonical data envelope"
            );
            require(
                    rewritten.getCompound(DataManager.ENVELOPE_DATA_KEY)
                            .getCompound("modules").getCompound("ns").getString("k").equals("v2"),
                    "rewritten envelope preserves committed module data"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testForgeAutosaveEnvelopeReload() throws IOException {
        Path dir = newTempDir();
        try {
            writeAutosaveEnvelope(dir, IDENTITY_A, Map.of("saved", snapshot("auto")));

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );
            require(
                    DataManager.getModuleData("saved").getString("k").equals("auto"),
                    "Forge/Minecraft-style autosave envelope reloads"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testWrongFormatVersionFailsClosed() throws IOException {
        Path dir = newTempDir();
        try {
            writeAutosaveEnvelope(dir, IDENTITY_A, Map.of("ns", snapshot("v")), 7);

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.FAILED
                            && result.failureCode().equals(DataManager.CODE_LOAD_FAILED),
                    "envelope with wrong payload FormatVersion fails closed with LOAD_FAILED"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testEnvelopeWrongWorldIdentityFailsClosed() throws IOException {
        Path dir = newTempDir();
        try {
            // Canonical envelope whose payload WorldIdentity does not match.
            writeAutosaveEnvelope(dir, IDENTITY_B, Map.of("ns", snapshot("v")));

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.FAILED
                            && result.failureCode().equals(DataManager.CODE_WORLD_IDENTITY),
                    "envelope with mismatched WorldIdentity fails closed with WORLD_IDENTITY"
            );
            require(
                    DataManager.getModuleData("ns").getAllKeys().isEmpty(),
                    "mismatched-identity envelope publishes no module state"
            );
        } finally {
            cleanup(dir);
        }
    }

    private static void testEnvelopeDataWrongTypeFailsClosed() throws IOException {
        Path dir = newTempDir();
        try {
            // Outer envelope has a 'data' key, but it is not a compound tag.
            // This must fail closed rather than being mis-read as a legacy raw
            // payload.
            Files.createDirectories(dir);
            CompoundTag outer = new CompoundTag();
            outer.putString(DataManager.ENVELOPE_DATA_KEY, "not-a-compound");
            outer.putInt("DataVersion", 3465);
            NbtIo.writeCompressed(outer, dir.resolve("fontainerepublic.dat").toFile());

            DataManager.resetForTest();
            DataManager.initForTest(
                    dir,
                    IDENTITY_A,
                    NbtDurableStore.INSTANCE,
                    () -> true,
                    new MutableClock(1_000),
                    new DurableCommitPolicy(0, 8 * 1024 * 1024)
            );

            DurableCommitResult result = DataManager.commitModuleData("ns", snapshot("v"));
            require(
                    result.status() == DurableCommitStatus.FAILED
                            && result.failureCode().equals(DataManager.CODE_LOAD_FAILED),
                    "outer 'data' of wrong NBT type fails closed with LOAD_FAILED"
            );
            require(
                    DataManager.getModuleData("ns").getAllKeys().isEmpty(),
                    "wrong-typed 'data' publishes no module state"
            );
        } finally {
            cleanup(dir);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static CompoundTag snapshot(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("k", value);
        return tag;
    }

    /**
     * Rebuilds the root exactly as {@code ModSavedData.save} + envelope do
     * from the current in-memory state — the autosave-path view of the same
     * state (payload under the canonical {@code data} envelope).
     */
    private static CompoundTag rebuildRootFromMemory(String identity) {
        CompoundTag payload = new CompoundTag();
        CompoundTag modules = new CompoundTag();
        modules.put("ns", DataManager.getModuleData("ns").copy());
        payload.put("modules", modules);
        payload.putString(ModSavedData.WORLD_IDENTITY_KEY, identity);
        payload.putInt(ModSavedData.FORMAT_VERSION_KEY, ModSavedData.CURRENT_FORMAT_VERSION);
        return DataManager.buildEnvelope(payload);
    }

    private static void writeRootFile(
            Path dataDir,
            String identity,
            Map<String, CompoundTag> modules
    ) throws IOException {
        Files.createDirectories(dataDir);
        CompoundTag root = new CompoundTag();
        root.putString("WorldIdentity", identity);
        root.putInt("FormatVersion", ModSavedData.CURRENT_FORMAT_VERSION);
        CompoundTag moduleTag = new CompoundTag();
        modules.forEach(moduleTag::put);
        root.put("modules", moduleTag);
        NbtIo.writeCompressed(root, dataDir.resolve("fontainerepublic.dat").toFile());
    }

    /**
     * Writes a Forge/Minecraft-style autosave envelope: the FontaineRepublic
     * payload under a top-level {@code data} compound plus the current
     * {@code DataVersion}, exactly as Minecraft's {@code SavedData} publishes.
     */
    private static void writeAutosaveEnvelope(
            Path dataDir,
            String identity,
            Map<String, CompoundTag> modules
    ) throws IOException {
        writeAutosaveEnvelope(dataDir, identity, modules, ModSavedData.CURRENT_FORMAT_VERSION);
    }

    private static void writeAutosaveEnvelope(
            Path dataDir,
            String identity,
            Map<String, CompoundTag> modules,
            int formatVersion
    ) throws IOException {
        Files.createDirectories(dataDir);
        CompoundTag payload = new CompoundTag();
        payload.putString("WorldIdentity", identity);
        payload.putInt("FormatVersion", formatVersion);
        CompoundTag moduleTag = new CompoundTag();
        modules.forEach(moduleTag::put);
        payload.put("modules", moduleTag);

        CompoundTag envelope = new CompoundTag();
        envelope.put(DataManager.ENVELOPE_DATA_KEY, payload);
        envelope.putInt("DataVersion", 3465);
        NbtIo.writeCompressed(envelope, dataDir.resolve("fontainerepublic.dat").toFile());
    }

    private static Path newTempDir() throws IOException {
        return Files.createTempDirectory("fr-core-002-test-");
    }

    private static void cleanup(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort test cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort test cleanup
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FaultInjectingStore implements DurableStore {
        private final NbtDurableStore delegate = NbtDurableStore.INSTANCE;
        private String failureCode;

        private void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        @Override
        public DurableStoreWrite writeAtomically(CompoundTag root, Path target)
                throws IOException {
            if (failureCode != null) {
                return new DurableStoreWrite(false, 0L, failureCode);
            }
            return delegate.writeAtomically(root, target);
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        private void advance(long milliseconds) {
            now += milliseconds;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
