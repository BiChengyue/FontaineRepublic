# MIGRATION-01: Static Contract Authority Migration

> **Status:** Accepted — Human Approved for FR-CORE-001-A
> **Related Design:** FR-CORE-001-A Core Framework Architecture Document (Revision-13 freeze candidate)
> **Approval Scope:** FR-CORE-001-A architecture synchronization and implementation preparation only.
> **Authority:** Human Developer approval recorded by task FR-CORE-001-A-DECISION-ACTIVATION-01.
> **Baseline Boundary:** Architecture v2.7 remains unchanged.

---

## Context

Architecture v2.7 describes module ID, dependencies, and priority through IModule methods. Revision-11 identified a compatibility conflict between that model and the proposed factory-only registration model, where dependency resolution must occur before a runtime module instance is created.

---

## Decision

For FR-CORE-001, static module contract authority moves to immutable ModuleDefinition records.

ModuleDefinition owns:

- module ID;
- required dependencies;
- optional dependencies;
- priority;
- metadata;
- factory.

IModule owns:

- runtime behavior;
- lifecycle execution;
- runtime state.

The factory is never invoked during registration or static validation. It creates a fresh runtime IModule instance for each server lifecycle. The runtime instance name is validated against ModuleDefinition.id and cannot override the registered definition.

This is an FR-CORE-001 migration direction. It does not edit or reinterpret the frozen Architecture v2.7 baseline.

---

## Alternatives Considered

| Alternative | Assessment |
|-------------|------------|
| Retain IModule methods as static authority | Rejected for FR-CORE-001. Reading metadata from a runtime instance conflicts with registration-time dependency resolution and fresh-instance lifecycle rules. |
| Move static authority to ModuleDefinition | Accepted. It establishes one immutable static source before runtime creation. |
| Transitional adapter | Not selected as the primary model. It may be proposed later only for a demonstrated compatibility need and requires explicit precedence and removal rules. |

---

## Consequences

- Registration supplies the complete static contract without creating an IModule instance.
- DependencyResolver operates entirely on ModuleDefinitions.
- Runtime instances cannot change dependencies, priority, or metadata.
- Existing prototype registration paths require migration during FR-CORE-001 implementation.
- Future Feature Modules must use the ModuleDefinition registration model.
- Any compatibility adapter remains outside the approved implementation unless separately authorized.

---

## Implementation Impact

- Introduce immutable ModuleDefinition and factory-only registration.
- Update IModule and CoreManager integration according to an approved implementation Task Card.
- Preserve runtime identity validation through `instance.getName() == definition.id`.
- Test that registration never invokes the factory and that each server lifecycle receives a fresh instance.
- Do not modify persistence format, Feature Modules, Architecture v2.7, or unrelated interfaces.

---

## Approval Status

**Accepted — Human Approved for FR-CORE-001-A.**

This decision authorizes architecture synchronization and implementation preparation only. Java implementation still requires a separate Human-authorized task.
