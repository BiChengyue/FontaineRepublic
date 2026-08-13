# FontaineRepublic Citizen Module Architecture v1.0

> **Task ID:** FR-CIT-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Citizenship status, political identity, and rank infrastructure
> **Dependency:** FR-CORE-001, FR-CORE-002 (durable gate for authoritative
> citizenship events), FR-DATA-001, FR-ID-001-A, FR-CMD-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

Roadmap v1.1 Phase 3 defines Citizen as the resident-identity system:
`CitizenManager + Citizen + CitizenRank + CitizenSavedData`, with ranks
GOD / COUNCIL / CITIZEN and the rule that **political identity is decoupled
from technical permission**. This design turns that layer into a concrete
module contract consistent with the approved Phase 2 batch.

FR-CIT owns:

- whether a player is a citizen (citizenship status and lifecycle);
- the citizen's political rank (infrastructure classification);
- bounded read queries used by later modules (Parliament eligibility,
  Government appointments, Justice standing).

FR-CIT does **not** own: public registry numbers or subjects (FR-ID), balance
or accounts (Economy), land (Land), offices (Government), or permissions
(future Permission system).

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Authenticated player identity | Minecraft UUID (PlayerData) |
| Subject identity / registry number | FR-ID |
| Citizenship status and rank | FR-CIT |
| Balance / accounts | Economy |
| Technical permission / OP | Future Permission system; never Citizen rank |
| Emergency authority | FR-EMG |
| Audit | FR-AUD / FR-EMG (emergency) |

Rules:

- `CitizenRank` never grants technical permission. `rank == GOD` must never
  be treated as OP or bypass (architecture v2.7 §3.3; roadmap risk section).
- FR-CIT must not contain balance, land, case, office, registry-number, or
  permission fields.
- FR-CIT must not duplicate FR-ID subject provisioning; it consumes
  `SubjectRegistryService` results via Services only.
- Client/phone projections are never authoritative.

---

## 3. Data Model

### 3.1 CitizenRecord

| Field | Type | Rule |
|---|---|---|
| `playerId` | UUID | Key; matches PlayerData identity |
| `subjectId` | FR-ID SubjectId | Bound once via FR-ID Service; never null after provisioning |
| `status` | `CITIZEN` / `NON_CITIZEN` / `SUSPENDED` / `REVOKED` | Server-authoritative |
| `rank` | `CitizenRank` | Infrastructure classification; see 3.2 |
| `firstCitizenAt` | epoch millis | Server-assigned; immutable once set |
| `recordRevision` | positive long | Increments once per committed mutation |

The initial default for every provisioned player is `status = CITIZEN`
(constitution: citizenship is the baseline political identity; specific
eligibility rules belong to later Parliament/Government designs) with
`rank = CITIZEN`.

### 3.2 CitizenRank

```text
GOD      // water-deity/creator political rank (constitutional office; NOT technical OP)
COUNCIL  // reserved for future council/parliamentary leadership designs
CITIZEN  // ordinary citizen (default)
```

Ranks are descriptive political classifications, not permissions. A later
approved design decides which rank each political office maps to. No code may
use rank to gate technical commands or bypass validation.

### 3.3 CitizenStore

```text
fontainerepublic.dat
└── modules
    └── citizen
        ├── StoreVersion: int
        ├── StoreRevision: long
        └── Citizens: compound  // <canonical UUID> -> CitizenRecord
```

Strict codec: declared fields only, canonical UUID keys, valid status/rank,
positive revisions, bounded record count and bytes, deterministic ordering,
fail-closed load validation (unknown fields / newer version rejected).

---

## 4. Service Contract

```java
// server/citizen/api/CitizenService.java (conceptual)
public interface CitizenService {
    CitizenRecord ensureCitizen(UUID playerId);       // lazy provisioning
    Optional<CitizenRecord> getCitizen(UUID playerId);
    CitizenReceipt setRank(UUID playerId, CitizenRank rank);   // authoritative
    CitizenReceipt setStatus(UUID playerId, CitizenStatus status); // authoritative
}
```

- `ensureCitizen` follows the ordered chain `PlayerData -> FR-ID subject ->
  Citizen record`; it is idempotent and returns the existing record.
- `setRank` / `setStatus` are authoritative mutations: build one complete
  replacement snapshot, submit through FR-CORE-002 `commitModuleData("citizen",
  ...)`, publish only after `COMMITTED`.
- Read queries are bounded and never enumerate the full store for consumers.
- All mutations run on the logical server owner thread.

### 4.1 Provisioning order

```text
verified login (PlayerData)
  -> SubjectRegistryService.ensurePlayerSubject(UUID)
  -> CitizenService.ensureCitizen(UUID)
```

Partial states allowed: `player-without-subject` (FR-ID), `subject-without-
citizen`. A Citizen record without a subject is invalid and rejected at load.

---

## 5. Consumption and Commands

- Future modules (Parliament, Government, Justice, Land) read citizenship
  through `CitizenService`; they never touch citizen NBT.
- Framework command (after separate CMD contribution approval):
  `/fr citizen info [player]` — own status/rank; bounded, non-enumerating.
- Rank/status changes are administrative/political actions; their authority
  and audit belong to a later Government/Parliament design plus FR-AUD.

---

## 6. Relationship to Approved Designs

| Design | Relationship |
|---|---|
| FR-ID-001-A | Citizen keys by SubjectId via Service; never duplicates registry numbers |
| FR-DATA-003-A | Name lookup resolves to UUID -> subject -> citizen |
| FR-ECO-001-C-ACCOUNT-ALIGN | Citizen has no financial fields; Economy has no citizenship fields |
| FR-EMG-001-A | Emergency actor verification independent of citizen rank |
| FR-AUD-001-A | Citizenship events recorded via AuditService (authoritative path) |
| FR-CORE-002-A | Authoritative citizen mutations use the durable gate |

---

## 7. Acceptance Matrix

| Test | Expected |
|---|---|
| Lazy ensure | PlayerData -> subject -> citizen; idempotent |
| Citizen without subject | Load rejected |
| Rank/status mutation | One snapshot; revision +1; committed only after gate |
| Rank never grants permission | Permission resolver test: rank has no technical effect |
| GOD not OP | No rank-to-OP mapping exists in code |
| Strict codec | Unknown fields rejected; deterministic encoding |
| Restart | Same citizens recovered; orphan tmp cleaned |
| Enumeration | No bulk citizen list API |
| Client projection | No client authority |

---

## 8. Non-Goals

- Voting/Parliament eligibility policy; Government appointment; Justice
  standing; land rights; balance; technical permissions; GUI; business
  packets; registry numbers; emergency authority.

## 9. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
