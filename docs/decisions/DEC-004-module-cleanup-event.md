# DEC-004: Module Cleanup Execution Event

> **Status:** Accepted — Human Approved for FR-CORE-001-A
> **Related Design:** FR-CORE-001-A Core Framework Architecture Document (Revision-13 freeze candidate)
> **Approval Scope:** FR-CORE-001-A architecture synchronization and implementation preparation only.
> **Authority:** Human Developer approval recorded by task FR-CORE-001-A-DECISION-ACTIVATION-01.

---

## Context

The module runtime lifecycle separates resource-aware shutdown from final runtime cleanup. The architecture requires one event owner for each phase and exactly-once shutdown for every runtime instance that entered initialization.

---

## Decision

`CoreManager` remains the only lifecycle coordinator.

At `ServerStoppingEvent`, CoreManager:

- saves required persistent state;
- iterates modules in reverse successful initialization order;
- invokes `shutdown()` at most once per runtime instance;
- skips `DEPENDENCY_FAILURE` containers;
- skips INIT_FAILURE instances whose guarded shutdown already executed;
- advances eligible modules to `STOPPED`.

At `ServerStoppedEvent`, CoreManager:

- executes `STOPPED → CLEANUP → TERMINATED`;
- disposes eligible RuntimeModuleContainers and their runtime instances;
- directly discards `DEPENDENCY_FAILURE` containers without lifecycle calls;
- closes and discards RuntimeAvailabilityResult;
- releases remaining runtime-scope resources.

Cleanup at `ServerStoppedEvent` must not require an active Server, World, or persistence context. Such resource-aware release belongs in `shutdown()` at `ServerStoppingEvent`.

---

## Alternatives Considered

| Alternative | Assessment |
|-------------|------------|
| Perform shutdown and final cleanup at `ServerStoppingEvent` | Rejected. It merges two lifecycle responsibilities and may clear runtime state while other shutdown operations are still running. |
| Perform final cleanup at `ServerStoppedEvent` | Accepted. It preserves the shutdown/cleanup boundary and provides one terminal disposal point. |
| Use an additional custom or non-Forge close event | Rejected for FR-CORE-001. It adds an unnecessary lifecycle owner and creates implementation ambiguity. |

---

## Consequences

- Shutdown remains resource-aware and happens before the server fully stops.
- Final cleanup is limited to terminal state clearing and disposal.
- `shutdown()` is guarded by `shutdownExecuted` and never retried.
- DEPENDENCY_FAILURE never invokes `init()`, `shutdown()`, or module cleanup.
- Runtime containers and instances are not reused across server lifecycles.
- ModuleRegistry and immutable ModuleDefinitions remain available for the next server lifecycle.

---

## Implementation Impact

- CoreManager owns both event handlers and every state transition.
- The normal lifecycle is `REGISTERED → INITIALIZING → ACTIVE → STOPPING → STOPPED → CLEANUP → TERMINATED`.
- Initialization failure performs guarded shutdown once, reaches `STOPPED`, and completes cleanup at `ServerStoppedEvent`.
- Dependency failure follows `REGISTERED → DEPENDENCY_FAILURE → DISCARD`.
- RuntimeAvailabilityResult is CoreManager-owned Server Runtime Scope state. It is closed at `ServerStoppedEvent`; its lifetime is not controlled by RuntimeModuleContainer.

---

## Approval Status

**Accepted — Human Approved for FR-CORE-001-A.**

This decision does not constitute Human Design Freeze or Java implementation authorization.
