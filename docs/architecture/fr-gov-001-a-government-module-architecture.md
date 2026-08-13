# FontaineRepublic Government Module Architecture v1.0

> **Task ID:** FR-GOV-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Government positions, ministries, offices, and official
> appointments
> **Dependency:** FR-CORE-002、FR-CIT-001、FR-INST-002（机构访问边界）、FR-AUD-001、
> FR-ID-001、FR-CMD-001-A、FR-DATA-001
> **Implementation Status:** Not authorized

---

## 1. Purpose

Roadmap v1.1 Phase 10 / Alpha 0.5 defines Government: `GovernmentPosition +
Ministry + Office` with position management, official appointment/dismissal,
and department definitions. This design turns that into a concrete module
consistent with the approved batch (four-pillar separation, physical
institution rule, political-vs-technical separation).

Government is the **executive** pillar: it administers laws and runs public
services. It does not legislate (Parliament), adjudicate (Court), or manage
the treasury (Central Bank).

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Positions / ministries / offices / appointments | FR-GOV |
| Citizenship and political rank | FR-CIT（消费，不存储） |
| On-site official duty | FR-INST-002（政府设施+终端+现场上下文） |
| Technical permission / OP | 未来 Permission 系统；政治职位永不等同 |
| Audit | FR-AUD |
| Legislative / judicial / fiscal powers | Parliament / Justice / Central Bank |

Rules:

- Political office never grants technical permission (rank/OP separation);
- Authoritative official actions (appoint/dismiss/sign records) require a
  valid on-site context at a registered Government facility/terminal
  (FR-INST-001-A §5.2; FR-INST-002);
- Remote information/preparation allowed (capability classes);
- No hard-coded coordinates; no cross-module NBT; SavedData/NBT only.

---

## 3. Data Model

### 3.1 GovernmentPosition

| Field | Type | Rule |
|---|---|---|
| `positionId` | stable id | Server-assigned; immutable |
| `ministryId` | stable id | Belongs to a ministry |
| `title` | bounded string | Display |
| `state` | `FILLED` / `VACANT` / `SUSPENDED` | Lifecycle |
| `holderRef` | optional OwnerReference | PLAYER_UUID or future OFFICE |
| `positionRevision` | positive long | +1 per committed mutation |

### 3.2 Ministry

`ministryId`, `name` (bounded), `state`, `revision`. Ministries define
departments; their detailed policy belongs to later designs.

### 3.3 Office

`officeId`, `positionId`, `holderRef`, `assignedAt`, `revokedAt` (optional),
`revision`. Appointment/dismissal changes are one snapshot each.

### 3.4 GovernmentStore

```text
fontainerepublic.dat
└── modules
    └── government
        ├── StoreVersion / StoreRevision
        ├── Positions / Ministries / Offices
```

Strict codec + fail-closed load + deterministic encoding + bounded limits.

---

## 4. Service Contract

```java
// server/government/api/GovernmentService（概念）
interface GovernmentService {
    PositionReceipt createPosition(CreatePositionRequest request);
    MinistryReceipt createMinistry(MinistryDraft draft);
    AppointmentReceipt appoint(UUID actor, PositionId position, OwnerReference holder,
                               OnSiteContext context);
    AppointmentReceipt dismiss(UUID actor, PositionId position, OnSiteContext context,
                               String reason);
    Optional<Office> currentOffice(PositionId position);
    List<PositionProjection> positionsByMinistry(MinistryId ministry);  // 有界
}
```

- `appoint`/`dismiss` are `ONSITE_OFFICIAL_DUTY`: require `InstitutionAccessService
  .validateAtMutation(context, ONSITE_OFFICIAL_DUTY, now)` at the final boundary;
- holder resolved through PlayerData/FR-ID Services; never a game name;
- each authoritative mutation: one replacement snapshot ->
  `commitModuleData("government", ...)` -> publish only after `COMMITTED`;
- read queries bounded; no enumeration API.

---

## 5. Commands and Consumption

- Future commands (after separate CMD contribution): `/fr government ministry
  create|list`, `/fr government position create|list`, `/fr government appoint|dismiss`
  (on-site gated), `/fr government office` (own/read).
- Future Parliament consumes positions/ministries through Services for
  eligibility and oversight; never touches government NBT.
- FR-INST-002 provides the on-site context; FR-AUD records authoritative
  actions; FR-EMG remains the emergency break-glass.

---

## 6. Acceptance Matrix

| Test | Expected |
|---|---|
| Ministry/position creation | Single snapshot + gate |
| Appointment/dismissal | Requires valid on-site context; single snapshot |
| Holder resolution | Via Services; invalid rejected |
| No technical permission | Rank/office never maps to OP (reflection/source guard) |
| Four-pillar boundary | No legislative/judicial/fiscal authority methods |
| Strict codec / restart | Fail closed; recovery |
| No enumeration | Bounded queries only |
| On-site absent | Official mutation rejected without context |

---

## 7. Non-Goals

- Legislative/judicial/fiscal powers; political policy; GUI/package; technical
  permissions; election process (Parliament); implementation.

## 8. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
