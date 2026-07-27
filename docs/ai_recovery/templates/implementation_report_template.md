# Implementation Report Template

> Standard format for documenting implementation work.
> Produced by the Implementer after task execution.
> Reference: `docs/ai_recovery/ai_team_governance.md` — Implementation Assessment sections.

---

## IMPORTANT: This is NOT an Approval

This implementation report documents what was done. It does **not**:

- Represent Human Approval
- Grant authorization to merge
- Grant permission to proceed to the next phase
- Replace Independent Review by the Reviewer

Only Human may approve, authorize merges, and make final decisions.

---

## Agent Identity

**Agent:** {agent name}
**Role:** Implementer
**Task ID:** {task identifier}
**Task Name:** {short descriptive name}

---

## Instruction Assessment

### Goal
{one-paragraph description of the implementation goal}

### Allowed Scope
- {allowed item 1}
- {allowed item 2}

### Forbidden Scope
- {forbidden item 1}
- {forbidden item 2}

---

## Repository State

**Branch:** {current branch}
**Commit:** {current commit hash}
**Working Tree:** {clean / dirty}

**Pre-existing State:**
- {observable: current file status, git status, untracked files}

**Implementer Secondary Claims:**
- {claims about who modified what, task ownership, sequencing}

---

## Changed Files

| File | Current State | Git Status |
|------|--------------|------------|
| {file path} | {what the current content shows} | {modified / untracked / new} |

**Implementer Secondary Claim:** The claim that the above files were modified by {agent} during this task is based on implementation history, not git authorship metadata.

---

## Git Audit Scope

### Current Task Modified Files

| File | Action | Status |
|------|--------|--------|
| {file path} | {created / modified} | {untracked / modified} |

### Existing Dirty Files (pre-existing, not modified by this task)

| File | Status | Observable Since |
|------|--------|-----------------|
| {file path} | {modified / untracked} | {date or prior task} |

### Ownership of Existing Changes

**Implementer Secondary Claim:** Attribution of pre-existing dirty files to specific prior tasks is based on conversational history, not git metadata. Git shows current dirty state but does not record which task caused each change.

### Scope Classification

| Scope Category | Files | Claim Type |
|----------------|-------|------------|
| Modified by this task | {file list} | Secondary Claim (authorship) |
| Pre-existing dirty | {file list} | Secondary Claim (ownership) |
| Untracked but content verifiable | {file list} | Repository Observable (content) |

---

## Evidence Classification

This implementation report has the following evidence profile:

| Dimension | Classification | Notes |
|-----------|---------------|-------|
| **Delivery Form** | {Inline / Attachment / Repository} | Select the applicable form based on how this report is delivered |
| **Location / Identifier** | {path / message reference / attachment ID} | Where the report can be found |
| **Evidence Weight** | **Secondary Claim** | The report itself interprets and summarizes implementation work. It is not direct Primary Evidence of correctness. |

### Delivery Form ≠ Evidence Weight

The Delivery Form does **not** determine the Evidence Weight. These are orthogonal dimensions per the Evidence Source Classification (Rev 08). An implementation report delivered as a Repository file remains a Secondary Claim; an implementation report delivered as Inline evidence in a chat message is also a Secondary Claim. The Delivery Form only describes how it was delivered.

### Embedded Primary Evidence

This report may contain embedded content that carries Primary Evidence weight independently:

| Embedded Content | Source | Weight Basis |
|-----------------|--------|--------------|
| `git status` / `git diff` output | Repository state at time of capture | Primary Evidence — directly observable command output |
| Build log output | Build tool stdout | Primary Evidence — verifiable by re-running the build |
| File content excerpts | Direct file read | Primary Evidence — verifiable by reading the file |

These embedded items are **not** transformed into Secondary Claims by being placed in this report. Each retains its original evidence weight based on its own source and verifiability.

---

## Executed Commands

- {command 1}
- {command 2}

---

## Build Verification

**Result:** {Pass / Fail / Not required}

**Output:**
```
{build output excerpt}
```

---

## Acceptance Criteria Results

| Criterion | Result | Evidence |
|-----------|--------|----------|
| {criterion 1} | {Pass / Fail / Secondary Claim} | {evidence description} |

---

## Architecture Impact

- **Module boundary change:** {Yes / No}
- **Core interface change:** {Yes / No}
- **Dependency change:** {Yes / No}

If Yes to any, describe the change and its justification.

---

## Self Review

### 1. Task Understanding
{confirm the implementation goal was correctly understood; note any ambiguity encountered}

### 2. Scope Compliance
{confirm all changes stayed within Allowed Scope; note any deviations or close calls}

### 3. Authority Impact
- Did this change expand any AI agent's authority? {Yes / No — explain}
- Did this affect Human decision points? {Yes / No — explain}
- Did this introduce any approval semantics? {Yes / No — explain}

### 4. Risk Assessment
- Were any Technical Deviation Alerts triggered? {Yes / No — list}
- Were any risks escalated? {Yes / No — describe}
- Are there remaining risks in the current implementation? {describe}

### 5. Governance Compatibility
| Principle | Status | Detail |
|-----------|--------|--------|
| Human Final Authority | Preserved | {how preserved} |
| Review ≠ Approval | Preserved | {how preserved} |
| Implementer ≠ Reviewer | Preserved | {how preserved} |
| Single Implementer Principle | Preserved | {how preserved} |
| Task-role Model | Preserved | {how preserved} |
| Evidence Source Classification | Applied | {how applied} |

---

## Governance Compliance

| Principle | Status | Detail |
|-----------|--------|--------|
| Human Final Authority | Preserved | {how preserved} |
| Review ≠ Approval | Preserved | {how preserved} |
| Implementer ≠ Reviewer | Preserved | {how preserved} |
| Single Implementer Principle | Preserved | {how preserved} |
| Task-role Model | Preserved | {how preserved} |

---

## Remaining Risks

### Repository Observable

- {risk observable from current repository state}

### Implementer Secondary Claims

| # | Claim | Why Not Repository-Observable |
|---|-------|-------------------------------|
| 1 | {claim} | {reason} |

---

## Review Request

**Reviewer:** {assigned Reviewer}
**Areas of focus:** {specific areas the Reviewer should examine}

**Unresolved Questions:**
- {question 1}
- {question 2}

---

## Change Log

| Version | Date | Prepared By | Change |
|---------|------|-------------|--------|
| 1.0 | {date} | {agent} | Initial report |
