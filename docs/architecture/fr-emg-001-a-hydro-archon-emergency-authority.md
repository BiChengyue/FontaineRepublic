# FontaineRepublic Hydro Archon Emergency Authority Infrastructure v1.0

> **Task ID:** FR-EMG-001-A
> **Revision:** FR-EMG-001-A-FIX-01 (security-review corrections)
> **Status:** Architecture Design — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Shared emergency-authority infrastructure for all state-power modules
> **Dependency:** FR-CORE-001, FR-DATA-001, FR-NET-001, FR-CMD-001, FR-INST-001-A/B, FR-ECO-001-A/B
> **Downstream alignment:** FR-ECO-001-C (candidate only; blocked on this
> architecture and not an input dependency)
> **Implementation Status:** Not authorized

---

## 1. Purpose

This document designs a shared emergency-authority infrastructure for all
current and future FontaineRepublic state-power modules. It defines:

- the Hydro Archon authority-verification boundary;
- a shared emergency action contribution contract;
- the typed target model;
- the two-step preview/confirm workflow;
- business mutation rules;
- the permanent audit envelope;
- the mandatory atomicity analysis against the current Core;
- the offline notification boundary;
- the command boundary;
- availability and failure modes;
- the security and abuse threat model.

This document is architecture design only. It does not authorize
implementation and does not approve specific political, legal, economic, or
gameplay behavior.

---

## 2. Human-Confirmed Principles

1. Every state-power module MUST preserve a controlled Hydro Archon emergency
   action interface.
2. Purposes include: debugging; technical correction; compensation; disaster
   relief; declared or actual emergency response.
3. Player targets MAY be offline.
4. Authorized actors are: the configured Hydro Archon Minecraft UUID; the
   dedicated server console, recorded as `SERVER_CONSOLE`.
5. Ordinary OP level is NEVER sufficient.
6. The interface is independent of: physical institution availability; player
   presence; optional client mod; ordinary official role or office; normal
   institution workflow.
7. Every action requires an explicit category and mandatory reason.
8. Every action requires permanent audit.
9. Each business module contributes only explicit emergency actions.
10. No generic NBT, repository, reflection, command-execution, or
    arbitrary-data mutation interface is allowed.

---

## 3. State-Power Module Scope

The architecture MUST support at least:

| Module | Emergency-mutable state |
|---|---|
| Parliament | Proposal/law records |
| Government | Administrative records |
| Court / Justice | Case records |
| Central Bank / Economy | Digital account balances, treasury (see FR-ECO-001-C) |
| Citizen and state registry | Identity registry records |
| Election | Election records |
| Land and City state registries | Parcel/city records |
| Future state-power modules | Authoritative state decisions |

Core, Network, Command, and PlayerData are infrastructure dependencies, NOT
automatically emergency-mutable business targets.

Each future module requires a separately reviewed emergency-action catalogue.
Examples in this document do not approve any specific behavior.

---

## 4. Authority Identity

The authority-verification boundary:

- Hydro Archon identity uses the Minecraft UUID;
- player name and display name are NEVER authority keys;
- UUID changes are not silently accepted (a configured-UUID change is a
  controlled configuration event, itself audited);
- ordinary OP and generic administrator permissions do NOT grant authority;
- the server console has a distinct actor identity (`SERVER_CONSOLE`);
- console actions never impersonate the Hydro Archon UUID;
- authority configuration changes are themselves audited;
- client state or packets cannot assert emergency authority.

The configured Hydro Archon UUID belongs to a shared emergency-authority
configuration owner. Merely editing a Forge configuration file is not an
authorized change. The owner loads a candidate value before emergency service
activation and compares its canonical digest with the last accepted value and
configuration revision stored in the shared emergency namespace.

The lifecycle is:

1. **First bootstrap:** no player UUID has emergency authority. Only the real
   local Dedicated Server console may establish the first UUID. The bootstrap
   event, old value `UNSET`, new UUID, server/world identity, timestamp, and
   configuration revision are durably recorded before the authority becomes
   usable.
2. **Controlled change:** an already authorized Hydro Archon or the real local
   Dedicated Server console previews and confirms a configuration-change
   request through the same token and shared-attempt journal rules. The new
   UUID is staged for the next server runtime; the running authority snapshot
   remains immutable.
3. **Next start:** the staged value is accepted only when its digest and
   revision match the durable configuration-change receipt. A mismatch,
   missing receipt, rollback, or manual file drift fails closed: no player UUID
   receives emergency authority. The real local Dedicated Server console may
   inspect and repair the condition through an audited recovery action.

Initial bootstrap, change, rejection, drift detection, and recovery records
are authoritative records in the shared emergency attempt/configuration
journal defined in Section 10. They are not business-module receipts.

`SERVER_CONSOLE` means only the server's authenticated, local Dedicated Server
console source for the current server instance. A source classifier MUST
explicitly reject RCON, command blocks, functions, integrated-server hosts,
generic entity-less sources, proxied sources, and ordinary OP. Testing
`source.getEntity() == null` or permission level alone is never sufficient.

This document does NOT design the entire future permission system; it defines
only the boundary required by this function.

---

## 5. Shared Emergency Action Contribution Contract

Business modules contribute explicit emergency-action descriptors through a
bounded, OPEN-to-FROZEN registry conceptually modeled on the existing command
contribution contract (FR-CMD-001-A; CommandContributionRegistry). Each
descriptor contains at least:

| Descriptor field | Meaning |
|---|---|
| Stable module id | Owning module |
| Stable action id | Unique within module |
| Action version | Semantic version of the action |
| Supported target type | PLAYER_UUID, CASE_ID, LAW_ID, ELECTION_ID, OFFICE_ID, PARCEL_ID, CITY_ID, module-owned id |
| Parameter schema | Bounded, explicit, typed |
| Required emergency category | Allowed categories |
| Stable provider identity | Immutable provider id and implementation digest/version |
| Runtime provider resolver | Resolves the current ACTIVE provider without capturing it |
| Preview/validation capability | Typed, side-effect-free validation and immutable plan |
| Mutation capability | Typed business-Service entry that commits one owned snapshot |
| Affected business revision source | Which revision(s) the action touches |
| Audit-data projection | What the module projects into the audit envelope |
| Offline-notification policy | Pending-notification behavior |
| Availability state | Whether the action is available |

Descriptor schemas and requests MUST be canonical and bounded:

- module id, action id, action version, provider identity, target type, and
  every parameter type have stable canonical encodings;
- strings define normalization, character policy, and maximum encoded length;
- collections define maximum entry count, deterministic ordering, and maximum
  total encoded size;
- parameter digests are computed over the canonical encoding, never map
  iteration order or display text;
- `module-owned id` is not an open escape hatch: every new target type requires
  a stable type id, a bounded codec, and separate catalogue review.

Rules:

- the shared infrastructure MUST NOT depend directly on every business module
  (dependencies point from business modules toward the shared contract);
- business modules MUST NOT duplicate: Hydro Archon UUID checking; console
  actor handling; confirmation-token generation; shared audit envelope
  validation; generic command-source authorization;
- dependencies MUST remain acyclic;
- a descriptor contains immutable metadata and a stable runtime resolver only;
  it MUST NOT capture a per-server module, Service, repository, world, player,
  command source, or mutable action implementation;
- preview returns a typed immutable plan; mutation accepts only the typed plan
  plus the confirmed request envelope and returns a typed immutable result;
- contribution APIs MUST NOT accept or expose `CompoundTag`, NBT paths,
  repositories, reflection handles, arbitrary Java callbacks, executable
  commands, shell/file paths, serialized object blobs, or unbounded maps;
- registration rejects duplicate `(module id, action id, version)`, duplicate
  provider identity, invalid schemas, unbounded fields, and providers whose
  declared owner does not match the business module;
- every contributed action still requires its own independently reviewed
  emergency-action catalogue entry; registry acceptance is not policy approval;
- final Java interfaces are NOT defined here — pseudocode and contracts are
  normative until implementation design is separately reviewed.

---

## 6. Target Model

Supported typed stable targets:

- offline or online player UUID;
- case id;
- law id;
- election id;
- office id;
- parcel id;
- city id;
- other module-owned stable identifiers.

For player targets:

- UUID is the only accepted target input under the current approved
  `PlayerDataService` API;
- player-name input remains disabled until a separately approved PlayerData
  lookup API provides a historical name index, explicit ambiguity/reuse
  detection, bounded lookup, and UUID result;
- after that API is approved, name is lookup input only and
  `PlayerDataService` resolves it to UUID; no emergency component may scan or
  access `PlayerDataRepository`, NBT, Capability, the online player list, or a
  fabricated `ServerPlayer`;
- ambiguous, reused, or unknown names always fail safely;
- final confirmation binds the resolved UUID;
- player logout between preview and confirmation does NOT invalidate an
  otherwise valid offline-safe action;
- the target business revision MUST still be revalidated at confirm.

Business notification state is NOT placed inside PlayerData
(FR-DATA-001 Section 2.4; FR-ECO-001-C Section 5).

---

## 7. Emergency Categories

At minimum:

| Category | Purpose |
|---|---|
| `DEBUG` | Debugging |
| `CORRECTION` | Technical correction |
| `COMPENSATION` | Compensation |
| `DISASTER_RELIEF` | Disaster relief |
| `EMERGENCY_RESPONSE` | Declared or actual emergency response |

Every action requires: one category; a non-empty bounded reason; actor
identity; a stable target; an explicit action id; parameters.

A category does NOT by itself authorize an action.

---

## 8. Preview and Confirmation

### 8.1 Preview Step

Preview MUST:

- resolve actor and target;
- validate action availability;
- validate parameters;
- read the relevant business revision;
- present the affected state and the intended result;
- create a server-side unguessable confirmation token.

### 8.2 Token Requirements

- 30-second default expiry;
- single use;
- bound to actor, module, action, target, category, reason, parameters, and
  relevant revisions;
- no authority stored on the client;
- invalidated on logout for player actors;
- invalidated on server stop;
- console token bound to the current server-instance epoch and the strict local
  Dedicated Server console source classification from Section 4;
- parameter or revision changes require a new preview;
- duplicate or concurrent confirmation cannot apply the action twice.

The token table is server-runtime memory owned exclusively by the shared
emergency service on the logical server main thread. It is neither client state
nor SavedData. Each entry carries a cryptographic digest of a high-entropy
token, the server-instance epoch, the complete canonical request binding, an
expiry, and exactly one state:

```text
ISSUED --atomic claim--> CLAIMED --every terminal outcome--> CONSUMED
   |                         |
   +--expiry/stop/logout-----+--never returns to ISSUED
```

The plaintext token is presented once and never persisted or logged. A digest
stored later in an audit record is evidence only and never reconstructs or
reactivates a token. Entries from another server-instance epoch are invalid
even if a stale digest or plaintext value reappears.

At confirm, the service atomically changes `ISSUED` to `CLAIMED` **before**
final validation or any business call. `CLAIMED` and `CONSUMED` reject all
other confirmations. The service then revalidates the trusted actor/source,
server epoch, descriptor/provider identity and version, canonical target,
category, normalized reason, canonical parameters, availability, and all bound
revisions. Success, validation rejection, provider failure, commit failure, and
unexpected exception all end in `CONSUMED`; a claimed token is never returned
to `ISSUED` and is never automatically retried. The corresponding success or
failure audit path is selected under Section 10.

Minecraft command callbacks are synchronous main-thread entry points, but the
service enforces the owner-thread and state-transition rules independently so
future server entry points cannot bypass at-most-once behavior.

### 8.3 Confirm Step

Confirm MUST revalidate everything at the final mutation boundary: actor,
action availability, parameters, target, category, reason, business revision,
provider identity, server-instance epoch, and claimed-token validity. No client
packet may confirm authoritatively.

---

## 9. Business Mutation Rules

Each module retains ownership of its data, invariants, validation, revisions,
persistence, and reversible/compensating action rules. Emergency authority
never permits raw repository access by callers.

Requirements:

- a failed mutation changes no business data;
- a failed mutation does not increment business revision;
- success increments relevant revisions exactly once;
- the business repository constructs one replacement namespace snapshot that
  contains the business state, authoritative success receipt, and any required
  pending offline notification;
- that replacement snapshot is submitted exactly once through the durable
  commit boundary analyzed in Section 11;
- commit failure publishes no newer repository model, business revision,
  receipt, notification, synchronization delta, chat output, or success result;
- successful commit publishes all three components together and increments
  relevant business/store revisions exactly once;
- an emergency action cannot pretend to be a normal official action (its
  audit actor remains `HYDRO_ARCHON` or `SERVER_CONSOLE`);
- where reversal is supported, it creates a new linked emergency action and
  NEVER deletes the original audit record;
- actions that cannot safely apply offline MUST declare this explicitly and
  reject offline use rather than loading or fabricating entities;
- player-targeted state actions SHOULD be offline-safe whenever their data is
  SavedData-backed.

Preview and mutation implementations MUST NOT perform external side effects.
Success feedback, optional chat notification delivery, synchronization, index
projection, and other observable effects occur only after the durable business
snapshot commit succeeds. A pending offline notification is authoritative
state in that snapshot; live delivery is only a projection of it.

---

## 10. Permanent Audit

### 10.1 Audit Envelope

A common immutable audit envelope contains at least:

| Field | Meaning |
|---|---|
| emergency action id | Unique |
| timestamp | Server-assigned |
| actor type | `HYDRO_ARCHON` or `SERVER_CONSOLE` |
| actor UUID | When applicable |
| module id | Owning module |
| action id and version | Stable action reference |
| target type and stable target id | Typed target |
| category | One of Section 7 |
| mandatory reason | Non-empty bounded |
| normalized parameters or safe parameter digest | Bounded |
| business revision before and after | Revision change |
| state summary before and after | Safe summary |
| success/failure | Mutation result |
| failure reason | When applicable |
| related or reversal action id | Link |
| server/world identity | Instance identity |
| confirmation-token identity or digest | Token reference |

### 10.2 Audit Rules

- attempts are distinguished from successful mutations;
- no business transaction id is fabricated for failed actions;
- the record is append-only;
- it is NOT pruned with ordinary module history (FR-ECO-001-C Section 15);
- it supports future transfer to an Audit module without losing evidence;
- secret case, citizen, or financial content is NOT leaked into public
  output.

The authoritative receipt is private server evidence, not command output. Each
action descriptor classifies every parameter and projected field as `PUBLIC`,
`AUTHORIZED_SUMMARY`, `SECRET_DIGEST_ONLY`, or `NEVER_RECORD`. The shared
validator rejects an unclassified field. Mandatory reason text is normalized,
bounded, private by default, and represented in ordinary projections by an
action id plus safe category/summary or digest. Tokens, raw NBT, credentials,
private case contents, full financial details, filesystem paths, and raw
exception text are never recorded in public output or ordinary logs.

`inspect` uses this closed matrix:

| Caller | Output |
|---|---|
| Hydro Archon UUID | Authorized redacted projection only; secrets remain digest-only unless a later reviewed module policy explicitly permits a named field |
| Real local Dedicated Server console | Same authorized redacted projection; no automatic full-NBT or secret dump |
| Ordinary OP / RCON / command block / function / player | Denied |
| Public query or optional client | Denied; a separately approved public audit projection would be required |

Receipts and journal entries use monotonically increasing sequence numbers,
previous-record digests, canonical envelope digests, schema versions, and
server/world identity so deletion, reordering, or replacement is detectable
during reconciliation. This is tamper evidence, not a claim that a hostile
filesystem administrator can be cryptographically prevented from rewriting
all local state.

Permanent evidence MUST NOT be one unbounded list. The persistence design must
define bounded append-only segments, bounded indexes, segment digests, and a
durable archival/export handoff. Closing a segment never deletes or rewrites
its evidence. Ordinary module-history pruning cannot remove receipt segments.
If the active segment, index, or archival boundary cannot accept another
record, new emergency mutations fail closed.

### 10.3 Audit Ownership

Audit authority is deliberately split by outcome, without duplicate authority:

1. The shared journal is authoritative for the fact that a request/confirmation
   attempt occurred and for configuration events. Before any business provider
   is entered, the shared service durably appends a bounded attempt-intent entry
   containing the attempt id, safe request digest, trusted source category,
   time, and `OUTCOME_NOT_YET_CLAIMED`. This entry never claims business success.
2. A **successful business mutation** has exactly one authoritative success
   receipt, owned by the business module and committed in the same replacement
   namespace snapshot as the business state and pending offline notification.
3. A request rejected before a business snapshot can be committed — including
   unauthorized source, unknown module/action, invalid or replayed token,
   unavailable/corrupt provider, preview rejection, configuration event, or
   commit failure — appends a terminal failure entry linked to its attempt
   intent in the shared emergency namespace. It never fabricates a business
   transaction id or claims that business state changed.
4. The shared emergency module also maintains a rebuildable query index and
   redacted replicated envelopes for successful receipts. Those projections
   are derived and non-authoritative.

Preview syntax errors that never reach the shared service are command-parser
events, not emergency attempts. Every request that reaches shared authority
verification receives an emergency action/attempt id. A confirm that claims a
token always produces either the business-owned success receipt or a
shared-journal failure entry. If the applicable authoritative audit store
cannot pass its durable precondition, the operation fails closed.

The shared attempt intent is committed before provider invocation, so a crash
may leave an explicitly incomplete attempt but cannot leave an unrecorded
business call. Reconciliation links a later business success receipt to that
attempt and derives the terminal success projection. If terminal failure
append itself becomes impossible after the intent was committed, the journal
retains the incomplete intent, the emergency service enters unavailable state,
and recovery must resolve it before further mutations. It must never rewrite
the intent into a fabricated result.

### 10.4 Receipt Provider and Reconciliation Contract

Each business module that contributes emergency actions also registers an
immutable receipt-provider identity and a stable runtime resolver. When the
current business module is ACTIVE, the resolver returns a read-only provider
Service with operations equivalent to:

```text
receiptWatermark() -> (providerId, receiptSchema, highestSequence, segmentDigest)
readReceiptPage(afterSequence, boundedLimit) -> immutable redacted envelopes
verifyReceipt(actionId, envelopeDigest) -> bounded verification result
```

The provider owns decoding and validation of its receipt schema. It returns
typed immutable projections, never NBT, repositories, mutable collections, or
business models. Page count, encoded bytes, time budget, and sequence range are
bounded. The shared emergency service never enumerates `ModSavedData`
namespaces and never calls `DataManager.getModuleData()` for another module.

Reconciliation begins only after the shared service and the relevant provider
are ACTIVE. It compares the provider watermark with the shared derived index,
imports missing redacted envelopes in sequence order, verifies digest-chain
continuity, and records a reconciliation checkpoint in the shared namespace.
The index publishes, per provider, `COMPLETE_THROUGH(sequence)`, `INCOMPLETE`,
or `TAMPER_OR_CORRUPTION_DETECTED`. If a module is unavailable, paging fails,
or a chain mismatch is found, the old verified index remains readable with an
explicit incomplete watermark; it must never claim completeness. `inspect`
reports that bounded state and does not fall back to raw storage.

Provider descriptors are Mod-lifetime frozen metadata. Provider Services and
the token table are server-runtime state resolved on each operation. Neither a
command-resource reload nor a later server start may reuse an old runtime
Service instance.

---

## 11. Atomicity Analysis (Mandatory)

### 11.1 Evidence from Current Core

- `ModSavedData` is a single `SavedData` container
  (`DATA_NAME = "fontainerepublic"`) holding a `Map<String, CompoundTag>`
  partitioned by module namespace (`ModSavedData.java`).
- `DataManager` is the only persistence gateway:
  `getModuleData(name)` / `putModuleData(name, tag)`; `putModuleData`
  replaces one namespace tag and calls `setDirty()` per call
  (`DataManager.java`).
- `putModuleData` returns `void`, silently does nothing when `savedData` is not
  initialized, and exposes no synchronous disk-write result. `saveAll()` also
  only calls `setDirty()`; actual `SavedData.save(root)` serialization occurs
  later under Minecraft's storage lifecycle.
- There is NO batch, transaction, or cross-namespace atomic commit API.
- `PlayerDataRepository` establishes the single-writer pattern: one namespace
  has exactly one owner. Its `commit()` calls a `store.save()` adapter before
  updating the repository map, but the production adapter only calls the
  `void` in-memory `DataManager.putModuleData`; it does not prove a durable
  save, report storage failure, or roll back a later disk-write failure.

### 11.2 Cross-Namespace Atomicity Conclusion

With the current API, two independent `putModuleData` calls (business
mutation and audit record) can be separated by an autosave, leaving the
business mutation durable while the audit record is not, or vice versa.
Cross-namespace atomicity therefore CANNOT be established from current code.
This is recorded as a design constraint, not silently assumed away.

Placing business state, receipt, and notification in one replacement namespace
tag does provide **namespace-snapshot cohesion**: they are replaced together in
the in-memory root and are later serialized together. It does **not** establish
that a confirm response represents durable disk commitment. Atomic visibility,
crash-consistent co-serialization, and acknowledged durability are distinct
properties and MUST NOT be conflated.

### 11.3 Option Comparison

#### Option A — Shared emergency module owns audit; business module owns mutation

| Dimension | Assessment |
|---|---|
| Mutation atomicity | None across namespaces with current API |
| Save-failure behavior | Business mutation may persist without audit |
| Crash behavior | Split brain (mutation without audit, or audit without mutation) |
| Revision ownership | Business module (unchanged) |
| Module dependency impact | Shared module depends on business audit projection only |
| Recovery procedure | Reconciliation through registered receipt providers; never a raw NBT scan |
| Audit authority | Shared module (centralized) |
| Implementation complexity | Low, but unsafe without Core enhancement |

Requires both cross-namespace commit semantics and acknowledged durability to
be acceptable. A batch method that merely performs several map replacements
and one `setDirty()` is insufficient.

#### Option B — Business module persists authoritative receipt atomically; shared module indexes

| Dimension | Assessment |
|---|---|
| Mutation atomicity | Namespace-snapshot cohesion: business mutation + success receipt + pending notification are submitted in ONE replacement tag |
| Save-failure behavior | Current API reports no durable save result; fail-closed behavior and rollback after later storage failure are unproven |
| Crash behavior | Last successfully serialized namespace contains all three or none; an acknowledged in-memory change may still be lost before serialization |
| Revision ownership | Business module (unchanged) |
| Module dependency impact | Shared module depends on descriptors + receipt schema only; acyclic |
| Recovery procedure | Reconcile through bounded registered receipt providers after they become ACTIVE; never scan raw NBT |
| Audit authority | Business module receipt (authoritative); shared index (derived, non-authoritative) |
| Implementation complexity | Medium for cohesion, plus a blocking durable-commit design dependency |

#### Option C — Shared transaction coordinator mutates root ModSavedData atomically

| Dimension | Assessment |
|---|---|
| Mutation atomicity | Would be atomic if the coordinator owned the whole root tag |
| Save-failure behavior | Unresolved unless the coordinator owns an acknowledged durable commit boundary |
| Crash behavior | Coordinator-controlled only if it owns serialization, commit acknowledgement, and recovery |
| Revision ownership | Conflict: revisions live inside module namespaces |
| Module dependency impact | Violates single-writer per namespace and module isolation (ADR-009); every module would need to route persistence through the coordinator |
| Recovery procedure | New coordinator recovery path |
| Audit authority | Coordinator (centralized) |
| Implementation complexity | High; requires invasive Core redesign |

Rejected: it violates ADR-009 module isolation and the established
single-writer pattern.

### 11.4 Durable Commit Gate

Before implementation, a separately reviewed persistence design MUST provide
one of these equivalent guarantees:

- a Core/repository durable-commit or flush contract that accepts an owned
  copied namespace snapshot, runs on the logical server owner thread, returns
  an explicit success/failure result, and publishes no newer repository model
  or external effect until durable success; or
- a write-ahead journal (WAL) with equally explicit fsync/commit marker,
  replay, rollback, corruption, world-identity, compaction, and recovery
  semantics.

The design must specify copy-in ownership, how a failure leaves the prior
authoritative snapshot published, what happens when persistence is
uninitialized or the server is stopping, and how restart recovery determines
the last committed record. A `putModuleDataBatch` that only changes memory and
calls `setDirty()` does not satisfy this gate.

### 11.5 Conclusion

Option B remains the preferred **cohesion and ownership model**: each business
module owns one snapshot containing business state, authoritative success
receipt, and pending notification, while the shared index remains derived. It
does not by itself provide acknowledged durability with the current Core API.

Therefore durable commit/flush or WAL architecture is a **blocking
Core/persistence design dependency before implementation**. FR-EMG-001-A may
proceed through architecture review with this explicit gate, but no
implementation may claim successful durable emergency mutation until the gate
is approved and met.

---

## 12. Offline Notification Boundary

For player-targeted actions:

- the responsible business module records pending-notification state in the
  same durable replacement snapshot as the successful business mutation and
  authoritative receipt (not PlayerData);
- next login may display a safe summary;
- the shared emergency audit is NOT a player notification database;
- notifications do not expose restricted information;
- acknowledgement does not change the underlying emergency audit;
- acknowledgement is a later business-module commit that changes only the
  notification delivery state and its own notification revision;
- absence of the optional client mod does not prevent notification
  (FR-NET-001-A Section 11 chat fallback).

---

## 13. Command Boundary

Common command shape:

- `/fr admin emergency <module> <action> ...`
- `/fr admin emergency confirm <token>`
- `/fr admin emergency inspect <actionId>`

Exact syntax remains subject to command architecture review
(FR-CMD-001-A Section 7).

`admin` is reserved by the existing `CommandContributionRegistry`; therefore
this command is not a normal feature contribution claiming a second `admin`
literal. Before implementation, FR-CMD alignment MUST approve a shared
foundation-owned admin child adapter (or another non-colliding syntax) that
preserves one `/fr` root. The adapter contains syntax and a stable runtime
resolver only. At every execution it resolves the current ACTIVE shared
emergency Service and never captures a per-server Service while building the
tree.

Requirements:

- generic OP gates are insufficient;
- commands call the shared authority service, never business repositories;
- no client packet performs authoritative confirmation;
- the server console path uses the same validation and audit flow;
- RCON, command blocks, functions, integrated-host sources, and generic OP
  follow no console fallback and are rejected by the Service even if parsing
  reaches the callback;
- no institution building is required for emergency actions (independent of
  physical institution availability).

---

## 14. Availability and Failure Modes

| Condition | Required behavior |
|---|---|
| Emergency infrastructure unavailable | Fail closed; no emergency mutation |
| Target business module unavailable | Fail closed for that module's actions |
| PlayerData lookup unavailable | Fail closed for player-target actions; non-player targets unaffected |
| Business SavedData corrupt | Fail closed; load-time reconciliation detects invariant violation |
| Audit persistence unavailable | Fail closed; no provider invocation before a durable shared attempt intent, and no successful mutation without its durable business receipt |
| Server stopping | Invalidate all pending confirmation tokens; no new previews |
| Target offline | Allowed for SavedData-backed offline-safe actions only |
| Target logs in or out during confirmation | Confirm still revalidates target revision; offline-safe actions proceed; non-offline-safe actions re-preview |
| Institution facilities destroyed | Does not affect emergency actions (break-glass) |
| Command-resource reloads | Fresh command nodes resolve the current Service; Mod-lifetime descriptors remain frozen and the server-runtime token table remains in the Service for independent lifecycle reasons |
| Receipt provider unavailable during reconciliation | Preserve the last verified index, mark provider `INCOMPLETE`, expose the watermark, and never scan raw NBT |
| Durable commit/flush or WAL unavailable | Implementation remains blocked; no emergency mutation may report success |
| Authority configuration drift | No player UUID authority; record/durably resolve through the real local Dedicated Server console recovery path |

Fail closed whenever authority, target, mutation, or audit integrity cannot
be established. The feature remains available when only the normal
institution workflow is unavailable.

---

## 15. Security and Abuse Review

| Threat | Mitigation |
|---|---|
| UUID spoofing | Actor UUID from authenticated server source; name never an authority key |
| OP escalation | Ordinary OP never sufficient; authority config audited |
| Console impersonation | Strict real-local-Dedicated-console classifier; RCON, command blocks, functions, integrated host, entity-less sources, and OP are rejected |
| Replay | Server-epoch-bound `ISSUED -> CLAIMED -> CONSUMED` token; second claim rejected; expiry 30 s |
| Confirmation-token theft | High-entropy plaintext presented once; digest-only runtime storage; actor/source and complete canonical request bound |
| Parameter substitution | Confirm compares canonical request digest, provider identity/version, and every normalized parameter against the claimed token |
| Stale revision | Confirm revalidates business revision; changed revision requires new preview |
| Duplicate submission | Atomic claim precedes validation and mutation; every outcome permanently consumes the token |
| Concurrent confirmation | Token table is main-thread-owned and state checked; `CLAIMED`/`CONSUMED` reject duplicates independently of command gates |
| Offline-name ambiguity | Name input disabled until an approved historical ambiguity-detecting PlayerData lookup exists; confirmation always binds UUID |
| Raw NBT backdoor | Typed immutable canonical requests/results only; registry rejects NBT, repository, reflection, command, object-blob, and arbitrary payload surfaces |
| Module action-id collision | Registry enforces unique module/action/version plus stable provider identity |
| Audit deletion or tampering | Append-only segmented receipts/journal, monotonic sequence, digest chain, provider watermark, and reconciliation detection |
| Secret-data leakage | Mandatory field classification, private receipt, fixed inspect matrix, digest-only secret projection, and safe logging |
| Action-provider replacement during reload | Frozen metadata binds provider identity/version; runtime provider is freshly resolved and rechecked at confirm |
| Authority configuration file drift | Compare candidate digest/revision to the last durable config receipt; fail closed until audited console recovery |
| Attempt omitted because business module is unavailable | Shared emergency namespace is authoritative for pre-commit failures and configuration events |
| Persistence acknowledged before durability | Implementation blocked until Section 11.4 durable commit/flush or WAL gate is met |
| Emergency interface as normal gameplay path | Break-glass semantics; audit distinguishes attempts; category/reason mandatory |

---

## 16. Dependency Graph

```text
Mod-lifetime definition path
  Business compiled contribution
      -> Frozen Emergency-Action Registry (metadata + stable resolver only)

Server-runtime module dependency path (A -> B means A requires B)
  Business state-power module -> Shared Emergency Authority module
  Shared Emergency Authority module -> Core persistence contract
  Shared Emergency Authority module -> optional approved PlayerData lookup API
  Shared Emergency Authority module -X-> any business module

Execution path
  Fresh /fr admin emergency command adapter
      -> runtime-resolve current Shared Emergency Authority Service
      -> frozen descriptor -> runtime-resolve current ACTIVE business provider
      -> provider commits one owned business snapshot
           [business state + authoritative success receipt + notification]
      -> shared index imports a bounded redacted receipt projection later

Failure/configuration path
  Shared Emergency Authority Service
      -> shared authoritative attempt/configuration journal
```

The shared module has no required ModuleDefinition edge to a business module;
business providers depend on the shared contract and register resolvers during
the Mod-lifetime window. Calling a provider selected from that frozen registry
is runtime inversion through a stable contract, not a reverse lifecycle edge.
Reconciliation waits for provider availability and carries an explicit
watermark. Core, Network, Command, and PlayerData remain infrastructure
dependencies, not emergency-mutable business targets.

---

## 17. Impact on FR-ECO-001-C

FR-ECO-001-C MUST:

- reference this shared authority contract;
- define Economy-specific ISSUE and RECLAIM actions only;
- not duplicate UUID checks, console handling, or confirmation-token logic;
- define the Economy mutation and the Economy audit projection;
- remain blocked until FR-EMG-001-A is approved.

FR-ECO-001-C is NOT created or modified by this task.

---

## 18. Non-Goals

This document does NOT design:

- specific parliamentary powers;
- specific executive emergency law;
- legal doctrine;
- court appeal policy;
- election outcome policy;
- monetary policy;
- land redistribution policy;
- normal administrator permissions;
- GUI;
- client authority;
- arbitrary data editing;
- implementation code.

---

## 19. Open and Blocking Questions

Implementation-blocking dependencies:

1. **Durable persistence:** approve the Section 11.4 durable commit/flush or WAL
   contract. Current `DataManager`/`ModSavedData` cannot report acknowledged
   durable success or storage failure.
2. **Command alignment:** approve how the shared emergency adapter attaches
   beneath reserved `admin` without violating FR-CMD-001 ownership, rebuild,
   and execution-time runtime-resolution rules.
3. **Audit storage implementation design:** approve concrete bounded segment,
   archive/export, corruption recovery, and capacity values satisfying Section
   10.2 before any permanent journal is implemented.
4. **Authority configuration integration:** approve the concrete server-config
   storage and startup ordering that implements Section 4 bootstrap, staged
   change, digest/revision comparison, and drift recovery.

Non-blocking policy questions for later separately scoped designs:

1. Whether additional emergency categories are needed beyond Section 7.
2. Whether a future approved PlayerData historical lookup permits player-name
   input; UUID-only targeting remains fully usable meanwhile.
3. Whether a future Audit module receives the immutable archived segments;
   transfer must preserve existing authority, digests, and evidence.

---

## 20. Review Gate

This document is architecture design only. It does not constitute
architecture approval, Human Approval, or implementation authorization.
Independent security architecture review is requested after completion.
