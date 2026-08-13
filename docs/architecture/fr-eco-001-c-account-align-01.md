# FR-ECO-001-C Account Alignment Revision v1.0

> **Task ID:** FR-ECO-001-C-ACCOUNT-ALIGN-01
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Align Economy account identity with the approved FR-ID-001-A
> subject model; resolves review finding F-001
> **Does not modify:** FR-ECO-001-A/B, FR-ECO-001-C, FR-ID-001-A, or any
> implementation
> **Implementation Status:** Not authorized

---

## 1. Problem

FR-ECO-001-A §2.2/§3.1 and FR-ECO-001-C §2.1/§2.3 key player accounts by
Minecraft UUID. FR-ID-001-A §5 establishes the one-account invariant:

```text
one Minecraft UUID
  -> at most one PLAYER_UUID OwnerReference
  -> exactly one natural-person SubjectId once provisioned
  -> exactly one permanent RegistryNumber
  -> exactly one full-function personal Economy account
```

and requires Economy to key the personal account by `SubjectId`, indexing the
same registry number for routing, without inventing a second public number.

This revision aligns the Economy contract with FR-ID while preserving
FR-ECO-001-A/B/C scope and disclosure decisions.

---

## 2. Alignment Decisions

### 2.1 Account key

- The personal Economy account is keyed by the natural-person `SubjectId`
  (internal opaque identifier from FR-ID), not by UUID.
- UUID remains the authoritative player identity and the actor-authentication
  key for self-service and emergency actor checks. UUID is a lookup input, not
  the account key.
- The `RegistryNumber` (`TT-NNNNNN-CC`) is the sole public routing number of
  the personal account (FR-ID §5).

### 2.2 Account creation and provisioning

- Account provisioning follows FR-ID §17.2 lazy ordering:
  `PlayerData -> SubjectRegistryService.ensurePlayerSubject(UUID) -> Economy
  ensure account by SubjectId`.
- A zero-balance account is created for a SubjectId only after the subject is
  provisioned. `subject-without-account` is the only allowed partial state
  (FR-ID §24.13).
- An Economy account without a Subject is rejected at creation/load.

### 2.3 Target input routes (all converge on SubjectId)

| Input | Route |
|---|---|
| Authenticated self UUID | Server context -> FR-ID resolve -> SubjectId |
| Explicit target UUID | PlayerData/FR-ID Services -> SubjectId |
| Registry number | FR-ID exact public lookup -> SubjectId |
| Player name | Disabled until FR-DATA-003 safe directory approved; then PlayerDirectoryService -> UUID -> FR-ID -> SubjectId |

At execution, Economy re-resolves the final SubjectId and revalidates its
status/revision (fail closed on non-ACTIVE where policy unresolved).

### 2.4 Ledger and transactions

- Transaction records store participant `SubjectId`s (stable), not game names
  (FR-DATA-003 §16.4). A safe bounded projection may additionally show the
  registry number to participants.
- No balance, account, or transaction field is stored in FR-ID; no SubjectId
  or registry number is stored in PlayerData.

### 2.5 Emergency catalogue

- `economy.issue` / `economy.reclaim` keep typed `PLAYER_UUID` targets in the
  FR-EMG envelope, but the Economy plan binds the resolved `SubjectId` at
  prepare time and the replacement snapshot keys the account by SubjectId.
- Emergency target resolution is idempotent under FR-ID provisioning rules;
  the shared attempt journal continues to record the stable typed target.

### 2.6 Registry-number routing

- A registry number alone permits payment routing only (FR-ID §5); it never
  authorizes debit, balance disclosure, or impersonation.
- Exact lookup is bounded and non-enumerating (FR-ID §13); Economy never
  scans the registry.

---

## 3. Section-by-Section Deltas (for the future consolidated Economy design)

| FR-ECO-001-C section | Alignment delta |
|---|---|
| §2.1 #1 | "UUID-backed digital account" -> "SubjectId-backed digital account (UUID identity resolved through FR-ID)" |
| §2.1 #3 | Safe lazy provisioning for a UUID already known to PlayerData AND already provisioned as a subject |
| §2.3 | Current target input: UUID/name/registry number resolve to SubjectId before any Economy operation; name input remains gated on FR-DATA-003 |
| §4.1 | Authoritative state keys accounts by SubjectId; supply reconciliation unchanged |
| §9/§10/§11 | Provider prepares and binds SubjectId; emergency envelope retains typed PLAYER_UUID |
| §12 | Replacement snapshot keys target account by SubjectId; success receipt links shared attempt id |
| §15 | Acceptance adds: subject-less account rejected; same subject never yields a second account; number routes to the same account |

FR-ECO-001-A §2.2/§3.1/§4.1 and FR-ECO-001-B §2 are aligned by the same
decisions; the consolidated implementation design will carry the final
wording.

---

## 4. Consistency Checks

| Check | Result |
|---|---|
| One-person one-account invariant (FR-ID §5) | Preserved |
| No balance in registry / no subject in PlayerData | Preserved |
| Public number sole routing address | Preserved |
| UUID still authoritative player identity | Preserved |
| Disclosure defaults (FR-ECO-001-B §4) | Unchanged |
| Emergency authority owned only by FR-EMG | Unchanged |
| Durability gate dependency (FR-CORE-002) | Unchanged; emergency still blocked until gate |
| Offline-name input disabled until FR-DATA-003 | Unchanged |

---

## 5. Non-Goals

- Implementation; changes to FR-ID/FR-ECO-001-A/B/C/FR-DATA-003; new public
  numbers; second accounts; balance policy; cash; name-lookup enabling.

## 6. Acceptance Matrix

| Test | Expected |
|---|---|
| Provision order | PlayerData -> subject -> account; no account without subject |
| Repeated ensure | Same SubjectId -> same account and number |
| UUID/name/number inputs | All converge to the same SubjectId account |
| Subject revoked/suspended | Economy policy fail closed where unresolved |
| Emergency ISSUE/RECLAIM | Plan binds SubjectId; snapshot keyed by SubjectId |
| Number routing | Number addresses the same single account |
| No balance in registry | Codec rejects financial fields |
| No subject in PlayerData | Codec rejects subject fields |

---

## 7. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent Economy/identity
review requested.
