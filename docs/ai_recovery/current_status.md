# Current Status

> Last updated: 2026-08-13
> Source of truth: CLAUDE.md, git history, codebase state

## Phase

Phase 1 - Infrastructure Layer (implementation complete; pending review/merge)

## Current Step

Phase 2 design batch was Human-confirmed on 2026-08-13
(FR-PHASE2-HUMAN-APPROVAL-01). Implementation phase is authorized per the
reviewed sequencing. FR-CORE-002 durable commit gate was implemented by the
deepseek v4 flash subprocess (commit `3813648`) and passed independent review
(Level 1-2; runtime verification pending). FR-AUD-001 audit module was
implemented (commit `2ffdd1d`) and passed independent review (Level 1-2;
runtime verification pending).

Infrastructure stage complete (FR-CORE-002 + FR-AUD-001 + FR-ID-001, all
implemented, independently reviewed Level 1-2, merged into develop).
Per Human instruction on 2026-08-13, further implementation dispatch is
resumed (Human: "批准阻断项实施；可以继续工作"). FR-ID-BOOTSTRAP-001
(original-person binding), FR-CIT-001 (citizen), and FR-LAND-001 (land)
implemented, reviewed Level 1-2, and merged into develop. FR-ECO-001
(economy player services) implemented, reviewed Level 1-2, and merged —
the approved implementation sequence (Audit -> Citizen -> Land -> Economy)
is complete. FR-DATA-003 (safe player directory) implemented, reviewed
Level 1-2, and merged into develop. FR-CMD-USER-001 (economy/citizen commands
+ login hooks) implemented, reviewed Level 1-2, and merged — the player-facing
layer is complete. FR-CMD-USER-002 (transfer by UUID / exact name / registry
number) implemented, reviewed Level 1-2, and merged.
FR-INST-002 (shared institution access boundary: facilities/terminals/on-site
contexts) implemented, reviewed Level 1-2, and merged.
FR-GOV-001 (government: positions/ministries/offices/appointments with on-site
gating) and FR-PAR-001 (parliament: proposals/votes/bills with legal state
machine and norm-level thresholds) implemented, reviewed Level 1-2, and
merged. FR-JUS-001 (justice: cases/evidence/verdicts with on-site gating and
Land intake) implemented, reviewed Level 1-2 (one defect found and fixed),
and merged — the institution sequence (Government -> Parliament -> Justice)
is complete.

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

Approved implementation sequence complete (7 modules) plus FR-DATA-003 and
FR-CMD-USER-001/002, FR-INST-002, FR-GOV-001, and FR-PAR-001, all implemented,
reviewed Level 1-2, merged into develop; FR-JUS-001 (justice) completes the
institution sequence. Pending per Human directives: player guidance after
features stabilize, then the UI-enabled client mod (FR Client, Beta/GUI).
Also pending: Level 3 runtime verification for all implemented modules
(checklist:
docs/development/LEVEL3-RUNTIME-VERIFICATION-CHECKLIST.md).

## Player Guidance

Player guide published at `docs/guide/player-guide.md`; in-game `/fr help`
per-module expansion (FR-CMD-GUIDE-001) dispatched. Client UI mod remains the
final phase per Human directive.

## Awaiting Human Decisions

1. Legacy v0 save handling (migration design candidate FR-DATA-MIGRATION-001-A
   ready; fresh-world path recommended);
2. Push develop to origin (local is 103 commits ahead);
3. Central Bank official duties (open policy questions);
4. Parliament extensions (guardian review/referendum/amendment);
5. Client UI mod start signal;
6. Workspace stray-directory cleanup confirmation.

Resolved 2026-08-13 ("都按建议"): legacy save = fresh world; push = done;
Central Bank defaults = all four proposals confirmed (pure ledger / public
treasury total with restricted details / single-use on-site context / own
balance only); Parliament extensions = deferred; client mod = final phase;
cleanup = approved set removed (credential-stage kept).

2026-08-14 Human directives: resume paused server-side tasks (Parliament
extensions, FR-EMG); institutions switch from terminals to registered zones
(FR-INST-002-B, implemented/reviewed/merged). FR-PAR-002 (legislative
extensions: guardian review / referendum / amendment pipeline) dispatched.
implemented/reviewed/merged. FR-EMG-001 (shared emergency infrastructure)
implemented/reviewed/merged. FR-EMG-ECO-001 (economy emergency action
catalogue: economy.issue / economy.reclaim provider + permanent success
receipts + receipt provider + registration wiring) implemented
(subprocess delivered persistence layer; Reviewer completed write surface/
provider/wiring/tests), independently reviewed (Level 1-2, full build green),
merged into develop, and full-stack smoke passed (12 modules initialized,
Ready, clean stop). Next server-side task: FR-EMG-CMD-001 (`/fr admin
emergency` command adapter) implemented, independently reviewed (Level 1-2,
full build green), merged into develop, and full-stack smoke passed (`/fr`
DEDICATED 6 contributions, 12 modules, Ready, clean stop). The paused
server-side task queue is now complete: zone-based institutions, parliament
extensions, shared emergency infrastructure, emergency action catalogue, and
the emergency command adapter are all implemented, reviewed, merged, and
smoke-verified. Client mod remains paused pending the Human start signal;
Level 3 real-machine verification of the emergency path is next.

## Next Task

Coordinate real-machine (Level 3) verification of the full server surface
with Human (console bootstrap, `/fr admin emergency` preview/confirm/inspect,
central bank on-site duties, crash window) per
docs/development/LEVEL3-RUNTIME-VERIFICATION-CHECKLIST.md §4.6. Client UI mod
enters the staged implementation phase per Human's established sequence:
Stage A (S2C presentation network surface, FR-CLIENT-001-IMPL-A) dispatched
2026-08-14, implemented, independently reviewed (Level 1-2, full build green,
protocol v2 + 3-message ledger, dedicated-server smoke clean with no client
classloading), and merged into develop. Stage B (GUI/HUD) materials are
prepared; Stage C is real-machine verification with Human. The Level 3 server
verification still awaits Human.

2026-08-14: FR-CLIENT-001-IMPL-B (client GUI/HUD/forms: /frclient command
surface, balance HUD/card, transfer form via /fr money pay, notifications,
live transaction flow, guide screen) implemented, independently reviewed
(Level 1-2, full build green). The full-stack smoke caught a Forge
DistExecutor.safeRunWhenOn safe-referent rejection of mod-owned client
classes; fixed by switching the mod-entry client init to an FMLClientSetupEvent
listener (dedicated server never loads client classes; smoke clean: 12 modules,
protocol v2, Ready, ExitCode=0). Stage B-1 merged into develop. Stage B-2
(citizen card / full history / institution-land views) and Stage C
(real-machine visual verification with Human) remain.

2026-08-14: FR-CLIENT-001-IMPL-B2 (citizen card + transaction history view,
protocol v3, ledger IDs 0-4, citizen-module presentation wiring, economy
history sync) implemented, independently reviewed (Level 1-2, full build
green, 31 tasks), merged into develop, and full-stack smoke passed (protocol 3,
5 messages, 12 modules, Ready, ExitCode=0, no client classloading). Client
stages A/B-1/B-2 complete. Remaining: Stage B-3 (institution/land views),
Stage C (real-machine visual verification with Human), and the Level 3 server
verification awaiting Human.

2026-08-14: FR-CLIENT-001-IMPL-B3a (government/parliament public info views,
protocol v4, ledger IDs 0-6, login snapshot via InstitutionPresentationSync)
implemented, independently reviewed (Level 1-2, full build green, 32 tasks),
merged into develop, and full-stack smoke passed (protocol 4, 7 messages, 12
modules, Ready, ExitCode=0, no client classloading). Player guide updated with
the FR Client usage section. Client stages A/B-1/B-2/B-3a complete. Remaining:
Stage B-3b (court/land views; land needs a new bounded read projection plus
design review), Stage C (real-machine visual verification with Human), and
the Level 3 server verification awaiting Human.

2026-08-14: FR-CLIENT-001-IMPL-B3b (court case summary + republic land
overview, protocol v5, ledger IDs 0-8, LandService.publicSummary single-value
aggregate) implemented, independently reviewed (Level 1-2, full build green,
33 tasks), merged into develop, and full-stack smoke passed (protocol 5,
9 messages, 12 modules, Ready, ExitCode=0, no client classloading). The
client first-release feature set is complete (10 /frclient views: money,
citizen, history, notifications, guide, government, parliament, court, land +
main menu). Remaining: personal land-usage rights view (future stage), Stage C
(real-machine visual verification with Human), and the Level 3 server
verification awaiting Human.

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
- Phase 1 infrastructure (FR-CMD-001 / FR-NET-001) passed independent review
  on 2026-08-13 (FR-CMD-001-REVIEW-04); remaining gaps are low-severity
  suggestions only.
- Phase 2 design candidates passed independent review (conditional) but await
  Human confirmation; two blockers remain: account-key alignment (resolved by
  FR-ECO-001-C-ACCOUNT-ALIGN-01 candidate) and the FR-CORE-002 durability gate.
- Dispatch pipeline verified working on 2026-08-13 (reasonix-cli v1.21.2 with
  `--output-format text -p`; probe succeeded). Early "network unreachable"
  conclusion was a sandbox test artifact and is retracted.
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
