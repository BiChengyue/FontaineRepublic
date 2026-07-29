# FontaineRepublic Command Foundation Architecture v1.0

> Task: FR-CMD-001-A
>
> Project: FontaineRepublic
>
> Version: 0.1.0-alpha
>
> Minecraft: 1.20.1
>
> Forge: 47.4.18
>
> Java: 17
>
> Architecture baseline: v2.7
>
> Status: Design candidate — not approved for production implementation

---

## 1. Purpose

This document defines the framework-level command foundation for
FontaineRepublic. It translates Architecture v2.7 Phase 0 Step 5 into an
implementable Forge 1.20.1 contract.

The foundation provides:

- one server command root named `/fr`;
- deterministic registration of future module subcommands;
- rebuild-safe integration with Forge `RegisterCommandsEvent`;
- a strict boundary between command parsing and server business services;
- execution-time resolution of the current server runtime;
- vanilla Brigadier compatibility for players without the optional FR client;
- consistent command feedback and exception logging;
- a minimal, read-only framework diagnostic surface.

It does not define business commands, business permissions, audit queries,
GUI, packets, or client-side command state.

---

## 2. Authority and scope

### 2.1 Authoritative side

Commands are server entry points. A command may parse a request and invoke an
owning server-side service, but it may not become an alternative authority
layer.

```text
Player, console, or RCON input
    ↓
Brigadier command tree
syntax + argument parsing + early source gate
    ↓
Command handler
source extraction + current-runtime resolution
    ↓
Owning server Service
permission + identity + authority + business validation
    ↓
Repository / SavedData
    ↓
Command feedback
```

The command layer must never:

- write authoritative NBT directly;
- call `DataManager.getModuleData()` or `putModuleData()`;
- duplicate permission or business rules owned by a Service;
- trust a player name supplied as identity;
- infer identity from display text;
- cache an authoritative business result;
- bypass an unavailable module.

### 2.2 Allowed implementation scope after future Human approval

FR-CMD-001 may provide:

- `RegisterCommandsEvent` integration;
- the `/fr` root;
- an immutable command-contribution registry;
- contribution validation and freeze;
- a rebuild-safe command-tree compiler;
- a current-runtime resolver;
- feedback and exception-boundary helpers;
- `/fr` framework information;
- `/fr help`;
- `/fr admin status`;
- `/fr admin modules`;
- automated contract tests;
- isolated Dedicated Server command validation.

### 2.3 Forbidden scope

FR-CMD-001 must not implement:

- Citizen commands;
- PlayerData profile commands;
- Economy, money, bank, treasury, or market commands;
- Land, city, government, parliament, court, or election commands;
- business mutations;
- `/fr admin save`;
- `/fr admin reload`;
- Audit queries or report generation;
- a business permission system;
- roles, citizenship ranks, offices, or social permissions;
- custom network packets;
- client commands;
- GUI, screen, HUD, key binding, or rendering;
- custom Brigadier argument types requiring an FR client;
- JSON storage;
- a command history database;
- an internal event bus;
- runtime command hot-plugging.

The production foundation ships with no feature-module command contribution.

---

## 3. Binding design decisions

| ID | Decision |
|---|---|
| CMD-D01 | Use exactly one root literal: `fr`. |
| CMD-D02 | Register server commands only through Forge `RegisterCommandsEvent` on `MinecraftForge.EVENT_BUS`. |
| CMD-D03 | Treat command definitions as Mod-lifetime immutable specifications and Brigadier nodes as rebuild-scoped objects. |
| CMD-D04 | Rebuild a fresh `/fr` tree for every `RegisterCommandsEvent`; never retain a node or dispatcher from a previous event. |
| CMD-D05 | Freeze the contribution registry during `FMLCommonSetupEvent.enqueueWork`, before command registration can occur. |
| CMD-D06 | Resolve the current Core runtime and feature services at command execution time, never while building the tree. |
| CMD-D07 | The Command Foundation is not an `IModule`; it owns no per-server mutable state and has no `init()` or `shutdown()` lifecycle. |
| CMD-D08 | The root remains visible to ordinary sources; each protected child owns its early Brigadier source gate. |
| CMD-D09 | Phase 0 diagnostics use Minecraft OP permission level 2 only as a temporary bootstrap gate. |
| CMD-D10 | A Brigadier `.requires(...)` gate is not authoritative business permission validation. Future Services must independently enforce permissions. |
| CMD-D11 | Essential output uses server-resolved literal or vanilla-translatable components so a client without FR resources sees readable text. |
| CMD-D12 | Only vanilla/Minecraft argument types are permitted in the production command tree during Alpha. |
| CMD-D13 | Player identity is obtained from the executing `ServerPlayer` UUID or an explicitly resolved vanilla player argument, never a raw name key. |
| CMD-D14 | Command callbacks are synchronous server-main-thread entry points. They do not start threads or use asynchronous business mutation. |
| CMD-D15 | Unexpected failures are logged with context and stack trace; users receive a safe failure message without internal details. |
| CMD-D16 | Registration rejects invalid or duplicate top-level literals before freeze. |
| CMD-D17 | Future modules contribute commands through the registry; they do not register another `/fr` root. |
| CMD-D18 | The foundation exposes read-only framework diagnostics only. |
| CMD-D19 | No static global dispatcher, node, player, service, or server cache is allowed. |
| CMD-D20 | Command tree construction must not query worlds, SavedData, runtime modules, players, or business services. |

---

## 4. Forge lifecycle model

### 4.1 Verified Forge 47.4.18 API

Forge 47.4.18 provides:

```java
public class RegisterCommandsEvent extends Event {
    public CommandDispatcher<CommandSourceStack> getDispatcher();
    public Commands.CommandSelection getCommandSelection();
    public CommandBuildContext getBuildContext();
}
```

The event:

- is fired on `MinecraftForge.EVENT_BUS`;
- is posted from Forge command registration while a `Commands` instance is
  constructed;
- is fired whenever `ReloadableServerResources` is recreated;
- therefore may occur more than once during one process;
- supplies a new registration context that must be used for that rebuild.

The implementation must use the actual mapped methods above. It must not invent
an event-bus or lifecycle API.

### 4.2 Lifecycle sequence

```text
Mod construction
    ├─ create CommandContributionRegistry
    ├─ create CommandBootstrap
    └─ register CommandBootstrap listener on MinecraftForge.EVENT_BUS

FMLCommonSetupEvent.enqueueWork
    ├─ register compiled framework command specifications
    ├─ future builds register compiled feature specifications
    ├─ validate registry
    └─ freeze immutable snapshot

Each command-resource construction or reload
    └─ RegisterCommandsEvent
         ├─ require frozen registry
         ├─ create a fresh /fr builder
         ├─ build fresh child nodes from specifications
         ├─ attach children in deterministic order
         └─ register the fresh root with this event's dispatcher

Each command execution
    ├─ read CommandSourceStack
    ├─ resolve current server runtime
    ├─ call current service if required
    └─ send feedback

ServerStoppingEvent / ServerStoppedEvent
    └─ no Command Foundation shutdown action
```

### 4.3 Why registration is not a server runtime module

`RegisterCommandsEvent` belongs to reloadable server-resource construction.
`IModule.init()` belongs to the FontaineRepublic per-server runtime after
dependency resolution.

Putting command registration in an `IModule` would:

- miss the correct Forge event;
- encourage retaining a dispatcher across reloads;
- create unclear ordering between command construction and runtime startup;
- duplicate nodes on integrated-server restart;
- make resource reload unsafe.

The command tree is infrastructure built from immutable definitions. The
services invoked by that tree remain per-server runtime objects.

---

## 5. Component architecture

```text
FontaineRepublic composition root
    ├─ CoreManager
    ├─ CommandContributionRegistry
    ├─ CommandRuntimeResolver
    └─ CommandBootstrap
          └─ RegisterCommandsEvent
                └─ FRCommand tree compiler
                      ├─ built-in framework children
                      └─ immutable contribution snapshot

Execution path
    CommandSourceStack
        ↓
    command callback
        ↓
    CommandRuntimeResolver
        ↓
    CoreManager current RuntimeModuleContainer
        ↓
    current Service API
```

### 5.1 `CommandBootstrap`

Responsibilities:

- own the Forge event-listener method;
- require a frozen contribution snapshot;
- reject a command-root collision;
- invoke the fresh tree compiler;
- register the root once for the current dispatcher;
- log registration outcome with environment and contribution count.

It must not:

- retain the event dispatcher;
- retain a Brigadier node;
- query `DataManager`;
- resolve runtime services during registration;
- own player or server state;
- register feature commands directly.

### 5.2 `CommandContributionRegistry`

Responsibilities:

- accept immutable command specifications during the open registration window;
- normalize and validate top-level literals;
- reject duplicates and reserved literals;
- freeze exactly once;
- expose an immutable, deterministically sorted snapshot.

The registry is Mod-lifetime configuration, not authoritative gameplay state.

### 5.3 `CommandContributionSpec`

A contribution specification contains only stable build-time contract:

```java
public record CommandContributionSpec(
        String topLevelLiteral,
        CommandTreeFactory treeFactory
) {}
```

The exact implementation name may differ, but the contract must remain:

- immutable;
- one explicit top-level literal;
- one factory that creates a fresh builder for each event;
- no stored dispatcher or built node;
- no stored runtime service;
- no side effects during construction.

### 5.4 `CommandTreeFactory`

Conceptual contract:

```java
@FunctionalInterface
public interface CommandTreeFactory {
    LiteralArgumentBuilder<CommandSourceStack> create(
            CommandBuildContext buildContext,
            CommandRuntimeResolver runtimeResolver
    );
}
```

The factory may:

- construct literals and vanilla arguments;
- attach `.requires(...)` early gates;
- attach command callbacks;
- use the stable resolver facade inside callbacks.

The factory must not:

- query a runtime while building;
- read SavedData;
- access a world or player list;
- perform permission decisions beyond source-gate construction;
- return the same builder instance twice;
- mutate static state.

### 5.5 `CommandRuntimeResolver`

The resolver is a stateless facade over `CoreManager`.

At execution time it may:

- inspect the current runtime availability snapshot;
- find the current `RuntimeModuleContainer` by `ModuleId`;
- require `ModuleState.ACTIVE`;
- type-check the current module instance;
- return the current Service API;
- produce a clear unavailable result.

It must not:

- cache a module instance;
- cache a Service;
- cache a `MinecraftServer`;
- return an object from a stopped runtime;
- use `DataManager` as a service locator.

The resolver pattern must match the restart-safety principle already used by
the Network Foundation.

---

## 6. Registry contract

### 6.1 Registration window

The registry has two states:

```text
OPEN
  └─ register valid specification
  └─ register valid specification
  └─ freeze()
         ↓
FROZEN
  ├─ snapshot allowed
  ├─ repeated freeze rejected
  └─ further registration rejected
```

No reopen operation exists.

### 6.2 Literal validation

A top-level literal must:

- be non-null;
- be non-blank;
- use lowercase ASCII letters, digits, `_`, or `-`;
- start with a lowercase ASCII letter;
- have a conservative maximum length of 32 characters;
- be unique in the registry;
- not equal a reserved foundation literal.

Reserved literals in v1:

- `admin`;
- `help`;

The root literal `fr` is not a contribution literal and is always reserved.

### 6.3 Deterministic order

The frozen snapshot is sorted by top-level literal using stable ASCII lexical
order.

Command semantics must not depend on registration order. Sorting makes:

- tree output deterministic;
- tests reproducible;
- future module additions reviewable;
- accidental ordering dependencies visible.

### 6.4 Duplicate handling

Duplicate literals are hard registration failures.

The registry must not:

- silently replace an earlier contribution;
- merge two independently owned top-level specifications;
- select a winner based on priority;
- log and continue with an ambiguous tree.

### 6.5 Root collision handling

Before registering `/fr`, `CommandBootstrap` checks the current dispatcher's
root for an existing child named `fr`.

If a node already exists:

- do not merge into an unknown root;
- log an explicit command-root collision;
- fail registration for the FR root in that dispatcher;
- do not remove or replace the existing node.

This protects both FontaineRepublic and other mods from silent command
ownership conflicts.

---

## 7. Production command surface

### 7.1 Root

The only root is:

```text
/fr
```

Executing `/fr` returns:

- the Mod name;
- the current Mod version if available without runtime access;
- a short indication that subcommands are available through Brigadier help.

It performs no data read or mutation.

The root must not have an administrator-only `.requires(...)` gate because
future public feature commands will live below it.

`/fr help` returns the same bounded framework help plus the currently compiled
top-level literals that the source may use. It does not query a runtime or
manually reimplement Brigadier permission filtering.

### 7.2 Read-only framework diagnostics

FR-CMD-001 may ship:

```text
/fr help
/fr admin status
/fr admin modules
```

The `help` child is public. Both `admin` diagnostics are protected by the
Phase 0 bootstrap gate:

```java
source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS)
```

In Minecraft 1.20.1, `Commands.LEVEL_GAMEMASTERS` is permission level `2`.

#### `/fr admin status`

May report:

- whether the Core runtime is available;
- whether dependency resolution completed;
- number of registered runtime modules;
- count of active and unavailable modules;
- current server environment as Dedicated or Integrated.

It must not report:

- player records;
- profile descriptions or locale;
- UUID lists;
- NBT;
- network addresses;
- secrets or filesystem paths;
- exception stack traces.

#### `/fr admin modules`

May report, for each registered module:

- module ID;
- lifecycle state;
- availability status;
- sanitized failure category if unavailable.

It must not expose raw exception messages or stack traces to the command
source. Full exceptions remain server-log evidence.

### 7.3 Deferred administrative commands

The Architecture v2.7 command examples mention:

```text
/fr admin reload
/fr admin save
```

They are not part of FR-CMD-001.

Reasons:

- configuration reload semantics are not yet defined;
- forced persistence ownership belongs to `DataManager` lifecycle policy;
- both commands mutate operational state;
- Permission Foundation is not yet available;
- their audit policy is not yet defined.

This is a scope clarification, not removal from the long-term roadmap.

---

## 8. Permission boundary

### 8.1 Temporary Phase 0 bootstrap gate

The built-in diagnostics use Minecraft OP permission level 2 only.

This gate:

- provides a safe minimum for development diagnostics;
- works for Dedicated Server operators;
- does not require Citizen;
- does not create a persistent permission database;
- does not define future FontaineRepublic roles.

### 8.2 What this task does not decide

FR-CMD-001 does not define:

- administrator identity;
- Citizen ranks;
- government office permissions;
- land permissions;
- permission inheritance;
- permission persistence;
- per-command configurable grants;
- role-to-permission mapping.

Those belong to `FR-PERM-001`.

### 8.3 `.requires(...)` is not business authority

Brigadier `.requires(...)` controls:

- whether a node is visible in the synchronized command tree;
- whether parsing may continue to that node;
- early rejection for an unsuitable source.

It is not sufficient for business authorization.

Future command flow must remain:

```text
.requires(...) early source gate
    ↓
parse arguments
    ↓
Service call
    ↓
PermissionService and business validation inside server authority
```

A future Service must reject an unauthorized operation even if a caller
reaches it through another server entry point.

### 8.4 Source identity

For a player-only command:

```java
ServerPlayer player = source.getPlayerOrException();
UUID actorId = player.getUUID();
```

Rules:

- the actor UUID comes from the authenticated command source;
- the client does not submit its own actor UUID;
- player names are display and lookup inputs, not identity keys;
- console and RCON must be handled explicitly instead of impersonating a
  player.

---

## 9. Optional-client compatibility

### 9.1 Command availability

The `/fr` command is a Server Core feature.

It must work for:

- a Forge client with the compatible FR Mod;
- a Forge client without the FR Mod;
- Dedicated Server console;
- Integrated Server host;
- RCON where Minecraft normally permits the command.

It must not depend on:

- `SimpleChannel`;
- `isRemotePresent()`;
- a client screen;
- client language files;
- a client-side cache.

### 9.2 Argument-type restriction

The production tree uses only vanilla/Minecraft argument types that the
unmodified Forge client understands.

FR-CMD-001 forbids:

- a custom `ArgumentType` registered by FontaineRepublic;
- custom suggestion payloads requiring an FR packet;
- client-only classes in a builder or callback;
- an argument serializer that a no-Mod client cannot decode.

This preserves the optional-client contract verified by FR-NET-001.

### 9.3 Feedback components

Essential feedback must render on a client without FontaineRepublic resources.

Allowed:

- `Component.literal(...)`;
- vanilla translation keys known to every Minecraft client;
- server-resolved text assembled from safe values.

Forbidden for essential feedback:

- FR-only translation keys sent unresolved to a no-Mod client;
- click actions that require an FR screen;
- hover data containing internal exceptions or NBT.

Future localization may add capability-aware enhanced output, but the plain
server-readable fallback remains mandatory.

---

## 10. Command-layer responsibilities

### 10.1 Command layer owns

- Brigadier syntax;
- argument parsing;
- source-type extraction;
- early `.requires(...)` source gates;
- current-runtime resolution;
- invocation of one owning Service API;
- mapping Service outcomes to command feedback;
- safe exception boundaries.

### 10.2 Service layer owns

- authoritative permissions;
- player identity rules;
- balance, ownership, citizenship, office, land, or legal validation;
- mutations;
- revision control;
- audit recording;
- transaction semantics;
- repository access.

### 10.3 Repository and storage layer own

- NBT schema;
- persistence;
- UUID indexing;
- revision and migration;
- atomic storage behavior.

### 10.4 Forbidden command shortcuts

Future commands must not:

```java
DataManager.getModuleData(...);
DataManager.putModuleData(...);
```

They must not directly construct a Repository, Codec, or SavedData object.

They must not mutate a returned model to avoid a Service call.

---

## 11. Threading and execution

### 11.1 Registration thread

`RegisterCommandsEvent` is a tree-construction phase.

During this phase:

- construct builders only;
- do not access a server runtime;
- do not load worlds;
- do not read players;
- do not call Services;
- do not read or write SavedData.

### 11.2 Execution thread

Minecraft server commands execute synchronously through the server command
dispatcher.

FR command callbacks must:

- complete synchronously;
- perform server state access on the server main thread;
- not create a worker thread;
- not use `CompletableFuture` for mutation;
- not block on network or filesystem I/O;
- not call client code.

If an implementation adds a defensive main-thread check, a violation must
fail explicitly and log an error. It must not silently schedule a duplicate
execution.

### 11.3 Suggestions

Suggestion providers are not business-query APIs.

Production suggestions may use:

- parsed static literals;
- vanilla safe suggestions;
- small immutable definition sets.

They must not:

- scan SavedData;
- enumerate private player records;
- mutate state;
- cache authoritative results;
- perform an unbounded world query.

---

## 12. Runtime availability

### 12.1 Build-time independence

The command tree is built whether or not a feature runtime will later become
ACTIVE.

This is necessary because:

- command construction may precede runtime startup;
- a required dependency may fail after the tree exists;
- an integrated server may stop and start again in the same Mod process.

### 12.2 Execution-time resolution

A feature command callback resolves its current module at execution:

```text
Command source
    ↓
CommandRuntimeResolver
    ↓
CoreManager.getRuntimeContainer(moduleId)
    ↓
container state == ACTIVE
    ↓
type-check module instance
    ↓
obtain current Service
```

If any step fails:

- do not execute partial business behavior;
- send a clear unavailable message;
- return command result `0`;
- log at WARN for expected unavailability;
- log at ERROR with exception for invariant violations.

### 12.3 Restart safety

After `ServerStoppedEvent`:

- old runtime containers are closed or discarded by Core;
- the resolver holds no old instance;
- existing command specifications remain valid;
- a later server execution resolves the new runtime instance.

No command callback may close over a per-server Service during tree
construction.

---

## 13. Feedback and error contract

### 13.1 Success

Use:

```java
source.sendSuccess(() -> Component.literal(message), false);
```

For read-only diagnostics:

- do not broadcast success to operators;
- return a positive Brigadier result, normally `1`;
- keep output bounded.

### 13.2 Expected failure

Examples:

- player-only command invoked by console;
- module unavailable;
- invalid service input;
- target not found;
- operation rejected by a Service.

Use:

```java
source.sendFailure(Component.literal(message));
return 0;
```

Expected user errors do not require stack traces.

### 13.3 Unexpected failure

For an unexpected `RuntimeException`:

- catch it at the command boundary;
- log command ID, source category, and server context;
- include the exception and stack trace in the server log;
- do not log secrets, raw NBT, or the full unredacted input line;
- send a generic internal-error message;
- return `0`;
- do not retry automatically.

### 13.4 Output bounds

One command execution must not produce unbounded chat output.

Framework diagnostics must:

- cap module lines to the registered module count;
- use one line per module at most;
- avoid dumping nested exceptions;
- avoid dumping full configuration or NBT;
- provide a summary before details.

Future list commands must define pagination in their own task.

---

## 14. Logging contract

### 14.1 Registration logs

Log at INFO:

- root literal;
- command environment;
- frozen contribution count;
- successful registration.

Log at ERROR:

- registry not frozen;
- invalid specification;
- duplicate literal;
- root collision;
- builder factory returning an invalid root;
- unexpected command-tree construction failure.

### 14.2 Execution logs

Do not log every successful public command by default.

Log:

- expected module unavailability at WARN when operationally useful;
- unexpected callback failures at ERROR with stack trace;
- permission denials only when security policy later requires it;
- no raw passwords, tokens, private descriptions, NBT, or network secrets.

Audit logging is not implemented by FR-CMD-001. Future business Services call
`AuditService`; the command layer does not independently create business audit
events.

---

## 15. Dependency rules

### 15.1 Allowed dependencies

```text
FontaineRepublic composition root
    → Command Foundation
    → CoreManager

Command Foundation
    → Minecraft / Forge / Brigadier
    → Core public runtime-query API

Future Feature Command
    → Command Foundation API
    → Feature Service API

Feature Service
    → Repository / Data
    → optional PermissionService
    → optional AuditService
```

### 15.2 Forbidden dependencies

| Dependency | Reason |
|---|---|
| `core` → `server.command` | Core must not depend on an adapter layer. |
| `data` → `server.command` | Persistence must not know command transport. |
| `server.command` → PlayerData Repository or Codec | Commands depend on Services only. |
| `server.command` → `client` | Dedicated Server safety. |
| `server.command` → raw `SimpleChannel` | Commands are independent of optional networking. |
| Feature command → another feature Repository | Cross-module storage access is forbidden. |
| Command builder → runtime module instance | Tree rebuild and restart safety. |

### 15.3 Core compatibility

FR-CMD-001 should use existing Core public queries:

- `CoreManager.getRuntimeAvailabilitySnapshot()`;
- `CoreManager.getRuntimeContainers()`;
- `CoreManager.getAvailability(ModuleId)`;
- `CoreManager.getRuntimeContainer(ModuleId)`;
- `RuntimeModuleContainer.state()`.

No Core modification is expected.

If implementation requires changing Core lifecycle, static contract,
dependency resolution, availability ownership, or disposal behavior, the
Implementer must stop and issue a Technical Deviation Alert.

---

## 16. Command contribution rules for future modules

### 16.1 Ownership

Each feature module owns exactly one top-level literal or an explicitly
approved set.

Examples for future tasks only:

| Module | Potential literal |
|---|---|
| Citizen | `citizen` |
| Economy | `money`, `bank` |
| Land | `land` |

These examples do not authorize those commands.

### 16.2 Registration

At Mod common setup, a future compiled module may:

1. register its `ModuleDefinition` with `ModuleRegistry`;
2. register its immutable command specification with
   `CommandContributionRegistry`;
3. finish before both registries freeze.

Registration must not instantiate the runtime module.

### 16.3 Service resolution

A future contribution owns a typed resolver or adapter that:

- resolves the current module;
- obtains its public Service;
- performs no storage access;
- returns unavailable if the module is not ACTIVE.

### 16.4 No root re-registration

A feature module must not call:

```java
dispatcher.register(Commands.literal("fr"));
```

Only `CommandBootstrap` owns the root.

---

## 17. Proposed implementation layout

The exact filenames may change during review, but responsibilities must remain
separated.

```text
src/main/java/com/fontainerepublic/server/command/
├── CommandBootstrap.java
├── FRCommand.java
├── FrameworkAdminCommand.java
├── CommandFeedback.java
├── CommandRuntimeResolver.java
├── api/
│   ├── CommandContributionRegistrar.java
│   └── CommandTreeFactory.java
└── registration/
    ├── CommandContributionRegistry.java
    ├── CommandContributionSpec.java
    └── CommandRegistrationException.java

src/test/java/com/fontainerepublic/server/command/
└── CommandFoundationTestMain.java
```

Expected existing-file modifications:

- `FontaineRepublic.java` for composition-root construction, listener wiring,
  framework-spec registration, and freeze;
- `build.gradle` only if an automated `commandFoundationTest` task is needed.

No PlayerData, Network Foundation, DataManager, ModSavedData, or Core
implementation file should require modification.

---

## 18. Implementation contract

### 18.1 Composition root

Conceptual wiring:

```java
private final CoreManager coreManager = ...;
private final CommandContributionRegistry commandRegistry =
        new CommandContributionRegistry();
private final CommandRuntimeResolver commandRuntimeResolver =
        new CommandRuntimeResolver(coreManager);
private final CommandBootstrap commandBootstrap =
        new CommandBootstrap(commandRegistry, commandRuntimeResolver);

public FontaineRepublic() {
    MinecraftForge.EVENT_BUS.addListener(
            commandBootstrap::onRegisterCommands
    );
}
```

During common setup:

```java
event.enqueueWork(() -> {
    commandRegistry.registerFrameworkCommands();
    commandRegistry.freeze();

    networkBootstrap.registerProductionMessagesAndFreeze();
    coreManager.closeRegistration();
});
```

The final ordering may place registrations before the enqueue block, but all
freezes must be deterministic and complete before server command
construction. The implementation report must state the exact order.

### 18.2 Fresh root compilation

Conceptual flow:

```java
public void onRegisterCommands(RegisterCommandsEvent event) {
    FrozenCommandTable table = registry.requireFrozenSnapshot();

    LiteralArgumentBuilder<CommandSourceStack> root =
            Commands.literal("fr")
                    .executes(context -> showRoot(context.getSource()));

    attachFrameworkChildren(root, event.getBuildContext());

    for (CommandContributionSpec spec : table.sortedSpecifications()) {
        root.then(spec.createFreshTree(
                event.getBuildContext(),
                runtimeResolver
        ));
    }

    event.getDispatcher().register(root);
}
```

The implementation must additionally:

- verify returned child literal matches the specification;
- reject null builders;
- enforce the root-collision rule;
- avoid retaining `root` or `event.getDispatcher()`.

### 18.3 Command result rules

| Outcome | Brigadier result |
|---|---:|
| Successful information or mutation | positive, normally `1` |
| Expected rejection | `0` |
| Module unavailable | `0` |
| Unexpected internal failure | `0` after logging |
| Syntax parse failure | Brigadier exception path |

---

## 19. Validation requirements

### 19.1 Pure contract tests

Automated tests must directly assert:

1. valid contribution registration succeeds while OPEN;
2. invalid literals are rejected;
3. duplicate literals are rejected;
4. reserved literals are rejected;
5. freeze produces immutable deterministic order;
6. repeated freeze is rejected;
7. post-freeze registration is rejected;
8. each tree build receives a fresh builder;
9. no dispatcher or node is retained by the registry;
10. a returned child whose literal differs from its spec is rejected.

### 19.2 Brigadier tree tests

Using a real Brigadier dispatcher compatible with the development runtime:

1. `/fr` is registered exactly once;
2. built-in `admin` children exist once;
3. contribution children are deterministically ordered;
4. a second fresh dispatcher receives an equivalent fresh tree;
5. rebuilding does not accumulate children;
6. a pre-existing `fr` root produces an explicit collision failure;
7. ordinary sources cannot traverse admin diagnostics;
8. permitted sources can execute read-only diagnostics;
9. unavailable runtime returns `0` and clear feedback.

Tests must assert outcomes, not merely execute registration code.

### 19.3 Source and boundary tests

Automated inspection must confirm:

- no `net.minecraft.client` reference in production command classes;
- no `DataManager.getModuleData()` or `putModuleData()` call;
- no import of PlayerData persistence or Codec classes;
- no raw `SimpleChannel` use;
- no static dispatcher, server, player, or Service cache;
- no business literals or business field names;
- only vanilla argument types are used.

### 19.4 Existing regression tests

Implementation validation must rerun:

- Core/build validation;
- `playerDataTest`;
- `networkFoundationTest`;
- full Gradle `build`;
- `git diff --check`.

---

## 20. Runtime acceptance matrix

Build success is not runtime proof.

After implementation review and explicit Human runtime authorization, isolated
runtime validation must cover:

| Scenario | Required observation |
|---|---|
| Dedicated Server start | `RegisterCommandsEvent` registers `/fr`; server reaches `Done`. |
| Console root command | `fr` returns readable framework information. |
| Console help command | `fr help` returns bounded available-subcommand guidance. |
| Console diagnostics | `fr admin status` and `fr admin modules` return bounded read-only output. |
| Matching FR client | `/fr` appears and executes. |
| Forge client without FR Mod | `/fr` appears and executes with readable feedback. |
| Non-OP player | Admin subtree is not executable. |
| OP player | Admin diagnostics execute successfully. |
| Resource reload | Command tree rebuilds without duplicate children or stale state. |
| Second server process | Fresh dispatcher registers the same tree without duplicate failure. |
| Runtime unavailable fixture | Command fails clearly without partial behavior. |
| Shutdown | No command-owned server, dispatcher, node, or Service state remains. |

The evidence package must record:

- candidate commit and JAR hash;
- server/client environment;
- command outputs;
- relevant permission level;
- resource reload result;
- two-process restart result;
- complete logs;
- process exit codes;
- repository before/after state.

---

## 21. Security and privacy

### 21.1 Trust boundary

All command input is untrusted.

Even console input must pass:

- syntax validation;
- source-type validation;
- runtime availability checks;
- Service validation for any future business behavior.

### 21.2 Sensitive output

Framework commands must not expose:

- full UUID collections;
- profile descriptions;
- NBT payloads;
- filesystem paths;
- access tokens;
- network secrets;
- raw exception messages to ordinary players.

### 21.3 Denial-of-service controls

FR-CMD-001 diagnostics are bounded and read-only.

Future commands that:

- enumerate records;
- perform searches;
- generate reports;
- query large regions;
- or trigger expensive computation

must define pagination, rate, and work limits in their own design.

### 21.4 Command injection

Handlers use typed Brigadier arguments. They must not:

- concatenate input into another command and execute it;
- use `server.getCommands().performPrefixedCommand(...)` as a business
  dispatch mechanism;
- pass unvalidated text to a shell, SQL engine, or file path.

---

## 22. Failure handling

### 22.1 Registration failure

The following are hard failures for FR command registration in the current
dispatcher:

- registry is not frozen;
- root collision;
- duplicate specification;
- invalid literal;
- factory returns null;
- factory returns the wrong top-level literal;
- unexpected tree-construction exception.

The implementation must:

- log the exact category;
- not publish a partial `/fr` tree;
- not replace another root;
- not modify Core or Data state.

### 22.2 Runtime resolution failure

Expected module unavailability is not a registration failure.

The tree remains present and the callback:

- reports module unavailable;
- returns `0`;
- performs no mutation.

### 22.3 Diagnostic failure

If Core status cannot be obtained:

- return a bounded unavailable message;
- log the unexpected reason;
- do not dump the exception to the source;
- do not fall back to stale cached state.

---

## 23. Rejected alternatives

### 23.1 Register commands in `ServerStartingEvent`

Rejected because Forge owns command construction through
`RegisterCommandsEvent`, including resource reloads.

### 23.2 Make Command Foundation an `IModule`

Rejected because command definitions are reloadable-resource infrastructure,
not per-server mutable module state.

### 23.3 Let each feature module register `/fr`

Rejected because ownership collisions, ordering, and duplicate roots become
ambiguous.

### 23.4 Cache feature Services in command objects

Rejected because integrated-server restart would retain stale instances.

### 23.5 Build the command tree from ACTIVE modules only

Rejected because command construction and runtime initialization have
different timing. Availability must be checked at execution.

### 23.6 Use custom FR argument types

Rejected because clients without the optional FR Mod must receive and execute
the server command tree.

### 23.7 Implement business permission rules in `.requires(...)`

Rejected because `.requires(...)` is only an early Brigadier gate and may not
become the sole authority for business operations.

### 23.8 Add `/fr admin save` and `/fr admin reload` now

Rejected for this phase because persistence, reload, permission, and audit
semantics are not yet defined.

### 23.9 Register a global command interception listener

Rejected because FR-CMD-001 owns only the `/fr` namespace and must not observe
or modify unrelated commands.

---

## 24. Implementation staging

After independent design review and separate Human implementation approval:

### Stage 1 — Pure contracts

- contribution specification;
- registry validation;
- deterministic freeze;
- runtime resolver interface;
- feedback contract;
- pure automated tests.

### Stage 2 — Forge integration

- `CommandBootstrap`;
- `RegisterCommandsEvent` listener;
- fresh tree compilation;
- root collision handling;
- composition-root wiring.

### Stage 3 — Framework commands

- `/fr`;
- `/fr help`;
- `/fr admin status`;
- `/fr admin modules`;
- bounded output and bootstrap OP gate.

### Stage 4 — Verification

- command contract test;
- PlayerData and Network regression tests;
- full build;
- independent repository review;
- explicitly authorized isolated runtime matrix.

No stage introduces a feature-module command.

---

## 25. Review gate

Before implementation, an independent Reviewer must verify:

1. compatibility with Minecraft 1.20.1, Forge 47.4.18, and Java 17;
2. use of the actual `RegisterCommandsEvent` API;
3. compatibility with Architecture v2.7 Phase 0 Step 5;
4. compatibility with FR-CORE-001 lifecycle and runtime freshness;
5. compatibility with FR-NET-001 optional-client behavior;
6. rebuild safety across resource reload and server restart;
7. absence of runtime Service caching;
8. separation from Permission Foundation;
9. absence of business commands and mutations;
10. safety of the read-only diagnostic surface;
11. feasibility of the automated and runtime acceptance matrices;
12. whether deferring `admin save/reload` requires an Architecture v2.7
    amendment.

Review does not authorize implementation. Human Design Freeze and a separate
implementation approval are required after review.

---

## 26. Primary references

- Forge 47.4.18 source:
  `net.minecraftforge.event.RegisterCommandsEvent`
- Forge 47.4.18 command hook:
  `net.minecraftforge.event.ForgeEventFactory.onCommandRegister(...)`
- Minecraft 1.20.1 mapped sources:
  `net.minecraft.commands.Commands`
- Minecraft 1.20.1 mapped sources:
  `net.minecraft.commands.CommandSourceStack`
- Project Architecture v2.7:
  `docs/architecture/architecture.md`
- FR-CORE-001 architecture:
  `docs/architecture/fr-core-001-a-architecture.md`
- FR-NET-001-A architecture:
  `docs/architecture/fr-net-001-a-network-foundation-architecture.md`
