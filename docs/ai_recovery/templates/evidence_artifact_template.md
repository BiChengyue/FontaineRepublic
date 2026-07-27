# Evidence Artifact Template

> Standard format for evidence documentation.
> Produced by the Implementer (or by an Evidence Task).
> Reference: `docs/ai_recovery/ai_multi_agent_collaboration_framework.md` §4

---

## IMPORTANT: Evidence Classification

This file is an **Evidence Artifact** with Delivery Form = **Repository**.

Its Evidence Weight depends on content:
- **Repository Observable Evidence** = Primary Evidence (directly verifiable from repository state)
- **Implementer Secondary Claims** = Secondary Claim (self-declared, not independently verifiable)

Delivery Form (Repository) does **not** determine Evidence Weight. The Reviewer must assess both dimensions independently.

---

## Agent Identity

**Agent:** {agent name}
**Role:** {Implementer / Evidence Task agent}
**Task ID:** {task identifier}
**Task Name:** {short descriptive name}
**Target:** {file path or scope of the evidence}

---

## Instruction Assessment

### Goal
{one-paragraph description of what this evidence documents}

### Allowed Scope
- {allowed item 1}
- {allowed item 2}

### Forbidden Scope
- {forbidden item 1}
- {forbidden item 2}

---

## Repository State

### Current Repository State

**Repository Observable:**
- {file path} currently exists at expected path, git status = {status}. Current header/content: {description}

**Implementer Secondary Claims:**
- The claim that {description of historical claim} is based on task sequencing, not git history — git records current state, not prior revision identity

---

## Current File Manifest

| File | Current State | Git Status |
|------|--------------|------------|
| {file path} | {current content description} | {untracked / modified / new} |

**Implementer Secondary Claim:** The claim that the above files were modified/created by {agent} during task {task ID} is based on implementation history, not git authorship metadata.

---

## Git Audit Scope

### Repository Observable Evidence

The following facts can be independently verified by any AI with repository access:

| Current Observable Fact | Verification Method | Evidence |
|-------------------------|-------------------|----------|
| {fact} | {command to verify} | {expected result} |

### Implementer Secondary Claims

The following facts are self-declared by the {agent role} and cannot be independently verified from repository state:

| Claim | Basis | Why Not Repository-Observable |
|-------|-------|-------------------------------|
| {claim} | {source of the claim} | {reason git cannot prove it} |

### Evidence Limitation Statement

**Repository-observable facts** are limited to: current git status, current tracked diff, untracked file listing, file existence on disk, and file content at time of reading. Any AI with repository access can independently verify these.

**Implementer Secondary Claims** include: which task caused each change, which agent performed modifications, the order and scope of edits, absence of concurrent modification, absence of intermediate modifications, and attribution of pre-existing dirty state to specific prior tasks. These depend on the implementer's record and conversational history.

This distinction is fundamental to the Evidence Source Classification (Rev 08): evidence delivery form (Repository file) does not determine evidence weight — a file on disk containing self-declared claims carries Secondary Claim weight regardless of its Repository delivery form.

---

## Acceptance Criteria Results

| Criterion | Result | Evidence |
|-----------|--------|----------|
| {criterion 1} | {Closed / Secondary Claim} | {evidence description} |

---

## Self Review

### 1. Does this change alter any permissions?
**No.** {justification}

### 2. Does this affect Human Authority?
**No.** {justification}

### 3. Does this affect Reviewer independence?
**No.** {justification}

### 4. Does this change Evidence Weight?
**No.** {justification}

### 5. Is this purely a documentation/classification action?
**Yes/No.** {justification}

---

## Governance Compliance

| Principle | Status | Detail |
|-----------|--------|--------|
| Human Final Authority | Preserved | {how preserved} |
| Review ≠ Approval | Preserved | {how preserved} |
| Implementer ≠ Reviewer | Preserved | {how preserved} |
| Single Implementer Principle | Preserved | {how preserved} |
| Task-role Model | Preserved | {how preserved} |
| Evidence Source Classification | Applied | Delivery Form and Evidence Weight are distinguished |

---

## Remaining Risks

### Implementer Secondary Claims

| # | Claim | Why Not Repository-Observable |
|---|-------|-------------------------------|
| 1 | {claim} | {reason} |

---

## Final Statement

This evidence artifact documents the implementation facts of {task ID}.

- This artifact **does not** represent Human Approval
- No commit has been executed
- No push has been executed
- Waiting for Codex Review
- Waiting for Human Approval
