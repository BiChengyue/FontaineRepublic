# FontaineRepublic Economy Account Disclosure and Cash Extension Policy v1.0

> **Task ID:** FR-ECO-001-B
> **Status:** Approved Design Record — Human-Confirmed Decisions
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Account model, disclosure, and cash-extension policy supplement to FR-ECO-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

This document records Human-confirmed architecture decisions that supplement
the approved Economy architecture (FR-ECO-001-A):

- the current single digital account model;
- the future cash extension seam;
- the default disclosure policy;
- the future public-disclosure extension boundary.

This document records decisions only. It does not authorize implementation of
cash, ATM, Economy, Central Bank, GUI, commands, or packets.

---

## 2. Current Account Model

The current phase uses **one authoritative digital account balance per
player** (FR-ECO-001-A Section 3.1).

The current phase does NOT implement:

- personal cash balance;
- personal deposit/withdraw workflow;
- physical banknotes;
- ATM behavior;
- interest;
- cryptocurrency.

Mobile and no-client access MAY provide:

- own balance;
- own history;
- ordinary player-to-player payment;
- payment and transaction notifications.

Central Bank `deposit/withdraw` remains an **official technical operation**
for authorized issuance, recovery, or account adjustment. It is NOT a
personal cash workflow (FR-ECO-001-A Section 6.4; FR-INST-001-A Section 5.4).

---

## 3. Future Cash Extension Seam

A future architecture boundary is preserved:

```text
Physical Cash System
    → Cash Exchange Service
    → EconomyService
    → Digital Account
```

This document reserves the boundary only. It does NOT reserve unused NBT
fields, enum values, item classes, or APIs.

Future cash integration MUST require:

- a separate approved design;
- schema migration where necessary;
- atomic cash/account exchange;
- anti-duplication validation;
- explicit value conservation rules.

### 3.1 Authority Boundary

- `EconomyService` remains the sole authority for digital account
  balances;
- a future Cash Exchange Service MAY orchestrate exchange but MUST NOT
  keep a duplicate authoritative digital balance;
- a future physical-cash system MAY own physical cash state only under its
  separately approved design;
- exchange MUST be atomic and value-conserving;
- failure MUST NOT leave both physical and digital value credited;
- no cash implementation, persistence field, API, item, or enum
  placeholder is introduced now.

---

## 4. Default Disclosure Policy

The current default disclosure policy:

| Item | Default |
|---|---|
| Player balance | Visible only to that player |
| Personal transaction records | Visible to participants |
| Exceptional official access | Requires business permission and auditing |
| Wealth leaderboard | Disabled |
| National treasury total balance | Public |
| Detailed treasury transactions | Restricted and audited |

This default policy answers the default-policy portion of FR-ECO-001-A
Open Question 4 (display authority for viewing other players' balances
and the treasury balance). The future public-disclosure extension
(Section 5) retains the non-default options.

### 4.1 Wealth Leaderboard and `/fr money top`

- the wealth leaderboard is disabled under the current default policy;
- `/fr money top` MUST NOT be implemented or registered in the current
  phase;
- it remains deferred to a future independently approved disclosure
  policy;
- that future policy MUST define whether presentation is named, anonymous,
  ranged, aggregated, or opt-in.

This document does not silently choose a future leaderboard format.

---

## 5. Future Public-Disclosure Extension

A policy boundary is preserved that MAY later transition toward public
balances or leaderboards.

The future disclosure policy MAY decide:

- access to another player's balance;
- whether a leaderboard exists;
- anonymous, named, ranged, or opt-in presentation;
- treasury transaction publication;
- player consent requirements.

The policy service MUST NOT be implemented now, and a future public mode MUST
NOT be hard-coded.

---

## 6. Decision Traceability

| Human decision | Recorded in |
|---|---|
| One authoritative digital account balance per player | Section 2 |
| No personal cash, deposit/withdraw workflow, banknotes, ATM, interest, or cryptocurrency in the current phase | Section 2 |
| Mobile and no-client access limited to balance, history, ordinary payment, notifications | Section 2 |
| Central Bank deposit/withdraw is an official technical operation, not a personal cash workflow | Section 2 |
| Cash extension seam direction (Physical Cash → Cash Exchange → EconomyService → Digital Account) with no placeholder reservations | Section 3 |
| Future cash integration requirements (separate design, migration, atomic exchange, anti-duplication, value conservation) | Section 3 |
| Default disclosure (balance private, records participant-only, leaderboard disabled, treasury total public, details restricted/audited) | Section 4 |
| Future public-disclosure options preserved, not implemented, not hard-coded | Section 5 |
| Default disclosure answers the default-policy portion of FR-ECO-001-A Open Question 4 | Section 4 |

---

## 7. Non-Goals

This document does not define or authorize:

- any implementation (cash items, ATM, GUI, packets, commands, policy
  service);
- the deposit/withdraw value-movement semantics (remains an open question —
  FR-ECO-001-A Section 14, question 1);
- monetary policy, taxation, interest, or markets;
- changes to any constraint in FR-ECO-001-A or FR-INST-001-A.

### 7.1 Informational Finding

FR-ECO-001-A's header still reads "Design Candidate — Pending Human
Review" although the document has been approved. This stale metadata is
an informational governance issue and is recorded here to be handled by
a separate approval record or a metadata-only task. FR-ECO-001-A itself
is NOT modified by this document.

---

## 8. Review Gate

This document records Human-confirmed decisions and does not constitute
implementation authorization. Independent architecture review is requested
after completion.
