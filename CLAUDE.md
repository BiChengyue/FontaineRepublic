# # FontaineRepublic Project Rules

## Project Identity

Project:

FontaineRepublic

Minecraft Version:

1.20.1

Forge Version:

47.4.18

Mod ID:

fontainerepublic

Branch:

develop

Architecture:

v2.7

Roadmap:

v1.1

Phase0 Technical Design:

v1.1

---

# Role

Your role is determined per task. Under the task-role model (docs/ai_recovery/ai_team_governance.md), you serve as Designer, Implementer, or Reviewer as assigned per task. Human is always the Approver.

Responsibilities:

- execute assigned tasks per approved design

- verify technical correctness

- report risks and scope violations

- maintain project state

Follow:

- Architecture v2.7

- Development Roadmap v1.1

- Phase0 Technical Design v1.1

---

# Current Progress

Current Phase:

Phase 1 - Infrastructure Layer (implementation complete) + client first-release
feature set complete; real-machine Level 3 verification executed 2026-08-14 with
release-blocking findings.

Completed:

- Phase 0 Architecture Foundation

- Architecture v2.7 baseline

- FR-CORE-001 Core Framework runtime baseline
  (`0d22129e32f1f72d9e726de4421d5e61d514f7c1`)

- FR-DATA-002 Player Identity Infrastructure
  (`4d2876f05983bca0e7a1e79045d3cf3e5e6e571c`)

- FR-NET-001 Network Foundation
  (`e3e8da019665ced81553db442d103fa85d0cfe9c`)

- FR-CMD-001 Command Foundation
  (`f9e1806398723867e9cd1dc48ddcc04aeeb7be3e`)

- Full server-side surface: FR-CORE-002, FR-AUD-001, FR-ID-001,
  FR-ID-BOOTSTRAP-001, FR-CIT-001, FR-LAND-001, FR-ECO-001, FR-DATA-003,
  FR-CMD-USER-001/002, FR-INST-002-B, FR-GOV-001, FR-PAR-001, FR-PAR-002,
  FR-JUS-001, FR-ECO-002, FR-CMD-GUIDE-001, FR-EMG-001, FR-EMG-ECO-001,
  FR-EMG-CMD-001 — all implemented, independently reviewed (Level 1-2), merged
  into `develop`.

- Client first-release feature set (FR-CLIENT-001-IMPL-A/B/B2/B3a/B3b):
  S2C presentation network surface and 10 `/frclient` views (money, citizen,
  history, notifications, guide, government, parliament, court, land + main
  menu), protocol v5, ledger IDs 0-8 — implemented, reviewed, merged, and
  smoke-verified.

- Communicator features (merged into `develop`):
  - FR-ITEM-001 (`fc4e8f1` `fde9e9f`) - portable Message Water Mirror item
    (right-click player -> transfer, block -> land, air -> main menu).
  - FR-TRADE-001 (`3c4b36f` `9872723`) - server-authoritative trade with
    atomic multi-leg settlement and 5% payer tax, protocol v6, ledger 9-15.
  - FR-MAIL-001 (`7118cf2` `c40c476`) - communicator mailbox, protocol v7,
    ledger 16-22.
  - FR-LAND-CLAIM-001 (`2a2354d`) - communicator land inspection/claim,
    protocol v8, ledger 23-26.
  - FR-LAND-002 (`ecc0edd` `046bfb6`) - personal land-usage rights view,
    protocol v9, ledger 27-28.

Current:

Current protocol is v10 with 30 production messages and 15 runtime
modules (FR-TRADE-003-A added the trade XP offer message ID 29 and experience
fields to the trade snapshot ID 15).
The full client feature set (all ten `/frclient` views plus the communicator
water mirror, trade, mail, land claim, and personal land-rights view) is
implemented, reviewed, merged into `develop`, and smoke-verified.

Real-machine Level 3 verification was executed on 2026-08-14
(docs/ai_recovery/evidence/FR-LEVEL3-FULL-RUNTIME-20260814-REPORT.md). It found
two release-blocking defects plus several FAIL/PARTIAL items. Both release
blockers are now fixed, reviewed, built, smoke-verified, merged into `develop`,
and pushed to origin:
1. mail data loss on clean stop/restart — root cause was the FR-CORE-002 100 ms
   shared durable-commit rate guard silently dropping the mail commit that
   immediately follows an economy commit; fixed with a bounded retry in the mail
   store (merge `c845898`).
2. no-FR-client join — the custom `fontainerepublic:communicator` item broke
   registry equality; fixed by FR-ITEM-002-A (vanilla clock carrier + bounded NBT
   + HMAC-SHA-256 signature + owner binding, persisted HMAC key, legacy id
   migration via MissingMappingsEvent; merge `dc211d0`).
A dedicated-server smoke (tmp/smoke-registry-20260814) reached Ready with
ExitCode=0, protocol v9/29 frozen, and the legacy id remapped cleanly.

Next:

Remaining before the next release candidate:
1. (done) communicator right-click-air offhand conflict + deprecation warnings
   (merge `5c9e12a`).
2. trade: FR-TRADE-003-A Secure Trade direct integration merged (`808e1b9`) —
   XP legs, identity, audit, protocol v10/30, SecureTrade MIT helpers. Deferred:
   the container-backed 54-slot menu (needs a custom MenuType that would break
   no-FR-client join, so the registry-safe plain TradeScreen + XP is used) and
   the durable trade journal (/fr trade history placeholder).
3. re-run real-machine Level 3 regression with Human (mail restart persistence,
   no-FR client join, /fr communicator issue + restart persistence, offhand
   right-click, XP trade, crash-window/RCON/old-protocol rejection).

Always read this section before starting work.

Never assume previous progress from memory.

---

# Scope Control

Do not implement future modules before their phase.

Do not add features because they seem useful.

If a feature belongs to a later phase:

- record suggestion

- do not implement

---

# Phase 0 Restrictions

Allowed:

- Core framework

- Configuration system

- Lifecycle management

- Data persistence foundation

- Network Foundation (framework-level networking only — no business packets)

Forbidden:

- Citizen module

- Land module

- Economy module

- Government module

- Justice module

- GUI systems

- Business networking (Citizen, Economy, Government, Land, Justice packets)

JSON backup/export:

Not implemented in Phase 0.

Scheduled for Alpha 0.2.

---

# Allowed Without Confirmation

You may:

- create files

- edit source code

- run Gradle commands

- run build commands

- run approved verification commands

---

# Ask Before

Request confirmation before:

- changing architecture documents

- changing roadmap documents

- adding dependencies

- changing Forge/Minecraft version

- deleting important files

- changing module boundaries

- changing core interfaces

Never:

- git reset --hard

- git push --force

- destructive branch operations

without explicit approval.

---

# Bidirectional Correction Mechanism

The implementation agent is not only an executor.

For every approved task:

## Before implementation

Read:

- [CLAUDE.md](http://CLAUDE.md)

- docs/architecture/

- docs/roadmap/

- relevant design documents

Confirm:

- task scope

- affected modules

- architecture constraints

---

## After implementation

Provide:

# Implementation Assessment

## 1. Task Understanding

Explain:

- implementation goal

- allowed scope

- excluded scope

## 2. Technical Evaluation

Evaluate:

- implementation approach

- Forge/Minecraft compatibility

- maintainability

## 3. Risk Analysis

Report:

- bugs

- compatibility risks

- architecture risks

- future maintenance risks

## 4. Architecture Consistency

Check:

- module boundaries

- dependencies

- server authority principle

## 5. Suggestions

Provide improvements.

---

# Architecture Impact Check

Before every commit output:

## Architecture Impact

Check:

- [architecture.md](http://architecture.md)

- roadmap documents

- [CLAUDE.md](http://CLAUDE.md)

- core interfaces

- module boundaries

- dependency structure

Rules:

- Code changes inside approved scope are allowed.

- Architecture changes require approval.

- Never silently change design principles.

If architecture impact exists:

stop and request review.

---

# Project Progress Maintenance

[CLAUDE.md](http://CLAUDE.md) is the project state record.

After every milestone update:

- Current Phase

- Completed Tasks

- Current Task

- Next Task

Milestone completion must keep:

Code state

=

Documentation state

=

Git history state

---

# Verification Strategy

Use layered verification.

## Level 1: Code Review

Check:

- logic correctness

- architecture consistency

- API usage

## Level 2: Build Verification

Run:

./gradlew build

## Level 3: Runtime Verification

Required for:

- Forge lifecycle

- Server events

- SavedData

- Networking

- World interaction

Prefer:

Dedicated server runtime verification

over creating unit test infrastructure.

Do not create test frameworks unless explicitly approved.

---

# Verification and Fix Separation

During verification:

If a bug is discovered:

1. Record the issue.

2. Finish current verification report.

3. Create a separate fix task.

Do not mix:

verification

+

unrelated fixes

---

# Temporary File Rules

During testing:

Avoid creating unnecessary:

- temporary Java classes

- test frameworks

- runtime files

Never automatically delete:

- run/world

- server data

- important project files

without confirmation.

---

# Git Workflow

Implementation work is done on a feature branch. Commits on a feature branch before Human Approval are normal.

After completing an approved task with Human Approval:

1. Ensure feature branch is up to date.

2. Run:

./gradlew build

3. Review changed files.

4. Update [CLAUDE.md](http://CLAUDE.md) if milestone completed.

5. Merge feature branch to develop (merge request or direct merge per project conventions).

Before merge output:

## Git Summary

Commit Message:

...

Changed Files:

...

Build Result:

...

Affected Modules:

...

Architecture Impact:

...

Implementation Assessment:

...

---

# Minecraft Architecture Rules

Maintain:

Server authority.

Client must not decide:

- economy state

- permissions

- land ownership

- political state

- persistent data

Core modules must not depend on future business modules.

---

# Data Rules

All persistent data must use:

Minecraft SavedData / NBT

Business modules access data through approved Core APIs.

Do not directly manipulate other modules'' storage.

