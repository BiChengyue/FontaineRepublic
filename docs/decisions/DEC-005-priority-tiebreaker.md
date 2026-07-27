# DEC-005: Priority Tiebreaker

> **Status:** Accepted — Human Approved for FR-CORE-001-A
> **Related Design:** FR-CORE-001-A Core Framework Architecture Document (Revision-13 freeze candidate)
> **Approval Scope:** FR-CORE-001-A architecture synchronization and implementation preparation only.
> **Authority:** Human Developer approval recorded by task FR-CORE-001-A-DECISION-ACTIVATION-01.

---

## Context

Dependency topology is the primary authority for module initialization order. A deterministic rule is still required for modules at the same dependency layer that have no dependency relationship.

---

## Decision

DependencyResolver orders modules using:

1. dependency topology;
2. priority;
3. stable registration order.

Priority is a secondary tiebreaker only. It never overrides required or optional dependency ordering.

Additional rules:

- priority applies only between modules in the same dependency layer;
- lower priority values initialize earlier;
- equal priorities are valid and use stable registration order;
- priority is not a map key;
- ModuleDefinition owns the immutable priority value;
- ModuleRegistry stores ModuleDefinitions without interpreting priority;
- DependencyResolver is the only component that applies priority ordering.

---

## Alternatives Considered

| Alternative | Assessment |
|-------------|------------|
| Topology → priority → stable registration order | Accepted. It preserves deterministic extensibility without weakening dependency authority. |
| Topology → stable registration order | Rejected for FR-CORE-001. It is simpler but provides no explicit ordering preference for independent modules. |

---

## Consequences

- Independent modules can express a deterministic initialization preference.
- Equal-priority modules cannot overwrite one another.
- A priority value must not be used to model a real dependency.
- Priority changes may alter order within one dependency layer and therefore require review and test coverage.

---

## Implementation Impact

- DependencyResolver includes priority in its comparator only after dependency topology.
- Automated tests must cover dependency-over-priority, different priorities, equal priorities, and stable registration order.
- Resolution diagnostics should expose the final initialization order.

---

## Approval Status

**Accepted — Human Approved for FR-CORE-001-A.**

This decision does not constitute Human Design Freeze or Java implementation authorization.
