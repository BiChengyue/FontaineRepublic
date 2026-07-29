# FontaineRepublic Network Foundation Architecture v1.0

> Task: FR-NET-001-A
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

This document defines the framework-level network foundation that future
FontaineRepublic modules may use. It translates Architecture v2.7 and DEC-003
into an implementable Forge 1.20.1 contract.

The foundation provides:

- one Forge `SimpleChannel`;
- deterministic protocol-version and message-ID management;
- bounded `FriendlyByteBuf` encoding and decoding rules;
- direction enforcement;
- server-main-thread dispatch;
- instance-owned per-server transport state;
- optional-client capability checks;
- guarded C2S rate limiting;
- a stable registration API for future modules.

It does not define any gameplay, business request, data synchronization, GUI,
or client cache.

---

## 2. Authority and scope

### 2.1 Authoritative side

The server is authoritative.

A network message may request an operation, but it may not:

- replace authoritative server data;
- grant permissions;
- establish identity;
- decide citizenship or government state;
- supply trusted balances, ownership, office, land, or court state;
- bypass the owning server-side service.

Transport validation and business validation remain separate:

```text
Client message
    ↓
Network Foundation
direction + sender + structure + rate
    ↓
Owning server Service
permission + authority + business rules + mutation
    ↓
Optional response or publication
```

### 2.2 Allowed implementation scope after a future implementation approval

The Network Foundation implementation may provide:

- channel bootstrap;
- protocol predicates;
- message registration and freeze;
- message-spec validation;
- buffer safety helpers;
- server dispatch helpers;
- optional-client send filtering;
- a server-runtime network module;
- a per-runtime rate limiter;
- framework-level tests and runtime validation fixtures.

### 2.3 Forbidden scope

FR-NET-001 must not implement:

- PlayerData packets or synchronization;
- Citizen, Economy, Government, Court, Land, or other feature packets;
- commands;
- GUI, screen, menu, HUD, key binding, or rendering;
- client authoritative state;
- a persistent network database;
- login-time business snapshots;
- a general internal event bus;
- JSON persistence;
- Capability-backed authoritative data.

The production foundation ships with no business message registrations.

---

## 3. Design decisions

| ID | Decision |
|---|---|
| NET-D01 | Use one channel named `fontainerepublic:main`. |
| NET-D02 | Initial protocol version is the exact string `"1"`. |
| NET-D03 | Channel creation and message-table registration occur once in the Mod lifecycle, not once per server runtime. |
| NET-D04 | Per-server mutable transport state belongs to a fresh Core runtime module instance. |
| NET-D05 | Message IDs are explicit, globally unique within the channel, append-only, and never reused. |
| NET-D06 | Forge 1.20.1 messages use `FriendlyByteBuf` encoders and decoders. |
| NET-D07 | Every message declares one fixed `NetworkDirection`. |
| NET-D08 | All server game-state access and service calls execute on the server main thread. |
| NET-D09 | An FR server accepts a Forge client whose FR channel is `ABSENT`, but does not automatically claim vanilla-client support. |
| NET-D10 | S2C sends are filtered with `SimpleChannel.isRemotePresent(connection)` immediately before sending. |
| NET-D11 | The foundation does not keep an authoritative client-capability cache. |
| NET-D12 | C2S rate-limit state is memory-only, per server runtime, and keyed by player UUID plus message ID. |
| NET-D13 | Future business modules own their messages and business handlers; the network layer owns transport enforcement only. |
| NET-D14 | No static global player, service, rate-limit, or connection cache is allowed. |

---

## 4. Two-lifecycle model

Forge networking and FontaineRepublic server modules have different
lifetimes. They must not be collapsed into one lifecycle.

```text
Mod instance construction
    ↓
Create NetworkBootstrap and SimpleChannel
    ↓
FMLCommonSetupEvent.enqueueWork
    ├── register complete compiled message table
    ├── validate duplicate IDs/classes/directions
    └── freeze registrations

Each Minecraft server run
    ↓
ServerStartingEvent
    └── CoreManager creates fresh NetworkRuntimeModule
          ├── fresh rate-limit state
          ├── active runtime guard
          └── runtime service resolver
    ↓
ServerStoppingEvent
    └── clear runtime-only network state
    ↓
ServerStoppedEvent
    └── discard runtime module instance
```

### 4.1 Mod-lifetime protocol bootstrap

`NetworkBootstrap` is created from the FontaineRepublic mod instance. It owns
the `SimpleChannel` and the registration window.

Responsibilities:

- construct the channel;
- expose a registration-only `NetworkMessageRegistrar`;
- register the complete compiled message table during common setup;
- reject invalid or duplicate specifications;
- freeze the table exactly once;
- expose a send-only facade after freeze.

It must not:

- hold player data;
- hold per-server rate state;
- hold a business service;
- query `DataManager`;
- register messages during `ServerStartingEvent`;
- reopen after freeze.

### 4.2 Server-runtime network module

`NetworkRuntimeModule` is a normal FR-CORE-001 runtime module with:

```text
ModuleId: network
Logical role: core:network
Required dependencies: none
Optional dependencies: none
Priority: before feature modules
```

The current `ModuleId` grammar does not accept `:`. Therefore its actual
registry ID is `network`; `core:network` is descriptive architecture notation,
not a value passed to `ModuleId`.

Each server start creates a new instance. It owns:

- the active/inactive runtime guard;
- per-player and per-message rate-limit buckets;
- transport-level server dispatch state;
- any future derived connection capability observations.

Shutdown must clear all runtime state. Restart must not reuse any bucket,
connection, player, or handler state from the previous server.

### 4.3 Bridge between lifetimes

Handlers registered at Mod setup cannot capture one server-runtime service
instance because that instance does not exist yet and changes after restart.

The bridge is an instance-owned resolver supplied by the mod entry point:

```text
Registered transport handler
    ↓
NetworkRuntimeResolver
    ↓
CoreManager.getRuntimeContainer(NetworkRuntimeModule.MODULE_ID)
    ↓
ACTIVE NetworkRuntimeService or unavailable
```

The resolver may capture the mod instance's `CoreManager`. It must not use a
static mutable service locator.

When no active runtime exists, an incoming application message is rejected and
logged. It must never fall back to stale runtime state.

---

## 5. Component boundaries

### 5.1 Planned packages

```text
com.fontainerepublic
├── common/network/
│   ├── NetworkBootstrap
│   ├── NetworkMessageRegistrar
│   ├── NetworkMessageSpec
│   ├── NetworkProtocol
│   ├── NetworkPayloadLimits
│   └── NetworkRegistrationException
├── server/network/
│   ├── NetworkRuntimeModule
│   ├── NetworkRuntimeService
│   ├── NetworkRuntimeResolver
│   ├── ServerNetworkDispatcher
│   ├── PacketRateLimiter
│   └── NetworkSendService
└── client/network/
    └── ClientNetworkExecutor
```

Exact filenames may be reduced during implementation if responsibilities
remain separated. They must not be combined into one global `PacketHandler`
god class.

### 5.2 Responsibility matrix

| Component | Owns | Must not own |
|---|---|---|
| `NetworkBootstrap` | Channel and registration lifecycle | Server-runtime player state |
| `NetworkMessageRegistrar` | Spec validation and freeze | Business dispatch |
| `NetworkMessageSpec` | Immutable message transport contract | Mutable handlers or game state |
| `NetworkRuntimeModule` | One-server runtime binding | Mod-lifetime message registration |
| `ServerNetworkDispatcher` | Main-thread transport pipeline | Business rules |
| `PacketRateLimiter` | Runtime-only transport quotas | Persistent punishment or permissions |
| `NetworkSendService` | Presence-filtered S2C send | Deciding business recipients |
| `ClientNetworkExecutor` | Physical-client execution boundary | Server authority |
| Feature message class | Immutable data plus buffer codec | Service lookup or mutation |
| Feature handler | Adapter to owning Service | Reimplementing Service rules |

---

## 6. Channel compatibility contract

### 6.1 Channel identity

```text
ResourceLocation: fontainerepublic:main
Protocol version: 1
```

Changing the channel name is a breaking deployment change.

### 6.2 Accepted-version predicates

For Forge 47.4.x, the predicates must follow this behavior:

| Local side evaluating remote | Accepted | Rejected |
|---|---|---|
| Client evaluating server | exact protocol `"1"` | `ABSENT`, incompatible version |
| Server evaluating client | exact protocol `"1"`, `NetworkRegistry.ABSENT.version()` | incompatible installed FR version |

The server predicate must not use `NetworkRegistry.ABSENT.toString()`. In Forge
47.4.x, the protocol meta-version is obtained from `ABSENT.version()`.

The baseline does not accept `NetworkRegistry.ACCEPTVANILLA`. Supporting a
non-Forge vanilla client is a separate deployment decision and is not implied
by supporting a Forge client without the FR mod.

### 6.3 Optional-client meaning

`ABSENT` means the remote Forge endpoint does not advertise this channel.

For an absent FR client:

- connection is allowed;
- no FR C2S message can originate from that client;
- no FR S2C message may be sent to it;
- core operations must remain available through commands or standard
  Minecraft interactions when those systems exist.

An installed but incompatible FR client is rejected during negotiation. It
must not be treated as absent.

### 6.4 Protocol-version changes

Increment the protocol version for any incompatible change, including:

- changing a registered ID's message class;
- changing field order or wire representation;
- changing a field from optional to required;
- changing direction;
- changing semantic meaning in a way old handlers cannot safely interpret;
- removing a message without retaining compatibility.

Adding a new append-only message may retain the version only when both
endpoints use the same universal JAR release. Because the current predicate
requires exact versions, the default safe policy is to increment for every
message-table change.

---

## 7. Message registry contract

### 7.1 Explicit IDs

Every message specification declares an explicit non-negative integer ID.

Rules:

1. IDs are unique across the entire channel.
2. IDs are recorded in one central ledger.
3. New IDs append after the greatest assigned ID.
4. Deleted IDs become tombstones and are never reused.
5. Existing IDs are never renumbered.
6. Registration order in Java source must not determine identity.
7. Duplicate ID or duplicate message class is a startup-fatal registration
   error.

Initial FR-NET-001 production ledger:

| ID | Owner | Message | Direction | Status |
|---:|---|---|---|---|
| — | Network Foundation | No production messages | — | Empty baseline |

The first future approved message receives ID `0`.

### 7.2 Immutable message specification

Conceptually, each compiled message is registered through:

```text
NetworkMessageSpec<MSG>
├── id
├── messageClass
├── direction
├── encoder(MSG, FriendlyByteBuf)
├── decoder(FriendlyByteBuf) -> MSG
├── transportHandler
└── C2S rate policy, when applicable
```

Requirements:

- direction is mandatory;
- encoder, decoder, and handler are non-null;
- C2S messages require an explicit rate policy;
- S2C messages do not carry a C2S rate policy;
- a spec is immutable;
- registration after freeze throws a clear exception;
- registration failure includes the ID and class in logs.

### 7.3 Registration timing

All compiled message specs are registered during
`FMLCommonSetupEvent.enqueueWork`.

Forbidden registration locations:

- `IModule.init()`;
- `ServerStartingEvent`;
- player login;
- world load;
- first send;
- lazy static initialization triggered by a packet.

Message registration must be deterministic before any connection uses the
channel.

### 7.4 Future module ownership

A future module owns its packet classes and transport adapters. During common
setup it contributes its already-approved specs to the registrar.

Registering a message does not make the owning runtime module available.
At dispatch time, the handler must resolve the current runtime module and
reject the message if the required runtime service is unavailable.

---

## 8. Forge 1.20.1 codec contract

### 8.1 Required codec API

SimpleChannel registration for Forge 1.20.1 uses:

- `BiConsumer<MSG, FriendlyByteBuf>` encoder;
- `Function<FriendlyByteBuf, MSG>` decoder.

Mojang `Codec` and `RecordCodecBuilder` are not the SimpleChannel wire contract
for this project version. They may be used for other data formats only when a
separate approved design requires them.

### 8.2 Bounds

No decoder may perform an unbounded read.

Foundation ceilings:

| Payload element | Maximum |
|---|---:|
| Entire application message | 32 KiB encoded payload |
| UTF string | 4,096 characters unless a smaller message-specific limit applies |
| Collection | 1,024 elements unless a smaller message-specific limit applies |
| Byte array | 32 KiB |

These are safety ceilings, not recommended business sizes. Message-specific
limits should be smaller.

Required practices:

- use `readUtf(maxLength)`;
- validate collection counts before allocation;
- reject negative lengths;
- reject values outside message-specific ranges;
- reject trailing or missing required structure when detectable;
- do not decode arbitrary Java serialization;
- do not accept an unrestricted client-supplied `CompoundTag`;
- do not log complete hostile payload contents.

### 8.3 Decoder failure

A malformed message must not reach a Service.

The failure path must:

- stop dispatch;
- log channel, message ID/class, direction, and remote player UUID when known;
- avoid echoing internal exception details to the client;
- allow Forge to terminate or reject the malformed message according to its
  connection handling;
- never partially mutate server data.

---

## 9. Direction and thread contract

### 9.1 Direction

Each registration uses the direction-aware SimpleChannel builder or equivalent
optional direction argument.

Direction is enforced twice:

1. Forge message registration declares the expected direction.
2. The transport handler verifies `context.getDirection()` before dispatch.

Direction mismatch is rejected before any Service lookup.

### 9.2 C2S processing

```text
Network thread
    ├── obtain Context once
    ├── verify PLAY_TO_SERVER
    ├── verify sender exists
    ├── accept only an already-decoded immutable message
    ├── enqueue work
    └── mark packet handled exactly once

Server main thread
    ├── verify NetworkRuntimeService is active
    ├── verify sender connection is still valid
    ├── apply transport rate policy
    ├── resolve owning runtime Service
    ├── call owning Service
    └── log contained failure
```

World, SavedData, Core runtime containers, permissions, and feature services
must not be accessed from the network IO thread.

### 9.3 S2C processing

S2C handlers enqueue client work and enter physical-client code only through
the approved side-isolation mechanism.

Common message classes must not import:

- `net.minecraft.client.*`;
- screen or renderer classes;
- FontaineRepublic `client` package classes.

Dedicated Server classloading must never resolve client-only classes.

### 9.4 Packet handled state

The foundation supplies one wrapper that guarantees handled state is set
exactly once for accepted, rejected, and exceptional paths.

Feature handlers must not independently toggle handled state.

---

## 10. Transport rate limiting

### 10.1 Ownership

Rate limiting is transport protection, not a permission or punishment system.
It belongs to `NetworkRuntimeService`.

State key:

```text
player UUID + message ID
```

State is:

- memory-only;
- server-runtime scoped;
- removed on player logout when practical;
- fully cleared on module shutdown;
- never persisted to SavedData;
- never synchronized to the client.

### 10.2 Policy

Every future C2S spec declares:

- bucket capacity;
- refill amount;
- refill interval;
- optional minimum spacing.

Policies must be reviewed with the owning feature. The Network Foundation does
not invent one universal gameplay rate.

The limiter executes on the server main thread after enqueue and before the
owning Service. This preserves transport ownership without introducing
cross-thread mutable buckets.

### 10.3 Rejection

Rate-limited requests:

- do not call the Service;
- do not mutate state;
- are logged through a suppression-aware warning to prevent log flooding;
- do not automatically kick or ban a player;
- do not create persistent penalties.

Punishment policy requires a separate security or permission design.

---

## 11. Optional-client send contract

### 11.1 Presence check

Before every S2C send:

```text
SimpleChannel.isRemotePresent(player.connection.getConnection())
```

If false, `NetworkSendService.trySendToPlayer` returns a non-exceptional
“not present” result and sends nothing.

The caller owns any command or chat fallback because only the owning feature
understands the response semantics.

### 11.2 No authoritative capability cache

The baseline queries the connection directly. It does not maintain a static
map of client capabilities.

If performance evidence later justifies a cache, it must be:

- derived from the live connection;
- server-runtime scoped;
- non-authoritative;
- cleared on logout and stop;
- safe when absent or stale by defaulting to “do not send”.

### 11.3 Send targeting

The foundation may expose guarded operations for:

- one player;
- a caller-supplied collection of players;
- Forge packet distributors where each recipient is capability-filtered.

It must not decide which players are business recipients.

---

## 12. Runtime availability and failure behavior

| Condition | Required behavior |
|---|---|
| Registration fails | Fail Mod setup with explicit ID/class diagnostic |
| Registration occurs after freeze | Throw and fail setup/test |
| Runtime network module unavailable | Reject application message; do not call stale service |
| Wrong direction | Reject before enqueue |
| Sender missing for C2S | Reject before enqueue |
| Malformed payload | Reject before Service |
| Rate exceeded | Drop before Service with suppressed warning |
| Owning feature module unavailable | Reject with module-unavailable diagnostic |
| S2C remote channel absent | Return “not sent”; no packet emission |
| Handler throws | Contain and log; no transport-thread crash |
| Server stops | Clear runtime state and reject later work |

Logs must contain a non-empty reason and enough identifiers to diagnose the
failure. Logs must not contain secrets or full hostile payloads.

---

## 13. Integration with existing Core Framework

### 13.1 Common setup order

The implementation must preserve this conceptual order:

```text
FMLCommonSetupEvent
    ├── load configuration
    ├── register NetworkRuntimeModule definition
    ├── register other module definitions
    └── enqueueWork
          ├── register and freeze network message table
          └── close Core module registration
```

The exact order inside `enqueueWork` must be deterministic. Failure to freeze
the network table prevents successful setup.

### 13.2 Server start order

```text
ServerAboutToStartEvent
    └── CoreManager.preValidate()

ServerStartingEvent
    ├── DataManager.init(server)
    └── CoreManager.startRuntime()
          ├── NetworkRuntimeModule.init()
          └── later feature modules
```

The Network Foundation does not change the established placement of
`DataManager.init()`.

### 13.3 Server stop order

```text
ServerStoppingEvent
    ├── DataManager.saveAll()
    └── CoreManager.stopRuntime()
          └── NetworkRuntimeModule.shutdown()

ServerStoppedEvent
    └── CoreManager.closeRuntime()
```

Network shutdown does not persist rate or connection state.

### 13.4 Dependency rules

- `network` depends on no feature module.
- A future runtime module that accepts network messages declares `network` as
  a required runtime dependency.
- A module that only provides commands and never uses FR packets does not need
  the network dependency.
- Network Foundation must not depend on PlayerData.
- Future PlayerData synchronization may depend on both `network` and
  `player-data`, but that is a separate task.

---

## 14. API exposure rules

Future modules may depend on approved Network Foundation interfaces:

- registration surface during common setup;
- server dispatch adapter;
- guarded S2C send surface;
- immutable rate policy.

They must not depend directly on:

- raw global `SimpleChannel`;
- mutable registry internals;
- another feature module's packet handler;
- `NetworkRuntimeModule` fields;
- static service state.

The raw channel may remain package-private. This prevents callers from
bypassing presence checks or registration freeze.

---

## 15. Test strategy

### 15.1 Pure contract tests

Automated tests must verify:

- duplicate ID rejected;
- duplicate message class rejected;
- negative ID rejected;
- missing direction rejected;
- late registration rejected;
- freeze is one-way;
- deterministic ledger order;
- exact protocol accepted;
- incompatible protocol rejected;
- server predicate accepts `ABSENT.version()`;
- client predicate rejects absent server;
- vanilla meta-version is not accepted by default;
- bounded string/array/collection reads reject oversize input;
- C2S registration without rate policy rejected;
- S2C registration with a C2S-only policy rejected;
- runtime resolver rejects missing/inactive runtime;
- rate state is cleared on shutdown.

Tests must assert outcomes, not only execute code paths.

### 15.2 Build verification

Required:

```text
gradlew.bat build --console=plain
git diff --check
```

No new test framework dependency is required. Existing project conventions may
use a deterministic `JavaExec` validation entry.

### 15.3 Dedicated Server validation

Required runtime evidence:

1. Dedicated Server starts without loading FontaineRepublic client classes.
2. Channel bootstrap logs channel name, protocol, and message count.
3. Message count is zero for the FR-NET-001 business-free baseline.
4. Server runtime starts and stops twice without duplicate registration.
5. A matching FR client connects.
6. A Forge client without the FR mod connects and receives no FR S2C message.
7. An incompatible FR protocol fixture is rejected.
8. No network, classloading, or stale-runtime exception appears.

Items 6 and 7 may use isolated test installations or a purpose-built test
fixture outside production source. Evidence must identify the exact candidate
commit and runtime directories.

Build success alone is not runtime proof.

---

## 16. Acceptance criteria

FR-NET-001 implementation is acceptable only when all are true:

1. One channel exists at `fontainerepublic:main`.
2. Initial protocol version is `"1"`.
3. Registration occurs once during Mod/Common Setup lifecycle.
4. No registration occurs in server runtime `IModule.init()`.
5. Registry freeze rejects late or duplicate registrations.
6. Message IDs are explicit and append-only.
7. Forge 1.20.1 `FriendlyByteBuf` codecs are bounded.
8. Every message has a fixed direction.
9. Server game-state work runs on the server main thread.
10. C2S rate state is runtime-scoped and non-persistent.
11. S2C sends check live remote channel presence.
12. An absent FR client is allowed and receives no FR packet.
13. An incompatible installed FR client is rejected.
14. Dedicated Server loads no client-only class.
15. No static global player or service cache exists.
16. No business packet or synchronization is included.
17. Network Foundation does not depend on PlayerData or a future feature.
18. Existing Core, DataManager, ModSavedData, and PlayerData behavior remains
    unchanged.
19. Automated contract tests, build, and runtime matrix pass.
20. Implementation receives independent review and Human Approval.

---

## 17. Corrections to Architecture v2.7 examples

This task does not edit the Architecture v2.7 baseline. It records precise
implementation corrections that an eventual architecture revision may
incorporate after separate approval.

| Existing example | FR-NET-001 contract |
|---|---|
| Feature module registers a message inside runtime `IModule.init()` | Register complete message table once during common setup |
| Packet example uses Mojang `Codec`/`RecordCodecBuilder` as SimpleChannel codec | Use bounded `FriendlyByteBuf` encoder/decoder for Forge 1.20.1 |
| `NetworkRegistry.ABSENT.toString()` | Use Forge 47.4.x protocol meta-version `NetworkRegistry.ABSENT.version()` |
| Rate limiting appears on the network IO thread | Keep transport ownership but mutate limiter state on server main thread |
| Generic `PacketHandler` may accumulate all responsibilities | Separate bootstrap, registrar, dispatcher, sender, and runtime state |
| Capability result may be cached without a lifecycle contract | Query live connection by default; any future cache is derived and runtime-scoped |

These corrections preserve the approved high-level architecture:

- server authority;
- one SimpleChannel;
- optional FR client;
- strict version compatibility;
- common packet data and side-specific handlers;
- main-thread business execution.

---

## 18. Rejected alternatives

### 18.1 Register messages during each server start

Rejected because the channel is Mod-lifetime state and repeated integrated
server runs would duplicate registrations.

### 18.2 Use automatic `id++` across distributed module registration

Rejected because registration-order changes can silently remap protocol IDs.

### 18.3 Allow feature modules to send through the raw channel

Rejected because it bypasses optional-client checks and protocol guardrails.

### 18.4 Store client capability in PlayerData

Rejected because capability is connection-derived, transient, and not player
identity.

### 18.5 Accept vanilla clients by default

Rejected because Architecture v2.7 only establishes optional FR Mod support on
a Forge client. Vanilla compatibility requires separate runtime evidence and a
deployment decision.

### 18.6 Ship a framework ping packet

Rejected because an unused production message permanently consumes protocol
surface and does not prove business behavior. Test fixtures may validate
transport without entering the production ledger.

---

## 19. Implementation staging

After design review and a separate Human implementation approval:

### Stage 1 — Pure contracts

- protocol constants;
- message specification;
- registrar validation;
- bounded codec helpers;
- protocol-predicate tests.

### Stage 2 — Forge bootstrap

- SimpleChannel creation;
- registration freeze;
- entry-point common-setup integration.

### Stage 3 — Server runtime

- NetworkRuntimeModule;
- resolver;
- dispatcher;
- rate limiter;
- guarded sender.

### Stage 4 — Verification

- deterministic automated validation;
- build;
- Dedicated Server and optional-client matrix;
- independent repository review.

No stage introduces a business packet.

---

## 20. Review gate

Before implementation, an independent Reviewer must verify:

1. compatibility with Forge 1.20.1 / 47.4.18;
2. consistency with Architecture v2.7 and DEC-003;
3. compatibility with FR-CORE-001 lifecycle ownership;
4. absence of business networking;
5. absence of static player/service caches;
6. optional-client predicate correctness;
7. registration timing and restart safety;
8. bounded codec and main-thread dispatch rules;
9. testability of the runtime acceptance matrix;
10. whether any correction requires a separate Architecture v2.7 amendment.

Review does not authorize implementation. Human approval is required after the
design review.

---

## 21. Primary references

- Forge SimpleImpl documentation:
  <https://docs.minecraftforge.net/en/1.20.x/networking/simpleimpl/>
- Forge Mod lifecycle documentation:
  <https://docs.minecraftforge.net/en/1.20.x/concepts/lifecycle/>
- Project Architecture v2.7:
  `docs/architecture/architecture.md`
- DEC-003 Network Foundation Boundary:
  `docs/decisions/DEC-003-network-boundary.md`
- FR-CORE-001 architecture:
  `docs/architecture/fr-core-001-a-architecture.md`
