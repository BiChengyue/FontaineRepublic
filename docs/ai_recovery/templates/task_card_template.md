# Task Card Template

> Standard format for task definition and handoff.
> Part of the AI Multi-Agent Collaboration Framework.
> Reference: `docs/ai_recovery/ai_multi_agent_collaboration_framework.md` §3.2

---

## Agent Identity

**Agent:** {agent name}
**Role:** {Coordinator / Implementer / Reviewer}
**Task ID:** {task identifier}
**Task Name:** {short descriptive name}
**Request Summary:** {one-line summary}
**Context Source:** {previous task ID or external reference}

---

## Project Context Snapshot

**Repository State**

- Branch: {current branch}
- Commit: {current commit hash}
- Working Tree: {clean / dirty — list key dirty files if any}

**Current Phase**

- Phase: {project phase}
- Step: {step number}
- Current Task: {current task description}

---

## Task Card

**Task ID:** {task identifier, invariant across handoffs}
**Task Name:** {short descriptive name}
**Task Type:** {Feature / Bug Fix / Refactor / Documentation / Architecture / Governance / Evidence / Audit}
**Risk Level:** {Low / Medium / High}
**Emergency:** {Routine / Emergency}
**Target Agent:** {recommended Implementer}
**Required Capability:** {skills or knowledge needed}
**Forbidden Agent:** {must-not-assign agent, if any}
**Human Authorization Reference:** {authorization record from Human gate, confirms task passed Human review — does not constitute final approval}
**Task Owner:** {agent or human}
**Assigned Roles**:
  - Coordinator: {agent}
  - Implementer: {agent, Human-confirmed}
  - Reviewer: {agent, Human-confirmed}
  - Approver: Human

**Goal:**
{one-paragraph description of what this task achieves}

**Scope:**
- {in-scope item 1}
- {in-scope item 2}

**Non-Goals:**
- {explicitly out-of-scope item 1}
- {explicitly out-of-scope item 2}

**Allowed Changes:**
- {type of change allowed 1}
- {type of change allowed 2}

**Forbidden Changes:**
- {type of change forbidden 1}
- {type of change forbidden 2}

**Affected Files/Classes:**
- {file path or class name 1}
- {file path or class name 2}

**Design References:**
- {document or ADR reference 1}
- {document or ADR reference 2}

**Design Constraints:**
- {constraint 1}
- {constraint 2}

---

## Acceptance Criteria

- [ ] {criterion 1}
- [ ] {criterion 2}
- [ ] {criterion 3}

---

## Required Validation

- {validation requirement 1 (e.g., "Build must pass" or "Runtime verification required")}
- {validation requirement 2}

---

## Risk Assessment

- **Architecture risk:** {description}
- **Data risk:** {description}
- **Network risk:** {description}
- **Security risk:** {description}

---

## Deviation Alert Conditions

- {condition that triggers a Technical Deviation Alert}
- {condition that requires Escalation to Human}

---

## Expected Output

> Output varies by Task Type. Select the applicable line:

- Implementation task: **Implementation Report** + **Evidence Artifact**
- Review task: **Audit Report**
- Evidence task: **Target Evidence Artifact** (no Implementation Report)
- Audit task: **Audit Report** (no default Evidence Artifact)

---

## Evidence Source

**Delivery Form:** {Inline / Attachment / Repository}
**Evidence Weight:** {Primary Evidence / Secondary Claim}
**Evidence Location:** {path or reference}

**Applicable Outputs:**
- {Code / Documentation / Configuration / Governance} changes: {description}

---

## Dependencies

- **Requires:** {Task ID or "None"}
- **Blocked by:** {Task ID or "None"}

---

## Lifecycle

The Task Card progresses through the following states per the Collaboration Framework (Rev 04):

| State | Description | Authority |
|-------|-------------|-----------|
| Draft | Created by Coordinator, not yet submitted | Coordinator |
| Human Review Pending | Submitted to Human for authorization | Coordinator → Human |
| Authorized | Human has reviewed and authorized execution. This is the Human Authorization Gate — it confirms the task may begin, not final approval of results | Human |
| Assigned | Authorized Task Card transmitted to Implementer via Coordinator handoff. Human confirms assignment; Coordinator performs communication handoff only | Human (assignment) / Coordinator (transmission) |
| Accepted | Implementer has completed Intake Validation per Routing Protocol §4. Task scope confirmed and accepted. This is **not** Reviewer Approval — it is Implementer readiness confirmation | Implementer |
| Completed | Task-type-specific outputs produced, ready for review | Implementer |
| Archived | Human has made final decision (approve or reject). Terminal state | Human |

**Status Fields** (supplementary, not lifecycle stages):

- **Under Review**: Reviewer is actively assessing outputs
- **Changes Requested**: Reviewer identified issues requiring Implementer fixes
- **Deferred**: Decision postponed to a later task

These fields refine the Completed → Archived transition but do not replace the main lifecycle states.

---

## Change Log

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 0.1 | {date} | {agent} | Initial draft |
