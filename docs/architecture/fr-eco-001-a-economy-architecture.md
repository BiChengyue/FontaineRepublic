# FontaineRepublic Economy Module Architecture v1.0

> **Task ID:** FR-ECO-001-A
> **Status:** Design Candidate — Pending Human Review
> **Priority:** P0
> **Dependency:** FR-CORE-001, FR-DATA-002, FR-NET-001, FR-CMD-001
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Scope:** Framework-level economy infrastructure only — no business policy

---

## 1. Purpose

This document defines the authoritative economy layer for FontaineRepublic.
It provides the first complete functional vertical (data → service → command
→ network sync) and validates module-to-module cooperation.

The Economy module provides:

- a server-authoritative player account system;
- a national treasury account;
- remote personal financial services: own-balance query, own transaction
  history, ordinary player-to-player payments, transaction notifications,
  and personal account services explicitly classified as mobile-safe;
- central-bank official duties: system deposit/withdraw operations (creating
  or removing money), treasury operation, account freeze/unfreeze, official
  account intervention, monetary issuance or destruction, and institutional
  financial administration;
- strict validation at every mutation boundary;
- NBT persistence through SavedData;
- a command interface (`/fr money`, `/fr bank`);
- non-authoritative network sync for balance display;
- optional audit-log integration.

This document conforms to the approved FR-INST-001-A physical-interaction
boundary (`docs/architecture/fr-inst-001-a-institution-physical-interaction-mobile-boundary.md`).
Central Bank official duties require a valid on-site context issued after
server-observed interaction with a registered terminal inside an active
registered Central Bank facility, and are not implemented until the
institution access contract exists.

It does not implement dynamic pricing, resource markets, interest, taxation,
business ownership, or GUI.

---

## 2. Architecture Principles

### 2.1 Server Authority

The logical server is the sole authority for all monetary state. A client may
request a transfer or query a balance but may never authoritatively set a
balance or bypass validation.

### 2.2 UUID-Backed Accounts

Every player account is keyed by Minecraft account UUID. A display name or
game name must not be used as an account identifier.

### 2.3 Immutable Transaction Records

Every completed mutation produces an immutable transaction record with a
monotonically increasing identifier. Transactions are append-only and never
modified after commit.

### 2.4 Validation Before Mutation

All business validation (sufficient balance, positive amount, valid target,
permission) executes inside the Service layer before any NBT write. The
command layer delegates to the Service and never duplicates validation.

### 2.5 Bounded Values

Account balances have a declared maximum (Long.MAX_VALUE / 2). Every string,
identifier, and collection has explicit size limits. The persistence layer
rejects any compound tag that exceeds schema bounds.

### 2.6 Module Isolation

Economy data lives in its own SavedData namespace (`"economy"`). The Economy
module accesses PlayerData through the PlayerDataService API, never by
direct NBT manipulation.

### 2.7 Institution Access Boundary

Remote personal financial services remain server-authoritative and MAY be
used without physical presence when Economy rules permit them. Central Bank
official duties MUST require a valid on-site context (FR-INST-001-A
Sections 3.1, 5.4, 7.3): registered Central Bank facility validation,
registered terminal interaction, on-site context validation, official role
or business permission, Economy business validation, and final mutation-time
revalidation.

OP permission alone, possession of an item, an open GUI, or knowledge of a
command MUST NOT satisfy the physical institution rule. Every mutation entry
point (command, standard interaction, container/book input, GUI, packet, or
installed client) MUST call the same server-side EconomyService path.

---

## 3. Data Model

### 3.1 EconomyAccount

| Field | Type | Rule |
|---|---|---|
| `playerId` | `UUID` | Primary key; required |
| `balance` | `long` | Non-negative; bounded |
| `accountRevision` | `long` | Monotonically increases after every mutation |
| `createdAt` | `long` | Epoch millis; server-assigned |
| `lastTransactionId` | `long` | Tracks most recent transaction |

Invariants:

- `balance` is always ≥ 0;
- every mutation increments `accountRevision` by exactly 1;
- `createdAt` is never modified after creation;
- `lastTransactionId` is the id of the most recent transaction.

### 3.2 EconomyTransaction

| Field | Type | Rule |
|---|---|---|
| `transactionId` | `long` | Monotonically increasing; ≥ 1 |
| `timestamp` | `long` | Epoch millis; server-assigned |
| `fromPlayerId` | `UUID` | Nullable — null for system deposits |
| `toPlayerId` | `UUID` | Nullable — null for system withdrawals |
| `amount` | `long` | Positive |
| `type` | `TransactionType` | DEPOSIT, WITHDRAWAL, TRANSFER |
| `reason` | `String` | Required; bounded length (≤ 128); display-only |

Invariants:

- `transactionId` is assigned by the repository and never reused;
- `amount` is always > 0;
- exactly one of `fromPlayerId` or `toPlayerId` may be null (never both);
- TRANSFER requires both IDs non-null.

### 3.3 TransactionType

```
enum TransactionType {
    DEPOSIT,      // system → player
    WITHDRAWAL,   // player → system
    TRANSFER      // player → player
}
```

### 3.4 EconomyStore

The top-level persistent container.

| Field | Type | Rule |
|---|---|---|
| `storeVersion` | `int` | Schema version; currently 1 |
| `storeRevision` | `long` | Monotonically increases |
| `treasuryBalance` | `long` | National treasury; non-negative |
| `nextTransactionId` | `long` | Next available transaction id |
| `accounts` | `Map<UUID, EconomyAccount>` | All player accounts |
| `transactions` | `CompoundTag` | Append-only transaction records (see 5.3) |

---

## 4. Service Layer

### 4.1 EconomyService Interface

```java
// server/economy/api/EconomyService.java
public interface EconomyService {
    long getBalance(UUID playerId);
    EconomyAccount getAccount(UUID playerId);
    EconomyAccount ensureAccount(UUID playerId);
    EconomyTransaction deposit(UUID toPlayerId, long amount, String reason);
    EconomyTransaction withdraw(UUID fromPlayerId, long amount, String reason);
    EconomyTransaction transfer(UUID fromPlayerId, UUID toPlayerId, long amount, String reason);
    long getTreasuryBalance();
    List<EconomyTransaction> getRecentTransactions(UUID playerId, int limit);
}
```

The on-site context for central-bank duties is validated by the shared
institution access boundary (future contract, FR-INST-001-A Section 15.1),
not by this interface signature. The service layer consumes the boundary's
validation result; the exact parameter or mechanism is determined when the
institution access contract is designed. This document does not define an
`OnSiteContext` class or API.

### 4.2 DefaultEconomyService Implementation

Located at `server/economy/service/DefaultEconomyService.java`.

Responsibilities:

1. **Balance check**: before any debit, verify `account.balance() >= amount`.
2. **Amount validation**: reject amount ≤ 0.
3. **Self-transfer guard**: reject `fromPlayerId.equals(toPlayerId)`.
4. **Account existence**: `require` or `ensureAccount` before mutation.
5. **Transaction creation**: assign id, record, update balances atomically.
6. **Revision guard**: repository enforces optimistic concurrency.
7. **Audit hook**: call AuditService when available (optional dependency).

### 4.3 Validation Pipeline (per mutation)

```
validatePositiveAmount(amount)
  → validateSourceHasFunds(account, amount)
  → validateTargetExists(toPlayerId)
  → validateNotSelfTransfer(from, to)
  → revalidateOnSiteContext(required for central-bank duties)
  → executeAtomicMutation(...)
  → auditLog(transaction)
```

Central-bank official duties MUST revalidate the on-site context at this
final mutation boundary even though the context was validated earlier
(FR-INST-001-A Sections 7.3, 9.2).

---

## 5. Persistence Layer

### 5.1 EconomyDataRepository

Located at `server/economy/persistence/EconomyDataRepository.java`.

Responsibilities:

- Load/store `EconomyStore` through `DataManager.getModuleData("economy")`;
- Assign `transactionId` and increment `storeRevision`;
- Enforce optimistic concurrency on account revisions;
- Reject NBT that exceeds schema bounds.

### 5.2 EconomyNbtCodec

Located at `server/economy/persistence/EconomyNbtCodec.java`.

Follows the same strict validation pattern as `PlayerDataNbtCodec`:

- Verify all required NBT keys and types before reading;
- Reject unknown keys;
- Enforce bounded string lengths;
- Validate UUID canonical format;
- Produce deterministic NBT output (sorted keys).

### 5.3 NBT Structure

```
EconomyStore
├── StoreVersion: int (1)
├── StoreRevision: long
├── TreasuryBalance: long
├── NextTransactionId: long
├── Accounts: CompoundTag
│   ├── <UUID>: CompoundTag
│   │   ├── PlayerId: UUID
│   │   ├── Balance: long
│   │   ├── AccountRevision: long
│   │   ├── CreatedAt: long
│   │   └── LastTransactionId: long
│   └── ...
└── Transactions: CompoundTag
    ├── <transactionId>: CompoundTag
    │   ├── TransactionId: long
    │   ├── Timestamp: long
    │   ├── FromPlayerId: UUID (or absent)
    │   ├── ToPlayerId: UUID (or absent)
    │   ├── Amount: long
    │   ├── Type: string (DEPOSIT|WITHDRAWAL|TRANSFER)
    │   └── Reason: string
    └── ...
```

### 5.4 Bounds

| Constraint | Value |
|---|---|
| Max accounts | 10,000 |
| Max transaction history | 100,000 (circular buffer — oldest pruned) |
| Max `reason` length | 128 characters |
| Max balance | `Long.MAX_VALUE / 2` |

---

## 6. Command Layer

### 6.1 Capability Classification

Every Economy operation is assigned exactly one interaction class from
FR-INST-001-A Section 4:

| Economy operation | Class | Physical presence |
|---|---|---|
| Query own balance | `REMOTE_INFORMATION` | Not required |
| View own transaction history | `REMOTE_INFORMATION` | Not required |
| Receive transaction notifications | `REMOTE_INFORMATION` | Not required |
| Ordinary player-to-player payment | `REMOTE_PERSONAL_SERVICE` | Not required |
| Personal account services explicitly classified mobile-safe | `REMOTE_PERSONAL_SERVICE` | Not required |
| System deposit/withdraw (create or remove money) | `ONSITE_OFFICIAL_DUTY` | Required |
| Treasury operation | `ONSITE_OFFICIAL_DUTY` | Required |
| Freeze/unfreeze or official account intervention | `ONSITE_OFFICIAL_DUTY` | Required |
| Monetary issuance or destruction; institutional financial administration | `ONSITE_OFFICIAL_DUTY` | Required |
| Emergency administrative recovery | `EMERGENCY_RECOVERY` | Special audited authority |

An operation MUST NOT be treated as remote merely because it has a command,
GUI, container, book, or packet surface (FR-INST-001-A Section 4).

### 6.2 Command Registration

Registered through `CommandContributionRegistry` as a contribution with
top-level literal `"money"`.

### 6.3 Command Tree

```
/fr
└── money
    ├── /fr money balance [player]
    │   REMOTE_INFORMATION — own balance, or target balance where a
    │   display-authority rule permits (see Open Question 4).
    ├── /fr money pay <player> <amount>
    │   REMOTE_PERSONAL_SERVICE — ordinary player-to-player payment.
    │   Self-pay rejected.
    ├── /fr money top
    │   REMOTE_INFORMATION — read-only, no identity exposure.
    └── /fr money history [page]
        REMOTE_INFORMATION — recent transactions for self.
```

### 6.4 Central Bank Commands

The `/fr bank` commands below represent central-bank official duties and are
classified `ONSITE_OFFICIAL_DUTY` (FR-INST-001-A Section 5.4). OP level is
an early source gate only and MUST NOT satisfy the on-site requirement.

```
/fr bank
├── /fr bank balance
│   Read-only treasury balance view. Display authority not yet defined
│   (see Open Question 4). No mutation authority.
├── /fr bank deposit <player> <amount>
│   ONSITE_OFFICIAL_DUTY — system deposit (creates money). Requires a valid
│   Central Bank on-site context.
└── /fr bank withdraw <player> <amount>
    ONSITE_OFFICIAL_DUTY — system withdrawal (removes money). Requires a
    valid Central Bank on-site context.
```

Rules:

- `deposit` and `withdraw` MUST require the full on-site validation chain:
  registered facility, registered terminal interaction, on-site context,
  official role or business permission, Economy business validation, and
  final mutation-time revalidation (FR-INST-001-A Section 7.3).
- These commands MUST NOT be implemented or registered before the
  institution access contract (facility directory, terminal directory,
  on-site context validation) is designed and available (FR-INST-001-A
  Sections 15.1, 16.4).
- `balance` is read-only presentation and never authorizes a mutation.

### 6.5 Command Design Rules

- Commands call `EconomyService`, never `EconomyDataRepository` directly;
- Brigadier `.requires()` is an early source gate only and is not business
  or institution authority (FR-CMD-001-A Section 8.3);
- Business permission (e.g., transfer limits) lives in Service layer;
- Central Bank mutation commands additionally require on-site context
  revalidation at the final mutation boundary (FR-INST-001-A Section 9.2);
- Commands, GUI, containers, books, packets, or an installed client mod
  must not create an alternative authority path — all mutation entry points
  call the same server-side EconomyService path;
- Amount arguments use `long` (no decimals); 1 unit = 1 currency unit;
- Player arguments use vanilla `EntityArgument.player()` (no custom types).

---

## 7. Network Layer

### 7.1 Packets

| Packet | Direction | Purpose |
|---|---|---|
| `EconomyBalanceSyncPacket` | S→C | Push current balance to client (presentation) |
| `EconomyTransactionNotifyPacket` | S→C | Notify participant of completed transaction (presentation) |

Both packets are non-authoritative presentation mechanisms and remain
optional (FR-NET-001-A Section 11). Client packets MUST NOT authoritatively
set balances, create transactions, issue or destroy money, grant Central
Bank authority, or fabricate an on-site context. The S2C packets above carry
no authority and do not change this rule.

### 7.2 Sync Strategy

- Balance is pushed on login and after every mutation affecting the player;
- No full-sync broadcast (privacy);
- No client-initiated balance query packet (clients call command or wait for push);
- Packets are optional — vanilla clients may see outdated balance until they
  run `/fr money balance`;
- Players without the optional client retain equivalent access through
  standard Minecraft interaction, commands, and chat feedback (FR-INST-001-A
  Section 11); S2C presentation packets are not required for parity.

### 7.3 Registration

Packets registered through `NetworkProductionMessageTable` during
`FMLCommonSetupEvent.enqueueWork`, consistent with FR-NET-001 contract.

---

## 8. Module Definition

### 8.1 ModuleRegistration

```java
public final class EconomyModule implements IModule {
    public static final ModuleId MODULE_ID = new ModuleId("economy");

    public static void register(ModuleRegistry registry) {
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Economy",
                        "1.0.0",
                        Optional.of("National economy and player accounts"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(new ModuleId("player-data")),      // required
                Set.of(),                                   // no optional deps yet
                30,                                         // tiebreaker; dependency topology guarantees after player-data
                EconomyModule::new
        );
        registry.register(definition);
    }

    @Override public void init() { /* wire repository + service */ }
    @Override public void shutdown() { /* null references */ }
}
```

### 8.2 Dependencies

- **Required**: `player-data` (FR-DATA-002) — identity verification.
- **Design dependency (future)**: shared institution access contract
  (FR-INST-001-A Section 15.1) — required before Central Bank official-duty
  commands or workflows are implemented; this document does not implement it.
- **Optional**: `audit` (future) — transaction logging.
- **Priority**: 30 (tiebreaker, lower = earlier; dependency topology ensures `player-data` initializes first; before future `land`).

---

## 9. Command Contribution Contract

### 9.1 ContributionSpec

```java
new CommandContributionSpec(
    "money",
    (buildContext, runtimeResolver) -> EconomyCommand.create(buildContext, runtimeResolver)
)
```

### 9.2 Runtime Resolution

`EconomyCommand` obtains `EconomyService` at execution time via:

```java
runtimeResolver.snapshot()
    .modules().stream()
    .filter(m -> m.moduleId().equals("economy") && m.available())
    .findFirst()
    // → coreManager.getRuntimeContainer(MODULE_ID)
    // → container.instance() → EconomyModule → service()
```

Unavailable module returns standard "Economy is currently unavailable" feedback.

---

## 10. Error Handling

### 10.1 Business Errors

| Condition | Feedback |
|---|---|
| Insufficient balance | "You do not have enough funds." |
| Amount ≤ 0 | "Amount must be positive." |
| Self-transfer | "You cannot send money to yourself." |
| Target not found | "Player has no economy account." |
| Balance overflow | "Transaction would exceed the maximum balance." |
| Missing on-site context for central-bank duty | "You must use a registered Central Bank facility terminal." |

### 10.2 System Errors

| Condition | Behavior |
|---|---|
| NBT corruption | Log error; reject load; block mutations |
| Repository unavailable | Command returns "Economy is currently unavailable" |
| Revision conflict | Retry once; then fail with "Transaction conflict; please retry" |

---

## 11. Implementation Staging

After Human design approval and separate implementation authorization:

Stages 1–3 (contracts, persistence, service) may remain separately staged:
they do not depend on physical facilities. The service methods that implement
central-bank official duties (system `deposit`/`withdraw`, treasury
operation) MAY be defined and unit-tested in these stages, but MUST NOT be
wired to any command, packet, GUI, or other entry point until the institution
access contract exists and the on-site context requirement is enforced.
Central Bank mutation commands are blocked until then.

### Stage 1 — Contracts & Models

- `EconomyAccount`, `EconomyTransaction`, `TransactionType` records;
- `EconomyStore` record;
- `EconomyService` interface;
- `EconomyNbtException` (following `PlayerDataNbtException` pattern);
- Pure automated tests (no Forge dependency).

### Stage 2 — Persistence

- `EconomyNbtCodec` (strict validation, bounded checks);
- `EconomyDataRepository` (SavedData integration, concurrency control);
- Repository automated tests.

### Stage 3 — Service

- `DefaultEconomyService` (full validation pipeline);
- `EconomyModule` registration + lifecycle;
- Integration with `PlayerDataService.require()`.

### Stage 4 — Commands

- `EconomyCommand` (`/fr money balance|pay|top|history`) — remote personal
  services (`REMOTE_INFORMATION` / `REMOTE_PERSONAL_SERVICE`);
- `BankCommand` (`/fr bank balance|deposit|withdraw`) — BLOCKED: the
  official-duty mutations must not be implemented or registered until the
  institution access contract exists (FR-INST-001-A Sections 15.1, 16.4);
  the read-only `balance` view MAY be staged only with a defined
  display-authority rule (see Open Question 4);
- Command contribution in `FontaineRepublic.onCommonSetup`.

### Stage 5 — Network

- `EconomyBalanceSyncPacket` (S→C, non-authoritative presentation);
- `EconomyTransactionNotifyPacket` (S→C, non-authoritative presentation);
- Login sync hook in `onPlayerLoggedIn`;
- No C2S authoritative economy packet is staged.

### Stage 6 — Verification

- `EconomyFoundationTestMain` (dependency-free contract tests);
- Gradle task `economyFoundationTest`;
- Runtime Dedicated Server verification;
- Full build regression;
- After the institution access contract exists: negative-path verification
  that central-bank mutations without a valid on-site context are rejected
  without mutation, and that leave/return cannot reuse an old context
  (FR-INST-001-A Section 17.2).

No stage introduces GUI, dynamic pricing, taxation, or business policy.

---

## 12. Phase 0 Compliance

### Allowed

- Economy data model, service, remote personal commands, and
  non-authoritative network presentation packets.

### Forbidden (not in this task)

- Central Bank official-duty commands or workflows before the institution
  access contract exists;
- GUI, screens, HUD;
- Dynamic pricing, resource markets, interest;
- Taxation, fines, government automatic deductions;
- Business ownership, company accounts, stock market;
- JSON backup/export (deferred to Alpha 0.2);
- Client-side balance caching as authority.

---

## 13. Rejected Alternatives

### 13.1 BigDecimal or double for balances

Rejected: Minecraft runs on `long` tick counters and item counts. Integer
currency avoids floating-point imprecision. 1 unit = 1 indivisible currency
unit. If sub-units are needed later, scale by 100 (cents) within long.

### 13.2 Per-player NBT files

Rejected: Architecture v2.7 specifies single `ModSavedData` per module until
measurable performance impact. Splitting is a future optimization, not a
Phase 2 concern.

### 13.3 Economy as a Forge Capability

Rejected: Capabilities are attached to entities/chunks. Economy state must
persist across player sessions and is logically a server-wide concern, not a
per-entity attachment.

### 13.4 Global ServiceRegistry singleton

Rejected: Architecture v2.7 explicitly forbids global service registries.
Modules wire their dependencies through `CoreManager.getRuntimeContainer()`.

### 13.5 Client-requested balance push

Rejected: Clients may only query through commands. Server pushes balance
after mutations. This prevents polling storms and maintains server authority.

---

## 14. Open Design Questions

These questions must be answered by later scoped designs and are not
blockers to approving this alignment:

1. What concrete value movement does a personal deposit/withdraw represent
   (wallet balance, cash item, account entry, or another representation)?
   Mirrors FR-INST-001-A Open Question 19.5. Not decided here.
2. Which Central Bank information (including treasury balance) is public,
   personal, official, or secret? Mirrors FR-INST-001-A Open Question 19.6.
3. Are on-site contexts single-use for all mutations or only selected
   actions? Mirrors FR-INST-001-A Open Question 19.4. Not decided here.
4. What display-authority rule applies to viewing other players' balances
   and the treasury balance?
5. What exact distance, timeout, and leave-detection policy applies to
   Central Bank on-site workflows? Mirrors FR-INST-001-A Open Question 19.3.

---

## 15. Primary References

- Approved institution boundary: `docs/architecture/fr-inst-001-a-institution-physical-interaction-mobile-boundary.md`
- Project Architecture v2.7: `docs/architecture/architecture.md`
- FR-CORE-001 architecture: `docs/architecture/fr-core-001-a-architecture.md`
- FR-DATA-001 architecture: `docs/architecture/fr-data-001-player-data-architecture.md`
- FR-NET-001-A architecture: `docs/architecture/fr-net-001-a-network-foundation-architecture.md`
- FR-CMD-001-A architecture: `docs/architecture/fr-cmd-001-a-command-foundation-architecture.md`
- PlayerDataService API: `src/main/java/.../playerdata/api/PlayerDataService.java`
- PlayerDataModule: `src/main/java/.../playerdata/PlayerDataModule.java`
- Forge 47.4.18: `net.minecraftforge.common.MinecraftForge`
