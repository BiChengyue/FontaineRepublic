# FR-LEVEL3-FULL-RUNTIME-20260814 Test Report

## 1. Report Status

- Date: 2026-08-14
- Environment: Minecraft 1.20.1, Forge 47.4.18, Java 17
- Server: local dedicated server, `127.0.0.1:25566`
- Tested clients:
  - current FontaineRepublic client (`Dev`)
  - second isolated current FontaineRepublic client (`MailTarget`)
  - Forge client without FontaineRepublic
- Evidence roots:
  - `tmp/full-test-20260814/`
  - `run/logs/latest.log`
- Git actions: none
- Production-code changes: none
- Test-only server setting: `run/server.properties` was temporarily changed to
  `enable-command-block=true` for command-source verification and restored to
  `false` after the test.

This report records Human-observed runtime results and server-log evidence. It
does not constitute release approval or authorization to implement the proposed
follow-up designs.

## 2. Executive Result

The server foundation, player identity, economy balance, emergency mutation,
land persistence, command registration, current-client handshake, and most
trade failure gates operated successfully in a real dedicated-server session.

The test also found two release-blocking defects:

1. mail stored during the session disappeared after a clean save, stop, and
   restart;
2. a Forge client without FontaineRepublic cannot join because the server
   registry contains the custom `fontainerepublic:communicator` item.

The current mail UI and current trade UI are not suitable for release. Trade is
planned to move to a controlled MIT-licensed Secure Trade adaptation rather than
receiving further large structural investment in the current presentation.

## 3. Acceptance Summary

| Area | Result | Runtime evidence |
|---|---|---|
| Dedicated-server start/stop | PASS | Server reached `Done`; clean DataManager/module/world shutdown; all dimensions saved |
| Current FR client connection | PASS WITH NOTE | First connection timed out; second connection succeeded |
| Command foundation | PASS | Normal and admin commands returned expected results |
| Client screen entry points | PASS WITH UI FINDINGS | All command entry points opened their corresponding screens |
| Communicator right-click in air | FAIL WITH OFFHAND CONFLICT | A usable offhand item such as a shield takes priority and prevents the main-hand communicator from opening its UI |
| Player identity persistence | PASS | Restart loaded 2 player records |
| Economy persistence | PASS | Restart loaded economy revision 7 and 3 accounts; player balance remained nonzero |
| Emergency issue/reclaim | PASS WITH FINDINGS | Issue `+500`, replay/expiry/insufficient-reclaim rejection worked |
| Emergency source protection | PASS FOR MUTATION | Command block could read `status`, but `preview` was rejected as non-console |
| Land inspect/claim | PASS WITH FINDINGS | Claim, overlap rejection, ownership view, gates, and restart persistence worked |
| Land personal-rights view | PASS | Self-only current rights, refresh and existing command boundary worked |
| Mail send/recipient/fee | PARTIAL PASS | Offline player resolution worked after subject/account provisioning; send fee deducted |
| Mail UI/read/claim | FAIL | Text overlap; Read had no observable effect; money attachment remained unclaimed |
| Mail notification | FAIL | No new-mail HUD/chat notification was observed |
| Mail restart persistence | FAIL | Clean restart loaded `revision=0, mailboxes=0, messages=0` |
| Trade happy path | PASS | Money + item exchange, 5-second lock, and default 5% tax completed |
| Trade failure paths | PARTIAL PASS | Insufficient funds, full inventory, disconnect, and locked-offer reset worked |
| Pure item trade | FAIL | Rejected with `capacity_exceeded` when monetary leg count was zero |
| Client-optional/no-client join | FAIL | Missing registry entry `fontainerepublic:communicator` terminated Forge handshake |
| Old FR protocol rejection | NOT TESTED | No protocol-v8 client artifact was available |
| RCON/function emergency source | NOT TESTED | Command-block source was tested; RCON/function were not configured |
| Crash-window recovery | NOT TESTED | Optional destructive test deferred |

## 4. Passed Runtime Behaviour

### 4.1 Core and Persistence

- Protocol v9 and 29 production messages initialized for the current client.
- Fifteen runtime modules were available during the test cycle.
- Clean stop called DataManager save and closed every runtime module.
- PlayerData, Economy, Emergency, and Land state survived restart.
- Restart loaded Land revision 3 with 3 parcels.
- Restart loaded Emergency revision 8 with 8 records and phase `ACTIVE`.
- Restart loaded Economy revision 7 with 3 accounts and next transaction ID 9.

### 4.2 Economy and Emergency Authority

- Console emergency preview produced a short-lived, single-use token.
- Confirming the token issued 500 units to the target player.
- Replaying the token was rejected.
- Confirming an expired token was rejected.
- Reclaiming more than the target balance was rejected without partial mutation.
- A command block could not execute emergency `preview`; it received the
  expected real-local-console rejection.
- Human confirmed that emergency issue/reclaim may bypass an account freeze for
  debugging, correction, compensation, disaster relief, and emergency response.

### 4.3 Land

- Communicator right-click on a block opened the land view.
- Existing `/fr land inspect` and `/fr land claim` paths worked.
- Claim creation, overlap rejection, holding gates, and restart persistence
  worked.
- The personal land-rights screen returned the current player's active rights.
- The current default generated parcel is intentionally a small fixed cuboid;
  Human later selected a future quick-template height of 9 blocks, from Y-4
  through Y+4.

### 4.4 Trade

- Two current-FR clients could establish a trade after the request UI was made
  visible.
- Money and an item were exchanged successfully.
- The five-second locked window operated.
- The default 5% payer-side tax was applied.
- Changing an offer during the locked phase reset agreement.
- Insufficient funds failed closed.
- Disconnect cancelled the session.
- A receiving inventory with no capacity failed closed.

## 5. Findings and Priority

### P0 — Mail data disappears after clean restart

The recipient saw mail during the running session. Both clients disconnected,
the server performed a normal DataManager save and clean world shutdown, and the
next start logged:

```text
[Mail] Runtime initialized (revision=0, mailboxes=0, messages=0)
```

Economy and Land loaded their prior revisions in the same world. This is a
module-specific persistence loss and must be fixed before mail is trusted with
money or item attachments.

### P0 — No-client compatibility is broken by the custom item registry

A Forge client without FontaineRepublic was rejected with missing registry data
for `fontainerepublic:communicator`. `displayTest` or an optional network channel
cannot overcome a missing item registry entry.

If no-client access remains an approved requirement, the communicator must not
be a server-side custom registry object. A future design should use a vanilla
item carrier with server-authenticated data and optional resource/client
presentation, or explicitly revoke the no-client requirement through a Human
architecture decision.

### P1 — Mail UI does not support its own delivery contract

- inbox text overlaps;
- selecting or pressing Read produced no observable state change;
- a 100-unit money attachment remained unclaimed;
- item attachments have no exposed composition path in the current client UI;
- no mail HUD/chat notification was observed;
- institution-addressed mail can be stored, but no usable institution mailbox
  access path was demonstrated.

### P1 — Current trade implementation has session and settlement defects

- a pending invitation is not shown while any Minecraft screen is open and is
  not reopened later;
- pending/requested sessions can remain stuck until logout;
- fixed screen coordinates clip buttons at smaller window sizes;
- inactive-phase controls overlap because visibility is restored too broadly;
- pure item trades fail with `capacity_exceeded` because a zero monetary-leg
  transaction performs an invalid `nextTransactionId - 1` capacity check.

The current trade presentation/session implementation should be replaced by the
approved controlled Secure Trade adaptation rather than independently rebuilt.

### P1 — Land interaction is not fully consumed

Holding the communicator and right-clicking a lever opened the land UI but also
toggled the lever. The land interaction hook must consume the block interaction
when it claims the action.

### P1 — Offhand use can suppress the communicator

When the communicator is held in the main hand and a shield or another usable
item is held in the offhand, right-clicking air activates or prioritizes the
offhand item and the communicator UI does not open. Communicator activation
needs an explicit, deterministic hand-priority rule so an unrelated offhand
item cannot silently disable the approved main-hand device action.

### P1 — Emergency inspection returns a stale attempt state

Inspecting attempt 4 returned the earlier `PENDING` record even after the issue
had succeeded. The lookup appears to select a record by record ID/first match
instead of the latest terminal record for the attempt ID.

### P2 — Emergency read-only status bypasses source classification

A command block can execute `fr admin emergency status` and receive phase,
revision, record count, and digests. Mutation preview remains protected. Human
or the security design must decide whether read-only status/inspect are intended
for generic admin sources; the current Level-3 wording says command-block
execution should be rejected and therefore does not match runtime behaviour.

### P2 — Emergency status provider count is misleading

`status` reported `providers=0` even though the Economy emergency provider
successfully previewed and executed an issue action.

### P2 — Login provisioning can hit the durable rate guard

An earlier login produced `Economy provisioning failed ... RATE_GUARD`. The
existing account allowed later testing to continue, but new-player provisioning
must not depend on timing that can fail during a normal login burst.

### P2 — Client presentation requires redesign

- many screens contain overlapping text;
- several controls are clipped or hidden at smaller sizes;
- client presentation should be Chinese-first for the current project phase;
- UI styling does not yet reflect Fontaine's institutional/world setting;
- the communicator uses a missing item model and appears purple/black;

## 6. Approved Follow-up Design Directions

### 6.1 Trade replacement

- Use Secure Trade's MIT-licensed item/XP offer concepts, countdown, request
  timeout, and disconnect handling as an internal controlled adaptation.
- Do not add Secure Trade as a mandatory external server dependency.
- Do not copy its settlement order unchanged: it lacks a money extension API,
  writes history before transfer, drops overflow items, and has no unified
  rollback for items, XP, and SavedData money.
- FontaineRepublic remains authoritative for money, tax, audit, and identity.
- Preserve attribution and the MIT license notice.
- Add money and experience to the same reviewed offer while retaining
  server-authoritative tax and settlement checks.

### 6.2 Tax policy

- Every tax rate is a server-authoritative variable, never a business-logic
  literal.
- Represent rates using integer basis points or another fixed-point integer
  representation; do not use floating point.
- Revalidate the current policy at final commit.
- Persist the applied rate, taxable base, and calculated tax on each receipt.
- Tax-policy changes require authority checks and audit.
- The current trade-tax default remains 5% until changed by approved policy.

### 6.3 Land application and pricing

- Allow applicants to enter two opposite corners manually.
- Allow communicator-assisted selection by clicking two blocks.
- Keep a 3x3x9 quick template (Y-4 through Y+4).
- Price only the non-overlapping purchasable volume; never create overlapping
  rights. Use bounded cuboid decomposition or reject excessive fragmentation.
- Calculate unit price from declared use and distance to the capital, other
  cities, and transport infrastructure.
- Validate that declared use fits the selected region: for example residential
  land must include a usable surface; mining can prefer underground regions;
  fishery requires appropriate water context; industry may use planning and
  distance constraints.
- Mark city and transport parcels distinctly. City, transport, and unowned land
  may allow safe interaction but deny breaking/placing; another player's parcel
  denies interaction, breaking, and placing unless an explicit public terminal
  or service rule grants access.
- Represent institution service zones as overlays associated with the relevant
  city/institution parcels.
- Depend on Xaero's World Map for land-type visualization; do not build a second
  general-purpose map.

## 7. Deferred or Blocked Manual Coverage

- Mail read/delete, attachment claim, partial item claim, broadcast cooldown,
  and restart recovery require mail fixes first.
- Pure item/XP/money combined trade should be tested against the replacement
  trade implementation, not used to expand the current UI.
- Protocol-v8 rejection requires an old-client artifact.
- RCON and function source rejection require dedicated test configuration.
- Central-bank physical terminal workflows require registered facilities/zones.
- Xaero land overlays and land pricing do not exist yet.
- Crash-window recovery remains an optional dedicated evidence cycle after P0
  persistence defects are fixed.

## 8. Recommended Repair Order

1. Stop mail data loss and add a clean-stop/restart regression test.
2. Resolve the no-client/custom-registry architecture conflict.
3. Fix mail selection/read/attachment delivery and notification behaviour.
4. Produce the Secure Trade adaptation architecture and safe settlement design.
5. Replace the current trade UI/session path and retest all trade failure cases.
6. Consume communicator land interactions and fix emergency inspect/status
   diagnostics.
7. Perform Chinese-first responsive UI and world-style redesign.
8. Design the new land application, pricing, zoning-suitability, permissions,
   and Xaero overlay features before implementation.

## 9. Final Test Gate

Result: **REVISE BEFORE NEXT RELEASE CANDIDATE**.

The underlying modular runtime is usable, but mail persistence loss and broken
no-client compatibility prevent release approval. This report does not replace
an independent repository review after fixes.
