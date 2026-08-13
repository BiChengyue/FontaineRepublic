# FontaineRepublic Land Module Architecture v1.0

> **Task ID:** FR-LAND-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Republic-owned land parcels, usage rights, zoning, access control,
> and violation-report entry
> **Dependency:** FR-CORE-001, FR-CORE-002, FR-DATA-001, FR-ID-001-A,
> FR-CIT-001-A, FR-AUD-001-A, FR-CMD-001-A, FR-NET-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

Roadmap v1.1 Phase 4-6 defines the land system: `LandManager + LandParcel +
UsageRight + LandAccess + LandSavedData`, a `PermissionResolver`, build and
interaction events, and `/fr land` commands. Core decisions:

- **Ownership is permanently `REPUBLIC`**; players may hold only usage rights
  (roadmap: "玩家拥有土地所有权 永不").
- No automatic compliance determination — building-vs-planning is judged by
  humans (roadmap: "自动违规判定 永不").
- The land module provides the spatial ownership foundation that
  FR-INST-001-A/B later consume for institution facilities (without hard-coded
  coordinates).

FR-LAND does **not** own: economic transactions (LandMarket is Beta), judicial
verdicts, planning approval, or technical permissions beyond land access.

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Parcel ownership | Permanently REPUBLIC; no transfer ever |
| Usage rights | FR-LAND (grant/renew/revoke) |
| Zone type / planning designation | FR-LAND (set) |
| Land access policy | FR-LAND (set; consumed by PermissionResolver) |
| Violation report entry | FR-LAND (create); adjudication by Justice later |
| Citizenship / subject | FR-CIT / FR-ID (consumed, not stored) |
| Balance / payments | Economy (not stored) |
| Institution facilities | Future FR-INST implementation consumes FR-LAND spatial data |
| Technical permissions | Future Permission system; land access is domain-specific |

Rules:

- No ownership transfer, sale, or lease in Alpha (LandMarket is Beta);
- No hard-coded coordinates anywhere;
- No automatic building-compliance judgment;
- Client/phone state is never authoritative;
- All persistent state in SavedData/NBT under namespace `land`;
- Usage grants and access changes are authoritative mutations through the
  FR-CORE-002 durable gate.

---

## 3. Data Model

### 3.1 LandParcel

| Field | Type | Rule |
|---|---|---|
| `parcelId` | stable id | Server-assigned; immutable |
| `dimension` | canonical string | World dimension |
| `region` | bounded box | Min/max block coords; validated bounds |
| `zoneType` | `ZoneType` | Planning designation |
| `ownership` | constant `REPUBLIC` | Never mutable |
| `access` | `LandAccess` | Default / allow / deny lists (bounded) |
| `usageRights` | map holder -> UsageRight | Bounded per parcel |
| `parcelRevision` | positive long | Increments once per committed mutation |

### 3.2 UsageRight

| Field | Type | Rule |
|---|---|---|
| `holder` | typed `OwnerReference` | `PLAYER_UUID:<uuid>` now; future types via reviewed adapter |
| `usageType` | `UsageType` | `USAGE_GRANT` only in Alpha (roadmap) |
| `grantedAt` / `expiresAt` | epoch millis | Server-assigned; optional expiry |
| `rightRevision` | positive long | Increments once per change |

Renewal updates `expiresAt`; automatic renewal is Beta (roadmap).

### 3.3 ZoneType / LandAccess

```text
ZoneType: RESIDENTIAL, COMMERCIAL, PUBLIC, GOVERNMENT, AGRICULTURAL, PROTECTED, OTHER
LandAccess: PUBLIC, RESTRICTED, PRIVATE   // resolved against holder/role at event time
```

Zone and access are descriptive policy inputs for `PermissionResolver`; they
never grant technical permission by themselves.

### 3.4 ViolationReport

`reportId`, `parcelId`, reporter holder, description (bounded), server time,
status (`OPEN` initial). Adjudication, evidence, and verdict belong to the
future Justice module; FR-LAND only creates the entry and exposes it read-only.

### 3.5 LandStore

```text
fontainerepublic.dat
└── modules
    └── land
        ├── StoreVersion: int
        ├── StoreRevision: long
        ├── Parcels: compound     // parcelId -> LandParcel
        ├── HolderIndex: compound // holder key -> parcelId[] (bounded)
        └── Reports: compound     // reportId -> ViolationReport (bounded)
```

Strict codec: declared fields only, canonical ids, bounded region/report
strings, deterministic ordering, fail-closed load (unknown fields/newer
version rejected), whole-snapshot validation.

---

## 4. Service Contract

```java
// server/land/api/LandService.java (conceptual)
public interface LandService {
    LandParcel createParcel(CreateParcelRequest request);        // authoritative
    UsageReceipt grantUsage(UUID actor, ParcelId parcelId, OwnerReference holder, long durationMillis);
    UsageReceipt renewUsage(UUID actor, ParcelId parcelId, OwnerReference holder, long durationMillis);
    UsageReceipt revokeUsage(UUID actor, ParcelId parcelId, OwnerReference holder);
    LandReceipt setZoneType(UUID actor, ParcelId parcelId, ZoneType zone);
    LandReceipt setAccess(UUID actor, ParcelId parcelId, LandAccess access);
    ViolationReceipt createViolationReport(ViolationDraft draft);
    Optional<LandParcel> getParcel(ParcelId parcelId);
    boolean canBuild(UUID player, ParcelId parcelId);     // PermissionResolver
    boolean canBreak(UUID player, ParcelId parcelId);
    boolean canInteract(UUID player, ParcelId parcelId);
}
```

Rules:

- All mutations on the logical server owner thread; each authoritative
  mutation builds one complete replacement snapshot and commits via
  `DataManager.commitModuleData("land", ...)`; publish only after `COMMITTED`;
- holders resolved through PlayerData/FR-ID Services (never raw NBT);
- read queries bounded; no enumeration API;
- `PermissionResolver` is config-driven; rank/OP never hard-coded (GOD rule);
- events (Build/Interaction) call the resolver at event time; a cached
  decision is never authoritative.

---

## 5. Events and Commands

- `BuildEventHandler` / `InteractionEventHandler` (BlockEntity interaction
  only, per Phase 0 design) consult `PermissionResolver.canBuild/Break/Interact`
  and fail closed on unknown parcels.
- Commands (after separate CMD contribution approval):
  `/fr land info`, `parcel create|list`, `usage grant|renew|revoke`,
  `zone set`, `access set`, `report` — bounded, non-enumerating feedback.
- No client packets carry authoritative land state; S2C presentation is
  non-authoritative and optional (FR-NET-001-A).

---

## 6. Relationship to Approved Designs

| Design | Relationship |
|---|---|
| FR-CORE-002-A | Authoritative land mutations use the durable gate |
| FR-ID-001-A | Parcel holder refs resolve via Services; no registry numbers stored |
| FR-CIT-001-A | Grant eligibility may consult citizenship; not stored in land |
| FR-ECO-001-A/C | No payments in Alpha; LandMarket deferred to Beta |
| FR-AUD-001-A | Land mutations recorded via AuditService (authoritative path) |
| FR-INST-001-A/B | Future institution facilities consume FR-LAND spatial data through the shared access boundary; no hard-coded coordinates |
| FR-DATA-003-A | Name lookup resolves to UUID before holder resolution |

---

## 7. Acceptance Matrix

| Test | Expected |
|---|---|
| Ownership immutable | `ownership` always REPUBLIC; no transfer API |
| Grant/renew/revoke | One snapshot; revisions +1; committed only after gate |
| Holder resolution | UUID via Services; invalid holder rejected |
| Zone/access change | Authoritative; deterministic codec |
| Violation report | Entry created; read-only; Justice integration later |
| PermissionResolver | Config-driven; rank/GOD has no bypass; events fail closed |
| No hard-coded coordinates | Code review + tests reject magic coords |
| No auto-compliance | No building-rule engine exists |
| Strict codec / restart | Unknown fields rejected; orphans cleaned; records recovered |
| Enumeration | No bulk land API |

---

## 8. Non-Goals

- Ownership transfer, sale, lease, auction (LandMarket Beta); dynamic pricing;
- automatic compliance; judicial adjudication; economic transactions;
- technical permissions beyond land access; GUI; client authority;
- hard-coded facility coordinates; institution workflow implementation.

## 9. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
