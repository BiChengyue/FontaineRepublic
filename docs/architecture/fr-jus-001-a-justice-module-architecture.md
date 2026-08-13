# FontaineRepublic Justice Module Architecture v1.0

> **Task ID:** FR-JUS-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Cases, evidence, verdicts, and the judicial pipeline
> **Dependency:** FR-CORE-002、FR-CIT-001、FR-INST-002、FR-AUD-001、FR-ID-001、
> FR-LAND-001（违规举报入口）、FR-CMD-001-A、FR-DATA-001
> **Implementation Status:** Not authorized

---

## 1. Purpose

Roadmap v1.1 Phase 12 / Alpha 0.7 defines Justice: `Case / Evidence / Verdict /
JudicialPipeline`, auto-filing from Land `ViolationReport`, and human-executed
verdicts. The constitution adds judicial independence, at least one effective
review, and evidence principles (FR-BL-004). This design turns both into a
concrete module.

Court is the **judicial** pillar: it adjudicates; it does not legislate,
administer, or manage fiscal flows.

---

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| Cases / evidence / verdicts / pipeline | FR-JUS |
| Parties / citizenship | FR-CIT（standing 消费） |
| On-site court work | FR-INST-002（法院设施+终端+现场上下文） |
| Violation reports | FR-LAND（入口；立案消费） |
| Verdict execution | 人工/对应模块（roadmap）；FR-JUS 只记录裁判 |
| Audit | FR-AUD |

Rules:

- Judicial independence: no other pillar can direct a verdict (FR-BL-004 §2);
- Filing, evidence submission, hearings, and judgments are on-site classes
  (FR-INST-001-A §5.3); public case info and own-case status are remote info;
- At least one effective review before finality where applicable
  (FR-BL-004 §3/§5);
- Evidence must be lawful, verifiable, relevant (FR-BL-004 §11);
- Verdicts are binding records; execution is separate and human-directed;
- Political rank never equals technical permission.

---

## 3. Data Model

### 3.1 Case

`caseId`, `caseType` (closed enum), `parties` (bounded), `sourceReportId`
(optional Land ViolationReport), `state` (pipeline), `courtLevel`, `revision`,
timestamps.

### 3.2 Evidence

`evidenceId`, `caseId`, `submittedByRef`, `description` (bounded),
`integrityDigest` (optional), `admitted` state, `revision`. Evidence is
append-only per case; no silent removal after admission.

### 3.3 Verdict

`verdictId`, `caseId`, `outcome` (closed enum), `reasoning` (bounded),
`judgeRef`, `level`, `issuedAt`, `revision`. Verdicts are immutable;
remedies are separate records (appeal/new trial links).

### 3.4 JusticeStore

```text
fontainerepublic.dat
└── modules
    └── justice
        ├── StoreVersion / StoreRevision
        ├── Cases / Evidence / Verdicts
```

Strict codec + fail-closed load + deterministic encoding + bounded limits.

---

## 4. Service Contract

```java
// server/justice/api/JusticeService（概念）
interface JusticeService {
    CaseReceipt fileCase(CaseDraft draft, OnSiteContext context);        // ONSITE_PUBLIC_SERVICE
    CaseReceipt acceptCase(UUID actor, CaseId caseId, OnSiteContext context); // ONSITE_OFFICIAL_DUTY
    EvidenceReceipt submitEvidence(EvidenceDraft draft, OnSiteContext context);
    VerdictReceipt issueVerdict(UUID judge, CaseId caseId, VerdictDraft draft,
                                OnSiteContext context);                  // ONSITE_OFFICIAL_DUTY
    Optional<Case> caseById(CaseId caseId);
    Page<CaseProjection> cases(long afterId, int limit);                 // 有界
}
```

- `fileCase`/`submitEvidence` are `ONSITE_PUBLIC_SERVICE`（现场公众服务）;
  `acceptCase`/`issueVerdict` are `ONSITE_OFFICIAL_DUTY`;
- final mutation boundary validates on-site context + standing + evidence
  admissibility; verdicts bind the record;
- each authoritative mutation: one replacement snapshot ->
  `commitModuleData("justice", ...)` -> publish after `COMMITTED`;
- auto-filing from Land ViolationReport is a bounded intake service;
- read queries bounded; no enumeration API.

---

## 5. Pipeline

```text
DRAFT -> FILED -> ADMITTED -> HEARING -> VERDICT_PENDING -> VERDICTED
                                           \-> REJECTED
VERDICTED -> REVIEW_REQUESTED -> REVIEWED（复核）-> FINAL
```

Every transition records actor/time/trigger/before/after/revision. Appeals
require a different reviewer composition where applicable (FR-BL-004 §5).

---

## 6. Acceptance Matrix

| Test | Expected |
|---|---|
| Filing/evidence | On-site gated; append-only evidence |
| Acceptance/verdict | On-site official duty; binding record |
| Land ViolationReport intake | Auto-filing bounded; no cross-module NBT |
| Pipeline | Closed enum; transition records; no illegal jumps |
| Independence | No other-module verdict direction |
| Review | At least one effective review path |
| No execution | Justice never mutates other modules |
| Strict codec / restart | Fail closed; recovery |
| On-site absent | Authoritative action rejected |

---

## 7. Non-Goals

- Verdict execution; constitutional review pipeline (future);
  AI/oracle assistance (谕示裁定枢机, Beta); GUI/package; technical
  permissions; implementation.

## 8. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
