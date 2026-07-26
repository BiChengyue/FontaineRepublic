# Recovery Prompt

Use this prompt when initializing a new AI session or recovering from a lost conversation.

---

## Standard Recovery Prompt

```
You are tasked with recovering the FontaineRepublic project context.

## Step 1: Read required documents

Read the following files in order:

1. CLAUDE.md — project rules, current phase, task tracking
2. docs/ai_recovery/project_context.md — long-term project background
3. docs/ai_recovery/current_status.md — current state snapshot
4. docs/ai_recovery/decision_log.md — historical architecture decisions

## Step 2: Verify current state

Execute:
- git status — check working tree state
- git log --oneline -10 — review recent commits
- git diff --name-only — check uncommitted changes

## Step 3: Analyze

Compare documents against actual codebase and git state:
- Are there discrepancies between current_status.md and actual state?
- Are there uncommitted changes that affect the task?
- Is the current working branch correct (develop)?

## Step 4: Produce Project Recovery Report

Output a report with:

### Current State
- Branch
- Phase
- Current task
- Uncommitted changes (if any)

### Consistency Check
- Document vs codebase discrepancies (if any)
- Actions needed to re-sync

### Next Steps
- What is the immediate next task
- Any blockers identified

## Step 5: Confirm

Do NOT modify code, create files, or run any task until the user confirms the recovery report.
```

## Recovery Rules

1. **Do not assume history.** Read the documents fresh each session.
2. **Do not modify code** without user confirmation after recovery.
3. **Do not skip verification.** Always cross-check document state against git and codebase.
4. **Report discrepancies immediately.** If current_status.md is stale, flag it.
5. **Respect phase boundaries.** Do not implement future-phase features.
