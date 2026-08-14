# FR-TRADE-002-A — Secure Trade Controlled Adaptation Architecture

> **Task ID:** FR-TRADE-002-A
> **Status:** Design Candidate — not Human Approval
> **Implementation Status:** Not authorized
> **Upstream reference:** SecureTrade `version/1.20.1` commit
> `add98b377ffc39e5d73789a874a08c5e369790ce`
> **License:** MIT; attribution and license notice required

The upstream branch head and raw license at that exact commit were independently
verified on 2026-08-14. Before any source is adapted, implementation must fetch
and archive the exact commit tree and its license notice again; a moved branch,
unverified archive or differently licensed fork is not an acceptable source.

## 1. Objective

Replace the current fragile trade presentation/session path with a
FontaineRepublic-owned, server-authoritative trade system that adapts proven
Secure Trade concepts while adding item, XP, FontaineRepublic money,
configurable tax, identity, audit and recovery integration.

Secure Trade is a source/reference input, not a mandatory external mod and not
a second runtime authority.

## 2. Why upstream cannot be installed unchanged

The reviewed Forge 1.20.1 source registers its own `MenuType` and sound events,
and its network channel requires an exact protocol match on both sides. A hard
dependency would make every client install Secure Trade and recreate the
optional-client failure.

Its execution path records history, transfers items with overflow drops, then
changes XP. Sessions/escrow are memory-only. It exposes no extension API for FR
money, tax, durable receipts or rollback. That architecture cannot be wrapped
safely from outside.

## 3. Adaptation Boundary

Concepts that may be adapted with MIT attribution:

- request expiry, cooldown and mutual-request auto-accept;
- two 27-slot offer areas;
- item and XP offers;
- offer changes clearing both ready states;
- both-ready countdown;
- item blacklist and optional distance/dimension policy;
- disconnect cancellation, history presentation and localization structure.

The following are not copied as architecture:

- Secure Trade mod bootstrap, registry objects and strict channel;
- independent request/session/history authority;
- memory-only escrow and history-before-settlement ordering;
- overflow drops and direct XP mutation without recovery.

## 4. Single Authority

```text
/fr trade commands -------+
                          |
Vanilla menu adapter -----+--> TradeService
                          |       |
Optional FR C2S UI -------+       +-- InventoryGateway
                                  +-- ExperienceGateway
                                  +-- EconomyTradeSettlementPort
                                  +-- TradeJournal / RecoveryInbox
                                  +-- AuditService
```

- `TradeService` owns the state machine and final decision;
- Economy remains the only money/treasury authority;
- Minecraft inventory/XP remains authoritative for physical assets;
- client screens, chat and menus are presentation only;
- there is one request table, one session identity and one trade history.

Old FR-TRADE-001 is retired only after migration tests pass; both authorities
must never accept live sessions concurrently.

## 5. Command Surface

```text
/fr trade <player>
/fr trade accept|deny|cancel
/fr trade offer money <amount>
/fr trade offer xp <points>
/fr trade ready
/fr trade history
```

An optional `/trade` root is a thin alias into the same adapter. On root
collision FontaineRepublic keeps `/fr trade`, disables the alias and logs the
reason. Collision detection occurs while contributions are still mutable and
before the command registry freezes: the alias contribution first checks the
pending root literals, and it is omitted rather than overwritten if any other
owner already claims `/trade`. After freeze no late replacement or retry is
permitted. Clickable chat always uses the canonical commands.

Targets use the approved player-directory/subject resolution rules. Execution
re-resolves identities and revisions at the final boundary.

## 6. Optional-Client Presentation

FR-TRADE-002 registers no new custom `MenuType` or sound event.

The no-FR GUI option uses the vanilla 54-slot generic container wire format:

- stable party A owns container slots `0..26` and party B owns `27..53`;
- each viewer receives its own vanilla menu instance over the same
  server-authoritative offer container and revision;
- each party may insert/remove only its own region;
- the counterparty region is read-only;
- shared changes are broadcast to both menus;
- server-side slot wrappers reject placement, pickup, quick-move, drag and
  collect operations against the counterparty region;
- every accepted slot mutation increments the offer revision and resynchronizes
  both viewers; stale menu/view tokens are rejected;
- server performs all slot authorization and item bounds.

An FR client may replace the recognized vanilla screen with a Chinese-first
Water Mirror screen. Recognition uses a bounded server-issued view/session
token, not a translatable title alone. The token binds session ID, viewer UUID,
menu container ID, offer revision and expiry and is consumed only as a
presentation binding; it never authorizes settlement.

Money, XP, ready and cancellation remain available through commands/clickable
chat for an FR-absent client. The FR client uses the existing optional FR
channel, extended through the normal ledger. No second channel is created. Use
vanilla sound events while optional-client compatibility is required.

Every appended message receives the next unused production ledger ID and the
message-table change increments `NetworkProtocol.VERSION` from the then-current
version (currently `9`) to the next version. Old and new tables must never share
one protocol version.

## 7. State Machine

```text
REQUESTED -> OPEN -> LOCKED -> PREPARED -> ASSETS_DEBITED
          -> MONEY_COMMITTED -> DELIVERY_PENDING -> COMPLETED

REQUESTED / OPEN / LOCKED -> CANCELLED
non-terminal recovery     -> COMPENSATING or resumed next phase
```

- pending requests have configurable timeout and cooldown;
- one active request/session per player;
- every offer change increments revision and clears both ready states;
- countdown starts only when both accepted the same offer digest and tax quote;
- final execution revalidates online state, identity, sources, combined
  inventory capacity, XP, balances, freezes, tax policy and revision;
- failure before `PREPARED` leaves all assets unchanged.

## 8. Item and XP Handling

### 8.1 Items

- item NBT/count are canonicalized and bounded;
- blacklisted or protected nested content is rejected before ready;
- capacity is simulated by inserting the complete incoming offer into one copied
  inventory, not checking each stack against the same free slot;
- no overflow item is dropped;
- source preimage and expected postimage are recorded;
- delivery failure enters `RecoveryInbox`, never silent loss.

### 8.2 XP

- offer uses total XP points, not display level;
- conversion/bounds use one server utility, `ExperiencePointMath`, whose
  integer-only level-to-total and total-to-level rules are unit-tested against
  Minecraft 1.20.1 boundaries;
- XP preimage/postimage are recorded;
- client cannot offer more than the server total;
- death or other XP changes invalidate the locked revision.

## 9. Money and Tax Policy

Every tax rate is a server-authoritative variable. Trade tax uses integer basis
points:

```text
default tradeTaxRateBps = 500
denominator = 10_000
tax = floor(offer * rateBps / 10_000)
```

Floating point is forbidden and arithmetic must be overflow-safe.

`TradeTaxQuote` records `rateBps`, denominator, policy revision/digest,
`quotedAt`, taxable bases and both taxes. The final boundary reads current policy
through a supplier/service. A changed quote clears ready and requires both
parties to confirm again.

The applied rate, base and tax are persisted in the Economy receipt, Trade
journal/history, audit payload and final player confirmation. Reload affects new
quotes; completed transactions are never recalculated.

A pure item/XP trade creates no money legs and does not call a zero-leg Economy
settlement. This removes the current zero-leg `capacity_exceeded` defect.

## 10. Durable Journal and Recovery

Cross-file ACID does not currently exist across player inventory data, XP,
Trade SavedData and Economy SavedData. Main-thread ordering must not be called
crash atomic.

`PREPARED` records:

- globally unique settlement ID and both parties;
- canonical offer revision/digest;
- item source preimages/postimages and escrow projection;
- XP preimages/postimages;
- money offers and accepted tax quote;
- phase and per-leg idempotency state.

Recovery protocol:

1. durable write `PREPARED`;
2. debit item/XP using pre/post images;
3. write `ASSETS_DEBITED` and invoke the supported player save boundary;
4. call Economy using idempotent `settlementId`;
5. write `MONEY_COMMITTED` / `DELIVERY_PENDING`;
6. deliver item/XP or create a durable recovery claim;
7. persist players, then write `COMPLETED`;
8. reconcile non-terminal settlements on restart before affected players start
   another trade.

Repeated recovery uses the same settlement ID. It never creates another money
leg, duplicates XP/items or drops uncertain items.

Provable recovery requires one of:

- a future Core batch/root commit covering Trade journal and Economy metadata;
  this capability does **not** exist in the current Core, whose durable API
  commits one module namespace at a time. Adding it is a separate Core interface
  change and requires explicit Human approval, implementation and review; or
- Economy idempotency plus a WAL with inventory/XP pre/post images and a login
  recovery gate.

Without a separately approved Core change, Stage D must use the second path.
The Trade module owns the bounded WAL and recovery inbox, Economy owns the
idempotent settlement receipt, and affected players remain login/operation
gated until reconciliation reaches a terminal state. Neither calling order nor
two successful single-namespace commits may be described as atomic.

Until approved, UI/state replacement may proceed but cannot claim crash-safe
combined settlement.

## 11. Persistence Ownership

If durable trading is approved, the existing Trade module owns a bounded
journal/recovery namespace through DataManager. This is not a new module.
Economy owns money receipts/idempotency. Audit consumes receipts and is not a
second transaction database. Unresolved recovery records cannot be trimmed.

## 12. Security Rules

- server resolves every actor/target and checks membership on every action;
- offer sequence/revision rejects replay and stale packets;
- ready binds exact offer digest and tax quote;
- client menu indexes and values are untrusted and bounded;
- item/XP/money are re-read at execution;
- disconnect/death/policy changes invalidate according to explicit rules;
- no client-supplied receipt, tax, subject or completion state is trusted;
- completed receipts and recovery are idempotent.

## 13. Configurable Variables

- request timeout and repeated-request cooldown;
- locked countdown;
- distance and dimension policy;
- item blacklist and nested-content rules;
- item/XP/money/history bounds;
- trade tax rate in basis points.

Security limits retain hard code caps so configuration cannot create unbounded
packets, loops or storage.

## 14. MIT Compliance

- preserve the Secure Trade copyright and MIT permission notice;
- add a third-party notices entry;
- record the reviewed upstream commit;
- identify adapted source/concepts;
- do not imply upstream endorsement.

## 15. Implementation Stages

### Stage A — Commands and request lifecycle

Canonical commands, timeout/cooldown/mutual request, one authority; no settlement
change yet.

### Stage B — Vanilla offer menu and FR presentation

Two 27-slot regions, combined capacity simulation, XP/money fields, responsive
Chinese FR screen, no custom registry/channel.

### Stage C — Runtime-safe settlement

Offer digest, item/XP/money/tax final revalidation, pure non-money route,
idempotent Economy key and no-drop recovery inbox.

### Stage D — Durable recovery

Trade journal, recovery login gate, crash injection, and approved Core batch or
WAL strategy.

Old FR-TRADE-001 is removed only after functional/failure parity is proven.

## 16. Validation Matrix

- FR current client and Forge-without-FR can request/accept/cancel;
- FR-absent client uses vanilla menu/commands and receives no FR packet;
- item, XP and money offers work independently and together;
- changed offer resets both ready states;
- timeout/cooldown release both players;
- combined overflow rejects without drop/partial mutation;
- insufficient XP/money and frozen accounts reject without partial mutation;
- tax-policy change resets ready and receipt records applied BPS/base/tax;
- pure item/XP route never calls zero-leg Economy settlement;
- disconnect/stop cancel or enter recoverable state;
- crash injection resumes/compensates idempotently;
- old/new trade authorities never coexist;
- protocol bounds, rate policies and side isolation pass runtime review.

## 17. Deferred Decisions

Human decisions required before corresponding stages:

1. FR-absent players receive vanilla trade GUI or commands only;
2. durable combined settlement uses Core batch commit or Trade WAL/login gate;
3. distance/dimension defaults and blacklist policy;
4. XP limits and history retention;
5. whether `/trade` alias is enabled when no collision exists.

## 18. Non-Goals

- installing Secure Trade as a dependency;
- running two trade authorities;
- importing upstream configuration/history;
- replacing Economy, PlayerData or Audit authority;
- cash-item exchange or bank-policy changes;
- changing the approved 5% default without policy approval;
- implementation without a separate task/review gate.

## 19. Review Gate

This candidate requires independent architecture/security review. Approval does
not authorize implementation, Core changes or release.
