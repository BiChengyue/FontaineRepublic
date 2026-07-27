# DEC-003: Network Foundation Boundary

> **Status:** Accepted — Human Approved for FR-CORE-001-A
> **Related Design:** FR-CORE-001-A Core Framework Architecture Document (Revision-13 freeze candidate)
> **Approval Scope:** FR-CORE-001-A architecture synchronization and implementation preparation only.
> **Authority:** Human Developer approval recorded by task FR-CORE-001-A-DECISION-ACTIVATION-01.

---

## Decision

Network Foundation is **not part of FR-CORE-001-A**. It remains an independent future task that builds on the completed Core Framework and requires separate design review.

FR-CORE-001 must not contain:

- business networking;
- packet protocol implementation;
- client synchronization;
- network authority logic.

---

## Context

Architecture v2.7 Section 9.2 Step 4 lists "Network Foundation" as a Phase 0 task. However, FR-CORE-001-A focuses on the module system, lifecycle management, and dependency resolution. The question is whether network implementation belongs in the same task or is separated.

---

## Alternatives Considered

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A: Separate Task** (accepted) | Network Foundation is a follow-up implementation task after FR-CORE-001 completes | Clear separation of concerns, Core Framework focuses on module lifecycle | Requires coordination handoff between tasks |
| **B: Include in FR-CORE-001-A** | Core Framework design includes network channel registration and packet skeleton | Single integrated design, fewer handoffs | Expands scope beyond module lifecycle, mixes concerns, delays Core Framework completion |

---

## Reasoning

1. FR-CORE-001-A scope is module lifecycle and CoreManager evolution — self-contained concerns that do not require networking.
2. Network Foundation requires the module system to be operational first (Core Framework is a prerequisite).
3. Architecture v2.7 Section 6 already defines the network architecture design — only implementation is needed.
4. Separating concerns allows Core Framework to stabilize before network complexity is added.

---

## Network Lifecycle Clarification

SimpleChannel registration belongs to the Mod initialization phase (mod constructor or FMLCommonSetupEvent), NOT the Server lifecycle. The Network Foundation task (separate from FR-CORE-001-A) handles its own lifecycle within the Forge networking model per Architecture v2.7 Section 6.

---

## Consequences

- **Positive:** Core Framework implementation scope is focused and achievable
- **Positive:** Network implementation builds on stable module lifecycle foundation
- **Negative:** Additional task handoff required between Core Framework and Network Foundation
- **Mitigation:** The Core Framework defines only the module identity boundary for a future `core:network` module; lifecycle integration is handled by the Network Foundation task

---

## Implementation Impact

- FR-CORE-001 contains no `SimpleChannel`, packet registration, protocol version, serialization, synchronization, or network-authority implementation.
- Network Foundation requires a separate Task Card, architecture review, and Human authorization.
- Business packets remain owned by their future Feature Modules and are not authorized by this decision.

---

## Approval Status

**Accepted — Human Approved for FR-CORE-001-A.**

This decision does not approve Network Foundation implementation or Human Design Freeze.

---

## Related

- FR-CORE-001-A Section 5 (DEC-003)
- Architecture v2.7 Section 6 (Network Architecture)
- Architecture v2.7 Section 9.2 Step 4 (Network Foundation)
- FR-CORE-001-A Revision-12 decision synchronization
