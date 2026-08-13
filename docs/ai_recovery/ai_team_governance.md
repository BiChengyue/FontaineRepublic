# AI Team Governance

> Governance reference: FR-GOV-V2-UPDATE
> Status: Pending Human Approval
> Last updated: 2026-07-27
> Baseline commit: `1ca7716` (docs: establish AI governance baseline)
> Baseline task: FR-GOV-001-BASELINE-01

## Migration Note

This document is proposed to supersede FR-GOV-AI-001, pending Human confirmation (FR-GOV-V2-HUMAN-APPROVAL-01). Key changes from v1:

- **Task-role model**: Roles are assigned per task, not fixed per AI.
- **Review vs. Approval separated**: Review verifies correctness; human grants approval. They are not the same.
- **Two-dimensional decision model**: Risk × Emergency, not a linear four-level scale.
- **Codex role**: Repository Principal Engineer — may perform design pre-review or repository post-review depending on risk.
- **No risk degradation**: Codex quota exhaustion does not reduce risk level — escalate or wait.
- **Standard artifacts**: Task Card (before), Implementation Report (after), Evidence Artifact (evidence), Audit Report (review).
- **Source of Truth**: Four-layer scope — Actual State · Normative Rules · Project Progress · Historical Context.
- **Communication Protocol**: All AI outputs must carry Agent Identity Header and Project Context Snapshot.

The Two-AI model (ADR-006) is proposed to be superseded by the Three-AI model (ADR-010), pending Human confirmation. The original design/implementation separation principle is preserved.

---

## Overview

FontaineRepublic uses a three-AI development team. This document defines governance rules, role allocation, workflows, and quality standards. The goal is to place each AI where it is most effective while maintaining independent validation.

### Human Final Authority

No AI decision is final without human confirmation. This applies to:

| Area | Human Authority |
|------|----------------|
| Architecture change | Human must approve |
| Scope change | Human must approve |
| Module boundary change | Human must approve |
| Core interface change | Human must approve |
| Task priority | Human decides |
| Release decision | Human decides |

AI may recommend, raise risks, and suggest alternatives, but the human makes the final call.

### Review vs. Approval

These are distinct concepts and must not be conflated:

| Concept | Definition | Who performs |
|---------|------------|-------------|
| **Review** | Verify correctness, completeness, and consistency | Any AI other than the producer |
| **Approval** | Grant authority to proceed to next stage | Human only |

A review is not an approval. An AI that reviews does not have authority to approve. Only the human grants final approval.

### Task-Role Model

Roles are not permanently assigned to AIs. Each task defines which AI performs which role:

| Role | Responsibility | Assigned To |
|------|---------------|-------------|
| **Designer** | Produce design, define interfaces, identify risks | Task-dependent |
| **Implementer** | Write code, build, verify | Task-dependent |
| **Reviewer** | Verify correctness, check compliance | Task-dependent (must differ from Implementer) |
| **Approver** | Grant final authority to proceed | Human only |

The same AI may serve different roles in different tasks, but must never serve as both Implementer and Reviewer in the same task.

---

## Team Capabilities

These are not fixed role assignments. They describe what each AI is best suited for.

### ChatGPT 5.6 sol — Architecture Lead

- **Access**: Web interface (no direct codebase access)
- **Cost**: Near-free, virtually unlimited quota
- **Strength**: Extended discussion, architectural reasoning, risk analysis,方案 comparison

**Best suited for**:
- Architecture design and module boundary definition
- Data model and network protocol design
- Major technical decisions
- ADR maintenance
- Code review (design compliance)
- Risk analysis
- Task card creation

---

### DeepSeek V4 Pro — Implementation Engineer

- **Access**: Claude Code environment (file system, git, Gradle, shell)
- **Cost**: Low API cost, suitable for high-frequency calls
- **Strength**: Tool execution, build verification, file manipulation

**Best suited for**:
- Code implementation per approved design
- File creation and modification
- Gradle build and build verification
- Routine debugging
- Document sync (CLAUDE.md, current_status.md)
- Git commit/push workflow
- AI recovery document maintenance

---

### Codex 5.6 sol — Repository Principal Engineer

- **Access**: Capable of direct repository understanding
- **Cost**: Limited quota — reserved for critical use
- **Strength**: Engineering execution, independent review, complex problem diagnosis, repository-level analysis

**Best suited for**:
- High-risk task design feasibility review (pre-review)
- Repository-level implementation audit (post-review)
- Git / diff / build / lifecycle / API usage compliance check
- Core interface modification review
- Forge lifecycle and network threading issue diagnosis
- Complex bug diagnosis
- Milestone-level comprehensive code audit

**Review mode depends on risk**:
- **Pre-review**: Review design before implementation (prevents wasted work)
- **Post-review**: Review implementation after commit (checks repository integrity)
- **Both**: For High Risk Mandatory Dual Review, Codex performs both pre-feasibility review and post-audit

---

## Decision Model

### Dimensions

Task workflow is determined by two independent dimensions:

| Dimension | Question | Levels |
|-----------|----------|--------|
| **Risk** | What is the impact of getting this wrong? | Low / Medium / High |
| **Emergency** | How time-sensitive is this task? | Routine / Emergency |

- **Risk** determines: reviewer, validation depth, and approval requirements.
- **Emergency** determines: scheduling urgency only.
- Emergency does **not** expand permissions, change scope, or remove Human Approval.

### Risk Definition

| Level | Impact | Examples |
|-------|--------|----------|
| **Low** | Cosmetic, non-functional, documentation only | Documentation, comments, formatting, non-functional metadata |
| **Medium** | Functional change within existing module boundaries | New independent method, existing interface extension with clear spec |
| **High** | Affects core interfaces, data flow, network, permissions, server authority | Core API change, Network Foundation, Server Authority change, module boundary change |

### Risk Classification Authority

| Action | Who | Rule |
|--------|-----|------|
| **Initial Risk** | Task Card creator | Risk level proposed when creating the task |
| **Risk Upgrade** | Any Reviewer | Any reviewer can require risk level upgrade. If upgraded, the workflow adjusts accordingly |
| **Risk Downgrade** | Human only | Implementer may not downgrade risk. Only Human can approve a lower risk level |

The Implementer must not change the risk level. If the Implementer believes the risk is misclassified, they must raise this in the Implementation Report for the Reviewer or Human to decide.

---

| Level | Definition | Examples |
|-------|------------|----------|
| **Routine** | No time pressure, standard workflow | Feature development, refactoring, documentation |
| **Emergency** | Production bug, crash, data loss, blocking issue | Server crash, data corruption, critical fix needed |

---

## Standard Workflows

### Low-Medium Risk, Routine

```
ChatGPT Design → DeepSeek Implement → ChatGPT Review → Human Approval
```

### High Risk, Routine

Two valid modes depending on risk profile:

**Mode A — Pre-review (design feasibility)**

```
ChatGPT Design
    ↓
Codex Feasibility Review
    ↓
DeepSeek Implement
    ↓
ChatGPT Review (implementation matches design)
    ↓
Human Approval
```

**Mode B — Post-review (repository audit)**

```
ChatGPT Design → DeepSeek Implement
    ↓
Codex Repository Audit (diff, build, API usage, lifecycle check)
    ↓
ChatGPT Review
    ↓
Human Approval
```

### High Risk Mandatory Dual Review

Required for: Core Interface, Network Protocol, Persistence Format, Module Boundary, Server Authority changes.

```
ChatGPT Design
    ↓
Codex Feasibility Review
    ↓
Human Approval (design approval)
    ↓
DeepSeek Implementation
    ↓
Codex Repository Audit (implementation review)
    ↓
Human Approval (final approval)
```

### Emergency (any risk level)

Emergency changes time priority only. It does not automatically cancel:
- Architecture restrictions
- Scope restrictions
- Human Authority

Only explicit Human Emergency Override allows modify-first, review-after.

```
DeepSeek Diagnose → ChatGPT Confirm Fix → DeepSeek Fix → ChatGPT Post-review → Human Approval
```

Fix first, review after. Speed takes priority.

### High Risk + Emergency

```
DeepSeek Diagnose → ChatGPT Confirm Fix → DeepSeek Fix → Codex Post-audit + ChatGPT Post-review → Human Approval
```

Fix first, then both Codex (post-audit) and ChatGPT (post-review) review the fix, then human grants final approval.

---

## Standard Implementation Sequence

All tasks follow this sequence. Implementation occurs on a feature branch. The feature branch is merged into the stable branch (develop) only after Human Approval. High-risk code must not enter a stable branch before Human Approval.

```
Task Card
    ↓
Implementation
    ↓
Build / Runtime Verification
    ↓
Implementation Report
    ↓
Review
    ↓
Fix if required
    ↓
Human Approval
    ↓
Merge to stable branch (develop)
```

### Key Rules

- **Task Card** must exist before implementation begins. It defines scope, risk level, and assigned roles.
- **Build / Runtime Verification** must pass before Review. A failed build is not reviewable.
- **Implementation Report** documents what was changed and why. It accompanies the code into Review.
- **Review** verifies correctness and compliance. If issues are found, the cycle returns to Implementation.
- **Human Approval** is the gate to stable branch. No high-risk code enters the stable branch without it.
- **Merge to stable** only after Human Approval or explicit Human Emergency Override. Commits on a feature branch before approval are normal.

---

## Quota and Risk Integrity

**Codex quota exhaustion must not cause risk degradation.**

If Codex is unavailable when a high-risk task requires review:

| Scenario | Action |
|----------|--------|
| Codex unavailable, high-risk routine task | Task waits until Codex is available |
| Codex unavailable, high-risk emergency | Human performs the review, or approves skipping Codex review with explicit acknowledgement of increased risk |
| Codex unavailable, milestone audit | Deferred to next available slot |

The risk level of a task is determined by the code change, not by which reviewers are available. Reducing review requirements because of quota limitations defeats the purpose of risk classification.

---

## Review Disposition

Every review yields a disposition that determines what happens next.

### Disposition States

| State | Meaning | Action Required |
|-------|---------|----------------|
| **Open** | Review initiated, pending analysis | Reviewer completes analysis |
| **Fixed** | Issue has been corrected in code | Re-review or proceed to next stage |
| **Accepted Risk** | Issue acknowledged but not fixed, Human accepts the risk | Human must explicitly document acceptance |
| **Rejected** | Review findings reject the change | Implementation must be revised |
| **Deferred** | Issue postponed to a future task | Task Card created for deferred issue |

### Rules

- **Blocker / Major issues**: Must reach **Fixed** or **Accepted Risk** (Human documented) before the task can proceed.
- **Human Emergency Override**: Only Human can set disposition to **Accepted Risk** for Blocker/Major issues.
- **Rejected** disposition means the implementation must be revised and re-reviewed. No shortcut to approval.
- **Deferred** requires a separate Task Card. The current task does not inherit the deferred issue's scope.

---

## Standard Artifacts

### AI Workflow Artifacts

Four artifact types support the AI workflow, required per task context:

| Context | Required Artifacts |
|---------|-------------------|
| **Every task** | Task Card |
| **Implementer task** | Implementation Report, Evidence Artifact |
| **Review task** | Audit Report |

### Human Authority Record

One record type documents the human approval decision:

- **Approval Record**: Documents what was approved, review history, and authorization scope. Only Human may confirm; AI may prepare in Draft state.

### Task Card (before implementation)

Produced by the Designer. Must include Agent Identity, Project Context Snapshot, and Task Card.

Output order: Agent Identity → Project Context Snapshot → Task Card.

```
**Agent Identity**

Agent:
Role:
Task ID:
Task Name:
Request Summary:
Context Source:

**Project Context Snapshot**

...

## Task Card

Task ID:
Task Name:
Risk Level: [Low / Medium / High]
Emergency: [Routine / Emergency]
Assigned Roles:
  Designer:
  Implementer:
  Reviewer:
  Approver (Human):

Goal:
Scope:
Non Goals:
Allowed Changes:
Forbidden Changes:
Affected files/classes:
Design References:
Design constraints:
Acceptance criteria:
Required Validation:
Deviation Alert Conditions:
Dependencies:
  - requires: [Task ID]
  - blocked by: [Task ID]
Created:
```

### Project Context Snapshot Format

Reference structure used in all formal outputs:

```
**Project Context Snapshot**

**Repository State**
Branch:
Commit:
Working Tree:

**Current Phase**
Phase:
Step:
Current Task:

**Recent Completed Work**

**Active Risks**

**Document Consistency**
```

### Implementation Report (after implementation, before repository review)

Produced by the Implementer. Must include Agent Identity, Project Context Snapshot, and Report. The Report references the feature branch commit hash. It is produced before the code enters repository review.

Output order: Agent Identity → Project Context Snapshot → Implementation Report.

```
**Agent Identity**

Agent:
Role:
Task ID:
Task Name:
Request Summary:
Context Source:

**Project Context Snapshot**

...

## Implementation Report

Task ID:
Implementer:

### Git State
Branch:
Commit:
Diff Summary:

### Changed Files
  - path/to/file: description of change
  - path/to/file: description of change

### Executed Commands
  - command description

### Build Verification
Result: [Pass / Fail]
Details:

### Runtime Verification
Result: [Pass / Not required]
Details:

### Acceptance Criteria Results
  - [Pass / Fail] Criterion 1
  - [Pass / Fail] Criterion 2

### Architecture Impact
  - [Yes / No] Module boundary change
  - [Yes / No] Core interface change
  - [Yes / No] Dependency change

### Remaining Risks

### Review Request
Reviewer:
Areas of focus:

### Unresolved Questions

Implementation Assessment:
### 1. Task Understanding
### 2. Technical Evaluation
### 3. Risk Analysis
### 4. Architecture Consistency
### 5. Suggestions
```

### Audit Report (after review)

Produced by the Reviewer. Must include Agent Identity, Project Context Snapshot, and Report.

Output order: Agent Identity → Project Context Snapshot → Audit Report.

```
**Agent Identity**

Agent:
Role:
Task ID:
Task Name:
Request Summary:
Context Source:

**Project Context Snapshot**

...

## Audit Report

Task ID:
Reviewer:
Review type: [Pre-review / Post-review / Both]
Audited branch:

Files reviewed:
  - path/to/file
  - path/to/file

### Findings

#### F-001
- **Severity**: [Blocker / Major / Minor / Suggestion]
- **Status**: [Open / Fixed / Accepted Risk / Rejected / Deferred]
- **Description**: 
- **Evidence**: 
- **Recommendation**: 

#### F-002
- **Severity**: [Blocker / Major / Minor / Suggestion]
- **Status**: [Open / Fixed / Accepted Risk / Rejected / Deferred]
- **Description**: 
- **Evidence**: 
- **Recommendation**: 

Compliance: [Pass / Conditional / Fail]

Recommendation:
  - Review passed → route to Human Approval
  - Changes required (list)
  - Reject (reasons)
```

### Evidence Artifact (evidence documentation)

Evidence Artifact is an **artifact type**, not an AI role. It is produced by the Implementer alongside the Implementation Report. Documents implementation evidence with explicit classification of what is repository-observable versus self-declared.

Required fields:
- Agent Identity Header
- Project Context Snapshot
- Evidence Classification (Delivery Form, Evidence Weight)
- Repository State (current file manifest, git status)
- Changed Files (with git status per file)
- Git Audit Scope (repository-observable facts vs. Implementer Secondary Claims)
- Self Review
- Governance Compliance
- Remaining Risks

Relationship with Evidence Classification:

| Concept | Description |
|---------|-------------|
| **Delivery Form** | How the evidence is delivered (Repository file) |
| **Evidence Weight** | What the content proves — repository-observable (Primary Evidence) or self-declared (Secondary Claim) |

The Delivery Form does not determine Evidence Weight. Each fact within the artifact is classified independently.

Output order: Agent Identity → Project Context Snapshot → Evidence Artifact.

---

## Source of Truth

Project facts are organized into four categories. These are information scope categories, not an authority hierarchy. The Conflict Resolution table below governs which source takes precedence when they disagree.

### Layer 1: Actual Implementation State

Sources that capture what the code actually does, without interpretation.

| Source | Records |
|--------|---------|
| **Working Tree** | Current uncommitted changes |
| **Git (committed)** | Full committed history |
| **Build Result** | Compilation success/failure |
| **Runtime Verification** | Observed behavior |

### Layer 2: Normative Rules

Sources that define what the code should do. These are design-level authorities.

| Source | Records |
|--------|---------|
| **Approved ADR** | Architecture decisions |
| **Architecture Documents** | Module design, principles, data flow |
| **Technical Design** | Implementation specifications |

### Layer 3: Project Progress

Sources that track what has been done and what is next.

| Source | Records |
|--------|---------|
| **CLAUDE.md** | Contains both Normative Rules (architecture rules, verification rules, scope rules) and Progress State (Current Phase, Current Task). The rules portion belongs to Layer 2; the status portion belongs to Layer 3 |
| **current_status.md** | Status snapshot |
| **Roadmap** | Development phases |
| **Task Card** | Active task definition |

### Layer 4: Historical Context

Sources that provide background but are not authoritative.

| Source | Records |
|--------|---------|
| **Chat history** | Temporary discussion context, not authoritative |

### Conflict Resolution

| Conflict Type | Resolution |
|---------------|------------|
| Code vs. ADR | **ADR prevails.** Code that violates an approved ADR is an architecture violation, not a new fact. Report the violation; do not treat committed code as overriding the ADR. |
| Progress doc vs. Git | **Git prevails.** CLAUDE.md / current_status.md must be corrected to match committed state. |
| Chat vs. Documented rule | **Documented rule prevails.** Chat history is not a source of truth. |
| Implementation vs. Normative Design | If actual code contradicts the normative design, this is a compliance issue that must be reported and resolved, not silently accepted. |

---

## Cross AI Review Principle

1. **High-risk design must be reviewed by a non-designer.** Design review is required before implementation begins. The reviewer must be an AI not involved in producing the design.

2. **Implementation must be reviewed by a non-implementer.** Code review verifies correctness, completeness, and compliance. The reviewer must be an AI not involved in writing the code.

3. **Audit is accepted, rejected, or returned for changes by Human.** The human determines whether an audit passes, requires fixes, or is rejected. AI review does not imply AI approval.

4. **AI Review ≠ Approval.** Only human grants final approval.

5. **Review responsibility depends on risk level.**
   - **Low-Medium risk**: ChatGPT reviews DeepSeek's implementation → Review passed → Human approval.
   - **High risk**: Codex (Repository Principal Engineer) performs design feasibility review or repository audit depending on task → Review passed → Human approval.
   - **High Risk Mandatory Dual Review**: Codex performs both feasibility pre-review and repository post-audit → Human approval at both stages.
   - **Emergency**: ChatGPT confirms the fix before implementation; ChatGPT reviews after → Human approval.
   - **High-risk emergency**: Codex performs post-audit; ChatGPT performs post-review → Human approval.
   - **Milestone audits**: Codex performs comprehensive audit as a fully independent third party → Human approval.

### Rationale

This principle prevents single-AI blind spots. Even the best AI can miss errors in its own output. Cross-validation by a different AI with a different perspective increases the probability of catching design flaws, implementation bugs, and architecture inconsistencies before they reach the codebase.

---

## Communication Protocol

### Agent Identity Header

Every formal AI output must begin with an Agent Identity Header:

```
**Agent Identity**

Agent: [AI name and version]
Role: [Current role for this task]
Task ID: [Task identifier]
Task Name: [Task name]
Request Summary: [One-sentence summary of the request]
Context Source: [Documents read before responding]
```

This header ensures that every AI output is self-documenting about who produced it, in what capacity, and based on what context.

### Output Requirements

| Output Type | Must Include |
|-------------|--------------|
| Task Card | Agent Identity Header + Project Context Snapshot |
| Implementation Report | Agent Identity Header + Project Context Snapshot |
| Evidence Artifact | Agent Identity Header + Project Context Snapshot |
| Audit Report | Agent Identity Header + Project Context Snapshot |
| Approval Record (Draft) | Agent Identity Header + Project Context Snapshot |
| Recovery Report | Agent Identity Header + Project Context Snapshot |
| Ad-hoc response | Agent Identity Header (truncated: Agent/Role/Task ID) |

---

## Key Discipline

### 1. One Task, One Code Modifier

Multiple AIs must not edit the same code area simultaneously. Task handoff must be clean (no partial state).

### 2. Design and Implementation Separation

Proposing a solution ≠ approval. Design requires separate review before implementation begins.

### 3. Review and Approval Separation

Review verifies correctness. Approval grants authority to proceed. They are different functions and must not be conflated.

### 4. Artifact Completeness

AI Workflow Artifacts (required per task context):

| Context | Required Artifacts |
|---------|-------------------|
| **Every task** | Task Card |
| **Implementer task** | Implementation Report, Evidence Artifact |
| **Review task** | Audit Report |

**Human Authority Record**: Approval Record (produced at the human approval gate)

Skipping required artifacts requires explicit human approval.

### 5. Cost Awareness

- Codex quota is finite. Use only for: design review of high-risk tasks, milestone audits, complex diagnostics.
- ChatGPT is the primary design and review engine due to zero cost.
- If Codex is unavailable, do not reduce risk classification — wait or escalate to human.

---

## Phase Scope Reference

This section defines what is in-scope and out-of-scope for each development phase. It resolves ambiguity about which features belong to which phase.

### Phase 0 — Core System Foundation

**Allowed:**
- Core framework (module lifecycle, manager registration)
- Configuration system (ForgeConfigSpec)
- Lifecycle management (init/shutdown ordering)
- Data persistence foundation (SavedData, NBT)
- **Network Foundation** — framework-level networking only:
  - Network channel framework
  - Packet registration
  - Protocol version
  - Serialization/TestPacket skeleton
  - **Business networking, Economy networking, Government networking, Player feature networking are FORBIDDEN in Phase 0**

**Forbidden:**
- Citizen module
- Land module
- Economy module
- Government module
- Justice module
- GUI systems
- Business networking
- Economy networking
- Government networking
- Player feature networking
- JSON backup/export (deferred to Alpha 0.2)

### Alpha 0.x and Later

Phase boundaries for Alpha modules will be defined when Phase 0 completes and the roadmap advances.

---

## Context Handoff Template

When handing off between AIs, include the Task Card (with Agent Identity Header and Project Context Snapshot) plus any relevant implementation notes. Do not pass large chat transcripts.

```
## Implementation Notes

Key decisions made:
Open questions:
Blockers:
```

---

## Incident Recovery

If an AI session is interrupted or context is lost:

1. New AI reads: CLAUDE.md, docs/ai_recovery/project_context.md, docs/ai_recovery/current_status.md, docs/ai_recovery/decision_log.md
2. Executes: git status, git log, diff check
3. Produces Project Recovery Report
4. Waits for user confirmation before making changes

See [recovery_prompt.md](recovery_prompt.md) for the full recovery procedure.
