# FontaineRepublic Shared Institution Access Boundary Architecture v1.0

> **Task ID:** FR-INST-002-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Shared facility/terminal/on-site-context boundary for the four
> institutions (FR-INST-001-A §15.1)
> **Dependency:** FR-INST-001-A/B（已批准）、FR-LAND-001（空间基础，已实现）、
> FR-CORE-002、FR-AUD-001、FR-CIT-001、FR-CMD-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

FR-INST-001-A requires the four institutions (Parliament, Government, Court,
Central Bank) to be physical places with registered facilities and terminals,
and one **shared institution-access boundary** so position/terminal checks are
not duplicated per module. This design defines that boundary concretely.

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Facility/terminal registration | Shared boundary（消费 FR-LAND 空间数据） |
| On-site context issuance/validity | Shared boundary |
| Institution roles/permissions | Future module designs（consumed, not owned） |
| Emergency recovery | FR-EMG（break-glass） |
| Spatial parcel/zone data | FR-LAND（只读消费，不复制） |

Rules:

- No hard-coded coordinates; facility regions come from FR-LAND parcels/zones;
- No per-module duplicate of position/terminal logic;
- Client/phone never asserts presence; context is server-runtime only;
- On-site context is short-lived, bounded, cleared on shutdown;
- Final mutation-time revalidation is mandatory and cannot be disabled.

---

## 3. Data Model（命名空间 `institution-access`）

```text
institution-access
├── StoreVersion / StoreRevision
├── Facilities: <facilityId> -> Facility
│   ├── institutionType, parcelId (FR-LAND), lifecycle state, revision
└── Terminals: <terminalId> -> Terminal
    ├── facilityId, institutionType, position (dimension + block/entity),
    │   capabilitySet, lifecycle state, integrity (anti-clone), revision
```

Facility lifecycle: ACTIVE / SUSPENDED / RELOCATING / DISABLED（FR-INST-001-A §6.1）。
Terminal registration requires a valid FR-LAND parcel reference; a terminal not
inside its facility's registered region is invalid.

---

## 4. On-Site Context Service

```java
// server/institutionaccess/api/InstitutionAccessService（概念）
interface InstitutionAccessService {
    OnSiteContext issueOnSiteContext(UUID playerId, TerminalId terminalId,
                                     CapabilityClass capability, Duration ttl);
    ValidationResult validateAtMutation(OnSiteContext context,
                                        CapabilityClass capability, long now);
    void invalidateOnLeave(UUID playerId);   // 移动/维度/登出/死亡事件
}
```

- Context binds player/institution/facility/terminal/capability/issue-time/expiry/
  facility+terminal revisions（FR-INST-001-A §7.2）；
- Public workflow: 6 blocks / 2 min / single-use（FR-INST-001-B §3.1）;
  official routine: 10/60 min session; high-risk: secure terminal / 30 s /
  single-use（FR-INST-001-B §3.2-3.3）；
- Presence: bounded 1-second check only for players with active contexts +
  lifecycle events（FR-INST-001-B §4）;
- All parameters server-configurable; final revalidation non-disableable.

---

## 5. Consumption Contract

```text
Parliament / Government / Court / Central Bank action
    -> InstitutionAccessService.validateAtMutation(context, capability)
    -> module Service（业务校验）
    -> Repository / SavedData mutation（FR-CORE-002 gate）
```

- Modules never read facility/terminal NBT directly; they call the service;
- Capability classes per FR-INST-001-A §4（REMOTE_* / ONSITE_* / EMERGENCY_RECOVERY）;
- Emergency actions bypass on-site via FR-EMG（never the normal path）;
- No-client parity: standard interaction with the registered terminal + chat
  feedback（FR-INST-001-A §11）.

---

## 6. Relationship to Approved Designs

| Design | Relationship |
|---|---|
| FR-INST-001-A/B | Boundary contract implemented; defaults from B |
| FR-LAND-001 | Facility regions = FR-LAND parcels/zones（Service-only） |
| FR-ECO-001-A | Central Bank official duties require valid context（已接线约束） |
| FR-EMG-001-A | Emergency break-glass independent of facilities |
| FR-AUD-001 | Facility/terminal changes and on-site actions audited |
| FR-CORE-002 | Authoritative registration mutations use the durable gate |

---

## 7. Acceptance Matrix

| Test | Expected |
|---|---|
| Facility/terminal registration | Valid FR-LAND reference; single snapshot + gate |
| On-site context issue/validate | Capability match; expiry; revision binding |
| Leave/return | Old context invalid; new interaction required |
| Public/official/high-risk defaults | Per FR-INST-001-B |
| Final revalidation | Mandatory at mutation boundary |
| No hard-coded coordinates | Source-scan guard |
| No-client parity | Standard interaction + chat path |
| Emergency override | FR-EMG only; normal path unaffected |
| Restart | Contexts cleared; registrations recovered |

---

## 8. Non-Goals

- Institution business permissions; building appearance; political rules;
  monetary policy; GUI; per-module duplicates; implementation.

## 9. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
