# FontaineRepublic Safe Player Directory Architecture v1.0

> **Task ID:** FR-DATA-003-A
> **Status:** Architecture Candidate - Pending Independent Review and Human Approval
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Depends on:** FR-DATA-001, FR-ID-001-A, FR-CMD-001-A
> **Implementation Status:** Not authorized

---

## 1. Purpose

This document defines a server-authoritative, exact-name player directory for
FontaineRepublic. Its narrow purpose is to resolve a server-verified Minecraft
game-name alias to one Minecraft UUID when, and only when, the result is safe
and unambiguous.

The primary future consumer is the Economy lookup path:

```text
exact player name
    -> PlayerDirectoryService
    -> Minecraft UUID
    -> SubjectRegistryService
    -> SubjectId and RegistryNumber
    -> Economy personal account
```

The directory does not own subjects, public registry numbers, accounts,
balances, citizenship, offices, permissions, or player-profile presentation.
It never makes a name an identity key.

This document defines architecture only. It creates no Java API, migration,
command, packet, GUI, or runtime behavior.

---

## 2. Normative Identity Boundary

### 2.1 Authority matrix

| Concern | Authority |
|---|---|
| Authenticated player identity | Minecraft UUID observed by the logical server |
| Base player record and verified last-known game name | PlayerData |
| Exact current/historical alias classification | Player directory inside `player-data` |
| Subject identity and public registry number | FR-ID |
| Account, balance, ledger, and financial status | Economy |
| Display name, locale, description | PlayerProfile presentation only |
| Online command suggestions | Vanilla server command source projection |

Minecraft UUID remains the sole player identity. A game name is a mutable
lookup alias. A normalized name is only an index key. A profile display name is
never valid directory input.

No consumer may persist a game name as a foreign key, infer identity from a
failure result, or treat successful name resolution as authentication of the
command actor.

### 2.2 Verified-name source

The directory accepts alias observations only from the same authenticated
logical-server login path that supplies `PlayerDataService.recordLogin`. It
must not accept names from:

- client packets or phone state;
- chat text, display names, scoreboards, nicknames, item NBT, or command labels;
- offline-mode guesses or external Mojang/Microsoft requests;
- Economy, FR-ID, Citizen, Enterprise, or institution repositories;
- administrative raw-NBT edits.

The term `current` in this document means the latest name verified by this
server for a known UUID. It does not claim real-time global ownership of a
Minecraft name while that player is offline.

### 2.3 Business isolation

The player directory contains no:

- SubjectId or RegistryNumber;
- balance, account, transaction, supply, or notification;
- citizenship, enterprise membership, office, court, land, or permission;
- profile display name, locale, or description;
- IP address, session credential, or device identifier.

Future modules depend only on the read-only `PlayerDirectoryService`. They do
not access PlayerData Repository, NBT codec, or directory indexes.

---

## 3. Name Validation and Normalization

### 3.1 Accepted syntax

An exact lookup input is valid only when all of the following hold:

- length is 1 through 16 ASCII characters;
- every character is `A-Z`, `a-z`, `0-9`, or `_`;
- there is no leading, trailing, or embedded whitespace;
- there are no formatting codes, control characters, Unicode confusables,
  normalization alternatives, separators, wildcard characters, or quoting;
- the entire input matches `[A-Za-z0-9_]{1,16}`.

Input is not trimmed. An input requiring trimming is `INVALID_INPUT`. This
prevents command and API callers from applying different normalization rules.

This rule applies to ordinary directory-query input. A server-verified login
observation entering the existing `PlayerIdentity` construction path continues
to use that model's established trim-then-validate behavior. The two paths do
not conflict: the repository indexes only the validated canonical value already
stored in `PlayerIdentity`, while untrusted query text is validated exactly as
received and is never normalized by trimming.

### 3.2 Canonical lookup key

The canonical lookup key is the validated ASCII input converted to lowercase
with locale-independent ASCII/`Locale.ROOT` rules. No Unicode normalization is
performed because Unicode is not accepted.

Examples:

| Input | Canonical key |
|---|---|
| `Fireflylover` | `fireflylover` |
| `PLAYER_01` | `player_01` |
| ` Player` | invalid |
| `Player*` | invalid |

Case variants of the same spelling address the same alias entry. The stored
last verified spelling may be returned only as safe presentation metadata; it
does not participate in identity comparison.

### 3.3 Exact-only contract

The directory supports exactly one validated name per request. It exposes no
prefix, substring, wildcard, fuzzy, phonetic, case-sensitive alternative,
range, batch, or all-player lookup.

---

## 4. Typed Resolution Result

### 4.1 Required result kinds

The future read-only service returns a closed typed result equivalent to:

| Kind | Meaning | UUID exposure |
|---|---|---|
| `UNIQUE_CURRENT` | Exactly one UUID is the current known owner and no different UUID has ever been observed with the alias | That UUID is available to authorized server consumers |
| `UNKNOWN` | No directory entry exists | None |
| `RETIRED` | The alias is known, has one historical UUID, and is not that UUID's current known name | None |
| `AMBIGUOUS` | More than one UUID is or has been associated with the alias, or safe uniqueness cannot be proven | None |
| `INVALID_INPUT` | Input fails the exact syntax contract | None |

Only `UNIQUE_CURRENT` carries a UUID. Consumers must exhaustively handle the
closed result type and must not extract a historical UUID from `RETIRED` or
`AMBIGUOUS`.

### 4.2 Public feedback

Ordinary commands may distinguish malformed syntax from a non-resolvable name,
but must map `UNKNOWN`, `RETIRED`, and `AMBIGUOUS` to the same bounded public
message, such as `Player name cannot be resolved uniquely.` This reduces
existence and rename-history probing.

Detailed classification may be logged or shown only through a separately
authorized diagnostic path with privacy controls. It must not reveal historical
UUID lists or names.

### 4.3 Resolution authority

A successful result is a routing answer, not proof that the command actor owns
the UUID. A mutating business operation separately authenticates the actor,
checks permissions, resolves the target again at execution, and applies its own
business validation.

---

## 5. Alias State Model

### 5.1 DirectoryEntry

Each canonical name maps to a bounded immutable entry containing equivalent
information to:

| Field | Rule |
|---|---|
| `normalizedName` | Canonical ASCII-lowercase key; must equal the NBT map key |
| `lastVerifiedSpelling` | Valid server-observed spelling; presentation only |
| `permanentlyAmbiguous` | Once true, never becomes false |
| `uniqueHistoricalOwner` | Present only while exactly one UUID has ever been associated |
| `currentOwners` | Canonical ordered UUID set for latest PlayerData names |
| `firstObservedAt` | Non-negative server time or migration sentinel defined by schema |
| `lastObservedAt` | Not before first observation |
| `entryRevision` | Positive and increments once per committed entry mutation |

The total number of `currentOwners` associations across the directory cannot
exceed the player-record count because each PlayerData record has one current
known name. `currentOwners` is bounded by the store's maximum player count.
Historical multi-owner membership is deliberately collapsed into the permanent
ambiguity bit; historical UUID collections are neither needed for resolution
nor exposed.

### 5.2 Derived result

The result is derived and never stored as an independently mutable truth:

```text
invalid input                         -> INVALID_INPUT
no entry                              -> UNKNOWN
permanentlyAmbiguous                  -> AMBIGUOUS
currentOwners has more than one UUID  -> AMBIGUOUS
currentOwners has exactly one UUID
  and equals uniqueHistoricalOwner    -> UNIQUE_CURRENT
currentOwners is empty
  and uniqueHistoricalOwner exists    -> RETIRED
every impossible combination          -> store corruption; fail closed
```

### 5.3 Permanent ambiguity

When a second distinct UUID is ever verified with the same normalized name:

- `permanentlyAmbiguous` becomes true in the same commit;
- `uniqueHistoricalOwner` is removed;
- the alias can never return `UNIQUE_CURRENT` under this architecture;
- later rename, retirement, or return does not clear ambiguity;
- recovery requires a future independently approved policy and migration, not
  an administrative mutation shortcut.

This conservative rule prevents historical name reuse from silently routing
money to the wrong person.

---

## 6. Rename and Reuse Semantics

### 6.1 First verified login

For a UUID without PlayerData, the verified login creates the PlayerData record
and observes its name in one proposed `player-data` replacement snapshot. If no
entry exists, the directory creates a unique current entry for that UUID.

### 6.2 Unchanged login

If the normalized name and verified spelling are unchanged, the login may
update only `lastSeenAt` under existing PlayerData rules. Directory entry
revision does not change merely because a player logs in again.

If only case differs, the canonical entry remains the same and the latest
verified spelling may update atomically with PlayerIdentity. This is one alias
observation, not a rename or second identity.

### 6.3 Rename

When UUID A changes from name X to name Y, one snapshot mutation must:

1. validate the current PlayerData and both directory entries;
2. remove A from X's `currentOwners`;
3. leave X as `RETIRED` if A was its only historical owner, or `AMBIGUOUS` if
   the entry was already ambiguous;
4. add A to Y's current ownership;
5. make Y permanently ambiguous if it has a different historical or current
   UUID;
6. update `PlayerIdentity.lastKnownGameName`;
7. increment the Player revision, changed entry revisions, and StoreRevision
   exactly once;
8. encode and replace the complete proposed snapshot before publishing it.

No intermediate state is visible.

### 6.4 Rename back

If UUID A returns to retired X and no other UUID has ever been associated with
X, X becomes `UNIQUE_CURRENT` again. If any different UUID has ever been
associated, X remains permanently `AMBIGUOUS` even when A is its only current
owner.

### 6.5 Duplicate current names

Two PlayerData records with the same normalized last-known name produce an
`AMBIGUOUS` entry. A loaded store that claims unique ownership while PlayerData
shows duplicate current ownership is structurally invalid and makes the
player-data module unavailable.

The system must not choose the newest login, lowest UUID, current online
player, or first map entry as a winner.

### 6.6 Concurrent observations

All mutations run on the logical server thread through the single repository.
Each proposal validates the current StoreRevision and relevant record
revisions. A stale proposal is rejected and recomputed; it cannot overwrite a
newer rename or clear ambiguity.

---

## 7. Persistence Model

### 7.1 Existing authority and namespace

The directory is stored inside the existing:

```text
fontainerepublic SavedData
└── modules
    └── player-data
```

It does not create a second SavedData or a second module namespace. The future
PlayerDataRepository remains the sole production writer of the entire
`player-data` namespace.

### 7.2 Proposed versioned layout

FR-DATA-003 requires a new PlayerData store version. A representative layout is:

```text
player-data
├── StoreVersion: int
├── StoreRevision: long
├── Players: compound
│   └── <canonical UUID>: existing versioned PlayerData record
└── Directory: compound
    ├── DirectoryVersion: int
    ├── MigrationProvenance: string enum
    └── Entries: compound
        └── <normalized-name>: compound
            ├── EntryVersion: int
            ├── EntryRevision: long
            ├── LastVerifiedSpelling: string
            ├── PermanentlyAmbiguous: byte
            ├── UniqueHistoricalOwner: int[4] (optional)
            ├── CurrentOwners: list<int[4]>
            ├── FirstObservedAt: long
            └── LastObservedAt: long
```

Exact field names and version numbers are implementation-design decisions, but
the ownership, validation, and atomicity in this document are normative.

### 7.3 Snapshot invariants

The strict codec validates at least:

- only known fields for the exact StoreVersion/EntryVersion;
- canonical UUID player keys matching `Identity.PlayerId`;
- canonical normalized-name keys matching each entry;
- valid verified spelling normalizing to the key;
- deterministic UUID ordering in `CurrentOwners` with no duplicates;
- every current owner exists in `Players` and its `lastKnownGameName`
  normalizes to that entry;
- every PlayerData record appears in exactly one entry's `currentOwners`;
- non-ambiguous entries have exactly one `uniqueHistoricalOwner` and zero or
  one matching current owner;
- ambiguous entries have no `uniqueHistoricalOwner`;
- timestamps and revisions are in range;
- total entries, current-owner associations, strings, and encoded namespace
  size are bounded;
- duplicate keys after normalization are rejected;
- unknown fields, wrong NBT types, impossible combinations, and newer versions
  are rejected.

Codec decoding performs structural validation only. It does not migrate data,
resolve names, update business state, or contact external services.

### 7.4 Deterministic encoding

Players are ordered by canonical UUID string, entries by normalized ASCII key,
and current owners by canonical UUID string. Optional fields are omitted rather
than represented by magic values. Equivalent snapshots encode identically.

### 7.5 Repository commit semantics

The repository constructs a complete immutable proposed snapshot containing
both Players and Directory. It validates and encodes the proposal before one
replacement of the `player-data` module compound.

Only after the storage adapter reports success may it replace live maps,
publish a PlayerData view, return a directory result based on the mutation, or
notify downstream consumers. A validation or save failure leaves Players,
Directory, Player revisions, entry revisions, and StoreRevision unchanged.

This gives atomic replacement within the in-memory module namespace. Section 9
states the unresolved disk-durability limitation.

---

## 8. Migration from Current PlayerData Store

### 8.1 Migration input and limitation

The current v1 store contains only each UUID's `lastKnownGameName`. It does not
contain complete rename or reuse history. Migration cannot reconstruct names
used before FR-DATA-003, cannot prove that an apparently unique name was never
used by another UUID, and must not contact Mojang, Microsoft, or another
external database to guess that history.

The migrated directory therefore means `known from this server's retained
PlayerData snapshot`, not complete global history. This limitation is recorded
as migration provenance and exposed to operators through bounded diagnostics,
not ordinary player lookup.

### 8.2 Deterministic migration

The isolated StoreVersion migration:

1. validates the complete old snapshot;
2. sorts PlayerData records by UUID;
3. validates and normalizes each `lastKnownGameName`;
4. groups records by normalized name;
5. creates a non-ambiguous current entry when a group has one UUID;
6. creates a permanently ambiguous entry when a group has multiple UUIDs;
7. uses a documented migration timestamp/sentinel without fabricating a
   historical first-seen fact;
8. records `MIGRATED_FROM_LAST_KNOWN_ONLY` provenance;
9. validates and encodes the entire new snapshot;
10. replaces the namespace once, or publishes nothing.

Migration preserves every PlayerData record revision. The successful namespace
replacement advances StoreRevision exactly once. Schema versions are not
business revisions.

### 8.3 Migration failure

On invalid names, excessive bounds, duplicate/corrupt UUIDs, encoding failure,
or storage failure:

- the original namespace remains the authoritative input;
- no partial Directory is installed or exposed;
- PlayerData and PlayerDirectory services do not become available;
- dependent modules remain unavailable;
- logs identify the migration step and safe aggregate reason without dumping
  the directory or private profiles;
- restart repeats the same deterministic migration.

No business module may perform this migration or scan PlayerData NBT.

### 8.4 Post-migration history

After successful activation, every later verified rename/reuse observation is
preserved under the permanent-ambiguity rule. The pre-migration blind spot
never justifies resolving a known retired or ambiguous entry.

---

## 9. Current-Core Durability Constraint

### 9.1 Evidence

Current `DataManager.putModuleData` delegates to `ModSavedData.putModuleData`,
which replaces the in-memory compound and calls `setDirty()`. It returns
`void`. `DataManager.saveAll()` also only calls `setDirty()` and logs `Saved`;
it does not perform or acknowledge a synchronous disk write.

The current PlayerDataRepository saves the encoded snapshot before replacing
its live map, which provides correct exception ordering for an injected store,
but the production adapter ultimately calls the same non-acknowledging method.
It proves in-memory replacement ordering, not crash-durable persistence.

### 9.2 Implementation blocker

FR-DATA-003 uses history to prevent wrong-recipient resolution. Publishing a
rename as safely recorded when its ambiguity evidence can be lost on crash may
later route money incorrectly. Therefore implementation is blocked until the
same separately approved Core durability gate required by FR-ID is available:

- an acknowledged durable namespace commit/flush contract with copy ownership,
  explicit failure, shutdown, and restart semantics; or
- a WAL with fsync/commit marker, replay, rollback, corruption, compaction, and
  world-identity rules.

A map replacement plus `setDirty()`, a log message, or a batch API without
durability acknowledgement does not satisfy the gate. FR-DATA-003 must consume
the approved shared persistence contract; it must not invent a private second
SavedData, JSON authority, or module-specific durability mechanism.

Until the gate is met, this architecture may be approved but directory code
must not be authorized for production identity or financial routing.

---

## 10. Service Boundary

### 10.1 Read-only contract

A future public server interface equivalent to `PlayerDirectoryService`
provides only:

```text
resolveExactGameName(input) -> PlayerNameResolution
```

`PlayerNameResolution` is the closed result in Section 4. The service may also
provide a UUID-current-name presentation query only if separately justified by
an existing PlayerData view; it must not widen name-to-UUID discovery.

The public service exposes no:

- mutation method;
- Repository, codec, NBT, or live map;
- iterator, stream, page, count-by-name, all-player list, or bulk method;
- UUID collection, historical owner, ambiguity participant, or migration data;
- prefix/fuzzy search;
- private PlayerProfile field;
- SubjectId, RegistryNumber, balance, or account.

### 10.2 Mutation ownership

Directory mutation is an internal part of the verified-login PlayerData
transaction. `PlayerDataService.recordLogin` (or its approved successor
orchestrator) supplies the authenticated UUID and verified game name to the
single repository mutation. Business modules cannot call an alias-observation
method.

### 10.3 Consumer sequence

Economy and other consumers use Services only:

```text
PlayerDirectoryService.resolveExactGameName(name)
    -> UNIQUE_CURRENT(UUID)
    -> SubjectRegistryService.resolvePlayer(UUID)
    -> SubjectId + RegistryNumber
    -> EconomyService
```

At no point does Economy read `lastKnownGameName`, directory NBT, or an index.
FR-DATA-003 does not read the FR-ID registry.

---

## 11. Commands and Suggestions

### 11.1 Suggested future commands

The future Economy design may support:

```text
/fr money account player <name-or-uuid>
/fr money pay <name-or-uuid-or-registry-number> <amount> [memo]
```

This document does not register those commands.

### 11.2 Tab suggestions

The server may use vanilla/Brigadier safe online-player suggestions for the
name argument. Suggestion providers must not:

- enumerate offline directory entries;
- enumerate UUIDs or FR-ID registry numbers;
- scan SavedData, Repository, or private service state;
- provide ambiguity/history details;
- cache a suggestion as an authoritative identity decision.

Players without the optional client receive the same suggestions and command
behavior through standard Minecraft mechanisms.

### 11.3 Execution-time resolution

Command execution validates and re-resolves the exact argument through
`PlayerDirectoryService` even if it originated from a suggestion. A player may
rename, disconnect, or become ambiguous after suggestions are generated.
Stale suggestions fail closed; they do not carry UUID authority.

Direct full UUID input bypasses name lookup but still resolves through
PlayerData/FR-ID Services. Registry-number input resolves through FR-ID. Input
grammar must distinguish these formats before invoking the relevant service.

---

## 12. Lifecycle and Dependency Order

### 12.1 Startup

```text
DataManager initialized
    -> player-data namespace loaded
    -> StoreVersion migration runs in isolation
    -> Players + Directory cross-validation succeeds
    -> PlayerDataRepository becomes active
    -> PlayerDataService and PlayerDirectoryService are exposed
    -> FR-ID and later consumers may start
```

If Directory decoding, migration, or cross-validation fails, `player-data` is
unavailable and required dependent modules must not start.

### 12.2 Runtime

- verified login is the only alias-observation source;
- read resolution occurs on the logical server thread or against an immutable
  bounded snapshot under a separately approved concurrency contract;
- no static global directory cache survives a server runtime;
- death, respawn, dimension change, and logout do not copy or rename aliases;
- logout may update PlayerData timestamps but does not alter directory state.

### 12.3 Shutdown and restart

The repository stops accepting mutations before runtime release. Runtime maps
are discarded. Restart reconstructs both Players and Directory solely from the
authoritative versioned namespace and revalidates every cross-index invariant.

---

## 13. Revision and Failure Semantics

### 13.1 Successful mutations

- a PlayerData identity change increments that player's revision exactly once;
- every changed directory entry increments its entry revision exactly once;
- the complete snapshot increments StoreRevision exactly once;
- unchanged directory entries retain revisions;
- a migration changes schema/store version and advances StoreRevision once but
  preserves player record revisions;
- publication occurs only after the approved storage contract reports success.

### 13.2 Failed mutations

Validation, stale revision, collision, bounds, encoding, persistence, or
durability failure changes no:

- PlayerData record or revision;
- directory entry, ambiguity flag, or entry revision;
- StoreRevision;
- published lookup result or downstream event.

The same verified observation may be retried idempotently. A retry recomputes
from the last authoritative snapshot and cannot erase ambiguity.

### 13.3 Time behavior

Timestamps are metadata, not conflict authority. Rename ordering is the
repository's committed StoreRevision order. A clock rollback cannot select a
name owner or clear ambiguity.

---

## 14. Security, Privacy, and Abuse Analysis

| Threat | Required mitigation |
|---|---|
| Name used as identity | UUID remains sole identity; name returns routing result only |
| Historical name reuse | Different UUID makes alias permanently ambiguous |
| Rename back clears evidence | Permanent ambiguity cannot be cleared by runtime mutation |
| Case-variant bypass | Strict ASCII validation and one lowercase canonical key |
| Unicode/confusable input | Reject all non-ASCII game-name characters |
| Malformed or oversized input | Validate before index access; fixed 16-character bound |
| Duplicate current names | Derive ambiguous result; inconsistent store fails closed |
| Stale command suggestion | Re-resolve exact input at execution |
| Client-forged alias | Accept observations only from authenticated server login |
| Repository/NBT bypass | Repository single writer; consumers use read-only Service |
| Offline directory enumeration | No enumeration API or offline Tab suggestions |
| Prefix/timing probing | Exact-only API, generic failures, normalized rejection work where practical |
| High-rate guessing | Bounded server-owned rate limit keyed by actor/source; values deferred |
| Unbounded history growth | Bounded entry count/namespace bytes; multi-owner history collapses to ambiguity flag |
| Malicious many renames | Capacity threshold, operational alert, fail-closed mutation; no history pruning that restores uniqueness |
| Migration corruption | Isolated deterministic migration and full validation before replacement |
| Migration blind spot | Explicit provenance; do not claim complete pre-migration history |
| Save failure | No live publication or revision change |
| Crash after unacknowledged save | Hard durability gate in Section 9 |
| Async/concurrent race | Logical-server single writer and revision revalidation |
| Error-detail privacy leak | Publicly merge unknown/retired/ambiguous responses |
| Profile-data disclosure | Directory stores and returns no PlayerProfile fields |
| Business-field contamination | No FR-ID, Economy, Citizen, office, court, or land data |

Rate limiting is a command/edge concern, not directory authority. A future
design must define values, bypass rules for trusted local diagnostics, restart
behavior, and denial response. This task does not implement a limiter.

---

## 15. Validation and Acceptance Matrix

| Scenario | Required outcome |
|---|---|
| First verified login | Player and unique current alias committed in one snapshot |
| Unchanged login | No directory revision change |
| Case-only verified change | Same key; presentation spelling and identity update atomically |
| Rename X to Y | X retired, Y current, PlayerIdentity updated atomically |
| Rename back to uniquely owned X | X may become unique current again |
| Rename back after another UUID used X | X remains ambiguous |
| Two UUIDs historically use one name | Permanent ambiguous result, no UUID exposed |
| Duplicate migration names | One ambiguous entry with all current owners |
| Exact unique offline lookup | `UNIQUE_CURRENT` with the one UUID |
| Retired alias lookup | `RETIRED`, no UUID |
| Ambiguous alias lookup | `AMBIGUOUS`, no UUID |
| Unknown alias | `UNKNOWN`, no UUID |
| Malformed/whitespace/Unicode input | `INVALID_INPUT` before index lookup |
| Case-insensitive input | Same canonical entry and result |
| Current-owner/index mismatch | Strict load rejection; module unavailable |
| Duplicate current-owner UUID | Strict codec rejection |
| Unknown/newer schema | Startup rejection without overwrite |
| Deterministic codec | Equivalent snapshots encode identically |
| Store migration | Players preserved; initial directory deterministic |
| Migration duplicate groups | Permanently ambiguous |
| Migration failure | Original input preserved; no partial service publication |
| Pre-migration name reuse | Explicitly unprovable; never fabricated |
| Save failure during rename | Player, entries, and all revisions unchanged |
| Durability acknowledgement failure | No new alias result published |
| Restart persistence | Same players, entries, ambiguity, and revisions recovered |
| Concurrent/stale rename proposal | Stale proposal rejected and recomputed |
| History capacity exhaustion | Fail closed without pruning or revision change |
| Public service surface | No enumeration, stream, page, count, or bulk UUID API |
| Online Tab suggestions | Vanilla online names only |
| Offline names/UUIDs/numbers in Tab | Never enumerated |
| Stale suggestion | Execution-time resolution decides result |
| Economy lookup | Directory UUID then FR-ID SubjectId; no raw storage access |
| Direct UUID target | Does not rely on name directory |
| Client absent | Command/chat behavior remains usable |
| Client spoof attempt | No alias observation or authority accepted |
| PlayerProfile contamination | No displayName/locale/description in directory |
| Business-field contamination | No subject, account, balance, citizen, office, court, or land field |

Acceptance requires outcome assertions against both live repository state and
persisted/reloaded snapshots. A normal build alone is not runtime evidence.

---

## 16. Impact Analysis

### 16.1 PlayerData store and codec

Future implementation requires a new StoreVersion and Directory section in the
same `player-data` compound. `PlayerDataStoreSnapshot` must represent Players
and Directory together. `PlayerDataNbtCodec` must strictly encode, decode, and
cross-validate both.

### 16.2 Repository and service lifecycle

`PlayerDataRepository` remains sole writer but must propose and commit combined
snapshots. Verified login must update `PlayerIdentity` and directory alias state
through one repository transaction. A read-only `PlayerDirectoryService` is
exposed only while the player-data runtime is active.

### 16.3 FR-ID

FR-ID remains independent of name history. After a unique UUID result, callers
use `SubjectRegistryService` to resolve or idempotently ensure the natural
person subject under its approved provisioning and durability rules.

### 16.4 Economy

Future Economy account lookup may accept a name, UUID, or RegistryNumber. Name
input uses PlayerDirectoryService first; UUID input uses PlayerData/FR-ID
Services; number input uses FR-ID. All successful routes converge on one
SubjectId and the same one-person/one-account invariant.

Economy must not cache name ownership as financial authority. It records stable
SubjectId/account participants in transactions, not game names.

The currently frozen FR-ECO-001-C text keeps offline-name input disabled and
uses UUID as its approved player target/account identity. Approval of this
document satisfies only the requested PlayerData directory design prerequisite;
it does not silently amend that Economy contract. A separately reviewed
`FR-ECO-001-C-ACCOUNT-ALIGN` must adopt the approved FR-ID subject/account model
and decide when exact-name input becomes available before any Economy command
may use this directory.

### 16.5 Commands

Future commands may suggest online names through vanilla sources, but execute
against fresh exact service resolution. Command feedback remains bounded and
does not expose directory classification details to ordinary users.

FR-CMD-001-A's non-enumerating suggestion and Service-only execution rules
remain unchanged. This document supplies no command contribution and does not
authorize `/fr money` registration.

No component described in this impact analysis is modified or authorized by
this task.

---

## 17. Open Questions and Blocking Dependencies

### 17.1 Implementation blocker

The acknowledged durable commit/flush or WAL contract in Section 9 is a hard
implementation blocker shared with the frozen FR-ID-001-A architecture at
commit `6e7a7315c268ff812f4814a0b4f0f1da5f16f7a4`. It must be designed, reviewed,
approved, and implemented before FR-DATA-003 may become authoritative for
financial routing. This repository reference does not contradict the recorded
Human approval; it avoids treating the frozen document's older status header or
the presence of a separate approval-record file as repository proof.

### 17.2 Deferred decisions

The following do not block architecture review but must not be silently chosen
by implementation:

1. exact maximum directory entry count and encoded namespace byte limit;
2. exact rate-limit values and trusted diagnostic policy;
3. concrete StoreVersion/DirectoryVersion numbers and migration timestamp
   sentinel representation;
4. operational behavior when a valid login rename reaches history capacity;
5. whether ordinary public feedback distinguishes `INVALID_INPUT` from generic
   non-resolution;
6. concrete diagnostic authority for viewing retired/ambiguous classifications;
7. whether safe current-name presentation is added to the service;
8. retention/export policy for aggregate migration and capacity metrics;
9. whether a future independently approved recovery policy may ever resolve a
   false-positive permanent ambiguity.

### 17.3 Non-blocking historical limitation

No architecture can reconstruct complete pre-migration name history from the
current `lastKnownGameName` field. Human approval accepts this known blind spot
only for initial migration; it does not permit external lookups or fabricated
history.

---

## 18. Implementation Sequencing

Required order:

1. FR-DATA-003-A architecture review and Human approval;
2. shared Core durability contract approval and implementation;
3. FR-DATA-003 implementation design and explicit Human authorization;
4. PlayerData StoreVersion migration and directory implementation;
5. independent repository, migration, privacy, and runtime review;
6. FR-ECO-001-C account-resolution alignment;
7. Economy implementation design and separate Human authorization.

Approval of this document authorizes none of the later steps.

---

## 19. Non-Goals

This design does not define or authorize:

- Java implementation, build changes, tests, or migration execution;
- Economy accounts, transfers, balances, supply, history, or notifications;
- FR-ID subjects, public-number allocation, or Hydro Archon bootstrap;
- external Mojang/Microsoft name or profile lookup;
- GUI, phone screen, packet, QR code, or client cache;
- physical identity cards, licences, certificates, or item NBT;
- fuzzy search, player-search UI, public player-list export, or offline Tab list;
- Citizen, Enterprise, Government, Parliament, Court, Land, or City behavior;
- permission, status, citizenship, office, or eligibility policy;
- raw-NBT repair, automatic ambiguity clearing, or administrator name override;
- a second SavedData, JSON live store, static global directory cache, or
  Capability authority.

---

## 20. Decision Summary

FR-DATA-003 stores a strict exact-name directory beside Players in the existing
`player-data` namespace. Minecraft UUID remains the only player identity.
Names are server-verified mutable aliases. Different historical UUIDs make an
alias permanently ambiguous; retired and ambiguous results expose no UUID.

PlayerIdentity and directory state change in one repository-owned snapshot.
The future public service is read-only and deliberately non-enumerating.
Online vanilla suggestions remain convenient, but offline names, UUIDs, and
registry numbers are never enumerated and command execution always re-resolves.

FR-DATA-003 ends at name-to-UUID. FR-ID owns UUID-to-subject/number routing, and
Economy owns the account. The current Core cannot acknowledge crash-durable
persistence, so authoritative implementation remains blocked by the same
approved durability gate as FR-ID.

This architecture candidate does not constitute architecture approval, Human
Approval, or implementation authorization.
