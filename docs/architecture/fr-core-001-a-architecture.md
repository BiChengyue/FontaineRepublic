# FR-CORE-001-A Core Framework Architecture Document

> **Status:** Decision-Synchronized Design — Revision-13 (Pending Human Design Freeze)
> **Authority:** Architecture v2.7 (Frozen Baseline)
> **This document is subordinate to Architecture v2.7. It does not modify or reinterpret the frozen baseline.**
> **Decision synchronization:** DEC-001, DEC-003, DEC-004, DEC-005, and MIGRATION-01 reflect Human decisions recorded by FR-CORE-001-A-DECISION-ACTIVATION-01.
> **Authorization boundary:** These decisions authorize architecture synchronization and implementation preparation only. Java implementation and Human Design Freeze remain separately gated.
> **Freeze identity:** A Human Design Freeze must reference one unique Git commit or blob containing the reviewed candidate. The current draft does not claim to be frozen.

---

## 1. Scope

### Includes

| Area | Description |
|------|-------------|
| **Module Framework** | Module identity model, registration, metadata |
| **Lifecycle Management** | State machine, valid/invalid transitions, Forge event integration points, failure states |
| **Dependency Resolution** | Dependency declaration, topological sort, cycle detection, missing dependency handling, stable ordering |
| **CoreManager Architecture** | Responsibilities: module registry, lifecycle orchestration, dependency resolution, error handling, query API |

### Excludes

| Area | Reason |
|------|--------|
| Economy | Business module — Phase 2+ per Architecture v2.7 |
| Government | Business module — Phase 5+ per Architecture v2.7 |
| Citizen System | Business module — Phase 1 per Architecture v2.7 |
| Land System | Business module — Phase 3 per Architecture v2.7 |
| GUI | Client layer — Phase 8 per Architecture v2.7 |
| Business Network | Network packets for feature modules — Phase 2+ per Architecture v2.7 |
| Permission Implementation | Permission framework is Phase 0 Step 6 per v2.7; Core Framework defines only the boundary integration point |
| **Runtime Enable/Disable** | Removed from FR-CORE-001 scope per N-04. Requires separate DEC and task if needed in future. |

---

## 2. Component Contract

### 2.1 ModuleRegistry

**Responsibility:**
Maintain the authoritative collection of all registered module definitions by Module ID. The registry stores **immutable ModuleDefinition** records — one per registered module — that persist across server lifecycles. Storage of a definition includes its immutable priority field, but ModuleRegistry does not interpret priority or apply ordering logic.

#### 2.1.1 ModuleDefinition

ModuleDefinition is an immutable record created during Mod Lifecycle registration. It is not modified during Server Lifecycle. It holds static module information only — no runtime state and no IModule instance.

**Contents:**

| Field | Source | Purpose |
|-------|--------|---------|
| `ModuleId id` | `register()` parameter | Authoritative identifier |
| `ModuleMetadata metadata` | `register()` parameter | Immutable static description stored in ModuleDefinition (display name, description, version). Not runtime state and not a runtime authority source. |
| `Set<ModuleId> requiredDependencies` | Provided at registration | Required dependency declarations — static contract |
| `Set<ModuleId> optionalDependencies` | Provided at registration | Optional dependency declarations — static contract |
| `int priority` | Provided at registration | Immutable static priority value owned by ModuleDefinition; consumed only by DependencyResolver as the DEC-005 tiebreaker |
| `IModuleFactory factory` | Registration | Creates a new IModule instance per server lifecycle. Never called during registration — only at server start. |

**ModuleDefinition does NOT hold:**
- An IModule instance (runtime instances are created per server — see N-08)
- Lifecycle state (belongs to RuntimeModuleContainer)
- Resolved dependencies (determined per server by DependencyResolver)
- Runtime data of any kind

**Static contract ownership — approved FR-CORE-001 migration model:**

Architecture v2.7 currently describes dependency and priority declarations through `IModule` methods. MIGRATION-01 authorizes FR-CORE-001 to move static module contract authority to registration metadata stored in ModuleDefinition.

Under the approved migration direction, all static contract properties are provided explicitly at registration and ModuleDefinition becomes the sole authoritative source for FR-CORE-001. This is an FR-CORE-001 migration direction, not a modification or reinterpretation of the frozen Architecture v2.7 baseline. Java implementation remains separately gated by an approved implementation task.

| Property | Source | Stored In |
|----------|--------|-----------|
| Module ID | `register()` id parameter | `ModuleDefinition.id` |
| Required dependencies | Provided as registration parameter | `ModuleDefinition.requiredDependencies` |
| Optional dependencies | Provided as registration parameter | `ModuleDefinition.optionalDependencies` |
| Priority | Provided as registration parameter | `ModuleDefinition.priority` |

**The factory is never called during registration.** `createInstance()` is invoked only during Server Runtime Creation (see 2.2). The factory's sole purpose is runtime instance creation — it is not a source of static contract data.

**Design rationale:** By making the static contract explicit at registration rather than reading it from a factory-created instance, the design eliminates any inconsistency between the registered contract and runtime behavior. The factory is never called during registration.

**IModuleFactory contract:**

```java
public interface IModuleFactory {
    IModule createInstance();
}
```

**Factory freshness requirement:** Each call to `createInstance()` must return a **new** IModule instance. The factory must not cache or reuse instances across calls. This ensures each server lifecycle gets a clean module instance with no stale state from previous runs.

**What the factory is NOT:** The factory is not a singleton holder, not a cache, and not a shared-instance provider. It is a creation-only contract.

Each server lifecycle gets its own set of instances. Instances from a previous server stop are discarded and must not be reused.

**Creation-only Contract:**

`createInstance()` constructs an IModule object and initializes internal fields only. It must not perform any operation that ties the instance to runtime environment state.

| Permitted in createInstance() | Forbidden in createInstance() |
|-------------------------------|-------------------------------|
| Object construction | Accessing `MinecraftServer` |
| Initializing internal fields | Accessing `World` or `Level` |
| Setting default values | Accessing `DataManager` or persistent storage |
| Allocating non-shared local state | Reading or writing saved data / NBT |
| | Registering Forge event handlers (`MinecraftForge.EVENT_BUS.register`) |
| | Registering business events or packet handlers |
| | Starting threads or scheduled tasks |
| | Creating long-lived resources (file handles, network connections, thread pools) |
| | Modifying global static state |
| | Calling `init()` — initialization belongs in the lifecycle `init()` method |

**Rationale:** `createInstance()` is called only at server start during RuntimeModuleContainer creation. Side effects in construction would produce unintended environmental changes, duplicate registrations, or resource leaks. All runtime initialization (event registration, resource acquisition, server-dependent setup) belongs in **`IModule.init()`** during ServerStartingEvent.

**Enforcement:**
- Server Runtime Phase: `createInstance()` failure → Module unavailable for this server lifecycle (no container created, required dependents fail)

**Registration process (FMLCommonSetupEvent) — no instance creation:**

The registration process does **not** call `createInstance()`. The static contract is provided explicitly.

```
Step 1: Module calls register(id, factory, metadata, requiredDependencies, optionalDependencies, priority)

Step 2: Registry validates:
          - ID: non-null, non-empty, valid format, no duplicate
          - Metadata: non-null; every field designated as required by the
            ModuleMetadata contract is non-null and non-blank
          - Dependencies: each dependency ID has valid format
          - Priority: valid integer range

Step 3: On success, stores immutable ModuleDefinition with:
          id + factory + metadata + requiredDependencies + optionalDependencies + priority
```

This is a basic validity check only. Optional descriptive fields may be empty where the existing `ModuleMetadata` contract permits it; registration validation does not introduce a new metadata field, schema, or runtime interpretation. Invalid required metadata rejects the registration, and no ModuleDefinition is stored.

**No IModule instance is created during registration.** The factory is stored in ModuleDefinition and called only during server start (see 2.2). The IModule interface's `getName()`, `getRequiredDependencies()`, `getOptionalDependencies()`, and `getPriority()` methods are not used during registration — the static contract is explicit.

#### 2.1.2 Registration Lifecycle

The ModuleRegistry operates in two distinct lifecycle scopes:

**Mod Lifecycle (runs once per mod load, persists across server starts/stops):**

| Phase | Action |
|-------|--------|
| Registration Window (FMLCommonSetupEvent) | Modules call `register(id, factory, metadata, requiredDeps, optionalDeps, priority)`. Registry validates ID uniqueness and format, basic required ModuleMetadata validity, dependency ID format, and priority range. No IModule instance is created. On success, stores immutable ModuleDefinition with explicit static contract. |
| Registration Closed (after FMLCommonSetupEvent post-queue) | Registry refuses new registrations. ModuleDefinition set is finalized for the lifetime of the mod container. No runtime state is created at this point. |

**Sever Lifecycle (runs per server start — ModuleDefinitions are read-only; new containers and instances are created):**

| Phase | Action |
|-------|--------|
| Pre-Init (ServerAboutToStartEvent) | No module instance creation. CoreManager performs pre-server validation (configuration, environment). DataManager.init() has not yet run. |
| Initialization (ServerStartingEvent) | CoreManager drives the full creation pipeline: DataManager.init() prepares persistence; DependencyResolver produces the structural order; then CoreManager processes each ModuleDefinition deterministically. If a required dependency is already unavailable, CoreManager creates a status-only RuntimeModuleContainer, records the REGISTERED → DEPENDENCY_FAILURE transition, and skips factory, validation, and init. Otherwise CoreManager calls `factory.createInstance()`, validates `instance.getName() == definition.id`, creates a RuntimeModuleContainer with `Optional.of(instance)`, and initializes it in resolved order. |
| Active Runtime | Registry supports query API (getModuleDefinition, hasModule). |
| Shutdown | Registry is read-only during shutdown. |
| Post-Shutdown (`ServerStoppedEvent`) | CoreManager closes RuntimeAvailabilityResult, directly discards DEPENDENCY_FAILURE containers, executes STOPPED → CLEANUP → TERMINATED for eligible containers, and disposes their runtime instances. ModuleDefinitions remain registered for the next server lifecycle. |

**Key rules:**
- Registration with a duplicate ID is rejected. The registry logs the conflict and returns a failure indicator. First registration wins; second is discarded. Duplicate ID is a **registration failure** — no ModuleDefinition is created for the rejected module.
- Module ID consistency is validated at **server start** after `factory.createInstance()` produces a new instance. The instance's `getName()` must match the registered ID (see 2.1.3 Consistency Rule). Registration itself does not validate against an instance — no instance exists yet.
- After the registration window closes, no new modules may be registered until the next mod container reload (game restart).
- The registry and its ModuleDefinitions persist across server starts. Server lifecycle events (start/stop) do not clear module definitions.
- ModuleDefinition owns the immutable static priority value.
- ModuleRegistry stores ModuleDefinitions, including each definition's priority field, but does not interpret or apply priority.
- DependencyResolver is the only component that consumes priority for ordering, as the accepted DEC-005 tiebreaker.
- Dependency resolution does NOT occur during Mod Lifecycle. It runs per Server Lifecycle (see 2.3).

#### 2.1.3 Module ID Rules

| Rule | Specification |
|------|---------------|
| **Source** | The Module ID is the `id` parameter passed to `register()`. This is the sole authoritative identifier for the module. |
| **Format** | Lowercase alphanumeric plus hyphens. Examples: `"core"`, `"economy"`, `"citizen-system"`. Follows Architecture v2.7 naming conventions. |
| **Validation** | At registration time, ModuleRegistry validates: non-null, non-empty, format matches allowed pattern, no duplicate ID exists. No IModule instance is involved. |
| **Normalization** | The registry normalizes the ID (trim whitespace, lowercase) before storage. |
| **Consistency Rule** | At server start, when `factory.createInstance()` produces a new IModule instance, the instance's `getName()` **must** exactly equal the ModuleDefinition's registered ID. If a mismatch is detected, the instance is **rejected** — this is a Runtime Factory Failure (see 3.8). There is only one source of truth for Module ID, and it is the `register()` id parameter. The IModule instance's `getName()` must match it, not define it. |

### 2.2 RuntimeModuleContainer

**Responsibility:**
Represent the per-server runtime status of a ModuleDefinition. A container stores lifecycle state, resolved dependencies, failure information, shutdown tracking, and an **explicitly optional** IModule instance. RuntimeModuleContainers are created during ServerStartingEvent. Eligible STOPPED containers complete cleanup and are discarded at ServerStoppedEvent according to DEC-004.

There is at most one RuntimeModuleContainer per structurally resolvable ModuleDefinition per server start. Structurally unresolvable definitions receive no container. A container that has entered TERMINATED or DEPENDENCY_FAILURE is never reused.

**Creation flow:**

```
┌────────────────────────────────────────────────────────────┐
│ Mod Lifecycle (FMLCommonSetupEvent):                       │
│   ModuleRegistry stores ModuleDefinitions                  │
│   No instances created                                     │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│ ServerStartingEvent:                                       │
│   DataManager.init()                                       │
│   DependencyResolver.resolve(ModuleDefinitions)            │
│   → ordered list + failures                                │
│                                                            │
│   For each ModuleDefinition in resolved order:             │
│     1. Check required dependencies in resolved order        │
│        → If unavailable: create status-only container,      │
│          record REGISTERED → DEPENDENCY_FAILURE, skip       │
│          factory, validation, init, and shutdown            │
│     2. Otherwise call factory.createInstance()              │
│        → If throws: no container created, module unavailable│
│     3. Validate instance.getName() == definition.id        │
│        → If mismatch: no container created, Runtime Factory│
│          Failure                                           │
│     4. Create RuntimeModuleContainer with Optional.of       │
│        (instance), state = REGISTERED                       │
│                                                            │
│   Initialize REGISTERED containers in resolved order        │
│   → dependency unavailable: DEPENDENCY_FAILURE             │
│   → success: ACTIVE → exactly-once shutdown → STOPPED   │
│   → init failure: exactly-once shutdown → STOPPED     │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
Post-shutdown disposal: ServerStoppedEvent owns cleanup and disposal for eligible STOPPED containers under DEC-004.
Next server start: new containers + new instances created
from preserved ModuleDefinitions.
```

**Runtime creation failure propagation clarification:** When factory creation, runtime Module ID validation, or module initialization fails, CoreManager records the directly failed module according to the existing failure rule. As processing continues in deterministic resolved order, every later module whose required dependency is unavailable follows the Section 2.4 propagation algorithm and enters DEPENDENCY_FAILURE. This clarification does not change the existing dependency propagation contract or create an additional failure path.

**Contents:**

| Field | Source | Purpose |
|-------|--------|---------|
| `ModuleDefinition definition` | From ModuleRegistry | Immutable definition reference |
| `Optional<IModule> instance` | `definition.factory.createInstance()` or empty | Explicit instance availability. Empty only when a required dependency failed before factory creation. Never represented by implicit `null`. |
| `ModuleState state` | Lifecycle state | Current per-server lifecycle state |
| `Set<ModuleId> resolvedDependencies` | DependencyResolver | Successfully resolved dependencies (per server) |
| `Throwable failureCause` | Lifecycle | If module failed, the exception or reason |
| `boolean initializationStarted` | CoreManager | Set immediately before invoking `init()` |
| `boolean initializationSucceeded` | CoreManager | Set only after `init()` returns successfully |
| `boolean shutdownExecuted` | CoreManager | Exactly-once guard; claimed before invoking `shutdown()` and never reset |

**Instance availability contract:**

| Container state | Instance requirement | Instance access |
|-----------------|----------------------|-----------------|
| REGISTERED | `Optional.of(instance)` for normal published containers. `Optional.empty()` is permitted only inside CoreManager's atomic, unpublished REGISTERED → DEPENDENCY_FAILURE construction path | No query API may observe an empty REGISTERED container |
| INITIALIZING / ACTIVE / INIT_FAILURE / STOPPING / STOPPED / CLEANUP | `Optional.of(instance)` required | Read-only access through `getInstance()`; lifecycle invocation remains CoreManager-only |
| DEPENDENCY_FAILURE | May be empty if failure was known before factory creation; may be present if an upstream module failed during the later initialization phase | Module instance access is forbidden regardless of presence. `getInstance()` returns `Optional.empty()` for public/query callers in this state; CoreManager must not call `init()` or `shutdown()` |
| TERMINATED | Container is stale and scheduled for disposal | Instance access is forbidden |

`RuntimeModuleContainer` never uses `null` to represent absence. A `requireInstance()`-style internal accessor must reject DEPENDENCY_FAILURE and TERMINATED states with an explicit lifecycle error.

**DEPENDENCY_FAILURE atomic publication contract:**
- CoreManager performs status-only container construction, REGISTERED → DEPENDENCY_FAILURE transition validation, failure-record assignment, and RuntimeAvailabilityResult update as one internal atomic lifecycle operation.
- A container with `REGISTERED + Optional.empty()` is an unpublished construction detail only. It must not be inserted into any externally queryable collection or returned by any query interface.
- CoreManager publishes the container and its corresponding availability record only after the DEPENDENCY_FAILURE transition and failure data are complete.
- External readers may observe either no published container for that module yet or the completed DEPENDENCY_FAILURE state. They must never observe an intermediate REGISTERED container without an instance.

**State ownership model:**

| Component | Role |
|-----------|------|
| **RuntimeModuleContainer** | **Stores** the current lifecycle state for this server run. Provides read-only state and instance query access. Does not initiate transitions. |
| **CoreManager** | **Orchestrates** lifecycle transitions. Decides when and how state changes occur. Creates containers at server start and discards each container at its defined terminal/disposal boundary. Separately owns and closes RuntimeAvailabilityResult at the end of the current Server Runtime Scope. |
| **LifecycleStateMachine** (if retained) | Encapsulates transition validation logic. Evaluates whether a proposed transition is valid. Does not hold state or drive transitions independently. CoreManager delegates validation to it. |

**Rule — single driver:**
CoreManager is the **sole driver** of state transitions. No other component (LifecycleStateMachine, external API caller, IModule implementation) may mutate RuntimeModuleContainer state. LifecycleStateMachine, if used, is a validation delegate only.

**Direct state mutation from outside the lifecycle orchestrator is forbidden.**

### 2.3 DependencyResolver

**Responsibility:**
Given the set of registered ModuleDefinitions for the current server lifecycle, determine a valid initialization order and identify any unresolvable modules. The DependencyResolver runs per server start, during ServerStartingEvent.

**Execution scope:**
- Runs **once per server lifecycle**, during ServerStartingEvent (after DataManager.init())
- Operates on the current set of ModuleDefinitions from the registry
- Reads dependency declarations from each ModuleDefinition (captured at registration time)
- Reads ModuleDefinition priority only as the DEC-005 ordering tiebreaker; it does not own or mutate the stored value
- Produces an ordering valid only for the current server lifecycle
- Does NOT run during Mod Lifecycle (FMLCommonSetupEvent)

**Required dependency resolution:**
- Parse each ModuleDefinition's `requiredDependencies` (captured at registration)
- Verify every required dependency resolves to a registered Module ID
- If a required dependency is missing, the ModuleDefinition is recorded as unresolvable in `DependencyResolutionResult`
- **Required dependency determines module availability.** A module with an unresolved required dependency cannot proceed to container creation.

**Optional dependency resolution:**
- Parse each ModuleDefinition's `optionalDependencies` (captured at registration)
- If an optional dependency is present and registered, include it in the resolved set
- If an optional dependency is missing, the dependent module proceeds without it
- The dependent module is responsible for checking dependency availability at runtime
- **Optional dependency does NOT determine whether a module starts.** A missing optional dependency is informational, not a failure.

**Topological sorting:**
- Build a directed graph where edges represent `A depends on B`
- Perform topological sort using Kahn's algorithm
- Required dependencies produce mandatory edges in the graph
- Optional dependencies produce edges that participate in ordering unless removed by cycle resolution

**Cycle detection — two-phase algorithm:**

**Phase 1 (required-only subgraph):**
1. Build a reduced graph containing only required dependency edges
2. Run DFS cycle detection on this reduced graph
3. If a cycle is detected in the required-only subgraph → **hard failure**. All modules in the cycle are recorded as unresolvable in `DependencyResolutionResult`. This cannot be resolved automatically — requires Human decision to break.
4. If no required-only cycle, proceed to Phase 2.

**Phase 2 (full graph with optional edges):**
1. Build the full graph including both required and optional edges
2. Run DFS cycle detection
3. If a cycle is detected that involves at least one optional edge:
   a. Identify all optional edges that participate in cycles
   b. Sort candidate optional edges by (source Module ID, target Module ID) in ascending alphabetical order for determinism
   c. Remove the first optional edge that breaks the cycle
   d. Log: `"Optional dependency cycle resolved: removed optional edge {A} → {B}"`
   e. Repeat detection until no cycles remain
4. If the remaining graph is acyclic, proceed with topological sort
5. Required-only cycles from Phase 1 are never silently resolved

**Deterministic edge removal rules:**
- Optional edges are removed in **ascending alphabetical order** of (source ID, target ID)
- This ensures identical results across runs on the same input
- The resolver logs each removed edge for Human review

**Missing dependency handling:**
- After all registrations are complete, the resolver validates every module's dependency list against the registry
- Missing required dependency → ModuleDefinition is recorded as unresolvable in DependencyResolutionResult
- Missing optional dependency → module proceeds without it, logged as informational

**Stable ordering rules:**

Modules are ordered by the following priority levels, applied sequentially:

| Priority | Rule |
|----------|------|
| **First** | Dependency topology. Modules are ordered by their position in the topological sort of the dependency graph. A module appears after all its dependencies. |
| **Second** | Priority value from ModuleDefinition (lower = earlier initialization). This is a tiebreaker for modules at the same topological level with no dependency relationship between them. |
| **Third** | Registration order (stable insertion order from ModuleRegistry). Ensures fully deterministic ordering across runs. |

**Rules for priority:**
- Priority is a **tiebreaker within the same topological level**, not a primary ordering mechanism.
- Priority values are not used as Map keys. Multiple modules with the same priority value must coexist without collision.
- If two modules share the same topological level and the same priority value, registration order determines their relative position.
- If priority is not explicitly provided at registration, it defaults to 50.

**Output — DependencyResolutionResult:**

The DependencyResolver produces an immutable `DependencyResolutionResult` covering **structural dependency analysis only**:

| Field | Description |
|-------|-------------|
| **Resolution state** | Whether the dependency graph is structurally valid (`VALID`) or has structural failures (`INVALID`) |
| **Initialization order** | List of ModuleDefinitions in topologically sorted order, with priority and registration order tiebreakers |
| **Unresolvable definitions** | ModuleDefinitions that failed structural dependency resolution (missing required dependency or cycle). These modules **never proceed to container creation**. |
| **Dependency graph** | The full directed graph (for debugging and Human review) |

**Structural failures only:** `DependencyResolutionResult` covers missing required dependencies and cycles. Runtime failures (factory, validation, init) are tracked in `RuntimeAvailabilityResult` (see 2.4).

**Immutability:** Once produced, `DependencyResolutionResult` is immutable for the server lifecycle. It is not modified by runtime outcomes.

### 2.4 RuntimeAvailabilityResult

**Responsibility:**
Track runtime availability outcomes for modules that passed structural dependency resolution. RuntimeAvailabilityResult is produced and owned exclusively by CoreManager during ServerStartingEvent.

**Lifetime contract:**
- RuntimeAvailabilityResult belongs exclusively to the current **Server Runtime Scope**.
- It is transient runtime state, not persistent data, and is never written to SavedData/NBT.
- It is not owned or destroyed by any RuntimeModuleContainer.
- CoreManager closes and discards it at `ServerStoppedEvent`, after the current server runtime has ended. It is never retained into the next server lifecycle.
- This lifetime is independent of DEC-004. DEC-004 selects only the execution location for module `CLEANUP → TERMINATED`; it neither extends nor shortens RuntimeAvailabilityResult lifetime.
- Module cleanup logic must not require RuntimeAvailabilityResult after the Server Runtime Scope closes.

**Scope — runtime failures only:**
RuntimeAvailabilityResult records failures that occur **after** structural dependency resolution, during the container creation and initialization phase:

| Failure Type | When Detected | Consequence |
|-------------|---------------|-------------|
| **Factory creation failure** | `factory.createInstance()` throws | Module unavailable; dependents propagate failure |
| **Runtime Module ID mismatch** | `instance.getName() != definition.id` | Module unavailable; dependents propagate failure |
| **Init failure** | `IModule.init()` throws | Module enters INIT_FAILURE; dependents propagate failure |

**It does NOT cover** structural dependency failures (missing dependency, cycle) — those are in `DependencyResolutionResult`.

**Creation timing:**

RuntimeAvailabilityResult is created **after** DependencyResolutionResult is produced. CoreManager then executes two deterministic phases:

```
DependencyResolver.resolve() → immutable DependencyResolutionResult
    ↓
CoreManager creates mutable RuntimeAvailabilityResult
    ↓
Phase 1 — runtime creation, in resolved order:
  required dependency already unavailable
    → create status-only container with Optional.empty()
    → record REGISTERED → DEPENDENCY_FAILURE
    → skip factory and ID validation
  otherwise
    → factory.createInstance()
    → validate getName() == id
    → create container with Optional.of(instance), state = REGISTERED
    ↓
Phase 2 — initialization, in resolved order:
  required dependency unavailable
    → transition existing REGISTERED container to DEPENDENCY_FAILURE
    → retain any internal instance only for disposal; prohibit access
    → skip init()
  otherwise
    → init()
```

**Modification permissions:**

| Operation | Permission |
|-----------|------------|
| Create RuntimeAvailabilityResult | CoreManager only |
| Record failure | CoreManager only |
| Query failures | Read-only — any component via CoreManager API |
| Modify after creation | Forbidden — immutable after ServerStartingEvent completes |

**Failure propagation rules:**

RuntimeAvailabilityResult is owned and managed exclusively by CoreManager. Propagation follows the resolved initialization order from `DependencyResolutionResult`, ensuring deterministic behavior.

**Algorithm — Required Dependency Failure Propagation:**

When a module fails at runtime (factory, ID validation, or init), CoreManager records the failure in `RuntimeAvailabilityResult.failedModules` and propagates to required dependents during the same creation or initialization phase:

```
Given: resolvedOrder = DependencyResolutionResult.initializationOrder
       unavailable = RuntimeAvailabilityResult.unavailableModules

For phase in [RUNTIME_CREATION, INITIALIZATION]:
    For each ModuleDefinition M in resolvedOrder:
        For each required dependency D of M, sorted by its position in resolvedOrder:
            If D ∈ unavailable:
                If phase == RUNTIME_CREATION and no container exists for M:
                    create RuntimeModuleContainer(M, Optional.empty(), REGISTERED)
                Else:
                    use the existing REGISTERED container for M

                CoreManager transitions M: REGISTERED → DEPENDENCY_FAILURE
                Record dependencyFailures, nearest source D, and recursive root cause
                Add M.id to unavailable
                Do not call factory, init, or shutdown for this branch
                Stop checking remaining dependencies (first failure wins)
                Continue to next ModuleDefinition

        If phase == RUNTIME_CREATION and no required dependency failed:
            call factory → validate ID → create container with Optional.of(instance)

        If phase == INITIALIZATION and no required dependency failed:
            call init() under the exactly-once lifecycle rules
```

**Key rules:**

1. **First failure wins:** When a module has multiple failed required dependencies, the first one encountered in `resolvedOrder` is recorded as the propagation source. This prevents non-deterministic HashMap iteration order.
2. **Deterministic ordering:** All propagation decisions use `resolvedOrder` (the same topologically sorted list from DependencyResolutionResult). No HashMap iteration, no implementation-dependent ordering.
3. **Transitive propagation:** If module C declares a required dependency on A, and A is in DEPENDENCY_FAILURE because B failed, then C also enters DEPENDENCY_FAILURE when checked. The propagation source for C is A (the nearest failed dependent), and the root cause is B (found by following `directFailureSource` records recursively).
4. **Optional dependency:** If a dependent's dependency on the failed module is optional, the dependent continues initialization normally. The dependent is responsible for checking availability at runtime via CoreManager API.
5. **Propagation stops at:** (a) modules that don't declare a required dependency on the failed module, (b) optional dependents, (c) modules already in DEPENDENCY_FAILURE or TERMINATED.

**Data model:**

`RuntimeAvailabilityResult` contains one `ModuleAvailabilityRecord` per structurally resolvable module. Sets such as failed, dependency-failed, and unavailable modules are derived read-only views of these records, not independently mutable sources of truth.

| `ModuleAvailabilityRecord` field | Contract |
|----------------------------------|----------|
| `ModuleId moduleId` | Module whose runtime availability is described |
| `AvailabilityStatus status` | One of `PENDING`, `AVAILABLE`, `DIRECT_FAILURE`, or `DEPENDENCY_FAILURE` |
| `Optional<ModuleId> directFailureSource` | Empty for an available module or a direct self-failure; for dependency propagation, the nearest unavailable required dependency selected by the first-failure-wins rule |
| `Optional<ModuleId> rootCauseModule` | Empty when available; otherwise the module where the original factory, ID-validation, or init failure occurred |
| `Optional<FailureReason> failureReason` | Empty when available; otherwise the original root failure reason, including failure category and diagnostic cause |
| `int propagationOrderIndex` | Zero-based position in `DependencyResolutionResult.initializationOrder` at which the final availability outcome was recorded |

**Mutation and snapshot contract:**

- CoreManager is the sole writer.
- During `ServerStartingEvent`, CoreManager uses a private controlled-mutation builder. Each module record may move from `PENDING` to exactly one final status; finalized records cannot be rewritten.
- For dependency propagation, `directFailureSource` is the first unavailable required dependency in resolved order. `rootCauseModule` and `failureReason` are copied from that source record, making direct and transitive propagation deterministic.
- When startup processing completes, CoreManager publishes an immutable `RuntimeAvailabilityResult` snapshot. No component, including CoreManager, may modify the published snapshot.
- CoreManager exposes read-only queries: `getAvailability(ModuleId)`, `isAvailable(ModuleId)`, and `getRuntimeAvailabilitySnapshot()`. Query results must not expose mutable internal collections.

**After ServerStartingEvent completes:**
- RuntimeAvailabilityResult remains immutable for the remainder of the server lifecycle
- Available only through the read-only CoreManager query API
- Closed and discarded by CoreManager at `ServerStoppedEvent`, independently of RuntimeModuleContainer disposal and DEC-004

---

## 3. Lifecycle Model

The lifecycle model governs a **single Server Lifecycle** within one set of RuntimeModuleContainers and IModule instances. ServerStoppingEvent brings initialized instances to STOPPED through exactly-once shutdown. Under DEC-004, ServerStoppedEvent owns final CLEANUP, TERMINATED, and eligible container disposal. A new server start creates fresh containers and fresh instances.

### 3.1 State Machine

```
REG ──→ INIT ──→ ACTIVE ──→ STOPPING ──→ STOPPED
 │       │                              ▲
 │       ▼                              │
 │  INIT_FAILURE ──→ STOPPING ──────────┘
 │
 └──→ DEPENDENCY_FAILURE
    (runtime dependency failure)


CLEANUP → TERM (normal lifecycle only)
```

**Key design decisions:**
- The state machine starts at **REGISTERED** and applies to a single RuntimeModuleContainer within one server lifecycle.
- **Resolution failures are Definition-level, not container-level.** The DependencyResolver produces a `DependencyResolutionResult` listing passing and failing ModuleDefinitions. No container is created for modules that fail structural resolution. Container states do not include RESOLVING or RESOLVE_FAILURE.
- **DUPLICATE_ID is NOT a container state.** Duplicate ID is a registration failure — no ModuleDefinition is created, no container exists for the rejected module.
- **CoreManager is the sole shutdown invoker.** `shutdown()` is guarded by `shutdownExecuted` and may run at most once for each runtime instance.
- **DEPENDENCY_FAILURE executes neither `init()` nor `shutdown()`.** The container may internally hold no instance (failure known in Phase 1) or an uninitialized instance (upstream init failed in Phase 2), but instance access and lifecycle invocation are forbidden in both cases.
- **INIT_FAILURE shutdown is immediate and exactly once.** CoreManager atomically claims `shutdownExecuted`, transitions INIT_FAILURE → STOPPING, invokes `shutdown()` once for partial-resource release, and moves the container to STOPPED. ServerStoppingEvent must not invoke it again. ServerStoppedEvent later owns CLEANUP → TERMINATED under DEC-004.
- **ServerStoppingEvent shuts down only successfully initialized containers** where `initializationSucceeded == true` and `shutdownExecuted == false`, in reverse successful initialization order.
- **DEPENDENCY_FAILURE is a terminal exit, not on the CLEANUP/TERM path.** It is discarded at server stop without passing through CLEANUP or reaching TERMINATED.
- **The CLEANUP → TERMINATED path applies only to successfully initialized modules and INIT_FAILURE modules that completed their exactly-once shutdown. ServerStoppedEvent owns this path under DEC-004.**
- **A container that has reached TERMINATED or DEPENDENCY_FAILURE is never reused.** On the next server start, new containers and new IModule instances are created from the preserved ModuleDefinitions.

**Cleanup ownership:** DEC-004 assigns `STOPPED → CLEANUP → TERMINATED` to ServerStoppedEvent. ServerStoppingEvent owns shutdown only and must not execute final cleanup.

### 3.2 Server Restart Flow

The state machine describes a single server run. Server restart involves full replacement of containers and instances, not state transition:

```
Every Server Start (initial start and restart use the identical algorithm):
  ModuleRegistry (preserved immutable ModuleDefinitions)
    → DataManager.init()
    → DependencyResolver.resolve(ModuleDefinitions) → immutable DependencyResolutionResult
    → CoreManager creates empty RuntimeAvailabilityResult

    Phase 1 — Runtime creation, in resolved initialization order:
      For each ModuleDefinition:
        If a required dependency is unavailable:
          Create status-only RuntimeModuleContainer with Optional.empty()
          Record REGISTERED → DEPENDENCY_FAILURE
          Skip factory, ID validation, init, and shutdown
        Else:
          Call factory.createInstance()
            → failure: record direct failure; no container
          Validate getName() == id
            → mismatch: record direct failure; no container
          Create RuntimeModuleContainer with Optional.of(instance), state = REGISTERED

    Phase 2 — Initialization, in resolved initialization order:
      For each existing container:
        Recheck required dependency availability
          → unavailable: REGISTERED → DEPENDENCY_FAILURE; skip init and shutdown
        Otherwise:
          initializationStarted = true; state = INITIALIZING
          Call init()
            → success: initializationSucceeded = true; state = ACTIVE
            → failure: state = INIT_FAILURE; record/propagate failure;
              CoreManager executes exactly-once shutdown immediately;
              state = STOPPED; ServerStoppedEvent performs later cleanup under DEC-004

    ServerStoppingEvent, reverse successful initialization order:
      For each container where initializationSucceeded && !shutdownExecuted:
        atomically set shutdownExecuted = true
        ACTIVE → STOPPING; call shutdown() once; state = STOPPED
      DEPENDENCY_FAILURE and already-shut-down INIT_FAILURE containers are skipped

    ServerStoppedEvent post-shutdown cleanup:
      STOPPED → CLEANUP → TERMINATED is executed exactly once
      for eligible containers under DEC-004.

Post-shutdown boundary:
  DEPENDENCY_FAILURE containers are discarded without lifecycle calls
  CoreManager closes and discards RuntimeAvailabilityResult at ServerStoppedEvent
  independently of module-container cleanup and DEC-004
  STOPPED containers and their instances remain available until ServerStoppedEvent
  At ServerStoppedEvent, eligible containers complete CLEANUP → TERMINATED and
  are discarded
  ModuleRegistry and ModuleDefinitions remain preserved

Next Server Start:
  Repeat this complete algorithm with new results, containers, and instances
```

**Rules:**
- Containers in DEPENDENCY_FAILURE are discarded at server stop — no CLEANUP or TERMINATED reached
- RuntimeAvailabilityResult is closed and discarded by CoreManager at `ServerStoppedEvent`; it is not persistent, does not cross server lifecycles, and is independent of module-container cleanup ownership
- DependencyResolutionResult is also discarded — re-resolved each server start (structural graph may change as module availability changes)
- TERMINATED containers are **never** reused. If a reference to a TERMINATED container is held by external code, it represents a stale handle.
- DEPENDENCY_FAILURE containers are also never reused. Next server start creates new containers.
- IModule instances from a previous server run are **never** reused. The factory creates a fresh instance each server start.
- Next server start creates **new** containers and **new** instances, unrelated to previous lifecycle objects.
- The ModuleRegistry is the bridge between server lifecycles: it preserves ModuleDefinitions so new containers and instances can be created.

### 3.3 State Definitions

| State | Meaning |
|-------|---------|
| **REGISTERED** | Normal container has a fresh IModule instance and awaits initialization. A status-only dependency-failure container may occupy REGISTERED only inside CoreManager's atomic, unpublished transition to DEPENDENCY_FAILURE; it is never query-visible in REGISTERED without an instance. |
| **INITIALIZING** | Module's `init()` method executing |
| **ACTIVE** | Module fully operational |
| **INIT_FAILURE** | Module's `init()` threw. CoreManager immediately claims and performs the exactly-once shutdown, then reaches STOPPED. CLEANUP → TERMINATED occurs later at ServerStoppedEvent under DEC-004. |
| **DEPENDENCY_FAILURE** | Required dependency failed at runtime. Container exists for status tracking with `Optional.empty()` or an inaccessible uninitialized instance. No `init()` or `shutdown()` is executed. Discarded at server stop without CLEANUP or TERMINATED. |
| **STOPPING** | Module's `shutdown()` method executing. Only modules that entered INITIALIZING reach this state. |
| **STOPPED** | Module shutdown completed, awaiting cleanup |
| **CLEANUP** | Post-shutdown static-state clearing at ServerStoppedEvent after exactly-once shutdown. Applies only to modules that reached ACTIVE or INIT_FAILURE. |
| **TERMINATED** | Normal lifecycle complete. Container and IModule instance are discarded and not reused. Reached only through CLEANUP. |

### 3.4 State Ownership

| Component | Responsibility |
|-----------|----------------|
| **RuntimeModuleContainer** | Stores the current state value for this server run. Provides read-only queries. Exposes controlled state mutation methods called only by CoreManager. |
| **CoreManager** | Sole driver of state transitions within a server run. Decides when to transition. Initiates all transition calls. Creates containers at server start and discards eligible containers at ServerStoppedEvent under DEC-004. |
| **LifecycleStateMachine** (if retained) | Pure validation: evaluates whether a proposed transition is valid per transition tables (3.5, 3.6). Does not hold state. Does not initiate transitions. |

**CoreManager is the only component that may mutate RuntimeModuleContainer state.**

### Exactly-Once Shutdown Contract

CoreManager is the only caller of `IModule.shutdown()`. The following rules apply to each runtime instance:

1. Immediately before `init()`, CoreManager sets `initializationStarted = true` and transitions REGISTERED → INITIALIZING.
2. If `init()` succeeds, CoreManager sets `initializationSucceeded = true` and transitions to ACTIVE.
3. If `init()` throws, CoreManager transitions to INIT_FAILURE, atomically claims the shutdown guard, transitions to STOPPING, immediately performs shutdown exactly once, and reaches STOPPED. This does not select the later CLEANUP event.
4. Before invoking `shutdown()`, CoreManager atomically changes `shutdownExecuted` from false to true. The flag remains true even if `shutdown()` throws; shutdown is never retried for that instance.
5. ServerStoppingEvent selects only containers where `initializationSucceeded == true && shutdownExecuted == false`, in reverse successful initialization order.
6. DEPENDENCY_FAILURE containers always have `initializationStarted == false`, `initializationSucceeded == false`, and `shutdownExecuted == false`; CoreManager never invokes lifecycle methods on them.
7. Already-shut-down INIT_FAILURE containers are excluded from ServerStoppingEvent by `shutdownExecuted == true`; ServerStoppedEvent performs their later cleanup under DEC-004.

### 3.5 Valid Transitions

| From | To | Condition |
|------|----|-----------|
| REGISTERED | INITIALIZING | Normal initialization begins |
| REGISTERED | DEPENDENCY_FAILURE | Required dependency failed at runtime. Container created for tracking, but no `init()` or `shutdown()`. Terminal state — discarded at server stop. |
| INITIALIZING | ACTIVE | `init()` completed without throwing |
| INITIALIZING | INIT_FAILURE | `init()` threw an exception |
| ACTIVE | STOPPING | Shutdown initiated by CoreManager |
| INIT_FAILURE | STOPPING | Cleanup shutdown — module's `shutdown()` called to release partial resources |
| STOPPING | STOPPED | `shutdown()` completed |
| STOPPED | CLEANUP | Cleanup phase initiated |
| CLEANUP | TERMINATED | Cleanup completed |

### 3.6 Invalid Transitions (Must Be Prevented)

| Transition | Why It Is Invalid |
|------------|------------------|
| REGISTERED → ACTIVE | Skips initialization |
| ACTIVE → REGISTERED | Module must shut down before next lifecycle |
| INIT_FAILURE → ACTIVE | Cannot recover from failure without reinitialization |
| INIT_FAILURE → DEPENDENCY_FAILURE | INIT_FAILURE modules execute `shutdown()`, not direct transition to DEPENDENCY_FAILURE |
| DEPENDENCY_FAILURE → CLEANUP | DEPENDENCY_FAILURE containers are discarded directly — no cleanup phase |
| DEPENDENCY_FAILURE → any state | Terminal state — container is discarded. Next lifecycle creates a new container. |
| TERMINATED → any state | Terminal state — container is discarded. Next lifecycle creates a new container. |
| STOPPED → ACTIVE | Module must go through full lifecycle in a new server run |
| STOPPED → REGISTERED | Module must be created as a new container in next server run |

### 3.7 Forge Event Mapping

**Mod Lifecycle (runs once per mod load — registration and definition-level validation only):**

| Forge Event | Lifecycle Action | Details |
|-------------|-----------------|---------|
| `FMLCommonSetupEvent` | **Registration Window** | Modules call `register(id, factory, metadata, requiredDependencies, optionalDependencies, priority)`. Registry validates ID uniqueness/format, basic required ModuleMetadata validity, dependency ID format, and priority range. No IModule instance is created during registration — the static contract is provided explicitly as registration parameters. On success, stores immutable ModuleDefinition with the factory for later instance creation. |
| `FMLCommonSetupEvent` (post-queue) | **Registration Closed** | Registration window closes. ModuleDefinition set is finalized for the lifetime of the mod container. **No runtime state is created.** **No Dependency Resolution is performed.** |

**Server Lifecycle (runs per server start — ModuleDefinitions preserved, new containers and instances created each time):**

| Forge Event | Lifecycle Action | Details |
|-------------|-----------------|---------|
| `ServerAboutToStartEvent` | **Pre-init (no module creation)** | CoreManager performs pre-server validation (configuration, environment). **No module instances created, no factory calls.** DataManager.init() has not yet run. |
| `ServerStartingEvent` | **Persistence → Resolution → Runtime Creation → Initialization** | `DataManager.init()` prepares persistence. `DependencyResolver.resolve()` produces immutable structural order. CoreManager creates RuntimeAvailabilityResult and processes definitions in that order. If a required dependency is unavailable, it creates a status-only DEPENDENCY_FAILURE container and skips factory, validation, init, and shutdown. Otherwise it calls factory, validates only Module ID, and creates a REGISTERED container with `Optional.of(instance)`. During initialization it rechecks availability, skips DEPENDENCY_FAILURE, and applies the exactly-once init/shutdown flags. |
| `ServerStartedEvent` | **ACTIVE confirmation** | Successfully initialized modules are ACTIVE. Failed or unavailable modules remain queryable through results/container state. |
| `ServerStoppingEvent` | **Exactly-once shutdown** | `DataManager.saveAll()` persists data. CoreManager iterates reverse successful initialization order and calls `shutdown()` only where `initializationSucceeded == true && shutdownExecuted == false`. It atomically sets the guard before invocation and advances those containers only through STOPPED. DEPENDENCY_FAILURE and already-shut-down INIT_FAILURE containers are skipped. This event does not own CLEANUP → TERMINATED. |
| `ServerStoppedEvent` | **Server Runtime Scope close / post-shutdown boundary** | CoreManager closes and discards RuntimeAvailabilityResult, directly discards DEPENDENCY_FAILURE containers with no instance access or lifecycle transition, executes CLEANUP → TERMINATED for eligible STOPPED containers, and disposes their runtime instances under DEC-004. Module cleanup must not require an active Server, World, persistence context, or RuntimeAvailabilityResult. ModuleRegistry and immutable ModuleDefinitions remain preserved for the next lifecycle. |

### 3.8 Error Handling

| Error Scenario | Behavior |
|----------------|----------|
| **Registration duplicate ID** | Log error, reject second registration. No ModuleDefinition created. First registration unaffected. |
| **Factory.createInstance() fails (Server Runtime Phase)** | Log error: "Factory failed to create instance for module [id] during server start". **No container created for the direct failure.** CoreManager records the failure in RuntimeAvailabilityResult and propagates to required dependents per resolved initialization order. **Each required dependent gets a RuntimeModuleContainer in DEPENDENCY_FAILURE state** (for tracking/query). No `init()` or `shutdown()` for DEPENDENCY_FAILURE containers. Next server start retries factory creation. |
| **Runtime Module ID mismatch** | Factory created an instance whose `getName()` does not match `ModuleDefinition.id`. This is a **Runtime Factory Failure**. Log error: "Module [id] factory produced instance with mismatched name: [actual]". **No container created for the direct failure.** CoreManager records the failure in RuntimeAvailabilityResult and propagates to required dependents per resolved initialization order. **Each required dependent gets a RuntimeModuleContainer in DEPENDENCY_FAILURE state.** No `init()` or `shutdown()`. The factory implementation is faulty and must be fixed by the module developer. |
| **Missing required dependency** | Structural failure. ModuleDefinition is recorded as unresolvable in DependencyResolutionResult. No container created. Log error listing missing dependency. |
| **Circular dependency (required-only)** | Structural failure. All modules in required-only cycle are recorded as unresolvable in DependencyResolutionResult. No containers created. Log error with cycle path. Requires Human decision to break. |
| **Circular dependency (with optional edges)** | Optional edges removed deterministically (see 2.3 Phase 2). Each removed edge logged. Non-optional cycle remains hard failure. |
| **Init throws exception** | Set state to INIT_FAILURE and record/propagate the runtime failure. CoreManager atomically sets `shutdownExecuted = true`, transitions INIT_FAILURE → STOPPING, calls `shutdown()` once immediately for partial-resource release, and reaches STOPPED. The container is excluded from ServerStoppingEvent. ServerStoppedEvent later executes CLEANUP → TERMINATED under DEC-004. Required dependents enter DEPENDENCY_FAILURE and execute no lifecycle methods. |
| **Shutdown throws exception** | Log error; retain `shutdownExecuted = true`; continue cleanup and remaining modules. Never retry shutdown for that runtime instance. |
| **Runtime exception (post-ACTIVE)** | Module responsible for its own error handling. Framework does not terminate modules at runtime — Human must inspect logs. |

**System-level behavior after failures:**
- The Core Framework always attempts to reach a stable ACTIVE state with remaining healthy modules
- Structural failures (missing dependency, cycle) → no container; recorded in DependencyResolutionResult
- Runtime failures (factory, ID validation, init) → direct failure may or may not have a container (INIT_FAILURE has one, factory/ID do not); all failures recorded in RuntimeAvailabilityResult
- **Required dependents of runtime failures always get a RuntimeModuleContainer in DEPENDENCY_FAILURE state** — no `init()`, no `shutdown()`, discarded at server stop
- Failed and unavailable modules are queryable via CoreManager API (DependencyResolutionResult + RuntimeAvailabilityResult + container state queries)
- The system does NOT halt on individual module failure
- Failed modules do not retry automatically — requires server restart or Human intervention

---

## 4. DEC-001: Event Foundation Boundary

### Status

> **Decision: Forge Lifecycle Integration Only**
> **Status: Accepted — Human Approved for FR-CORE-001-A**
> This decision does not authorize an internal event bus, Java implementation, or Human Design Freeze.

### Decision

The Core Framework provides Forge lifecycle integration points only. It does NOT implement an internal event bus or cross-module event infrastructure.

### Alternatives Considered

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A: Forge Lifecycle Only** (accepted) | Modules communicate via direct API calls through CoreManager's query API. Forge's existing `MinecraftForge.EVENT_BUS` is used only for Forge/Minecraft lifecycle events. | Simpler, no new infrastructure, less code to maintain, leverages Forge's existing event system | Tighter coupling between modules, no dedicated cross-module notification channel |
| **B: Internal Event Bus** | Core provides event dispatch for internal module events | Looser coupling, easier to add cross-cutting concerns (audit, logging) | New infrastructure design required, event hierarchy decisions, ordering semantics, listener lifecycle management — scope expansion beyond Phase 1 |

### Reasoning

- Architecture v2.7 Section 2.2 states modules "使用明确接口通信" (communicate through clear interfaces), implying direct API calls rather than event publish-subscribe.
- Forge's existing `MinecraftForge.EVENT_BUS` is available for Forge/Minecraft lifecycle events only. It must **not** be used as a module business communication channel.
- An internal event bus would require: event type hierarchy, listener registration API, dispatch ordering, cancellation semantics, and lifecycle binding — this is a separate architecture decision better made when a concrete need arises.
- For Phase 1 (Core Framework), there are no cross-module notification scenarios that require an event bus. Module lifecycle events (init, shutdown) are handled by CoreManager directly.

### EVENT_BUS Boundary

| Permitted Use | Forbidden Use |
|---------------|---------------|
| Forge lifecycle events (server start/stop, world load/save) | Module internal business communication |
| Minecraft system events (player join/quit, block events) | Cross-module data change notifications |
| Mod-specific Forge events (Register events) | Custom event dispatch for module-to-module calls |

> **Rationale:** Using `MinecraftForge.EVENT_BUS` for module business communication creates implicit, untracked dependencies between modules, bypasses the Core Framework's module dependency system, and makes module availability non-deterministic. Module-to-module communication must go through explicit API calls via CoreManager's query API.

### Consequences

- **Positive:** Faster implementation, reduced complexity, no new infrastructure to maintain
- **Positive:** Direct API calls provide compile-time type safety and explicit dependency tracking
- **Positive:** CoreManager's query API provides sufficient module discovery
- **Positive:** Clear boundary between system events (Forge EVENT_BUS) and module communication (API calls)
- **Negative:** Adding cross-cutting concerns (e.g., automatic audit logging for all state changes) later may require refactoring to an event-driven model
- **Mitigation:** If Option B becomes necessary in Phase 3+, the Core Framework's module registry provides the integration point to hook into module operations

---

## 5. DEC-003: Network Foundation Boundary

### Status

> **Decision: Separate Network Foundation Task**
> **Status: Accepted — Human Approved for FR-CORE-001-A**
> This decision does not authorize Network Foundation implementation or Human Design Freeze.

### Decision

Network Foundation (SimpleChannel setup, packet skeleton, serialization utilities) is **NOT part of FR-CORE-001-A**. It is a separate implementation task that builds on the completed Core Framework. It belongs architecturally to Phase 0 per Architecture v2.7 Section 9.2 Step 4, but is not within FR-CORE-001-A's scope.

### Alternatives Considered

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A: Separate Task** (accepted) | Network Foundation is a follow-up implementation task after FR-CORE-001 completes | Clear separation of concerns, Core Framework focuses on module lifecycle | Requires coordination handoff between tasks |
| **B: Include in FR-CORE-001-A** | Core Framework design includes network channel registration and packet skeleton | Single integrated design, fewer handoffs | Expands scope beyond module lifecycle, mixes concerns, delays Core Framework completion |

### Reasoning

- FR-CORE-001-A focuses on: module system, lifecycle management, dependency resolution, CoreManager evolution. These are self-contained concerns that do not require networking.
- Network Foundation (Phase 0 Step 4 per v2.7) requires the module system to be operational first — Core Framework is a prerequisite, not a parallel concern.
- Architecture v2.7 Section 6 already defines the network architecture in detail (SimpleChannel pattern, two-phase packet handling, protocol version management). The design exists — only implementation is needed.
- Separating concerns allows the Core Framework to stabilize before adding network complexity.

### Network Lifecycle Clarification

**SimpleChannel registration and network lifecycle belong to the Mod initialization phase (FMLCommonSetupEvent or mod constructor), NOT to the Server lifecycle.**

The FR-CORE-001-A design does not define network lifecycle integration points. Previous versions of this document incorrectly referenced `ServerAboutToStartEvent` for channel registration and `ServerStoppingEvent` for channel closure. These were architectural errors:

- Forge's `SimpleChannel` is registered once during mod construction, not per server start.
- The Network Foundation task (separate from FR-CORE-001-A) will handle its own lifecycle within the Forge networking model per Architecture v2.7 Section 6.
- The Core Framework defines only the module identity boundary for a future `core:network` module. The network module's lifecycle is managed by the Network Foundation task, not FR-CORE-001-A.

---

## 6. Persistence Compatibility

### Preserved

The following persistence components are unchanged:

| Component | Status | Detail |
|-----------|--------|--------|
| `ModSavedData.DATA_NAME` | **Preserved** | Remains `"fontainerepublic"` |
| `ModSavedData.save()` | **Preserved** | NBT structure unchanged. Existing `CompoundTag` with `"modules"` key remains. |
| `ModSavedData.load()` | **Preserved** | Same NBT format. Backward compatible with existing saves. |
| `DataManager.getModuleData(name)` | **Preserved** | Signature and semantics unchanged |
| `DataManager.putModuleData(name, tag)` | **Preserved** | Signature and semantics unchanged |
| Module data isolation model | **Preserved** | Each module accesses only its own key. Cross-module access via API, not NBT sharing. |

### Migration Statement

**No persistence migration is required.**

- No NBT schema changes
- No DATA_NAME changes
- No data ownership transfer
- No data format conversion
- No `schemaVersion` changes
- No server save incompatibility

The DataManager continues to function as the persistence gateway. Business modules in Phase 1+ will use the existing API (`getModuleData`/`putModuleData`) without modification to the underlying storage.

---

## 7. IModule Migration Contract

### Backward Compatibility

The existing `IModule` interface must remain functional for all currently registered modules:

```java
public interface IModule {
    String getName();
    void init();
    void shutdown();

    default int getPriority() { return 50; }

    // New default methods (added in FR-CORE-001):
    default List<String> getRequiredDependencies() { return List.of(); }
    default List<String> getOptionalDependencies() { return List.of(); }
    default String getDisplayName() { return getName(); }
    default String getDescription() { return ""; }
    default String getVersion() { return "1.0.0"; }
}
```

**Key rules:**
- All new methods have default implementations, so existing `IModule` implementors continue to compile and run without changes
- Existing methods (`getName`, `init`, `shutdown`, `getPriority`) retain their exact signatures
- Modules that do not provide explicit dependency lists at registration will have empty dependency lists (default), preserving their current behavior
- **Approved FR-CORE-001 migration behavior:** Under MIGRATION-01, the IModule instance's dependency and priority methods cease to be authoritative for FR-CORE-001. Registration parameters become the static contract, and only `getName()` is validated at runtime. Architecture v2.7 remains unchanged; Java implementation requires a separate approved task.

### IModuleFactory

Under the approved FR-CORE-001 migration direction, modules are registered via a factory instead of a direct instance:

```java
public interface IModuleFactory {
    IModule createInstance();
}
```

Existing modules can adapt by wrapping their instance creation and providing explicit static contract:

```java
registry.register("core",
    CoreModule::new,
    metadata,
    List.of(),           // requiredDependencies
    List.of(),           // optionalDependencies
    50                   // priority
);
```

The factory is called once per server start. **Factory-only registration is enforced.** There is no direct-instance registration path. A factory that returns the same instance across calls violates the per-server freshness contract and produces undefined behavior at server boundaries.

Under MIGRATION-01, the static contract (ID, dependencies, priority, metadata, and factory) is provided explicitly at registration and ModuleDefinition is the sole authoritative source for FR-CORE-001.

### Unified Dependency Resolution

**All modules, including legacy modules without dependency declarations, participate in the same DependencyResolver.** There is no separate sorting path:

| Module Type | Dependency List | Ordered By |
|-------------|----------------|------------|
| Modules with dependency declarations | Explicit dependency lists provided at registration | Topological sort → priority → registration order |
| Legacy modules (no dependency override) | Empty dependency lists (default at registration) | Topological sort (no edges → all same level) → priority → registration order |

**There is no legacy TreeMap-based priority sorting.** Priority serves as a secondary tiebreaker within the DependencyResolver (see 2.3 Stable Ordering Rules). Legacy modules transition to the new system by providing empty dependency lists at registration (either explicitly or by default).

### Coexistence Period

During migration, the Core Framework supports two module types simultaneously:

| Mode | Description | Duration |
|------|-------------|----------|
| **Default Dependencies** | Module registered with empty dependency lists. Ordered by priority then registration order within the single DependencyResolver. | Throughout Phase 1 |
| **Declared Dependencies** | Module registered with explicit dependency lists. Full topological ordering with priority tiebreaker. | Available from FR-CORE-001 completion |

These are not separate sorting systems. Both modes flow through the same DependencyResolver. The only difference is whether the dependency lists are empty (default) or populated (explicit).

### Migration Strategy

**Migration authorization boundary:** MIGRATION-01 resolves the architecture direction for FR-CORE-001. The migration must not begin until a separate Human-authorized Java implementation task is issued.

1. **Phase 1 (Core Framework implementation):** All existing core modules (core:lifecycle, core:config, core:data) are migrated to Declared Dependencies and IModuleFactory first, since they are owned by the Core Framework team.
2. **Phase 2 (adoption period):** Business module developers add dependency declarations and factory registration when they implement their modules. No forced migration.
3. **Phase 3+ (default deprecation):** After all modules are migrated, the default empty dependency registration may be deprecated, requiring explicit dependency declarations.

**The IModule interface is not deprecated.** Only the default (empty) dependency registration fallback may be deprecated in the future. The interface itself remains the module contract, but the static contract data is always provided at registration, not read from the interface.

---

## 8. Implementation Boundary

### After FR-CORE-001 Implementation, the Following Is Possible

- Register modules via factory with unique IDs, duplicate/mismatch detection, and explicit dependency declaration
- Define required and optional dependencies per module
- Resolve initialization order at server start via deterministic topological sort with priority and registration order tiebreakers
- Detect circular dependencies using two-phase algorithm (required-only hard failure, optional edge removal)
- Handle missing required dependencies gracefully (dependent module disabled, system continues)
- Handle optional-only cycles gracefully (optional edges broken deterministically, warning logged, system continues)
- Drive module lifecycle through Forge events (registration → resolution → container/instance creation → init → active → shutdown → cleanup)
- Create fresh IModule instances per server start via IModuleFactory
- Query module state and instance at runtime (getModule, getModuleState, getModulesByState)
- Handle init failures with proper cleanup and propagation to dependents
- Support clean server restart: ModuleDefinitions preserved across stops, new containers and instances created on start
- Clear eligible module runtime state at ServerStoppedEvent under DEC-004, while preserving module definitions and factories across server lifecycles
- Migrate existing modules to dependency-based ordering incrementally through the single DependencyResolver

### After FR-CORE-001 Implementation, the Following Is NOT Possible

- Network packet handling (requires separate Network Foundation task)
- Command registration (requires separate Command Framework task)
- Permission checking (permission framework is Phase 0 Step 6 per v2.7)
- Business module operations (citizen, economy, land, government — Phase 1+)
- GUI display (Phase 8)
- Runtime module hotswap (not a goal per Architecture v2.7)
- Internal event bus (excluded from FR-CORE-001 under accepted DEC-001)
- **Runtime module enable/disable** (removed from FR-CORE-001 scope per N-04 — requires separate DEC and task)
- Toggle or DISABLED state (not part of Core Framework lifecycle)

---

## 9. Architecture Deviations

This section preserves revision history. **Historical records are non-normative and may describe superseded designs.** Terms and flows recorded below must not be used as the current implementation contract; Sections 1–8 and unresolved Human decision gates define the current candidate design.

### Revision-01 Corrections (F-01 through F-12)

Applied in FR-CORE-001-A-FIX-01. See archived content for full detail.

### Revision-02 Corrections (N-02 through N-06)

Applied in FR-CORE-001-A-FIX-02 following FR-CORE-001-A-FIX-01-AUDIT-01. See archived content for full detail.

### Revision-03 Corrections

Applied in FR-CORE-001-A-FIX-03 following FR-CORE-001-A-FIX-02-AUDIT-01.

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-07** | DependencyResolver execution timing was ambiguous: the design referenced resolution in both FMLCommonSetupEvent (post-queue) and Server Lifecycle. Module registration and runtime resolution were mixed, causing lifecycle conflict. | **Unified execution flow:** Mod Lifecycle (FMLCommonSetupEvent) handles registration, validation, and dependency capture only — no resolution. ServerStartingEvent is the sole execution point for DependencyResolver, operating on freshly created RuntimeModuleContainers. Resolution runs after DataManager.init() and before module initialization. |
| **N-08** | ModuleDefinition held an IModule instance reference, meaning server restarts would reuse the same module object. This prevents clean state separation and risks stale state across server runs. | ModuleDefinition stores an **IModuleFactory** instead of an IModule instance. A factory (`createInstance()`) is called once per server start to produce a fresh IModule. RuntimeModuleContainer holds the per-server instance. Previous instances are discarded at server stop and never reused. |
| **N-09** | State machine included UNREGISTERED as a runtime-visible state. In the ModuleDefinition/RuntimeModuleContainer model, containers are created from existing definitions — UNREGISTERED has no observable meaning at runtime and confused the state model. | Removed UNREGISTERED from the state machine. The starting state is REGISTERED (container created, awaiting resolution). UNREGISTERED has no representation at runtime — it existed only during the transient container-creation step within ServerAboutToStartEvent. |

### Revision-04 Corrections

Applied in FR-CORE-001-A-FIX-04 following FR-CORE-001-A-FIX-03-AUDIT-01. See archived content for full detail.

### Revision-05 Corrections

Applied in FR-CORE-001-A-FIX-05 following FR-CORE-001-A-FIX-04-AUDIT-01.

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-13** | Registration process called `factory.createInstance()` to read static contract from a temporary IModule instance. If the factory constructor had side effects (accessing server, world, DataManager), this would pollute the runtime environment. Factory had no defined side-effect contract. | **Eliminated registration-time instance creation entirely.** Static contract (id, dependencies, priority) is now provided **explicitly at registration** — not read from any instance. Factory is never called during registration. Added Creation-only Contract for `createInstance()` with explicit Permitted/Forbidden table: object construction and field initialization only; no server/world/DataManager access, event registration, thread creation, or static state modification. All runtime initialization deferred to `IModule.init()`. |
| **N-14** | Runtime instance identity validation was optional and left as a warning. A factory could produce an instance with `getName()` different from the registered ID without causing a hard failure. | **Added mandatory runtime Module ID validation:** at server start, after `factory.createInstance()`, the framework validates `instance.getName() == ModuleDefinition.id`. Mismatch is a hard failure (Runtime Factory Failure) — no container created, required dependents enter RESOLVE_FAILURE. ModuleDefinition is the sole authority for dependencies and priority; instance declarations cannot override. |

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-10** | Section 7 allowed a backward-compatibility path where a factory could return the same instance across calls, violating the per-server-instance freshness requirement. Direct-instance registration bypassed the factory contract entirely. | **Removed all direct-instance registration paths.** Factory-only registration enforced. Added explicit freshness requirement: each `createInstance()` call must return a new instance. Factory that returns a cached/shared instance violates the contract. |
| **N-11** | Static contract (id, dependencies, priority) was implicitly tied to runtime IModule instance methods. Two instances from the same factory could declare different dependencies at registration vs. server start. | Static contract moved into ModuleDefinition as the **sole authoritative source**. ModuleDefinition stores id, dependencies, and priority as immutable fields captured at registration. Runtime IModule instance is not a source of static contract — it only provides runtime behavior. Optional post-creation validation may verify instance matches definition. |
| **N-12** | Factory failure had a single error entry that conflated registration-time failure with server-runtime failure. Both phases produce different consequences and must be handled separately. | **Split factory failure into two phases:** Registration Phase failure → no ModuleDefinition created, registration rejected. Server Runtime Phase failure → module unavailable for current server lifecycle, required dependents fail, next server start retries. |

### Revision-06 Corrections

Applied in FR-CORE-001-A-FIX-06 and FR-CORE-001-A-FIX-07 following Codex Independent Review audit.

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-13/N-15** | Registration-time factory invocation and validation instance creation were still referenced. Factory lifecycle had no single consistent call site. | **Factory has one call site:** Server Runtime Creation (ServerStartingEvent). `createInstance()` is never called during registration, FMLCommonSetupEvent, or ServerAboutToStartEvent. Creation-only Contract defines Permitted/Forbidden operations. |
| **N-14/N-22** | Runtime validation scope was undefined — all IModule methods were implied as validation targets. | **Runtime validation is limited to `instance.getName() == ModuleDefinition.id`.** Dependencies, priority, and metadata are not runtime authority sources — they are static contract provided at registration. |
| **N-16** | Static contract was described as originating from IModule interface default methods in migration sections. | **ModuleDefinition is the sole static contract source.** All static properties (id, dependencies, priority, metadata) are provided explicitly via `register()` parameters. |
| **N-17/N-20** | RESOLVE_FAILURE was a container state, but DependencyResolver runs on ModuleDefinitions before containers exist. Containers cannot hold resolution failure state. | **Resolution failures are Definition-level, not container-level.** DependencyResolver produces a `DependencyResolutionResult` listing passing and failing ModuleDefinitions. No container is created for failed modules. RuntimeModuleContainer state machine no longer includes RESOLVE_FAILURE or RESOLVING. |
| **N-18** | ModuleMetadata was ambiguously defined. | **ModuleMetadata is an immutable static description** stored in ModuleDefinition at registration. Not runtime state, not authority source. |
| **N-21** | Container creation order was inconsistent across sections. | **Unified order:** Dependency Resolution → factory.createInstance() → Runtime ID Validation → create RuntimeModuleContainer → attach instance → REGISTERED. No container exists if factory or validation fails. |

### Revision-07 Corrections

Applied in FR-CORE-001-A-FIX-08 following Codex Independent Review audit.

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-23** | DependencyResolutionResult mixed structural dependency analysis and runtime failure tracking, causing ownership ambiguity. | **Split into two distinct concepts:** `DependencyResolutionResult` (structural only — graph validity, required deps, ordering; immutable) and `RuntimeAvailabilityResult` (runtime failures — factory, ID validation, init; owned by CoreManager). Added Section 2.4 defining creation timing, modification permissions, and failure propagation rules (direct + transitive). |
| **N-24** | Modules with runtime dependency failure transitioned REGISTERED → STOPPING → STOPPED without executing `init()` or `shutdown()`, making STOPPING/STOPPED semantics inaccurate. | **Added `DEPENDENCY_FAILURE` terminal state.** Modules whose required dependency fails at runtime transition REGISTERED → DEPENDENCY_FAILURE directly. No `init()` or `shutdown()` is called. Container is tracked as unavailable and discarded at server stop. STOPPING/STOPPED now represent lifecycle states that only modules entering INITIALIZING can reach. |

### Revision-08 Corrections

Applied in FR-CORE-001-A-FIX-09 following Codex Independent Review audit.

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-23** | RuntimeAvailabilityResult failure propagation was underspecified — no deterministic order, allowing HashMap iteration order to determine which failed dependency is reported. | **Defined deterministic propagation algorithm:** CoreManager propagates in `DependencyResolutionResult` resolved initialization order. "First failure wins" rule when multiple deps fail. Propagation source, reason, and root cause are recorded. HashMap iteration and implementer-choice of failure reason are explicitly forbidden. |
| **N-24** | Shutdown boundary was unclear — DEPENDENCY_FAILURE modules had no explicit exclusion from ServerStoppingEvent. | **Defined shutdown boundary:** Only modules that reached INITIALIZING or ACTIVE execute `shutdown()`. DEPENDENCY_FAILURE modules bypass ServerStoppingEvent entirely. Shutdown order is reverse successful initialization order. |
| **N-25** | Failed dependent container policy was unspecified — did DEPENDENCY_FAILURE modules get RuntimeModuleContainers or not? | **Adopted RuntimeModuleContainer policy:** When a required dependency fails at runtime, the dependent still receives a RuntimeModuleContainer (state = REGISTERED → DEPENDENCY_FAILURE). The container exists for status querying, debugging, and lifecycle tracking — but no `init()` or `shutdown()` is executed. |
| **N-26** | DEPENDENCY_FAILURE was implicitly described as feeding into CLEANUP → TERMINATED, but failed modules never execute cleanup logic. | **DEPENDENCY_FAILURE is a separate terminal exit.** DEPENDENCY_FAILURE containers are discarded at server stop without passing through CLEANUP or TERMINATED. The normal CLEANUP → TERM path applies only to modules that reached ACTIVE or INIT_FAILURE. Diagram updated to show DEPENDENCY_FAILURE as an off-ramp. |
| **N-27** | ServerStoppedEvent description did not distinguish DEPENDENCY_FAILURE container handling from normal container handling. | **Split ServerStoppedEvent behavior:** Normal containers pass through CLEANUP → TERMINATED. DEPENDENCY_FAILURE containers are discarded directly — no CLEANUP, no TERMINATED. Forge Event Mapping updated with explicit rules for both paths. |

### Revision-09 Corrections

Applied in FR-CORE-001-A-FIX-10 following FR-CORE-001-A-FIX-09-AUDIT-01. This entry records a document revision only; it does not claim approval, activation, or implementation authorization.

| Finding | Issue | Correction |
|---------|-------|------------|
| **N-24 / N-29** | INIT_FAILURE cleanup could call `shutdown()` immediately and again during ServerStoppingEvent. | Added CoreManager-owned exactly-once shutdown tracking: `initializationStarted`, `initializationSucceeded`, and `shutdownExecuted`. INIT_FAILURE claims and performs cleanup shutdown immediately once. ServerStoppingEvent processes only successfully initialized containers whose shutdown guard is false. |
| **N-25** | DEPENDENCY_FAILURE required a RuntimeModuleContainer but could skip factory creation, leaving the instance contract undefined. | Changed the container instance field to explicit `Optional<IModule>`. Defined state-dependent availability and access rules; implicit null is forbidden. DEPENDENCY_FAILURE prohibits instance access and all lifecycle invocations whether an internal uninitialized instance is absent or present. |
| **N-28** | High-level startup and restart summaries still implied every definition invokes factory. | Unified all startup, restart, creation, and Forge event descriptions around the two-phase branch: unavailable required dependency → status-only DEPENDENCY_FAILURE container with no factory/init; otherwise factory → ID validation → normal container → init. |

### Revision-10 Corrections

Applied in FR-CORE-001-A-FIX-11 following FR-CORE-001-A-FIX-10 Freeze Review. This entry records a draft document revision only; it does not claim approval, activation, freeze, or implementation authorization.

| Finding | Correction |
|---------|------------|
| **R09-01** | Removed dual Forge-event ownership of CLEANUP → TERMINATED. DEC-004 now exclusively gates cleanup-event selection; no pre-approval implementation assumption is made. |
| **R09-02** | Recorded the Architecture v2.7 IModule-method versus registration-metadata conflict as a Pending Human migration decision and removed the false “Preserved” claim. |
| **R09-03** | Added the complete ModuleAvailabilityRecord contract, controlled mutation rules, immutable publication, and read-only query API. |
| **R09-04** | Added the requirement that a freeze candidate reference one unique reviewed Git commit or blob. |
| **R09-05** | Classified revision history as non-normative historical material that may contain superseded designs. |

### Revision-11 Corrections

Applied in FR-CORE-001-A-FIX-12 following FR-CORE-001-A-FIX-11 Freeze Review. This entry records a draft document revision only; it does not claim approval, activation, freeze, or implementation authorization.

| Finding | Correction |
|---------|------------|
| **R10-01** | Defined RuntimeAvailabilityResult as transient CoreManager-owned Server Runtime Scope state. CoreManager discards it at ServerStoppedEvent independently of RuntimeModuleContainer disposal and DEC-004; DEC-004 remains limited to module CLEANUP → TERMINATED event selection. |
| **R10-02** | Separated priority ownership, storage, and use: ModuleDefinition owns the immutable value, ModuleRegistry stores the containing definition without interpreting priority, and DependencyResolver consumes it only if DEC-005 retains the tiebreaker. |

### Revision-12 Decision Synchronization

Applied in FR-CORE-001-A-DECISION-ACTIVATION-01 after Human approval of DEC-001, DEC-003, DEC-004, DEC-005, and MIGRATION-01. This revision synchronizes approved decisions and implementation preparation boundaries. It does not claim Human Design Freeze or Java implementation authorization.

| Decision | Synchronized Result |
|----------|---------------------|
| **DEC-001** | Forge lifecycle integration only; no internal event bus in FR-CORE-001. |
| **DEC-003** | Network Foundation remains a separate future task. |
| **DEC-004** | ServerStoppingEvent owns exactly-once shutdown; ServerStoppedEvent owns cleanup, termination, and eligible runtime-container disposal. |
| **DEC-005** | Dependency ordering is topology → priority → stable registration order. |
| **MIGRATION-01** | ModuleDefinition owns the FR-CORE-001 static contract; IModule owns runtime behavior. Architecture v2.7 remains unchanged. |

### Revision-13 Clarifications

Applied in FR-CORE-001-A-FIX-13 following the Revision-12 Freeze Review. This entry records documentation clarifications only; it does not change an accepted decision, claim Human Design Freeze, or authorize Java implementation.

| Finding | Clarification |
|---------|---------------|
| **R12-01** | Defined basic registration-time ModuleMetadata validation: metadata must be non-null and fields already designated as required by its contract must be non-null and non-blank. No metadata schema or runtime interpretation was added. |
| **R12-02** | Defined DEPENDENCY_FAILURE construction and publication as one CoreManager-owned atomic operation. Empty REGISTERED containers remain unpublished and cannot be observed through query interfaces. |
| **R12-04** | Clarified that direct runtime creation or initialization failures propagate to later required dependents through the existing deterministic Section 2.4 algorithm. |

### Alignment with Architecture v2.7

| v2.7 Requirement | FR-CORE-001-A (Revision-13) Alignment |
|------------------|--------------------------------------|
| Module dependencies via `getRequiredDependencies()` / `getOptionalDependencies()` (Section 3.3) | **Approved FR-CORE-001 migration direction.** MIGRATION-01 moves static authority to ModuleDefinition for FR-CORE-001 without modifying Architecture v2.7. |
| Topological sort for init order (Section 3.3) | Preserved with Kahn's algorithm, two-phase cycle detection, priority tiebreaker, registration order tiebreaker |
| Reverse order for shutdown (Section 3.3) | Preserved in lifecycle state machine |
| Priority as tiebreaker (Section 3.3) | **Accepted under DEC-005.** Priority applies only after dependency topology and before stable registration order. |
| Unique Module ID enforcement (Section 3.3) | Preserved with strict rejection on mismatch |
| Failure propagation (Section 3.3) | Preserved with explicit state machine transitions |
| Server authority principle (Section 2.1) | Preserved — per-server instance creation enforces clean state |
| Three-layer architecture (Section 3.1) | Preserved — Core Framework operates within server layer |
| IModule interface (Section 3.3) | **Migration authorized under MIGRATION-01 for FR-CORE-001.** IModule retains runtime behavior; ModuleDefinition becomes static authority. The frozen baseline document is not edited. |

**Potential future concern:** As the module system matures, explicit dependency injection through the module container may be preferred over static access patterns. This is not a deviation — it is a future refinement outside current scope.

---

## 10. Human Decisions and Implementation Gates

### Decision Status

| ID | Human Decision | Status |
|----|----------------|--------|
| **DEC-001** | Forge lifecycle integration only; internal Event Bus deferred to an independent architecture task. | **Accepted — Human Approved for FR-CORE-001-A** |
| **DEC-003** | Network Foundation remains a separate future task. | **Accepted — Human Approved for FR-CORE-001-A** |
| **DEC-002** | Runtime module enable/disable remains outside FR-CORE-001. | **Removed from scope — requires separate DEC and task** |
| **DEC-004** | ServerStoppingEvent owns exactly-once shutdown; ServerStoppedEvent owns cleanup, termination, and eligible runtime-container disposal. | **Accepted — Human Approved for FR-CORE-001-A** |
| **DEC-005** | Ordering is dependency topology → priority → stable registration order. | **Accepted — Human Approved for FR-CORE-001-A** |
| **MIGRATION-01** | ModuleDefinition owns the FR-CORE-001 static contract; IModule owns runtime behavior. | **Accepted — Human Approved for FR-CORE-001-A** |

### Implementation Blockers

The architecture decision blockers identified in Revision-11 are resolved for implementation preparation. Java implementation remains blocked until Human issues a separate implementation authorization.

| Decision | Implementation Dependency | Architecture Decision Blocker? | Resolved Contract |
|----------|---------------------------|--------------------------------|-------------------|
| DEC-002 | Runtime enable/disable | No | Outside FR-CORE-001. |
| DEC-004 | Shutdown and cleanup event ownership | No | Shutdown at ServerStoppingEvent; cleanup and eligible container disposal at ServerStoppedEvent. |
| DEC-005 | Dependency ordering | No | Topology → priority → stable registration order. |
| MIGRATION-01 | Static contract and registration API | No | ModuleDefinition static authority with factory-only runtime creation. |

**Decision impact summary:**
- ModuleRegistry stores immutable ModuleDefinitions containing the approved static contract.
- Runtime lifecycle uses fresh factory-created instances per server lifecycle.
- DependencyResolver applies topology, priority, then stable registration order.
- CoreManager performs exactly-once shutdown at ServerStoppingEvent.
- CoreManager performs eligible cleanup, termination, and disposal at ServerStoppedEvent.
- Internal Event Bus and Network Foundation remain outside FR-CORE-001.
- Java implementation must not begin without a separate Human-authorized task.

**Rule:** The implementer must follow the accepted decisions exactly. Any deviation affecting lifecycle, static-contract authority, dependency ordering, module boundary, persistence, networking, or permissions requires a Deviation Alert and Human direction.

### Decision Process

Each decision record follows this lifecycle:

```
Draft → Proposed → For Human Review → Human Decision → Confirmed or Rejected
```

DEC-001, DEC-003, DEC-004, DEC-005, and MIGRATION-01 have reached **Confirmed** for FR-CORE-001-A architecture synchronization and implementation preparation. The architecture document remains **Pending Human Design Freeze**, and Java implementation remains unauthorized until a separate Human instruction is issued.
