# Audit Report Template

> Standard format for independent review findings.
> Produced by the Reviewer after implementation review.
> Reference: `docs/ai_recovery/ai_team_governance.md` — Audit Report section

---

## IMPORTANT: Review ≠ Approval

This audit report contains findings and recommendations from independent review.

It does **not**:
- Represent Human Approval
- Authorize merging or deployment
- Replace Human judgment on disputed items

Only Human may approve. The Reviewer assesses; Human decides.

---

## Agent Identity

**Agent:** {agent name}
**Role:** Reviewer
**Task ID:** {audit task identifier}
**Task Name:** {short descriptive name}
**Request Summary:** {original task summary}
**Context Source:** {implementation task ID}

---

## Project Context Snapshot

**Repository State**

- Branch: {audited branch}
- Commit: {commit hash at time of review}
- Working Tree: {clean / dirty}

---

## Audit Report

**Task ID:** {task ID under review}
**Reviewer:** {agent name}
**Review type:** {Pre-review / Post-review / Both}
**Audited branch:** {branch name}

**Files reviewed:**
- {file path 1}
- {file path 2}

**Evidence sources consulted:**
- {Evidence Source block for each source}

---

## Findings

### F-{001}
- **Severity:** {Blocker / Major / Minor / Suggestion}
- **Status:** {Open / Fixed / Deferred}
- **Proposed Status:** {Accepted Risk} — Reviewer may propose; see Accepted Risk Rules below
- **Final Disposition:** {Accepted Risk / Rejected / Deferred} — set only after Human decision
- **Disposition Authority:** {Reviewer / Human}
- **Human Decision Reference:** {required for Final Disposition to be set}
- **Description:** {detailed description of the finding}
- **Evidence:** {reference to the evidence supporting this finding}
- **Recommendation:** {specific recommended action}

### F-{002}
- **Severity:** {Blocker / Major / Minor / Suggestion}
- **Status:** {Open / Fixed / Deferred}
- **Proposed Status:** {Accepted Risk} — Reviewer may propose; see Accepted Risk Rules below
- **Final Disposition:** {Accepted Risk / Rejected / Deferred} — set only after Human decision
- **Disposition Authority:** {Reviewer / Human}
- **Human Decision Reference:** {required for Final Disposition to be set}
- **Description:** {detailed description of the finding}
- **Evidence:** {reference to the evidence supporting this finding}
- **Recommendation:** {specific recommended action}

---

## Severity Summary

| Severity | Count |
|----------|-------|
| Blocker | {N} |
| Major | {N} |
| Minor | {N} |
| Suggestion | {N} |
| **Total** | **{N}** |

### Open Findings

| ID | Severity | Summary |
|----|----------|---------|
| F-{001} | {Severity} | {brief summary} |

### Closed Findings

| ID | Severity | Resolution |
|----|----------|------------|
| F-{001} | {Severity} | {Fixed / Accepted Risk / Rejected / Deferred} |

---

## Governance Verification

| Principle | Result | Assessment |
|-----------|--------|------------|
| Human Authority | {Pass / Fail} | {assessment} |
| Review ≠ Approval | {Pass / Fail} | {assessment} |
| Implementer ≠ Reviewer | {Pass / Fail} | {assessment} |
| Single Implementer Principle | {Pass / Fail} | {assessment} |
| Task-role Model | {Pass / Fail} | {assessment} |
| Evidence Source Classification | {Pass / Fail} | {assessment} |

---

## Compliance

**Overall Compliance:** {Pass / Conditional / Fail}

**Recommendation:**
- Review passed → route to Human Approval
- Changes required (list specific items)
- Reject (reasons)

### Accepted Risk Rules

1. The Reviewer **may propose** Accepted Risk as a Proposed Status for any finding.
2. Accepted Risk becomes the **Final Disposition** **only** when a **Human Decision Reference** exists.
3. Without Human Decision Reference, Accepted Risk remains a proposal — the finding Status stays Open.
4. The Disposition Authority field distinguishes Reviewer proposal from Human confirmation:
   - `Reviewer` = proposed, awaiting Human decision
   - `Human` = confirmed by Human decision reference

---

## Final Statement

This audit report documents the independent review of {task ID}.

- This report **does not** represent Human Approval
- The Reviewer has not modified any implementation files
- Waiting for Implementer fixes (if applicable)
- Waiting for Human Approval
