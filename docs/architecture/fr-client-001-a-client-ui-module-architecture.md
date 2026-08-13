# FontaineRepublic Client UI Module Architecture v1.0

> **Task ID:** FR-CLIENT-001-A
> **Status:** Design Candidate — Pending Independent Review and Human Approval
> **Project:** FontaineRepublic
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17
> **Purpose:** Optional client mod for visual operations (FR Client Features)
> **Dependency:** FR-NET-001-A（channel/protocol）、已实现服务端模块、FR-CMD-001-A
> **Implementation Status:** Not authorized（Human 指示为最后阶段）

---

## 1. Purpose

Human directive (2026-08-13): finally develop the UI-enabled client mod for
visual operations. This design defines the optional client as a **presentation
and input-forwarding layer** per architecture v2.7 §5: it never decides state,
never stores authoritative data, and never bypasses server validation.

First-release scope (visual operations for implemented features):

- Economy: balance card, transaction history view, transfer form;
- Citizen: identity/rank card;
- Land: parcel/usage info view (region read-only);
- Government/Parliament/Court: public info views and status;
- Notifications: incoming transfer / case updates / bill milestones;
- Guidance: in-client help linking to `/fr help` and the player guide.

## 2. Normative Boundaries

| Concern | Authority |
|---|---|
| All state | Server |
| Client | Display, input forwarding, optimistic presentation only |
| Mutations | Re-enter the same server command/Service paths |
| Presence/facility | Never asserted by client (FR-INST-002) |
| No-client parity | Preserved; commands/chat remain fully functional |

Rules (architecture v2.7 §5):

- Client cache is optimistic display only; never authority;
- Every mutation submits via the same server path (commands or C2S request
  packets that carry operation+params only — server resolves identity/context);
- No authoritative C2S packet; no client-side permission/business decisions;
- S2C sync is non-authoritative presentation (FR-NET-001-A);
- `client/` package isolated via DistExecutor + package rules; server never
  imports client classes;
- Protocol version mismatch rejected at handshake (existing channel).

---

## 3. Client Structure

```text
com.fontainerepublic.client/
├── ClientManager.java            # DistExecutor-init; registries
├── gui/
│   ├── money/                    # balance/history/transfer screens
│   ├── citizen/                  # identity card
│   ├── land/                     # parcel info (read-only)
│   ├── institution/              # government/parliament/court info
│   └── guide/                    # in-client help
├── hud/                          # balance/notification HUD
└── net/                          # S2C packet handling (presentation only)
```

Screens are thin views over server-provided projections; forms submit through
the same command/Service entry points (or approved C2S request packets that
carry no authority).

---

## 4. Network Surface

Non-authoritative S2C presentation packets (FR-NET-001-A registration):

- `BalanceSyncPacket` / `TransactionNotifyPacket`（economy）;
- `NotificationPacket`（offline incoming / case / bill）;
- `InstitutionInfoPacket`（public info refresh）.

No authoritative C2S packets in first release; forms either open the relevant
command path or send a bounded request packet that the server revalidates
fully (identity from connection context, never from client payload).

---

## 5. Acceptance Matrix

| Test | Expected |
|---|---|
| No-client parity | All features usable without client |
| Client cache non-authoritative | No client-side state decides anything |
| Mutation revalidation | Forms fail without server validation |
| No presence spoof | Client cannot assert on-site context |
| Package isolation | server/ never references client/ |
| Handshake mismatch | Rejected at connect |
| S2C presentation only | Packets carry display data, never authority |
| Guidance present | In-client help + guide link |

---

## 6. Non-Goals

- Client-side storage/authority; permission decisions; business logic;
  full map/rendering overlays (later revisions); implementation (final phase).

## 7. Review Gate

Design candidate only. Does not constitute architecture approval, Human
Approval, or implementation authorization. Independent review requested.
