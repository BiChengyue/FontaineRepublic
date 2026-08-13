# FontaineRepublic Durable Commit Gate Architecture v1.0

> **Task ID:** FR-CORE-002-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Acknowledged durable persistence contract for module namespaces
> **Consumers:** FR-ID-001-A §9, FR-DATA-003-A §9, FR-EMG-001-A §11.4, FR-ECO-001-C §12/§14
> **Implementation Status:** Not authorized

---

## 1. Purpose

Current Core persistence (`DataManager.putModuleData` + `ModSavedData.setDirty`)
replaces in-memory state and defers disk serialization to Minecraft's autosave
lifecycle. It cannot prove that a mutation survived a crash, so it cannot back
permanent public numbers, rename/ambiguity evidence, emergency receipts, or
financial routing.

This document designs the acknowledged durable commit contract that the
Phase 2 candidates require. It is a Core infrastructure change, not a business
module. It does not authorize any business implementation.

---

## 2. Consumer Requirements (collected from approved candidates)

| Consumer | Required guarantee |
|---|---|
| FR-ID-001-A §9 | Acknowledged durable namespace commit/flush with copy ownership, explicit failure, shutdown and restart semantics; or WAL with fsync/commit marker, replay, rollback, corruption, compaction, world-identity rules |
| FR-DATA-003-A §9 | Same gate; must consume the shared contract, not invent a second SavedData, JSON authority, or module-specific durability mechanism |
| FR-EMG-001-A §11.4 | Accepts one owned copied namespace snapshot; owner-thread execution; explicit success/failure; no newer repository model or external effect before durable success; define copy-in ownership, failure leaves prior authoritative snapshot published, uninitialized/stopping behavior, restart recovery of last committed record |
| FR-ECO-001-C §12 | Emergency replacement snapshot committed exactly once through the approved durable boundary; ordinary transfers keep the existing non-acknowledged path unless separately required |

Common non-negotiables:

- no external framework (Minecraft-native only);
- single root `fontainerepublic.dat` SavedData container preserved;
- single writer per module namespace preserved;
- no second authoritative database, JSON live store, or Capability authority;
- deterministic encoding and fail-closed load validation;
- server authority and owner-thread discipline.

---

## 3. Selected Approach

**Primary:** synchronous atomic whole-root commit (`DurableCommit`), implemented
with Minecraft-native NBT serialization, fsync, and atomic file replacement.

**Supporting:** a bounded crash-recovery journal only for crash-window
diagnosis and orphan cleanup; no replay of business state is required because
the atomic rename is the commit point.

**Rejected:** WAL as the primary mechanism (higher complexity, replay and
compaction semantics exceed current needs); per-namespace segment files
(storage-layout change and migration, deferred as a future optimization);
`putModuleDataBatch` that only changes memory and calls `setDirty()` (does not
satisfy the gate).

---

## 4. Commit Contract

### 4.1 New Core API surface (conceptual)

```java
// core/DataManager.java (extended; existing methods unchanged)
public enum DurableCommitStatus { COMMITTED, FAILED, UNINITIALIZED, STOPPING }

public record DurableCommitResult(
    DurableCommitStatus status,
    String moduleName,
    long bytesWritten,
    long durationMillis,
    String failureCode        // stable, non-secret; empty on success
) {}

public static DurableCommitResult commitModuleData(String name, CompoundTag snapshot);
```

Rules:

1. `name` must be a registered module namespace; `snapshot` must be the
   complete immutable replacement for that namespace (copy-in ownership: the
   caller's tag is encoded and never mutated or retained by reference).
2. Runs on the logical server owner thread only. Calling from another thread
   is rejected with `FAILED` and a stable code, never queued.
3. The commit writes the **complete root** (current in-memory modules with the
   one namespace replaced), not a fragment.
4. Returns `COMMITTED` only after the new root file is fsync'd and atomically
   renamed into place. Only then does the in-memory module map swap and does
   any downstream effect (receipt, chat, sync, notification) become visible.
5. On failure the in-memory map, revisions, and all downstream state remain
   unchanged; the prior authoritative file remains published.
6. `UNINITIALIZED` when `DataManager` has not been initialized for a server;
   `STOPPING` during `ServerStoppingEvent` shutdown window.

### 4.2 Storage mechanics

File layout (unchanged from current):

```text
world/data/fontainerepublic.dat        authoritative root
world/data/fontainerepublic.dat.tmp    commit staging (same volume)
```

Commit sequence on the owner thread:

1. validate namespace + snapshot (codec-agnostic size and type bounds);
2. build the complete root `CompoundTag` (existing modules + replacement);
3. serialize to `*.dat.tmp` with `NbtIo.writeCompressed` (gzip, deterministic
   key order via `CompoundTag` semantics);
4. open the temp file channel, `force(true)` (fsync) to flush data + metadata;
5. atomically replace `fontainerepublic.dat` with `*.dat.tmp`
   (`Files.move(REPLACE_EXISTING, ATOMIC_MOVE)`; fall back to replace-exists
   only with an explicit non-atomic result code that still requires fsync of
   the directory where the platform supports it);
6. swap the in-memory module map; return `COMMITTED`.

Crash windows:

- before step 4: temp file exists, root unchanged — restart deletes orphan
  `*.dat.tmp`;
- between step 4 and 5: temp is durable but not authoritative — restart deletes
  it; root unchanged;
- after step 5: new root is authoritative and durable — restart loads it.

No replay is required for atomicity. A bounded `commit-journal`
(`fontainerepublic.commit.log`, append-only, fsync'd, capped) may record
stable commit ids for diagnostics and tamper-evidence; it is never used to
reconstruct business state and must not become an authority.

### 4.3 World identity

The root tag carries a `WorldIdentity` field (server-level UUID + canonical
world data-dir digest) written at first init and validated on every load.
A root whose identity does not match the current world is rejected (fail
closed), preventing cross-world copy or restore of authoritative state.

### 4.4 Autosave interaction

Minecraft's `DimensionDataStorage.saveAll` continues to write the same root
file on its schedule and at shutdown. Both paths run on the server owner
thread; the commit contract must not be called from any other thread. The
in-memory root is the single source of truth for both paths, so autosave and
commit cannot diverge. Commit frequency is bounded by a configurable
per-tick/interval guard to protect server performance (see §6).

---

## 5. Failure Modes

| Condition | Required behavior |
|---|---|
| Disk full / IO error during temp write | `FAILED`; memory and published file unchanged; stable failure code |
| fsync failure | `FAILED`; temp discarded; nothing published |
| Atomic rename unsupported / fails | `FAILED`; memory unchanged; prior file authoritative |
| DataManager uninitialized | `UNINITIALIZED`; no write |
| Server stopping | `STOPPING`; no new commit; pending in-memory state flushed by existing `saveAll` |
| Autosave concurrent with commit | Not possible on owner thread; enforced by thread check |
| Orphan temp after crash | Startup deletes `*.dat.tmp`; logged |
| Corrupt/foreign root on load | Fail closed: persistence unavailable; dependent modules unavailable; no auto-repair |
| Cross-world root copy | Rejected by world-identity check |
| Commit flood | Configurable rate guard; `FAILED` with stable code when exceeded; no partial effects |

---

## 6. Performance and Bounds

- Commit is a synchronous whole-root write; it is intended for low-frequency,
  high-importance mutations (subject provisioning, rename/ambiguity evidence,
  emergency receipts), not for high-frequency ticks.
- Ordinary Economy transfers continue to use the existing non-acknowledged
  `putModuleData` path unless a consumer's contract requires the gate
  (FR-ECO-001-C §4.2 preserves this separation).
- Bounds to define at implementation design (not silently chosen here):
  max committed bytes per namespace, min interval between commits, max
  journal size, commit retry policy (no automatic retry of business effect).

---

## 7. Lifecycle

```text
DataManager.init(server)
  -> resolve world data dir, world identity
  -> load root; validate identity/version/checksum; clean orphan temp
  -> publish module namespaces only when validation passes

Runtime
  -> module commit (owner thread) -> COMMITTED/FAILED/...
  -> autosave path unchanged

ServerStopping
  -> commit gate returns STOPPING
  -> saveAll() flushes remaining state

Restart
  -> repeat load/validate; last committed root is authoritative
```

Repository services (e.g., future SubjectRegistryRepository,
PlayerDataRepository commit path, Emergency provider) call the gate only after
they have built and validated their complete immutable namespace snapshot.

---

## 8. Acceptance Matrix

| Test | Expected result |
|---|---|
| Commit success | `COMMITTED`; new root file exists; memory matches file |
| Save failure injected | `FAILED`; memory/revisions unchanged; prior file intact |
| fsync failure injected | `FAILED`; nothing published |
| Uninitialized call | `UNINITIALIZED` |
| Stopping call | `STOPPING`; no mutation |
| Off-thread call | `FAILED`; stable code |
| Crash before rename (simulated) | Restart loads old root; temp cleaned |
| Crash after rename (simulated) | Restart loads new root |
| Cross-world root | Load rejected |
| Corrupt root | Fail closed; dependent modules unavailable |
| Deterministic encode | Same snapshot -> same file bytes (gzip headers excluded) |
| Autosave after commit | Same authoritative content; no divergence |
| Rate guard exceeded | `FAILED` with stable code; no partial effects |

Acceptance requires an injected storage adapter that can report failure at
each step, plus a real temporary-directory integration test. A normal build
alone is not runtime evidence.

---

## 9. Governance and Sequencing

- This design modifies Core persistence interfaces (`DataManager` /
  `ModSavedData`), which per CLAUDE.md requires confirmation before change.
  Approval of this design is the requested confirmation.
- Required order: approve this design -> independent implementation design ->
  explicit Human implementation authorization -> implement -> independent
  repository/runtime review.
- Consumers (FR-ID, FR-DATA-003, FR-EMG, Economy emergency) remain blocked
  until the gate is approved and implemented.

---

## 10. Non-Goals

- WAL as primary mechanism; per-namespace segment files; JSON/DB/Capability
  storage; auto-repair of corrupt state; asynchronous commit acknowledgement;
  any business module implementation; changes to consumer designs.

---

## 11. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent Core/security review
is requested.
