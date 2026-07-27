# Approval Record Template

> Standard format for recording Human approval decisions.
> Only Human may approve. AI agents must not self-approve.
> Reference: `docs/ai_recovery/ai_team_governance.md` — Human Final Authority principle

---

## IMPORTANT: Human Only

This record documents a Human approval decision. It must:

- Be created **by** Human or **at Human's explicit direction**
- Reference the specific task, implementation, and audit being approved
- Not be created by any AI agent on its own authority

An AI agent may **prepare a Draft** of this record for Human review. Only Human may confirm it.

---

## Record State

**Record State:** {Draft — Not Human Confirmed / Human Confirmed}

**Prepared By:** {agent name, if AI-prepared}
**Human Approver:** {Human name or identifier}
**Human Confirmation Reference:** {reference confirming Human's decision}
**Human Confirmation Time:** {timestamp of Human confirmation}

> **Rules:**
> - An AI agent may prepare this record in **Draft** state.
> - Only Human may set **Human Confirmed** state.
> - An AI agent must never generate a Human Confirmed record on its own authority.

---

## Approval Record

**Record ID:** {unique identifier, format: APR-{TASK-ID}-{SEQUENCE}}
**Date:** {approval date}

---

## Task Reference

**Task ID:** {task being approved}
**Task Name:** {task description}
**Task Type:** {Feature / Bug Fix / Refactor / Documentation / Architecture / Governance / Evidence / Audit}
**Risk Level:** {Low / Medium / High}

---

## Supporting Documents

Each supporting document is classified by its evidence properties and review status:

| Document | Delivery Form | Evidence Weight | Location / Identifier | Status | Reviewed By | Review Timestamp / Decision Ref |
|----------|--------------|----------------|----------------------|--------|-------------|--------------------------------|
| Task Card | {Inline / Attachment / Repository} | {Primary Evidence / Secondary Claim} | {path or reference} | {Reviewed / Not Reviewed / N/A} | {agent or Human} | {timestamp or reference} |
| Implementation Report | {Inline / Attachment / Repository} | {Primary Evidence / Secondary Claim} | {path or reference} | {Reviewed / Not Reviewed / N/A} | {agent or Human} | {timestamp or reference} |
| Evidence Artifact | {Inline / Attachment / Repository} | {Primary Evidence / Secondary Claim} | {path or reference} | {Reviewed / Not Reviewed / N/A} | {agent or Human} | {timestamp or reference} |
| Audit Report | {Inline / Attachment / Repository} | {Primary Evidence / Secondary Claim} | {path or reference} | {Reviewed / Not Reviewed / N/A} | {agent or Human} | {timestamp or reference} |
| Inline Evidence | Inline | {Primary Evidence / Secondary Claim} | {message reference} | {Reviewed / Not Reviewed / N/A} | {agent or Human} | {timestamp or reference} |
| Attachment Evidence | Attachment | {Primary Evidence / Secondary Claim} | {attachment identifier} | {Reviewed / Not Reviewed / N/A} | {agent or Human} | {timestamp or reference} |

> **Delivery Form** (Inline / Attachment / Repository) describes how the evidence was delivered.
> **Evidence Weight** (Primary Evidence / Secondary Claim) describes epistemic weight. These are orthogonal dimensions.

---

## Decision

**Decision:** {Approved / Approved with Conditions / Rejected / Deferred / Accepted Risk}

**Conditions (if applicable):**
- {condition 1}
- {condition 2}

**Rationale:**
{free-text rationale from Human}

---

## Authorized Actions

By approving, Human authorizes the following:

- {action 1 (e.g., "Merge feature branch to develop")}
- {action 2 (e.g., "Proceed to next phase")}

---

## Scope Confirmation

**Approval Scope:**

This approval covers only the work described in the referenced Task Card. It does **not** authorize:

- Work outside the approved Task Card scope
- Future tasks not yet reviewed
- Changes to governance or architecture without separate authorization

---

## Post-Approval State

- **Branch:** {target branch for merge}
- **Merge authorized:** {Yes / No / N/A}
- **Next task:** {next task ID or "None"}

---

## Change Log

| Version | Date | Prepared By | Change |
|---------|------|-------------|--------|
| 1.0 | {date} | {agent or Human} | Initial draft |
