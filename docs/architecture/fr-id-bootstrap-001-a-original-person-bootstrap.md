# FontaineRepublic Original Person Bootstrap Architecture v1.0

> **Task ID:** FR-ID-BOOTSTRAP-001-A
> **Status:** Approved — Human Confirmation FR-ID-BOOTSTRAP-HUMAN-APPROVAL-01 (2026-08-13)
> **Implementation Status:** Authorized (console-only one-time binding)
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Audited, Human-authorized one-time binding of the original
> Hydro Archon personal subject (`10-000001-61`) to the designated player UUID
> **Resolves:** FR-ID-001-A §4.4 / §17.1 / §22.1 implementation blocker
> **Dependency:** FR-ID-001-A, FR-CORE-002, FR-DATA-001, FR-CMD-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

FR-ID reserves `10-000001-61` permanently for the original Hydro Archon's
private natural-person subject and sole personal account, but the one-time
binding of that number to the designated player UUID is explicitly left to a
separate design. This design provides it.

The binding is **immutable once committed**: resignation, succession, logout,
rename, or a change to FR-EMG authority never changes the private owner
(FR-ID §4.4). It must never be inferred from FR-EMG's current configured UUID.

---

## 2. Normative Requirements (from FR-ID §4.4)

Every bootstrap attempt (including rejected input, persistence failure,
idempotent replay, incomplete recovery, and successful binding) must produce a
durable, ordered, auditable trail containing at least:

1. stable attempt identity + server-assigned time / ordered monotonic sequence;
2. classified Human-authorization input source (no raw credentials or unbounded dump);
3. safe projection or cryptographic digest of the target UUID;
4. stable result code distinguishing rejection / incomplete / idempotent
   no-op / persistence failure / success;
5. idempotency correlation linking retries and restart recovery;
6. before/after binding digests or state references to detect unauthorized
   replacement;
7. explicit retention, tamper-evidence, and restart-recovery bounds.

---

## 3. Bootstrap Source and Actor

The only accepted source is the **real local Dedicated Server console**
(same strict classification as FR-EMG §4: rejects RCON, command blocks,
functions, integrated-server hosts, entity-less sources, and ordinary OP).
The console is the Human operator's direct interface.

Command shape (after FR-CMD alignment approval):

```text
/fr admin bootstrap subject-hydro <uuid> <reason>
/fr admin bootstrap status
```

- The command is **not** a normal feature contribution under `admin`; it is a
  foundation-owned admin child adapter with execution-time runtime resolution
  (consistent with the FR-EMG command boundary).
- The operator must confirm the exact UUID; the reason is mandatory and bounded.
- The console source classification is revalidated at the final mutation
  boundary; a preview/confirm token is not required for this first bootstrap
  because it is console-only and single-use-by-construction, but every
  invocation is fully audited.

---

## 4. Storage and Attempt Trail

Authoritative attempt evidence lives in the `subject-registry` namespace:

```text
subject-registry
├── BootstrapState: compound
│   ├── Phase: UNBOUND | BOUND
│   ├── BoundUuidDigest: byte[32]     // SHA-256 of canonical UUID
│   ├── BoundAt: epoch millis
│   └── TrailHeadDigest: byte[32]     // digest of last attempt record
└── BootstrapAttempts: compound       // ordered by attempt id
    └── <attemptId>: compound
        ├── AttemptId / At / SourceClassification / ResultCode
        ├── UuidDigest / ReasonDigest
        ├── PrevDigest / SelfDigest
        └── IdempotencyKey
```

Decisions (made here, subject to review):

- **Trail owner:** `subject-registry` namespace, because restart reconciliation
  must happen before the registry service publishes (FR-ID §17.2). This is a
  deliberate selection of the option FR-ID §4.4 left open.
- **Retention:** append-only, bounded segments by attempt count/bytes; closing a
  segment never rewrites it; no pruning of attempts (they are tiny).
- **Tamper evidence:** digest chain (`PrevDigest` + `SelfDigest`); load rejects
  missing/reordered/altered attempts.
- **Recovery:** on restart, reconcile `BootstrapState` with the trail head; a
  mismatch or missing success record fails closed (registry unavailable for
  ordinary provisioning until resolved by a new audited console attempt).

---

## 5. Commit Semantics

1. Validate console source + syntax + UUID + reason.
2. Append an `ATTEMPT_PENDING` trail record (durable, via FR-CORE-002 gate).
3. Resolve the PlayerData record:
   - absent -> append `INCOMPLETE` (retry later with same idempotency key);
   - present -> validate no existing binding conflict.
4. Materialize the original-person subject bound to the UUID (reserved number,
   type 10, status ACTIVE) in one replacement snapshot containing:
   subject record + number/owner indexes + `BootstrapState = BOUND` +
   terminal success trail record.
5. Commit via `DataManager.commitModuleData("subject-registry", ...)`; publish
   only after `COMMITTED`.
6. Ordinary type-10 provisioning opens only after `Phase == BOUND`
   (or after a Human-approved decision to open with the office-only state).

The binding is one-way and immutable: there is no unbind, reassign, or
correction path. Any later replacement attempt is rejected with a stable code.

---

## 6. Relationship Boundaries

| Concern | Authority |
|---|---|
| Target UUID source | Human console (bootstrap) |
| Binding truth | subject-registry BootstrapState + trail |
| Emergency actor UUID | FR-EMG (never the bootstrap source) |
| Office holder/succession | Future approved design (never the private binding) |
| Audit | Trail in-registry (authoritative); FR-AUD may ingest safe projections later |
| Durability | FR-CORE-002 gate |

---

## 7. Acceptance Matrix

| Test | Expected |
|---|---|
| First successful bind | BOUND; subject + indexes + trail terminal success in one snapshot |
| Rejected input | Durable attempt with rejection code; no binding |
| PlayerData absent | INCOMPLETE attempt; retry idempotent with same key |
| Persistence failure | No binding published; incomplete attempt recoverable |
| Replay / duplicate | Same idempotency key; no second binding |
| Restart reconciliation | State and trail reconcile before service publish; mismatch fails closed |
| Tamper detection | Missing/reordered/altered attempts reject load |
| Immutability | Any later rebind/transfer rejected |
| FR-EMG separation | Changing emergency UUID changes nothing in the registry |
| Console impersonation | RCON/command block/function/integrated host rejected |
| Retention bounds | Segments bounded; no pruning of attempts |
| Deterministic encode | Equivalent snapshots encode identically |

---

## 8. Non-Goals

- Office-holder binding/succession; official account; FR-EMG configuration;
  ordinary provisioning; enterprise/institution adapters; implementation.

## 9. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
