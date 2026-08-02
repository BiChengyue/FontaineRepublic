# FontaineRepublic Institution Session and Zone Policy v1.0

> **Task ID:** FR-INST-001-B
> **Status:** Approved Design Record — Human-Confirmed Decisions
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Shared institution access model supplement to FR-INST-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

This document records Human-confirmed architecture decisions that refine the
approved institution physical-interaction boundary (FR-INST-001-A). It
defines a shared institution access model for Parliament, Government, Court,
and Central Bank:

- the citizen **public workflow** near registered public terminals;
- the official **routine workflow** inside registered internal work zones;
- the **high-risk workflow** for state-authority actions;
- presence detection rules;
- facility sub-zone support.

This document records decisions only. It does not authorize implementation of
facilities, zones, sessions, terminals, commands, GUI, packets, or any module
functionality.

---

## 2. Normative Language

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY**
retain the meaning defined in FR-INST-001-A Section 2.

- **Public workflow:** institutional business performed by citizens near a
  registered public terminal.
- **Official routine workflow:** routine institutional duties performed by an
  official within a registered internal work zone.
- **High-risk workflow:** state-authority actions requiring a secure terminal
  and single-use authorization.
- **Work session:** a server-side session for an official's routine duties
  within a registered internal work zone.
- **Work zone:** a registered sub-zone of an institution facility dedicated
  to official work.
- **Secure terminal:** a registered terminal inside a secure operations area
  used for high-risk actions.

---

## 3. Workflow Model

### 3.1 Public Workflow

Citizens perform institutional business near a registered public terminal.

Human-confirmed defaults:

| Parameter | Default |
|---|---|
| Terminal distance | 6 blocks |
| Context maximum lifetime | 2 minutes |
| Context binding | One business workflow per context |
| Successful submission | Consumes the context |
| Leaving range | Invalidates the context |
| Returning | Does NOT restore the context |

The public workflow is single-use: after a successful submission the context
is consumed and a new interaction with the terminal is required for the next
submission (answers FR-INST-001-A Open Question 19.4 for public workflows).

**Public-workflow failure policy:**

- input-validation or business-validation failure does NOT mutate business
  data;
- such failure does NOT consume the public workflow context;
- the citizen MAY correct and retry while the original two-minute context
  remains valid;
- leaving range, expiry, lifecycle invalidation, terminal revocation, or
  facility revocation still invalidates the context;
- repeated failures are subject to a future bounded abuse/rate-control
  policy;
- this task does not define or implement the rate limiter.

### 3.2 Official Routine Workflow

Officials MAY perform routine duties within a registered internal work zone.

Human-confirmed defaults:

| Parameter | Default |
|---|---|
| Idle timeout | 10 minutes |
| Hard session limit | 60 minutes |
| Idle refresh | Only valid institutional actions refresh idle time |
| Movement or AFK activity | Does NOT refresh idle time |
| Leaving the work zone | Invalidates the session |
| Logout, death, dimension change, server stop, facility revocation, terminal revocation, migration, destruction | Invalidates the session |

An official session is valid only for:

- the correct institution;
- a registered internal work zone;
- an active work session;
- actions inside the official's business authority.

Official status alone does NOT provide authority. Officials conducting
private business MUST use the citizen public workflow.

**Return after leaving (official routine workflow):** leaving the work zone
invalidates the session; returning to the work zone does NOT restore the
old session; a new terminal interaction is required.

### 3.3 High-Risk Workflow

High-risk state-authority actions MUST require all of:

- a valid official work session;
- the correct business authority;
- the corresponding registered secure terminal;
- a maximum distance of 6 blocks;
- single-use authorization;
- a maximum lifetime of 30 seconds;
- binding to the specific action and material parameters;
- final mutation-time revalidation.

Examples by institution:

| Institution | High-risk actions |
|---|---|
| Parliament | Formal voting; law publication |
| Government | Major administrative orders |
| Court | Formal judgment issuance |
| Central Bank | Issuance; destruction; freeze/unfreeze; major treasury transfer |

**High-risk failure policy:**

- once a high-risk request reaches the final submission boundary, its
  single-use authorization is consumed;
- consumption occurs whether the business mutation succeeds or fails;
- a failed mutation changes no business data;
- the consumed authorization cannot be replayed;
- retry requires a new high-risk single-use authorization;
- the underlying valid official routine work session is not automatically
  terminated by the failed high-risk submission;
- normal session invalidation rules still apply.

Authorization consumption is separate from business revision changes: a
failed business mutation MUST NOT increment the business revision or
partially persist data.

**Return after leaving (high-risk workflow):** leaving the terminal range
invalidates the high-risk authorization; returning does NOT restore it; a
new secure-terminal interaction is required.

---

## 4. Presence Detection

Presence detection MUST follow these rules:

- a bounded periodic check runs every one second, only for players with
  active contexts;
- lifecycle and location events (logout, death, dimension change, server
  stop, facility or terminal revocation, migration, destruction) invalidate
  contexts immediately;
- final mutation-time revalidation is mandatory and MUST NOT be disabled;
- the server MUST NOT scan all players without active contexts;
- no hard-coded world coordinates may be used for presence or zone checks.

---

## 5. Facility Sub-Zones

Facilities MUST support registered sub-zones, including:

- public lobby;
- public service area;
- internal office area;
- meeting or hearing area;
- secure operations area;
- terminal interaction range.

All parameters in Section 3 MUST be server-configurable. The final
mutation-time revalidation (Section 4) MUST NOT be disabled by configuration.

---

## 6. Four-Institution Applicability Matrix

| Institution | Public workflow | Official routine workflow | High-risk workflow |
|---|---|---|---|
| Parliament | Public terminal business | Internal office duties | Formal voting; law publication |
| Government | Public terminal business | Internal office duties | Major administrative orders |
| Court | Public terminal business | Internal office duties | Formal judgment issuance |
| Central Bank | Public terminal business | Internal office duties | Issuance; destruction; freeze/unfreeze; major treasury transfer |

The safety model is identical for all four institutions; only the
institution-specific business permissions differ, and those permissions are
owned by each module's future design, not by this document.

---

## 7. Spatial Ownership Dependency

This document does NOT define Land/City storage implementation. Facility
geometry and sub-zone registration depend on the future spatial ownership
model (FR-INST-001-A Section 15.2). Until the required spatial contracts
exist, production workflows MUST NOT be implemented with temporary
hard-coded coordinates.

---

## 8. Non-Goals

This document does not define or authorize:

- any implementation (facilities, blocks, block entities, zones, sessions,
  terminals, commands, GUI, packets);
- Land or City storage;
- institution-specific business permission models (belong to future module
  designs);
- changes to any constraint in FR-INST-001-A;
- monetary policy, legal doctrine, or political rules.

---

## 9. Decision Traceability

| Human decision | Recorded in |
|---|---|
| Public workflow defaults (6 blocks, 2 minutes, single-use, consume on submit, leave invalidates, return does not restore) | Section 3.1 |
| Official routine workflow defaults (10/60 minutes, action-only refresh, full invalidation list) | Section 3.2 |
| Official status alone gives no authority; private business uses citizen workflow | Section 3.2 |
| High-risk workflow requirements (secure terminal, 6 blocks, 30 seconds, single-use, action binding, final revalidation) | Section 3.3 |
| Presence detection (1-second bounded check, immediate events, mandatory final revalidation, no full scan, no hard-coded coordinates) | Section 4 |
| Sub-zone support and configurable parameters (final revalidation non-disableable) | Section 5 |
| Spatial ownership dependency recorded, Land/City storage not defined | Section 7 |
| Presence detection and session parameters answer the applicable part of FR-INST-001-A Open Question 19.3 (distance, timeout, leave-detection) | Sections 3, 4 |

This traceability does not claim to resolve facility ownership or
terminal-form questions (FR-INST-001-A Open Questions 19.1, 19.2).

---

## 10. Review Gate

This document records Human-confirmed decisions and does not constitute
implementation authorization. Independent architecture review is requested
after completion.
