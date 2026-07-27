# FR-CORE-001 Java Implementation Task

> **Status:** Prepared — Pending Human Implementation Authorization
> **Task Type:** Major Core Implementation
> **Design Input:** FR-CORE-001-A Revision-13 freeze candidate
> **Decision Input:** DEC-001, DEC-003, DEC-004, DEC-005, MIGRATION-01
> **Authority Boundary:** This document prepares implementation scope only. It does not authorize Java changes, activate Human Design Freeze, or approve implementation.

---

## 1. Goal

Transform the current prototype module lifecycle classes into the production-ready FR-CORE-001 framework defined by the Human-frozen FR-CORE-001-A design.

The implementation must provide:

- immutable static module definitions;
- deterministic registration and dependency resolution;
- fresh runtime module instances per server lifecycle;
- explicit runtime lifecycle state;
- deterministic runtime failure propagation;
- exactly-once shutdown;
- server-stop cleanup and runtime-container disposal;
- read-only runtime availability reporting.

---

## 2. Non-Goals

FR-CORE-001 must not implement:

- Feature Modules;
- Economy;
- Government;
- Citizen;
- Land;
- Industry;
- Military;
- GUI;
- Network Foundation;
- packet protocol or client synchronization;
- Permission system;
- internal Event Bus;
- runtime module enable/disable or hot swap;
- persistence schema migration;
- Architecture v2.7 changes;
- new external dependencies.

---

## 3. Allowed Changes

Java changes may be authorized in a future implementation task for:

- `CoreManager`;
- `IModule` migration required by MIGRATION-01;
- `ModuleDefinition`;
- `ModuleMetadata`;
- `IModuleFactory`;
- `ModuleRegistry`;
- `DependencyResolver`;
- `DependencyResolutionResult`;
- `RuntimeModuleContainer`;
- `RuntimeAvailabilityResult`;
- `ModuleAvailabilityRecord`;
- lifecycle state and failure-reason types;
- Forge lifecycle integration for CoreManager;
- focused automated tests for the approved Core Framework behavior.

Supporting documentation may be synchronized only when required to describe the implemented contract or validation result.

---

## 4. Forbidden Changes

The implementer must not:

- change Architecture v2.7;
- change accepted Decision content;
- add Feature Module behavior;
- add Network Foundation code;
- add business packets;
- add client-side gameplay authority;
- change the persistence data name, NBT schema, or DataManager public contract;
- add dependencies without separate Human approval;
- extend module boundaries beyond FR-CORE-001;
- implement a second dependency resolver or legacy priority-sorting path;
- use priority to override dependency topology;
- create module instances during registration or static validation;
- reuse one module instance across server lifecycles;
- begin work without Human implementation authorization.

---

## 5. Design References

- `docs/architecture/architecture.md` — Architecture v2.7 Frozen Baseline
- `docs/architecture/fr-core-001-a-architecture.md` — decision-synchronized design candidate
- `docs/decisions/DEC-001-event-foundation.md`
- `docs/decisions/DEC-003-network-boundary.md`
- `docs/decisions/DEC-004-module-cleanup-event.md`
- `docs/decisions/DEC-005-priority-tiebreaker.md`
- `docs/decisions/MIGRATION-01-static-contract-authority.md`
- Existing Core source:
  - `FontaineRepublic.java`
  - `IModule.java`
  - `CoreManager.java`
  - `ConfigManager.java`
  - `DataManager.java`
  - `ModSavedData.java`

The implementation must reference the Human Design Freeze commit/blob once that freeze exists. Until then, this task remains preparation-only.

---

## 6. Approved Architecture Contracts

### 6.1 Static contract

ModuleDefinition is the FR-CORE-001 authority for:

- module ID;
- required dependencies;
- optional dependencies;
- priority;
- metadata;
- factory.

IModule owns runtime behavior, lifecycle execution, and runtime state.

### 6.2 Dependency ordering

DependencyResolver applies:

1. dependency topology;
2. priority within the same dependency layer;
3. stable registration order for equal priority.

Dependency topology is always authoritative.

### 6.3 Runtime creation

For each server lifecycle:

1. DataManager initializes.
2. DependencyResolver produces an immutable structural result.
3. CoreManager processes definitions in deterministic order.
4. Definitions with unavailable required dependencies receive DEPENDENCY_FAILURE containers without factory execution.
5. Eligible definitions call `factory.createInstance()`.
6. Runtime ID is validated against ModuleDefinition.id.
7. RuntimeModuleContainer is created.
8. Module initialization executes.

Factory creation must not occur during registration, FMLCommonSetupEvent, or static validation.

### 6.4 Lifecycle

Normal:

```text
REGISTERED
→ INITIALIZING
→ ACTIVE
→ STOPPING
→ STOPPED
→ CLEANUP
→ TERMINATED
```

Dependency failure:

```text
REGISTERED
→ DEPENDENCY_FAILURE
→ DISCARD
```

Initialization failure:

```text
REGISTERED
→ INITIALIZING
→ INIT_FAILURE
→ STOPPING
→ STOPPED
→ CLEANUP
→ TERMINATED
```

### 6.5 Shutdown and cleanup

At `ServerStoppingEvent`:

- CoreManager invokes shutdown in reverse successful initialization order.
- `shutdown()` executes at most once per runtime instance.
- INIT_FAILURE instances already shut down are skipped.
- DEPENDENCY_FAILURE never calls shutdown.

At `ServerStoppedEvent`:

- CoreManager closes RuntimeAvailabilityResult.
- DEPENDENCY_FAILURE containers are discarded directly.
- Eligible STOPPED containers execute CLEANUP → TERMINATED.
- Eligible runtime containers and instances are disposed.
- ModuleDefinitions remain registered.

---

## 7. Implementation Phases

### Phase 1 — Core data structures

Create:

- Module ID/value validation;
- ModuleMetadata;
- ModuleDefinition;
- factory contract;
- lifecycle and availability enums/records.

Exit criteria:

- static records are immutable;
- no factory call is required to construct or validate a ModuleDefinition;
- no runtime state is stored in ModuleDefinition.

### Phase 2 — Module registration

Implement ModuleRegistry:

- unique ID enforcement;
- immutable definition storage;
- stable registration order;
- registration-window close;
- read-only lookup.

Exit criteria:

- duplicate IDs fail without replacing the first definition;
- registration after close fails;
- priority is stored but not interpreted.

### Phase 3 — DependencyResolver

Implement:

- missing required-dependency detection;
- required-cycle hard failure;
- deterministic optional-edge removal;
- topology → priority → stable registration order;
- immutable DependencyResolutionResult.

Exit criteria:

- output is deterministic;
- dependency topology cannot be overridden by priority;
- optional-cycle handling is logged and reproducible.

### Phase 4 — Runtime container lifecycle

Implement:

- RuntimeModuleContainer;
- explicit instance availability contract;
- legal state transitions;
- shutdown tracking;
- RuntimeAvailabilityResult and ModuleAvailabilityRecord.

Exit criteria:

- DEPENDENCY_FAILURE instance access is forbidden;
- invalid transitions fail deterministically;
- RuntimeAvailabilityResult has one CoreManager writer and read-only consumers.

### Phase 5 — Factory creation flow

Implement per-server runtime creation:

- one fresh instance per eligible definition;
- no factory invocation for dependency-failed modules;
- factory failure recording;
- no instance reuse across server starts.

Exit criteria:

- registration never invokes the factory;
- cached or shared runtime instances violate validation;
- factory failure propagates to required dependents.

### Phase 6 — Runtime validation

Implement mandatory:

```text
instance.getName() == ModuleDefinition.id
```

Exit criteria:

- mismatch is a hard runtime factory failure;
- no RuntimeModuleContainer is published for direct factory/ID failure;
- runtime instance cannot override dependencies, priority, or metadata.

### Phase 7 — Shutdown and cleanup

Integrate:

- ServerStoppingEvent exactly-once shutdown;
- reverse successful initialization order;
- INIT_FAILURE guarded immediate shutdown;
- ServerStoppedEvent cleanup, termination, and disposal;
- RuntimeAvailabilityResult close.

Exit criteria:

- no duplicate shutdown;
- DEPENDENCY_FAILURE executes no lifecycle methods;
- old containers and instances are not reused after restart.

### Phase 8 — Automated tests

Add focused tests for all acceptance criteria without introducing an unrelated test framework.

---

## 8. Acceptance Criteria

1. ModuleDefinition is immutable and contains the complete approved static contract.
2. Registration does not create or inspect IModule instances.
3. Duplicate IDs are rejected deterministically.
4. Dependency ordering is deterministic across repeated runs.
5. Dependency topology always overrides priority.
6. Equal priorities preserve stable registration order.
7. Missing required dependencies and required cycles produce structural failure.
8. Optional cycles are resolved through the documented deterministic edge-removal rule.
9. Each eligible module receives a fresh runtime instance per server lifecycle.
10. Runtime ID mismatch is a hard failure.
11. Required runtime failure propagation records direct source and root cause deterministically.
12. INIT_FAILURE invokes shutdown at most once.
13. DEPENDENCY_FAILURE invokes neither init nor shutdown.
14. ServerStoppingEvent follows reverse successful initialization order.
15. ServerStoppedEvent performs eligible cleanup, termination, and disposal.
16. RuntimeAvailabilityResult does not persist or cross server lifecycles.
17. Server restart creates new containers and instances from preserved ModuleDefinitions.
18. Existing persistence DATA_NAME, NBT structure, and DataManager API remain unchanged.
19. No Network Foundation, internal Event Bus, Permission system, or Feature Module implementation is introduced.
20. Gradle build succeeds.

---

## 9. Required Validation

### Static and unit-level validation

- deterministic dependency ordering;
- duplicate ID rejection;
- required dependency failure;
- required-cycle failure;
- optional-cycle deterministic resolution;
- priority cannot override topology;
- equal-priority stability;
- no registration-time factory execution;
- runtime ID mismatch failure;
- factory failure propagation;
- INIT_FAILURE exactly-once shutdown;
- DEPENDENCY_FAILURE lifecycle;
- illegal transition rejection.

### Lifecycle validation

- first server start creates fresh containers and instances;
- ServerStoppingEvent shuts down eligible instances exactly once;
- ServerStoppedEvent performs cleanup and disposal;
- second server start creates new containers and new instances;
- ModuleDefinitions persist between server lifecycles;
- RuntimeAvailabilityResult does not persist between server lifecycles.

### Build

Required command:

```text
gradlew.bat build
```

### Runtime verification

Forge lifecycle behavior requires dedicated-server validation after build success and explicit runtime-test authorization. Build success alone is not runtime proof.

---

## 10. Deviation Alert Conditions

Stop implementation and report a Deviation Alert if:

- the Human Design Freeze reference is absent or inconsistent;
- architecture and source contracts conflict;
- a required interface change exceeds MIGRATION-01;
- lifecycle behavior cannot be implemented without changing an accepted Decision;
- persistence format or DataManager API would change;
- network, client, permission, or Feature Module code becomes necessary;
- a new dependency appears necessary;
- existing dirty files overlap implementation targets without clear ownership;
- deterministic ordering or exactly-once shutdown cannot be demonstrated.

The implementer must not resolve these conditions through an undocumented design choice.

---

## 11. Required Output

After authorized implementation, return an Implementation Report containing:

- Agent Identity;
- repository branch, HEAD, and working-tree state;
- modified files;
- diff summary;
- executed commands;
- build result;
- automated test results;
- runtime verification result or explicit not-run status;
- acceptance-criteria results;
- architecture impact;
- remaining risks;
- unresolved questions;
- independent review request.

Review is not Human approval. Merge and final acceptance remain Human decisions.
