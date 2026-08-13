# Current Status

> Last updated: 2026-08-13
> Source of truth: CLAUDE.md, git history, codebase state

## Phase

Phase 1 - Infrastructure Layer (implementation complete; pending review/merge)

## Current Step

Phase 1 infrastructure is complete and merged to `develop`. Phase 2 design
candidates (identity registry, economy, institutions, emergency authority)
were produced on 2026-08-02 and await independent review before approval and
implementation dispatch.

Phase 2 design candidates were independently reviewed on 2026-08-13
(FR-PHASE2-DESIGN-REVIEW-01, conditional pass; see
`docs/ai_recovery/evidence/FR-PHASE2-DESIGN-REVIEW-01-AUDIT-REPORT.md`), and
three follow-up design candidates were added: FR-CORE-002-A (durable commit
gate), FR-AUD-001-A (audit module), FR-ECO-001-C-ACCOUNT-ALIGN-01 (account
key alignment).

## Completed Tasks

| # | Task | Commit | Date |
|---|---|---|---|
| 1 | Architecture v2.7 baseline | `256d6bd` | 2026-07-27 |
| 2 | FR-CORE-001 Core Framework runtime baseline | `0d22129` | 2026-07-28 |
| 3 | FR-DATA-002 Player Identity Infrastructure | `4d2876f` | 2026-07-29 |
| 4 | FR-NET-001 Network Foundation | `e3e8da0` | 2026-07-29 |
| 5 | FR-CMD-001 Command Foundation | `f9e1806` | 2026-07-30 |

### FR-CORE-001

- Provides the approved Core Framework runtime baseline.
- Completion commit: `0d22129e32f1f72d9e726de4421d5e61d514f7c1`.

### FR-DATA-002

- Provides the shared Player Identity Infrastructure.
- Uses UUID identity, SavedData persistence, revision control, a service layer,
  and strict NBT validation.
- Does not implement Citizen, Economy, Government, Court, Land, GUI, packets,
  or client synchronization.
- Human-approved commit:
  `4d2876f05983bca0e7a1e79045d3cf3e5e6e571c`.

### FR-NET-001

- Provides the framework-level Network Foundation.
- Implements message registration, protocol versioning, rate limiting,
  server-side dispatching, and production message table.
- Does not implement business packets (Citizen, Economy, Government, Land,
  Justice).
- Implementation commit:
  `e3e8da019665ced81553db442d103fa85d0cfe9c`.
- On feature branch `codex/fr-net-001-implementation`.

### FR-CMD-001

- Provides the framework-level Command Foundation.
- Implements `/fr` root, `/fr help`, `/fr admin status`, `/fr admin modules`,
  contribution registry with freeze, runtime resolver, and feedback helpers.
- Does not implement business commands (Citizen, Economy, Government, Land,
  Justice).
- Implementation commit:
  `f9e1806398723867e9cd1dc48ddcc04aeeb7be3e`.
- On feature branch `codex/fr-cmd-001-implementation`.

## Current Task

Design-candidate phase: FR-CORE-002-A / FR-AUD-001-A /
FR-ECO-001-C-ACCOUNT-ALIGN-01 produced; awaiting Human confirmation of the
Phase 2 design batch before implementation designs.

## Next Task

Review the six Phase 2 design candidates and issue approval recommendations;
then dispatch implementation of approved designs (Audit -> Citizen -> Land ->
Economy per roadmap order).

## Phase 2 Design Candidates (added 2026-08-02)

All are design candidates only — implementation is not authorized until
approved. Committed on `codex/fr-inst-001-a-design`.

| Design | Content | Status |
|---|---|---|
| FR-INST-001-A/B | Institutions (Parliament/Government/Court/Central Bank) are physical places; client is a "mobile device" | Pending review |
| FR-ECO-001-A/B/C | Economy Phase 1 scope: UUID accounts, login provisioning, balance/history; `economy.issue`/`economy.reclaim` emergency catalogue | Pending review |
| FR-EMG-001-A | Hydro Archon emergency authority architecture (actor verification, journaling, audit index) | Pending review |
| FR-ID-001-A | Unified digital subject registry; public number `TT-NNNNNN-CC`; two fixed Hydro Archon numbers | Pending review |
| FR-DATA-003-A | Safe player directory (exact name -> UUID -> subject -> account); NOT yet committed | Pending review |
| FR-CORE-002-A | Durable commit gate (fsync + atomic replace) required by FR-ID/FR-DATA-003/FR-EMG/Economy emergency | Pending review (added 2026-08-13) |
| FR-AUD-001-A | Append-only audit module (roadmap Phase 2 gap) | Pending review (added 2026-08-13) |
| FR-ECO-001-C-ACCOUNT-ALIGN-01 | Economy account keying aligned to SubjectId per FR-ID | Pending review (added 2026-08-13) |

## Scope Boundaries

- Phase 1 infrastructure is complete. FR-NET-001 and FR-CMD-001 are on feature
  branches awaiting review.
- No business packets, feature-module commands, or client-side features are
  authorized.
- Citizen is not the immediate implementation task.

## Known Risks

- ConfigManager currently has no configuration entries.
- Data loss on hard JVM termination remains an inherent SavedData limitation.
- Phase 2 design candidates passed independent review (conditional) but await
  Human confirmation; two blockers remain: account-key alignment (resolved by
  FR-ECO-001-C-ACCOUNT-ALIGN-01 candidate) and the FR-CORE-002 durability gate.
- Implementation dispatch network to opencode.ai was unreachable on
  2026-08-13 (probe shelved until network/proxy available).
- `develop` is two commits ahead of `origin/develop` (FR-CMD-001 not pushed).
- Workspace contains historical uncommitted changes and stray directories
  (`.reasonix` state, deepseek-worktrees, credential-stage, etc.) not yet
  cleaned; cleanup pending user confirmation.

## Project Baselines

- Minecraft: 1.20.1
- Forge: 47.4.18
- Java: 17
- Architecture: v2.7
- Roadmap: v1.1
- Phase 0 Technical Design: v1.1
