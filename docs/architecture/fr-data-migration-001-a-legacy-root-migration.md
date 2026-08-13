# FontaineRepublic Legacy Root Migration Architecture v1.0

> **Task ID:** FR-DATA-MIGRATION-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Handle pre-FormatVersion legacy `fontainerepublic.dat` roots
> (e.g., the 2026-07-28 v0 prototype save)
> **Resolves:** FR-LEVEL3-RUNTIME-REVIEW-01 F-002
> **Dependency:** FR-CORE-002（加载/世界身份）、FR-AUD-001
> **Implementation Status:** Not authorized

---

## 1. Problem

The current loader rejects roots without `FormatVersion` / `WorldIdentity`
(fail closed). The repository contains a v0 prototype root (60 bytes, no
`FormatVersion`, legacy `data`/`modules` structure) from 2026-07-28. A server
started on that world would become unusable until the file is removed manually.

This design provides a **safe, audited, fail-closed-by-default** path.

---

## 2. Normative Rules

- Genuinely corrupt or foreign roots stay fail-closed (no auto-repair);
- A root with recognized current-module data is never silently discarded;
- Migration decisions are explicit, console-audited, and recorded in FR-AUD;
- No weakening of world-identity checks for normal v1 roots.

---

## 3. Detection

At load, classify the root:

| Class | Criteria | Handling |
|---|---|---|
| v1 current | Has `FormatVersion=1` + `WorldIdentity` | Normal load |
| v0 legacy-empty | No `FormatVersion`/`WorldIdentity`; legacy structure present; **no recognized current-module data** | Fresh-replace (below) |
| v0 legacy-with-data | Legacy structure containing data that maps to a current namespace | Console-only audited migration (future) or fail closed |
| Corrupt/foreign | Unreadable, wrong types, non-matching identity with data | Fail closed (unchanged) |

Recognition of "no current-module data": the legacy `modules` subtree contains
no keys that match current module namespaces (audit, citizen, land, economy,
etc.) with non-empty values.

---

## 4. Fresh-Replace Semantics (v0 legacy-empty)

1. Log and record an audited migration note (FR-AUD, `SYSTEM` actor,
   `MIGRATION`, "legacy-empty root replaced at first load");
2. Create a new v1 root with current `FormatVersion` and `WorldIdentity`;
3. Proceed with normal empty-world startup;
4. The legacy file is overwritten by the next save (no separate archive in
   this revision; the 60-byte root carries no recoverable business data).

This is **not** an auto-repair of corruption; it is a deterministic,
content-verified upgrade of a known-empty legacy layout.

---

## 5. Legacy-With-Data (future)

If a legacy root contains data that maps to a current namespace, a future
revision defines a console-only migration command with:

- full preview of mapped namespaces;
- explicit confirmation;
- audited before/after digests;
- no silent field loss.

---

## 6. Acceptance Matrix

| Test | Expected |
|---|---|
| v1 normal root | Unchanged behavior |
| v0 legacy-empty root | Fresh-replace + audit note; modules available |
| v0 with current-module data | Fail closed (no silent discard) |
| Corrupt bytes | Fail closed (unchanged) |
| Foreign world identity with data | Fail closed (unchanged) |
| Migration note | FR-AUD entry recorded once |

---

## 7. Non-Goals

- Full field-level legacy data conversion; auto-repair of corruption;
  weakening world identity; implementation.

## 8. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
