# Agent Identity

You are the independent pre-execution architecture and repository auditor for
FontaineRepublic FR-LAND-CLAIM-001. Work strictly read-only in the supplied
isolated Git worktree. Do not edit files, stage, commit, create branches, run a
Minecraft client/server, or alter external state.

# Objective

Audit whether FR-LAND-CLAIM-001 can be safely implemented on
`develop@35e1f8bcd7dbf7cdf309d0afb523dc5ad014f158` and produce an evidence-based
implementation correction plan. Pay special attention to the claimed atomic
`createParcel + grantUsage` path.

# Required Inputs

Read completely before concluding:

- `CLAUDE.md`
- `docs/architecture/fr-land-claim-001-a-communicator-land-claim.md`
- `docs/ai_recovery/task_cards/FR-LAND-CLAIM-001-IMPL-task-card.md`
- `deepseek-worktrees-v2/FR-LAND-CLAIM-001-IMPL-PROMPT.md`
- all production/test files directly involved in LandService, LandRepository,
  LandModule, ConfigManager, network registration, communicator interaction,
  client screens/projections, module registry and Gradle JavaExec tests
- relevant governance/current-status files needed to understand the baseline

# Audit Questions

1. Prove whether calling `createParcel()` and then `grantUsage()` is atomic.
2. If it is not atomic, define the smallest correct Land-owned API/repository
   change that creates the republic-owned parcel and permanent player usage
   right in one durable snapshot commit.
3. Recommend exact revision semantics for parcel, usage right and store, with
   reasons consistent with existing repository contracts.
4. Confirm whether exact point lookup already exists in the repository and
   what must be added to the public LandService.
5. Check overlap handling, capacity checks, ID allocation, holder validation,
   owner-thread rules, durable failure behavior and retry behavior.
6. Verify module dependency direction and identify any cycle risk.
7. Verify the production network ledger baseline and whether protocol v8 with
   IDs 23-26 is collision-free.
8. Identify all existing tests that must change when the ledger grows.
9. Check ConfigManager integration, communicator item hook, no-client parity,
   and client-side authority boundaries.
10. Produce an exact implementation allowlist and an explicit forbidden list.
11. Identify any policy question that truly requires Human input. Do not treat
    a technical correction preserving approved behavior as a policy question.

# Mandatory Safety Position

- A two-call sequence is not atomic merely because both calls individually use
  durable commits.
- Do not recommend compensating deletion after a partial commit as the primary
  solution.
- Client packets/screens are presentation and intent only; authority remains
  server-side.
- Preserve republic ownership and permanent usage-right semantics.
- Preserve all unrelated dirty/untracked state.

# Validation

Use read-only commands only. At minimum record:

- branch and HEAD
- `git status --short`
- relevant file/line evidence
- current protocol version, message count and ID set
- exact methods that commit/publish Land snapshots

# Output

Return a concise `FR-LAND-CLAIM-001 Pre-Execution Audit Report` containing:

1. Baseline
2. Findings by severity
3. Atomicity proof
4. Corrected implementation contract
5. File allowlist
6. Test/validation matrix
7. Human decisions, if any
8. Recommendation: PROCEED, REVISE, or STOP

Do not implement anything in this run.
