# FontaineRepublic Parliament Module Architecture v1.0

> **Task ID:** FR-PAR-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Legislative proposals, votes, bills, and the legal state machine
> **Dependency:** FR-CORE-002、FR-CIT-001、FR-GOV-001（职位，未来资格）、FR-INST-002、
> FR-AUD-001、FR-ID-001、FR-CMD-001-A、FR-DATA-001
> **Implementation Status:** Not authorized

---

## 1. Purpose

Roadmap v1.1 Phase 11 / Alpha 0.6 defines Parliament: `Proposal / Vote / Bill /
LegislationPipeline (DRAFT -> DEBATING -> VOTING -> PASSED/REJECTED)`, with
bills executed manually by the corresponding module after passage. The
constitution adds a normative hierarchy and a closed legal state machine
(FR-BL-003 §12: DRAFT/REVIEW/VOTING/APPROVED/PUBLISHED/ACTIVE/SUSPENDED/
INVALID/EXPIRED). This design turns both into a concrete module.

Parliament is the **legislative** pillar. It does not execute administration
(Government), adjudicate (Court), or manage fiscal flows (Central Bank).

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Proposals / votes / bills / legal state | FR-PAR |
| Citizenship / political rank | FR-CIT（投票资格消费） |
| On-site official duty | FR-INST-002（议会设施+终端+现场上下文） |
| Norm hierarchy | 宪法 v3.0 §11 / FR-BL-003 §1-2（校验输入） |
| Law execution | 对应模块手动执行（roadmap）；FR-PAR 只记录状态 |
| Audit | FR-AUD |

Rules:

- Formal submission and voting are `ONSITE_OFFICIAL_DUTY`（FR-INST-001-A §5.1）;
  public information/consultation is `REMOTE_INFORMATION`; drafting is
  `REMOTE_PREPARATION`;
- Votes require citizenship (FR-CIT Service), one vote per citizen per ballot;
- Bill states are a closed enum; every transition records actor, time, trigger,
  and result（FR-BL-003 §12 / FR-BL-005 §12）;
- Norm level is validated at submission; a bill cannot downgrade its own
  required threshold;
- Political rank never equals technical permission.

---

## 3. Data Model

### 3.1 Proposal

`proposalId`, `title` (bounded), `normLevel` (CONSTITUTION_BASIC / ORGANIC /
ORDINARY / ADMINISTRATIVE), `fullText` (bounded), `proposerRef`, `state`, timestamps,
`revision`. Submission is one authoritative snapshot.

### 3.2 Vote

`voteId`, `proposalId`, `ballotState` (OPEN/CLOSED), `counts` (for/against/abstain,
bounded tallies), `requiredThreshold` (validated by norm level), `closedAt`.
Each citizen vote is a bounded immutable record (`voterRef`, `choice`).

### 3.3 Bill

`billId`, `proposalId`, `state` (closed enum), `publishedAt` (optional),
`executionModule` (optional), `revision`. Passage computes the threshold from
the frozen citizen list at vote open (FR-BL-005 §2).

### 3.4 ParliamentStore

```text
fontainerepublic.dat
└── modules
    └── parliament
        ├── StoreVersion / StoreRevision
        ├── Proposals / Votes / Bills
```

Strict codec + fail-closed load + deterministic encoding + bounded limits.

---

## 4. Service Contract

```java
// server/parliament/api/ParliamentService（概念）
interface ParliamentService {
    ProposalReceipt submitProposal(ProposalDraft draft, OnSiteContext context);
    VoteReceipt openVote(UUID actor, ProposalId proposal, OnSiteContext context);
    VoteReceipt castVote(UUID voter, VoteId vote, VoteChoice choice, OnSiteContext context);
    BillReceipt closeVoteAndAdvance(VoteId vote, OnSiteContext context);   // PASSED/REJECTED
    Optional<Bill> bill(BillId billId);
    Page<ProposalProjection> proposals(long afterId, int limit);
}
```

- `submitProposal` / `openVote` / `castVote` / `closeVoteAndAdvance` are
  `ONSITE_OFFICIAL_DUTY`（castVote: 议会公民现场投票）;
- final mutation boundary validates on-site context + citizenship + threshold;
- each authoritative mutation: one replacement snapshot ->
  `commitModuleData("parliament", ...)` -> publish after `COMMITTED`;
- read queries bounded; no enumeration API.

---

## 5. State Machine

```text
DRAFT -> REVIEW -> VOTING -> APPROVED -> PUBLISHED -> ACTIVE
                             \-> REJECTED
ACTIVE -> SUSPENDED | INVALID | EXPIRED（后续裁决/期限）
```

Every transition writes an immutable transition record (actor/time/trigger/
before/after/revision). Thresholds: ordinary majority; organic 2/3;
constitutional basic 3/4（宪法 v3.0 §13; FR-BL-003 §5; FR-BL-005 §9）.
Water-god guardian review (72h) and referendums are deferred revisions.

---

## 6. Acceptance Matrix

| Test | Expected |
|---|---|
| Proposal submission | Requires on-site context; norm level validated |
| Vote open/cast/close | Citizenship check; one vote per citizen; threshold math |
| Bill state machine | Closed enum; transition records; no illegal jumps |
| Threshold by norm level | Correct 1/2, 2/3, 3/4 computation (ceiling) |
| No execution | Parliament never mutates other modules |
| No technical permission | Rank/office never maps to OP |
| Strict codec / restart | Fail closed; recovery |
| On-site absent | Authoritative mutation rejected |

---

## 7. Non-Goals

- Law execution; water-god review; referendums; constitutional amendment
  pipeline (deferred revisions); GUI/package; technical permissions;
  implementation.

## 8. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
