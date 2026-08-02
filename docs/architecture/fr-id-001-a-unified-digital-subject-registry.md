# FontaineRepublic Unified Digital Subject Registry Architecture v1.0

> **Task ID:** FR-ID-001-A
> **Status:** Design Candidate — Pending Independent Architecture and Security Review
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Shared digital subject registry and permanent public-number infrastructure only
> **Implementation Status:** Not authorized

---

## 1. Purpose

This document defines a server-authoritative registry for permanent public
subject numbers used by registered natural persons and, after separately
approved integrations, enterprises, state institutions, cities, and other
legal subjects.

The public number has the canonical form:

```text
TTNNNNNNCC
```

and the presentation form:

```text
TT-NNNNNN-CC
```

For a natural person, the number is also the only public routing number of the
person's single full-function Economy account. It is not a password, login
credential, permission, balance record, or replacement for Minecraft UUID.

Two Human-fixed numbers are explicit exceptions to ordinary random allocation:

- `10-000001-61` permanently identifies the original Hydro Archon's private
  natural-person subject and sole personal account;
- `00-000001-95` permanently identifies the Hydro Archon office as a distinct
  office subject and will route to a future official account.

The private person and the permanent office are different subjects. Neither
number identifies the current emergency-authority actor.

There are no physical identity cards, bank cards, licences, certificates, or
authoritative item NBT. Optional-client phone views are presentation only.

---

## 2. Normative Boundaries

### 2.1 Authority ownership

| Concern | Authority |
|---|---|
| Authenticated player identity | Minecraft UUID through PlayerData |
| Subject identity, type, public number, registry status | FR-ID registry |
| Current or historical game-name lookup | Future PlayerDirectoryService |
| Balance, transactions, supply, financial status | Economy |
| Hydro Archon office-holder/succession binding | Future approved office-authority owner |
| Hydro Archon emergency actor UUID and console classification | FR-EMG |
| Citizenship | Future Citizen module |
| Enterprise membership or representation | Future Enterprise module |
| Institutional office and authority | Relevant state-power module |
| Facility and on-site context | Future institution-access boundary |
| Phone display | Non-authoritative projection |

Neither a player name, display name, public number, type prefix, phone cache,
command suggestion, nor item can prove identity or authority.

The original Hydro Archon's immutable personal subject, the permanent Hydro
Archon office subject, the future office-holder binding, and FR-EMG's current
authorized UUID are four separate facts. No component may infer one from
another.

### 2.2 Digital-only contract

- no physical credential object exists;
- no credential issue, loss, replacement, or expiry workflow exists;
- numbers do not expire with time;
- a number remains reserved after suspension, revocation, or dissolution;
- players without the optional client receive equivalent command/chat access;
- every mutation is revalidated by server Services at the final boundary.

### 2.3 Business isolation

FR-ID MUST NOT contain citizenship, office, court, land, enterprise behavior,
balance, transaction, supply, tax, credit, or permission fields. Other modules
reference a `SubjectId` through public Services and own their own state.

---

## 3. Subject Model

### 3.1 SubjectRecord

The authoritative record is an immutable/value-style aggregate:

| Field | Type | Rule |
|---|---|---|
| `schemaVersion` | positive integer | Exact supported record schema |
| `subjectId` | opaque UUID-backed `SubjectId` | Internal permanent identity |
| `registryNumber` | `RegistryNumber` | Public permanent routing identifier |
| `subjectType` | closed `SubjectType` | Must agree with number type code |
| `ownerReference` | typed `OwnerReference` | Stable external owner identity |
| `status` | `SubjectStatus` | Server-authoritative lifecycle state |
| `revision` | positive long | Increments once per committed record mutation |
| `createdAt` | epoch milliseconds | Assigned once by server |
| `updatedAt` | epoch milliseconds | Server-assigned, not before creation |

`SubjectId`, `RegistryNumber`, `SubjectType`, and `OwnerReference` never change
after creation. Status changes replace the immutable record and increment its
revision exactly once.

The server generates `SubjectId` independently of the public number using an
injected UUID source, rejects collision with any existing SubjectId, and never
accepts a client-selected SubjectId. Its UUID representation is an internal
storage choice and does not make it a player UUID.

### 3.2 SubjectId and public number

`SubjectId` is the internal foreign key used between server modules. The
public `RegistryNumber` is a human-usable lookup and payment-routing address.
They are deliberately distinct so formatting or public lookup cannot become
database identity.

### 3.3 Subject types

| Type-code range | Meaning | Current authorization |
|---|---|---|
| exact `00` | Fixed Hydro Archon office subject only | Exact reserved number only; no allocation pool |
| `10–19` | Natural persons | `10` may be used by approved implementation |
| `20–29` | Enterprises and operating entities | Reserved only |
| `30–39` | State institutions | Reserved only |
| `40–49` | Cities and local public entities | Reserved only |
| `50–79` | Future approved legal subjects | Reserved only |
| `80–89` | Special public subjects | Reserved only |
| `90–99` | System, migration, and technical use | Never ordinary allocation |

Reserved ranges do not create a module, subject, legal status, or permission.
A concrete type code requires a separately reviewed integration.

Type `00` is not a general sovereign, office, system, or migration range. It
accepts only the exact Human-fixed `00-000001-95` record. Every other `00`
number is invalid and cannot be generated, registered, or routed.

### 3.4 OwnerReference

An owner reference is a tagged, bounded value:

```text
PLAYER_UUID:<canonical UUID>
OFFICE_ID:HYDRO_ARCHON
ENTERPRISE_ID:<future stable id>
INSTITUTION_ID:<future stable id>
CITY_ID:<future stable id>
<future-approved-type>:<bounded canonical id>
```

`PLAYER_UUID` is obtained from authenticated server state or
`PlayerDataService`; it is never derived from a game name. The sole initially
recognized office owner reference is the constant `OFFICE_ID:HYDRO_ARCHON`,
used only by the exact fixed office subject. It refers to the office itself,
not its holder. Future owner types must contribute a closed codec and Service
integration without giving FR-ID direct access to their repositories or NBT.

### 3.5 Subject status

Infrastructure states are:

| State | Infrastructure meaning |
|---|---|
| `ACTIVE` | Registry record is usable subject to consumer business checks |
| `SUSPENDED` | Temporarily non-active; number remains reserved |
| `REVOKED` | Registration permanently revoked; record and number retained |
| `DISSOLVED` | Non-person subject ended; record and number retained |

There is no automatic expiry. This document does not decide who may change a
state or the political, judicial, administrative, or commercial grounds.
Consumers receive the status and fail closed where their policy is unresolved.

---

## 4. Registry Number Contract

### 4.1 Canonical representation

- canonical storage and digest form: exactly ten ASCII digits `TTNNNNNNCC`;
- display form: `TT-NNNNNN-CC`;
- parsers accept exactly either canonical form or display form;
- parsers do not strip arbitrary punctuation, Unicode digits, whitespace, or
  formatting codes;
- leading zeroes in the six-digit serial are significant;
- NBT stores the canonical ten-character string, never an integer;
- presentation separators never affect equality.

### 4.2 Serial allocation

`NNNNNN` is selected uniformly from `000000` through `999999` by a
server-owned cryptographically strong random generator. Production randomness
is hidden behind an injected `RegistrySerialGenerator`-style contract so tests
can deterministically force collisions and exhaustion.

Allocation rules:

1. validate that the concrete type code is enabled;
2. request a six-digit candidate from the injected generator;
3. compute the check digits;
4. reject a candidate present in either the active-number index or permanent
   reservation set;
5. retry only to a configured bounded attempt limit;
6. report bounded allocation failure when attempts are exhausted;
7. never fall back to sequential allocation, a client-selected value, another
   type range, or number reuse.

True capacity is 1,000,000 permanently allocated numbers per concrete type
code before explicit reservations. Type `10` permanently removes serial
`000001` from its ordinary random pool, leaving at most 999,999 ordinary
candidates. Type `00` has no random pool. Capacity and bounded-retry exhaustion
are distinct results. Operators may inspect aggregate capacity telemetry, but
no public enumeration is exposed.

### 4.3 Check digits: ISO-style MOD 97-10 rule

This design selects a numeric MOD 97 rule because it produces two digits,
detects common transcription errors, is deterministic, and needs no secret.

For the eight-digit body `B = TTNNNNNN` interpreted as a non-negative decimal
integer, calculate:

```text
CC_VALUE = 98 - ((B * 100) mod 97)
CC = CC_VALUE as exactly two decimal digits
```

The resulting ten-digit canonical number satisfies:

```text
numeric(TTNNNNNNCC) mod 97 = 1
```

Because `(B * 100) mod 97` is in `0` through `96`, valid generated check values
are mathematically limited to `02` through `98`; `01` is not reachable from
the formula. Parsing verifies both the type rule and the complete-number
remainder. Arithmetic must avoid locale and floating point; digit-by-digit
modular accumulation is the normative implementation method. A valid checksum
proves only correct transcription, never authority.

### 4.4 Human-fixed reserved numbers

Exactly two fixed numbers extend the ordinary allocation contract:

| Number | Canonical | Subject | Ownership and routing rule |
|---|---|---|---|
| `10-000001-61` | `1000000161` | Original Hydro Archon natural person | Permanently bound to the initially designated player's UUID and sole private personal account |
| `00-000001-95` | `0000000195` | Hydro Archon office | Permanently bound to `OFFICE_ID:HYDRO_ARCHON` and routes only to a future official account |

Both canonical values satisfy the Section 4.3 rule:

```text
1000000161 mod 97 = 1
0000000195 mod 97 = 1
```

These are data-contract constants, not generator outputs. Startup validation
must verify their exact digits, checksum, subject type, owner-reference kind,
and reservation status. They cannot be overridden through configuration,
client input, a generic registration request, an emergency category, or raw
NBT mutation.

The initially designated personal owner is captured once through a separately
approved, audited bootstrap process. After successful binding:

- resignation, removal from office, later succession, logout, rename, or a
  change to FR-EMG authority does not change the private owner;
- the private number is never transferred to a successor;
- a successor receives an ordinary personal subject number through normal
  allocation if not already provisioned;
- the office number never changes when the office holder changes.

The concrete source, authorization, and startup order for the one-time original
personal-owner bootstrap remain an implementation-blocking design question.
FR-ID must not infer the owner by reading FR-EMG's current configured UUID.

Every bootstrap attempt, including rejected input, persistence failure,
idempotent replay, incomplete recovery, and successful binding, must produce a
durable and auditable attempt trail under the future separately approved
bootstrap design. Each attempt record must contain at least:

- a stable attempt identity plus server-assigned time or an equivalent ordered
  monotonic sequence that preserves attempt order;
- the classified Human-authorization input source, without raw credentials or
  an unbounded input dump;
- a safe projection or cryptographic digest of the target UUID;
- a stable result or error code that distinguishes rejection, incomplete,
  idempotent no-op, persistence failure, and success;
- an idempotency correlation linking retries and restart recovery to the same
  requested binding;
- sufficient before/after binding digests or state references to detect
  unauthorized replacement;
- explicit retention, integrity/tamper-evidence, and restart-recovery bounds.

This requirement does not select the audit namespace, persistence owner,
receipt schema, segmentation policy, archive mechanism, or implementation.
Those choices remain part of the separately approved bootstrap design.

### 4.5 Permanent non-reuse

Every successfully committed record permanently reserves its number. A
revoked or dissolved record remains in the registry and therefore acts as its
own tombstone. If a future archival design moves inactive records, it must
leave an authoritative reservation tombstone containing the canonical number,
subject id, terminal status, and integrity reference. Deletion, reassignment,
rotation, transfer, and manual correction of a number are forbidden.

Malformed or conflicting stored reservations make the FR-ID module unavailable
rather than silently repairing or reallocating numbers.

The two fixed numbers are reserved before ordinary allocation begins, even if
their Subject records have not yet materialized. An existing ordinary record,
index, or tombstone claiming either fixed number with a different owner is
fatal corruption; startup must not reassign, swap, or auto-repair it.

---

## 5. Personal One-Account Invariant

For natural persons:

```text
one Minecraft UUID
    -> at most one PLAYER_UUID OwnerReference
    -> exactly one natural-person SubjectId once provisioned
    -> exactly one permanent RegistryNumber
    -> exactly one full-function personal Economy account
```

Rules:

- a player cannot open, rotate, transfer, or own a second personal account;
- the registry number is the sole public number of that account;
- player rename, logout, inactivity, or client absence changes nothing;
- Economy keys the personal account by `SubjectId` and indexes the same
  registry number for routing; it does not invent a second public number;
- FR-ID stores no account balance or transaction data;
- account financial state and subject registry state remain separate;
- a known number permits payment routing only, not debit, balance lookup, or
  impersonation.

Future enterprises and institutions each have one subject number routing to
one primary account. Whether they may have purpose-specific accounts is an
explicit future Human decision and is not implied by this design.

### 5.1 Original person and permanent office separation

The two Hydro-related subjects never merge:

| Concern | Original Hydro person | Hydro Archon office |
|---|---|---|
| Number | `10-000001-61` | `00-000001-95` |
| Owner | One immutable `PLAYER_UUID` | Constant `OFFICE_ID:HYDRO_ARCHON` |
| Account | Sole private personal account | Future official office account |
| Holder changes | Never transferred | Number stable; holder binding changes elsewhere |
| FR-EMG authority | No automatic grant | No automatic grant |

Private and official balances, transactions, revisions, status, disclosure,
and audit records are separate. No automatic sweep, inheritance, transfer, or
shared balance occurs on appointment, succession, resignation, or emergency
authority change. The future official account and succession workflow require
separate Human-approved designs.

---

## 6. Storage and Repository Authority

### 6.1 SavedData location

FR-ID uses the existing `fontainerepublic` SavedData container and exactly one
new module namespace:

```text
fontainerepublic.dat
└── modules
    └── subject-registry
        ├── StoreVersion: int
        ├── StoreRevision: long
        ├── Subjects: compound
        ├── Numbers: compound
        ├── Owners: compound
        └── Reservations: compound
```

No second SavedData, JSON live store, Capability authority, client database,
or static global subject cache is permitted.

### 6.2 Indexes

| Structure | Key -> value | Authority rule |
|---|---|---|
| `Subjects` | canonical SubjectId -> SubjectRecord | Primary registry records |
| `Numbers` | canonical RegistryNumber -> SubjectId | Exact public lookup only |
| `Owners` | canonical typed OwnerReference key -> SubjectId | Enforces one subject per owner |
| `Reservations` | RegistryNumber -> reservation projection | Permanent non-reuse if record is archived |

All indexes are encoded in deterministic lexical key order and validated
bidirectionally on load. Every Subject must have exactly one matching number
and owner index; every index must point to exactly one matching Subject.
Duplicate subjects, owners, numbers, mismatched type codes, unsupported fields,
or orphan indexes reject the whole snapshot.

Owner index keys use a reversible, versioned canonical encoding of the typed
reference, not a lossy display string or collision-prone truncated digest.
Load validation enforces the allowed relationship between owner-reference type
and subject type.

The snapshot also carries versioned bootstrap metadata sufficient to prove
that both fixed numbers are reserved and, once established, that the original
personal owner binding cannot change. This metadata must not duplicate the
current Hydro office holder or FR-EMG authority UUID. The fixed office subject
uses its constant owner-reference index; a future office-holder binding lives
outside FR-ID.

Bootstrap state metadata must support idempotent restart recovery and a stable
correlation to the durable attempt trail required by Section 4.4. The metadata
must never silently overwrite or summarize away failed and incomplete
attempts. The future bootstrap design must decide whether authoritative attempt
evidence lives in this namespace or another approved owner and how correlation,
retention, and tamper evidence work; this document does not make that storage
choice.

The initial version retains all Subject records. `Reservations` nevertheless
contains the two Human-fixed numbers from the first valid registry snapshot so
they remain unavailable before their records materialize. Other live records
are inherently reserved by `Subjects` and `Numbers`; if later archival removes
one from those maps, the same number must first receive an authoritative
reservation tombstone. This rule makes non-reuse explicit but does not
authorize archival.

### 6.3 Strict codec and bounds

The codec:

- accepts only declared fields and exact NBT types;
- validates canonical UUIDs, canonical numbers, checksums, type agreement,
  timestamps, statuses, and positive revisions;
- caps total subjects, encoded owner length, per-index count, and complete
  namespace bytes under implementation-approved limits;
- rejects unknown newer versions;
- performs migrations on an isolated copy and validates the complete result;
- returns immutable snapshots and never exposes mutable `CompoundTag`;
- writes deterministic ordering for reproducible tests and digests.

Schema migration is explicit and one version at a time. Migration failure
leaves the original namespace unchanged and prevents Service publication.

### 6.4 Single writer

Only `SubjectRegistryRepository` may load, encode, or replace the
`subject-registry` namespace. Other modules depend on `SubjectRegistryService`,
never the Repository, codec, `DataManager`, or NBT. Mutations run on the
logical server owner thread.

Every committed mutation constructs one complete immutable replacement
snapshot, increments `StoreRevision` once and each changed record revision
once, asks the store to persist it, and publishes the new in-memory snapshot
only after the approved persistence boundary reports success.

---

## 7. Service Boundary

Conceptual behavior, not final Java signatures:

```text
ensureSubjectForPlayer(playerUuid) -> SubjectView
findBySubjectId(subjectId) -> Optional<SubjectView>
resolveExactRegistryNumber(number) -> PublicRoutingResult
findSubjectForPlayer(playerUuid) -> Optional<SubjectView>
registerTypedSubject(trustedRegistrationRequest) -> SubjectView   [future]
```

The public Service returns immutable, purpose-bounded views. It does not
expose all-subject iteration, number-prefix lookup, owner-prefix lookup,
repositories, NBT, mutable indexes, balances, profiles, citizenship, roles, or
permissions.

`registerTypedSubject` is a future contribution boundary, not an implemented
general registration API. Each owner type requires an approved caller identity,
typed validation provider, eligibility policy owner, and module dependency.

### 7.1 Public routing result

Exact number lookup distinguishes internally:

- `ROUTABLE_ACTIVE` with immutable `SubjectId`, type, and public number;
- `KNOWN_NON_ACTIVE` without private reason;
- `UNKNOWN_OR_INVALID`;
- `REGISTRY_UNAVAILABLE`.

Ordinary output should collapse unknown, malformed, reserved, and unauthorized
details as appropriate to resist probing. Consumers must re-resolve and
revalidate status at the final mutation boundary.

---

## 8. Natural-Person Provisioning

### 8.1 Preconditions

Provisioning accepts only a canonical UUID already present in authoritative
PlayerData. FR-ID calls `PlayerDataService.find/require`; it does not inspect
PlayerData Repository or NBT and does not accept a game name as owner identity.

### 8.2 Idempotent algorithm

On the logical server owner thread:

1. require PlayerData availability and the existing UUID record;
2. build canonical `PLAYER_UUID:<uuid>` OwnerReference;
3. look up the owner index;
4. if present, validate type and return the same persisted subject;
5. if absent, allocate one unused number and one internal SubjectId;
6. construct Subjects, Numbers, Owners, and Reservations in one immutable
   registry replacement snapshot;
7. validate the complete snapshot;
8. persist through the approved acknowledged boundary;
9. only after success publish and return the subject.

Duplicate requests serialize on the owner thread. A second request observes
the owner index and returns the existing subject. Tests may invoke a guarded
concurrent entry adapter, but actual mutation remains owner-thread confined.

Ordinary provisioning must never generate type `00` and must treat type `10`
serial `000001` as already reserved. The separately approved original-person
bootstrap is the only path that may bind `10-000001-61`, and it remains
idempotent for the exact same designated UUID only.

### 8.3 Failure behavior

- allocation collision retries without publishing or reserving the failed
  candidate;
- bounded retry exhaustion returns failure and changes no revision;
- validation or save failure publishes no SubjectId, number, index, revision,
  command success, phone projection, or downstream provisioning event;
- restart retries from authoritative persisted indexes;
- a persisted subject is always returned unchanged after later failures;
- no recovery path allocates a second subject for the same owner;
- unexpected duplicate-owner or duplicate-number state fails the module closed.

---

## 9. Current-Core Persistence Constraint

### 9.1 Evidence

Current `ModSavedData` stores all module compounds in one map under
`fontainerepublic`, but `DataManager` exposes only per-namespace
`getModuleData` and `void putModuleData`. The latter replaces memory and calls
`setDirty()`; it provides no synchronous disk-write acknowledgement, rollback,
batch transaction, or cross-namespace atomic commit. `saveAll()` also only
marks dirty.

PlayerData's production store follows the correct single-writer and
copy-before-decode pattern, but its `save()` ultimately calls the same void
`putModuleData`. It proves replacement ordering, not acknowledged durability.

### 9.2 Blocking durability gate

The requirement “do not publish a permanent number before persistence
success” cannot be proven with the current API. Before FR-ID implementation,
a separately approved Core persistence contract MUST provide either:

- acknowledged durable namespace commit/flush with copy ownership, explicit
  success/failure, shutdown behavior, and restart recovery; or
- a WAL with fsync/commit marker, replay, rollback, corruption, compaction,
  and world-identity rules.

Merely adding a batch map replacement plus `setDirty()` does not satisfy this
gate. Until it is met, no implementation may present a newly allocated number
as permanent.

This is the same class of durability dependency identified by FR-EMG, but
FR-ID must receive an explicitly approved integration rather than assume that
another module's future implementation automatically solves it.

---

## 10. Cross-Namespace Provisioning and Recovery

### 10.1 Required order

Current Core cannot atomically replace PlayerData, FR-ID, and Economy
namespaces. The selected strategy is monotonic, ordered, and idempotent:

```text
1. PlayerData UUID exists durably
2. FR-ID subject exists durably
3. Economy personal account is ensured for that SubjectId
```

The allowed partial state is:

```text
PlayerData present + Subject present + Economy account missing
```

It is recoverable by retrying `ensurePersonalAccount(subjectId,
registryNumber)`. An Economy account without a valid subject is forbidden.

### 10.2 Failure matrix

| Failure point | Authoritative result | Retry behavior |
|---|---|---|
| PlayerData absent | Nothing created | Wait for verified PlayerData creation |
| FR-ID allocation/commit fails | No subject published; no Economy call | Retry FR-ID ensure |
| FR-ID succeeds, Economy unavailable | Subject remains valid; account absent | Retry account ensure with same subject |
| Economy validation/commit fails | No account published; subject unchanged | Retry same idempotency key |
| Crash after FR-ID durable commit | Subject recovered from FR-ID | Ensure missing Economy account |
| Crash after Economy durable commit | Both recovered | Ensure returns existing account |

Fixed-subject bootstrap follows the same durable ordering. The office subject
may exist before its future official Economy account. The original personal
subject requires its authoritative PlayerData UUID first. Neither missing
account permits reuse or reassignment of the fixed number.

Economy account creation uses `SubjectId` as the stable idempotency key and
must validate the matching RegistryNumber and natural-person type. It never
allocates a number or creates a subject. FR-ID never creates a balance.

### 10.3 Reconciliation

At login and before a personal financial operation, the orchestration path may
idempotently ensure the chain in dependency order. Bounded administrative
reconciliation may report counts of player-only, subject-without-account, and
healthy states through Services. It must not scan raw module NBT or expose
identities publicly.

No compensating deletion is used. A durable subject whose Economy step failed
retains its permanent number. This preserves non-reuse and makes retries safe.

---

## 11. Economy Integration Contract

The future routing path is:

```text
RegistryNumber
    -> SubjectRegistryService exact resolution
    -> SubjectId + type + current registry status
    -> EconomyService one personal account
```

FR-ID owns subject identity, type, number, owner link, status, and registry
revision. Economy owns account existence, balance, transactions, account
revision, financial status, total supply, history, and notifications.

For natural persons, Economy enforces one account per SubjectId and no second
public account number. Ordinary payment resolution may accept player name,
UUID, or RegistryNumber, but all paths must converge to the same SubjectId and
personal account before mutation.

“Exactly one account” is the healthy-state invariant after Economy provisioning.
The recoverable subject-without-account state from Section 10 is transitional
and cannot perform financial operations until the same account is ensured.

The original person's account is an ordinary private full-function personal
account with a special permanent number. The Hydro office's future account is
an official account owned by the office subject. Economy must not treat them as
aliases or move funds between them because an actor holds office. Knowing or
paying either number grants no debit, inspection, office, or FR-EMG authority.

When registry status is non-`ACTIVE`, Economy must revalidate through the
Service and fail closed until a separately approved policy defines which
credits, debits, emergency actions, or read-only views remain allowed. FR-ID
must not encode financial consequences into status.

---

## 12. Digital Presentation and No-Client Parity

An optional phone screen may display the server-provided public number,
subject type, safe registry status, and separately authorized Economy
projections. It stores no authoritative subject or financial copy.

Requirements:

- no physical card, certificate, licence, QR item, or identity-item NBT;
- copying a displayed number grants no authority;
- every mutating request re-enters server Services and revalidates actor UUID;
- client caches are connection-scoped, revision checked, and disposable;
- a client cannot select, edit, rotate, suspend, or revoke a number;
- command/chat offers the same essential view and routing capability without
  the optional client;
- this document does not authorize GUI, screen, packet, or QR implementation.

---

## 13. Exact Public Lookup and Privacy

Registry numbers are public routing identifiers, not secrets. Public lookup is
nevertheless exact and bounded:

- one fully formed canonical or formatted number per request;
- checksum and type validation before repository lookup;
- no prefix, substring, range, wildcard, fuzzy, or type-wide queries;
- no all-subject enumeration, bulk export, or suggestion-provider scan;
- no balance, private profile, citizenship, role, owner UUID, or transaction
  disclosure;
- bounded generic feedback for invalid, unknown, reserved, and non-active
  results;
- server-owned rate-limit guidance keyed by actor/source, independent of
  registry authority;
- timing-sensitive implementations should normalize rejection work where
  practical and must never return internal index diagnostics.

Tab suggestions may contain safe fixed command literals. They must not
enumerate registry numbers. Possession of a number only permits an otherwise
authorized consumer to address the subject or route a payment.

---

## 14. Relationship to Player Names and FR-DATA-003

FR-ID owns no current-name or historical-name index. The future safe lookup
path is:

```text
exact verified player name
    -> PlayerDirectoryService
    -> Minecraft UUID
    -> SubjectRegistryService
    -> RegistryNumber
```

FR-DATA-003 must treat names as aliases, detect ambiguity/reuse, and fail
closed. It must not enumerate the subject registry. Online player names may be
offered through vanilla safe suggestions; offline names, UUIDs, and subject
numbers are not enumerated. Command execution always resolves exact input
again through server Services.

Direct UUID input follows the same UUID-to-subject Service path. Neither
Economy nor FR-ID reads PlayerData NBT or `lastKnownGameName` directly.

---

## 15. Future Entity Integration

Future Enterprise, Institution, and City modules may request registration only
through a typed, separately approved Service adapter. Each integration must
define:

- stable owner identifier and canonical codec;
- trusted provider identity and runtime resolver;
- who is eligible to register the subject;
- idempotency key and lifecycle ordering;
- primary-account provisioning contract;
- status-change authority and audit;
- failure and restart reconciliation;
- Human decision on any purpose-specific accounts.

FR-ID does not depend directly on those module repositories and does not
decide enterprise creation, citizenship, office, government power, court
authority, parliamentary power, city ownership, or eligibility.

Type prefixes remain descriptive. A `30` prefix cannot grant institutional
authority, and a `20` prefix cannot prove enterprise representation.

---

## 16. Emergency and Correction Boundary

A future, separately approved FR-EMG catalogue may expose typed FR-ID actions
for `DEBUG`, `CORRECTION`, `COMPENSATION`, `DISASTER_RELIEF`, or
`EMERGENCY_RESPONSE`. Category alone never authorizes a mutation.

Any such action must:

- use FR-EMG actor, preview, confirmation, token, attempt journal, durability,
  receipt, and audit contracts;
- call a closed FR-ID Service operation rather than Repository/NBT;
- preserve number non-reuse and owner uniqueness;
- never perform arbitrary number assignment, reassignment, raw NBT editing, or
  identity impersonation;
- be independently reviewed as a concrete action catalogue.

This document defines no action catalogue and authorizes no emergency
implementation.

---

## 17. Adoption and Migration

### 17.1 Initial condition

There is no existing subject registry. Existing PlayerData UUIDs remain valid
player identities but have no subject until provisioned. No citizenship,
balance, status history, or number may be inferred from PlayerData.

Before any ordinary type-10 allocation is exposed, the initial registry
snapshot must reserve both `10-000001-61` and `00-000001-95`. The office
subject may be materialized idempotently from its constant owner reference.
The original-person subject may be materialized only after the separately
approved bootstrap has durably bound the intended existing PlayerData UUID.

### 17.2 Selected strategy: lazy, bounded provisioning

Natural-person subjects are created when first required after the feature is
available, normally on verified login or before an approved personal Economy
operation. This is selected over eager numbering because:

- current `PlayerDataService` intentionally exposes no all-player enumeration;
- business modules must not scan PlayerData NBT;
- a startup-wide allocation would be unbounded and could delay availability;
- lazy ensure is naturally idempotent and respects existing Service boundaries.

The process uses the ordered chain in Section 10. It does not contact Mojang,
Microsoft, or another external service. It does not assign citizenship and
does not create balance inside FR-ID.

If migration discovers either fixed number attached to the wrong owner,
duplicated, absent from reservations after bootstrap completion, or present as
an ordinary random allocation, the entire FR-ID startup fails closed. It does
not renumber any subject. Restart repeats incomplete bootstrap steps with the
same immutable bootstrap identity and idempotency keys.

Restart recovery must first reconcile bootstrap metadata with the durable
attempt trail. It must not infer success from a reserved number alone, erase an
incomplete attempt, create a new unrelated idempotency identity, or publish a
binding whose durable success evidence cannot be established.

### 17.3 Restart and observability

Each ensure operation is independently durable and retryable after the
Section 9 persistence gate is met. Restart reads and fully validates registry
indexes before exposing the Service. Migration telemetry may include bounded
aggregate counts, collision retries, failures by stable error code, and
subject-without-account reconciliation counts. Logs must not print bulk UUIDs,
numbers, private owner references, or raw NBT.

A future explicit maintenance migration may process bounded pages supplied by
a separately approved PlayerData administrative Service. It is not authorized
here and cannot bypass lazy provisioning invariants.

---

## 18. Threat Model

| Threat | Required mitigation |
|---|---|
| Forged number | Exact parser, checksum, authoritative exact index |
| Checksum bypass | Recompute digit-by-digit and validate concrete type |
| Allocation collision | Authoritative number/reservation index and bounded retry |
| Duplicate personal subject | Unique canonical owner index and owner-thread ensure |
| SubjectId collision | Server-generated UUID, authoritative index check, bounded retry |
| Number reuse | Retained record/reservation tombstone; deletion prohibited |
| Reserved personal number allocated randomly | Seed reservation before allocator opens; explicit candidate exclusion |
| Fixed office number generalized into type-00 pool | Exact-value-only parser/registration rule; no type-00 generator |
| Successor receives predecessor's private number | Immutable PLAYER_UUID owner binding; successor uses normal personal subject |
| Private and official account confusion | Distinct SubjectIds, owner types, account keys, ledgers, and routing |
| Office number used as authority | Office-holder and FR-EMG checks occur in their own authoritative Services |
| FR-EMG UUID drift changes registry owner | No dependency or automatic synchronization between bindings |
| Bootstrap attempt omitted or rewritten | Durable ordered attempt trail with stable result codes, state digests, retention, and tamper evidence |
| Bootstrap retry creates a second history | Stable idempotency correlation across retries and restart recovery |
| Bootstrap audit leaks target identity | Safe UUID projection/digest and classified source; no raw credential/input dump |
| Bootstrap metadata and audit disagree | Fail-closed reconciliation before binding publication |
| Owner substitution | Typed immutable owner reference; UUID from PlayerData |
| Type-prefix privilege escalation | Prefix descriptive only; business permission revalidated |
| Client spoofing | Client projection never authority; server resolves every mutation |
| Direct NBT mutation | Repository single writer and Service-only access |
| Registry enumeration | No listing/prefix/range API; bounded exact lookup only |
| Timing/existence probe | Generic result projection, rate-limit guidance, normalized rejection |
| Cross-module partial failure | Ordered durable ensures and idempotent reconciliation |
| Stale status | Final Service re-resolution and status/revision validation |
| Concurrent provisioning | Logical owner thread plus unique owner/number indexes |
| Migration restart | Persisted owner index returns same subject; no compensating delete |
| Compromised display data | Display grants no authority; server re-resolves canonical state |
| Malformed/corrupt store | Strict whole-snapshot validation and fail-closed startup |
| Allocation exhaustion attack | No client-selected number, bounded attempts, protected provisioning |
| Static runtime leakage | Fresh server-run repository; no static subject cache |
| Disk-write acknowledgement gap | Implementation blocked by Section 9 gate |

---

## 19. Validation and Acceptance Matrix

| Test | Expected result |
|---|---|
| Valid number generation | Enabled type, six digits, correct MOD 97 check |
| Fixed personal checksum | `1000000161 mod 97 = 1` |
| Fixed office checksum | `0000000195 mod 97 = 1` |
| Checksum verification | Valid accepted; altered digit rejected |
| Formatted/unformatted parse | Both normalize to identical canonical value |
| Malformed input | Whitespace, Unicode digits, wrong hyphens/length rejected |
| Collision retry | Existing/reserved candidate skipped without revision change |
| Bounded retry exhaustion | Explicit failure; no subject or reservation published |
| True allocation exhaustion | Type unavailable; no fallback or reuse |
| Permanent non-reuse | Revoked/dissolved number cannot be allocated |
| Type-10 fixed serial exclusion | Ordinary generator never returns/commits serial `000001` |
| Type-00 closed exception | Only `00-000001-95` is valid; no random allocation exists |
| Fixed reservations before service | Both constants unavailable to ordinary allocation from first startup |
| Original personal binding | Exact designated UUID binds once and never changes |
| Original holder resigns | Private subject and account remain with original UUID |
| Hydro succession | Office number remains; successor private number is unchanged/ordinary |
| Private/official account isolation | Distinct account identity, balance, ledger, revision, and audit |
| Number possession | Neither fixed number grants office or emergency authority |
| FR-EMG separation | Current authorized UUID changes no FR-ID owner or number |
| Bootstrap rejected attempt audit | Durable ordered record with stable rejection code and safe target projection |
| Bootstrap persistence failure audit | No binding publication; durable/incomplete outcome remains recoverable |
| Bootstrap idempotent replay audit | Same correlation; no second binding; replay outcome remains visible |
| Bootstrap restart recovery | Metadata and attempt trail reconcile before resuming or publishing |
| Bootstrap tamper detection | Missing, reordered, or altered attempt evidence fails closed |
| Bootstrap retention boundary | Attempts remain available under approved bounded retention/archive rules |
| One UUID one subject | Repeated ensure returns identical IDs and number |
| Concurrent duplicate ensure | Exactly one committed subject |
| Save-failure atomicity | No in-memory publication, revision, output, or Economy call |
| Restart persistence | Same owner resolves same subject and number |
| Strict codec | Wrong/unknown field or NBT type rejected |
| Duplicate number corruption | Whole registry rejected |
| Duplicate owner corruption | Whole registry rejected |
| Index mismatch/orphan | Whole registry rejected |
| Status mutation | Same identity/number; record and store revision +1 |
| PlayerData present, subject absent | Idempotent subject ensure succeeds once |
| Subject present, Economy absent | Same subject used to ensure one account |
| Economy account without subject | Creation/load rejected by Economy contract |
| No balance in registry | Codec/model reject financial fields |
| Type prefix without permission | Institutional/enterprise action denied |
| Client-authored number/status | Rejected; no mutation |
| No physical credential | All flows succeed without item dependency |
| No-client parity | Command/chat returns essential safe projection |
| Exact public lookup | One number only; no private owner/balance disclosure |
| No enumeration API | Public Service has no list/prefix/range operation |
| No Tab enumeration | Number suggestions never scan registry |
| Lazy adoption restart | Partial batch is safe; each owner retries independently |
| Deterministic codec | Same immutable snapshot produces equivalent ordered NBT |

Implementation acceptance additionally requires evidence that the Section 9
durability gate is satisfied; an injected in-memory store alone is not proof
of acknowledged disk durability.

---

## 20. Lifecycle and Dependency Order

Conceptual module dependencies:

```text
PlayerData <- Subject Registry <- Economy
                    ^
                    |
        future Enterprise/Institution/City adapters
```

The Subject Registry runtime starts only after PlayerData is ACTIVE, validates
its namespace completely, then exposes its Service. Economy starts after the
registry when it adopts SubjectId account ownership. Shutdown stops Economy
before FR-ID and clears all runtime repositories. Definitions remain
mod-lifetime; Services and repositories are recreated for every server run.

No name directory dependency is required for UUID provisioning. FR-DATA-003
is a lookup convenience consumer/provider in the command resolution path, not
the owner of subject identity.

---

## 21. Impact and Sequencing

Required project order:

1. approve FR-ID-001-A architecture;
2. align and approve FR-DATA-003-A safe player directory;
3. align and approve FR-ECO-001-C account/subject-number model;
4. design and approve the acknowledged persistence integration required by
   Section 9;
5. prepare independent implementation designs;
6. obtain explicit Human implementation authorization;
7. implement in dependency order and independently review each candidate.

Approval of this document does not authorize any later step. In particular,
FR-DATA-003, Economy, subject-registry code, commands, client UI, and migration
remain unimplemented.

### 21.1 Expected future document impacts

- FR-DATA-003: names resolve to UUID only, then FR-ID resolves the subject;
- FR-ECO-001-C: UUID-backed account wording changes to SubjectId-owned account,
  with the registry number as sole public personal account number;
- PlayerData implementation: no subject fields added; Service remains UUID
  identity dependency;
- Command architecture: exact number arguments and no registry enumeration;
- Network/client: optional bounded projection only after separate approval.

Approved documents are not modified by this task.

---

## 22. Open Questions and Blocking Dependencies

### 22.1 Implementation blockers

The acknowledged durable commit/flush or WAL contract in Section 9 is a hard
implementation blocker. Current Core cannot prove that a newly displayed
permanent number survived disk failure or crash.

The one-time original-person bootstrap is also blocked until a separate design
defines the audited Human-authorized source of the immutable player UUID,
first-start ordering, durable bootstrap marker, retry behavior, and recovery
when that PlayerData record is not yet present. Current FR-EMG authority must
not be used as an implicit source.

That design must also assign authoritative ownership and concrete persistence
for the Section 4.4 bootstrap attempt trail, including its schema, ordering,
idempotency correlation, safe UUID projection, stable results, restart
reconciliation, tamper evidence, capacity/retention, and archive or preservation
boundary. This document intentionally does not select those mechanisms.

### 22.2 Deferred decisions

The following are intentionally deferred and do not block architecture review:

1. concrete maximum subject count and encoded namespace byte limit;
2. bounded allocation retry count and operational alert threshold;
3. which future concrete codes within reserved type ranges are assigned;
4. authority and policy for status transitions;
5. financial behavior for each non-`ACTIVE` registry status;
6. whether enterprises or institutions may later hold purpose-specific
   accounts;
7. retention/archive design if complete inactive records ever leave the active
   subject map;
8. rate-limit values for exact public lookup;
9. future bounded administrative migration paging contract;
10. the future module and contract owning Hydro office-holder succession;
11. the future official-account provisioning and financial policy for the
    permanent office subject;
12. the concrete authoritative owner, namespace, retention/capacity values,
    and archival boundary for bootstrap attempt evidence, to be resolved by the
    separately approved bootstrap design.

None of these may be silently selected by implementation.

---

## 23. Non-Goals

This design does not define or authorize:

- implementation code, build changes, GUI, screens, packets, or physical
  identity/card/certificate items;
- Citizen, Enterprise, Government, Parliament, Court, Land, City, or Economy
  implementation;
- taxation, credit, loans, interest, markets, cash, or monetary policy;
- multiple personal accounts or personal account-number rotation;
- political or institutional eligibility;
- institution-specific permissions;
- player-name history or fuzzy player search;
- public registry export or bulk enumeration;
- arbitrary emergency mutation or manual number assignment;
- client-authoritative identity, number, status, or financial state;
- Hydro office succession, official-account implementation, or FR-EMG actor
  configuration and authorization.

---

## 24. Decision Summary

If approved, this architecture establishes:

1. Minecraft UUID remains authoritative player identity.
2. FR-ID owns a distinct permanent internal SubjectId and public RegistryNumber.
3. Numbers use `TT-NNNNNN-CC` with random six-digit serial and MOD 97 check.
4. `10-000001-61` is permanently bound to the original Hydro person's private
   subject/account; `00-000001-95` is permanently bound to the Hydro office and
   future official account.
5. The two fixed numbers satisfy MOD 97, are reserved before ordinary
   allocation, and never enter a random pool.
6. Original person, permanent office, future holder binding, and FR-EMG UUID
   authority remain strictly separate.
7. Every original-person bootstrap attempt requires durable, ordered,
   tamper-evident and restart-recoverable audit evidence; concrete storage
   ownership remains deliberately unresolved pending separate approval.
8. Numbers never expire, change, transfer, or return to the allocation pool.
9. One natural person has exactly one full-function Economy account, addressed
   publicly only by the subject number.
10. FR-ID contains no balance, citizenship, role, or business authority.
11. No physical credential exists; phone and command output are projections.
12. Exact public lookup is bounded and non-enumerating.
13. Provisioning is owner-threaded, idempotent, and ordered PlayerData -> FR-ID
   -> Economy, with subject-without-account as the only allowed partial state.
14. Current Core lacks acknowledged durability, so implementation is blocked
    until the explicit persistence gate is approved and met.

This document is a Design Candidate. It does not constitute architecture
approval, Human Approval, or implementation authorization.
