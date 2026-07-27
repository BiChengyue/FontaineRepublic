# DEC-001: Event Foundation Boundary

> **Status:** Accepted — Human Approved for FR-CORE-001-A
> **Related Design:** FR-CORE-001-A Core Framework Architecture Document (Revision-13 freeze candidate)
> **Approval Scope:** FR-CORE-001-A architecture synchronization and implementation preparation only.
> **Authority:** Human Developer approval recorded by task FR-CORE-001-A-DECISION-ACTIVATION-01.

---

## Decision

The Core Framework provides **Forge Lifecycle Integration Only**. It does not implement an internal event bus.

The approved lifecycle integration points are:

- `FMLCommonSetupEvent`
- `ServerAboutToStartEvent`
- `ServerStartingEvent`
- `ServerStoppingEvent`
- `ServerStoppedEvent`

Any future internal event bus requires an independent architecture task and Human approval.

---

## Context

The Core Framework must define how modules communicate with each other and how cross-cutting concerns (logging, audit, state change notifications) are handled. Two approaches exist: leverage Forge's existing event system plus direct API calls, or build a dedicated internal event infrastructure.

---

## Alternatives Considered

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A: Forge Lifecycle Only** (accepted) | Modules communicate via direct API calls through CoreManager query API. `MinecraftForge.EVENT_BUS` is used only for Forge/Minecraft lifecycle events. | Simple, no new infrastructure, leverages existing Forge system | Tighter coupling, no dedicated cross-module notification channel |
| **B: Internal Event Bus** | Core provides event dispatch for internal module events (init, state change, data change) | Looser coupling, easier to add cross-cutting concerns | New infrastructure, event hierarchy, ordering, lifecycle complexity |

---

## Reasoning

1. Architecture v2.7 Section 2.2 specifies modules "使用明确接口通信" — direct API calls through defined interfaces.
2. Forge's `MinecraftForge.EVENT_BUS` is used for Forge/Minecraft lifecycle events only. It is not a module business communication channel.
3. An internal event bus requires design decisions (event type hierarchy, listener registration, dispatch ordering, cancellation, lifecycle binding) that are out of scope for Core Framework Phase 1.
4. No cross-module notification scenarios exist in Phase 1 that require an event bus.

---

## Consequences

- **Positive:** Faster implementation, reduced complexity
- **Positive:** Direct API calls provide compile-time type safety
- **Negative:** Cross-cutting concerns (automatic audit logging) may require refactoring later
- **Mitigation:** CoreManager's query API provides the hook point for future event infrastructure if needed

---

## Implementation Impact

- FR-CORE-001 implements Forge lifecycle listeners and their CoreManager mappings only.
- FR-CORE-001 must not add an internal event bus, business-event hierarchy, or module publish-subscribe API.
- Feature modules communicate through explicit approved interfaces.
- This decision does not authorize Feature Module implementation.

---

## Approval Status

**Accepted — Human Approved for FR-CORE-001-A.**

This decision is not a Human Design Freeze and does not authorize Java implementation by itself.

---

## Related

- FR-CORE-001-A Section 4 (DEC-001)
- Architecture v2.7 Section 2.2
- FR-CORE-001-A Revision-12 decision synchronization
