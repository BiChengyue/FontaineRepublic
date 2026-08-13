# FontaineRepublic Audit Module Architecture v1.0

> **Task ID:** FR-AUD-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Append-only national audit ledger for ordinary institutional and
> business actions
> **Dependency:** FR-CORE-001, FR-CORE-002 (durable commit gate for
> authoritative entries), FR-DATA-001, FR-NET-001, FR-CMD-001
> **Implementation Status:** Not authorized

---

## 1. Purpose

Roadmap v1.1 Phase 2 defines Audit as the second infrastructure layer:
record the Republic's important actions, append-only, no update/delete,
consumed directly by every later module. This design turns that layer into a
concrete module contract.

FR-AUD records ordinary institutional, administrative, financial, identity,
land, and governance actions. It is **not** the shared emergency-authority
journal: FR-EMG-001-A owns emergency attempts, configuration events, and
receipt segments. FR-AUD may later ingest safe derived projections only under
a separately approved handoff and never duplicates emergency authority.

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Emergency attempt/configuration journal | FR-EMG |
| Emergency success receipts (business-owned) | Owning business module (FR-EMG indexes) |
| Ordinary audit ledger | FR-AUD |
| Business state and revisions | Owning business module |
| Actor identity | PlayerData / SERVER_CONSOLE classification (FR-EMG for emergency) |
| Audit privacy/redaction policy | FR-AUD (ordinary), FR-EMG (emergency) |

FR-AUD must not:

- contain business state, balances, land parcels, cases, or laws;
- update or delete entries (append-only);
- become the authority for any business decision;
- replace or duplicate the FR-EMG shared journal;
- expose secrets in public or ordinary command output;
- require the optional client or custom packets.

---

## 3. Data Model

### 3.1 AuditEntry

| Field | Type | Rule |
|---|---|---|
| `entryId` | positive long | Monotonic; assigned once at commit |
| `timestamp` | epoch millis | Server-assigned |
| `actorType` | `PLAYER` / `SERVER_CONSOLE` / `SYSTEM` | Closed enum |
| `actorId` | canonical string or UUID | Bounded; UUID for players |
| `category` | `AuditCategory` | Closed enum, see 3.2 |
| `moduleId` | stable module id | Owning module |
| `actionId` | stable action id | Bounded string |
| `targetType` / `targetId` | typed, bounded | Optional stable target reference |
| `classification` | `PUBLIC` / `AUTHORIZED_SUMMARY` / `SECRET_DIGEST_ONLY` | Every entry classified; no unclassified entries |
| `summary` | bounded string | Safe human-readable projection |
| `payloadDigest` | optional SHA-256 | Canonical digest of the full payload |
| `revision` | positive long | Increments once per commit |

The full payload (bounded, typed) is stored separately from the projection and
is never rendered into ordinary output. Secrets are stored only as
`SECRET_DIGEST_ONLY` fields plus the payload digest by default.

### 3.2 AuditCategory

Initial closed set (extensible only by reviewed design):

```text
IDENTITY, LAND, FINANCE, LEGISLATION, ADMINISTRATION, JUDICIAL,
GOVERNANCE, EMERGENCY_REFERENCE, SYSTEM
```

`EMERGENCY_REFERENCE` holds only safe cross-references (action id, timestamp)
to FR-EMG records, never duplicated emergency content.

### 3.3 AuditStore

```text
fontainerepublic.dat
└── modules
    └── audit
        ├── StoreVersion: int
        ├── StoreRevision: long
        ├── NextEntryId: long
        ├── Segments: list of bounded segments
        │   └── SegmentId / StartEntryId / EndEntryId / PrevDigest / Entries
        └── Index: entryId -> segment/offset
```

Entries live in bounded append-only segments; each segment carries a digest of
the previous segment (tamper-evidence chain). Closing a segment never rewrites
it. When the active segment cannot accept another record, new audit writes
fail closed (no silent loss).

---

## 4. Service Contract

```java
// server/audit/api/AuditService.java (conceptual)
public interface AuditService {
    AuditEntry record(AuditDraft draft);              // best-effort/autosave path
    AuditReceipt recordAuthoritative(AuditDraft draft); // FR-CORE-002 gate
    Optional<AuditEntry> getEntry(long entryId);      // authorized projection
    Page<AuditEntry> page(long afterEntryId, int limit); // bounded, authorized
}
```

- `record`: normal path; uses the existing SavedData save cycle. Documented
  risk: may be lost on hard crash before autosave.
- `recordAuthoritative`: required by consumers whose contract demands
  acknowledged durability; consumes FR-CORE-002 `commitModuleData("audit", ...)`
  and returns a receipt only after `COMMITTED`.
- `getEntry` / `page`: read-only, bounded, projection-only, respect
  classification; never expose `SECRET_DIGEST_ONLY` plaintext or raw payload
  to ordinary callers.
- No update, delete, clear, export-all, or enumeration-by-secret methods.

All mutations run on the logical server owner thread.

---

## 5. Consumption Rules

- Economy: audit hooks are optional (FR-ECO-001-A §8.2); when enabled, each
  committed transaction produces one `FINANCE` entry referencing the
  transaction id; emergency success receipts stay in the Economy namespace,
  not FR-AUD.
- FR-EMG: no dependency on FR-AUD; FR-EMG's shared journal is authoritative
  for emergency attempts. A future separately approved handoff MAY import
  safe projections into FR-AUD with `EMERGENCY_REFERENCE`.
- Future modules (Land, Government, Parliament, Justice): record their own
  actions via `AuditService`; they never write audit NBT directly.
- Command: a read-only `/fr admin audit` inspect surface may be added only by
  a separately approved command contribution under the reserved `admin`
  boundary (FR-CMD-001-A), with the same inspect matrix discipline as
  FR-EMG §10.2 (no secret dump, no raw NBT).

---

## 6. Durability and Retention

- Normal entries: autosave path (acceptable for diagnostic/statistical audit).
- Authoritative entries (legal evidence, financial routing, identity events):
  FR-CORE-002 gate; entry visible only after `COMMITTED`.
- Capacity: bounded segments + bounded total bytes; values deferred to
  implementation design (never silently chosen).
- Archive/export: a future Audit archival handoff requires a separate design;
  ordinary pruning that deletes evidence is forbidden.
- FR-EMG receipts are never pruned by FR-AUD retention.

---

## 7. Failure Modes

| Condition | Behavior |
|---|---|
| Store corrupt | Fail closed; audit unavailable; no auto-repair |
| Active segment full | New writes fail closed; log stable code |
| Durable gate failure (authoritative entry) | No entry visible; no revision change |
| Business module unavailable | Audit still available for other modules |
| Audit unavailable | Business modules continue but must not claim audit-covered compliance |
| Actor unknown | Entry recorded with actor type + bounded id only; no fabricated identity |

---

## 8. Acceptance Matrix

| Test | Expected |
|---|---|
| Append-only invariant | No update/delete API; codec rejects mutation of committed entries |
| Segment digest chain | Deletion/reorder detected on load |
| Authoritative record failure | Injected store failure -> no receipt, no revision change |
| Classification enforcement | Unclassified entry rejected; secret plaintext not in ordinary projection |
| Bounded paging | No unbounded enumeration |
| Tamper evidence | Altering a segment breaks chain; fail closed |
| Restart | Last committed segments reloaded; orphan temp cleaned |
| FR-EMG isolation | Emergency journal and FR-AUD remain separate authorities |
| Deterministic codec | Same snapshot -> equivalent ordered NBT |

---

## 9. Sequencing and Governance

- Depends on FR-CORE-002 approval/implementation for authoritative entries.
- Normal-path audit entries may be staged without the gate after separate
  authorization.
- This design modifies no core interface and adds one new module namespace;
  module registration follows FR-CORE-001 contracts.

## 10. Non-Goals

- Emergency journal replacement; business state storage; GUI; packets;
  export/archive implementation; retention values; deletion/repair of
  evidence; any consumer implementation.

## 11. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
