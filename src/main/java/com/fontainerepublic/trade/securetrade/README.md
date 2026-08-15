# Secure Trade adaptation (FR-TRADE-003-A)

This package adapts concepts and code from
[Navielon/SecureTrade](https://github.com/Navielon/SecureTrade) (MIT,
Copyright (c) 2026 Secure Trade Mod Authors), upstream commit
`add98b377ffc39e5d73789a874a08c5e369790ce`.

The license text and notices are archived under
`docs/third_party/securetrade/`. This is a source/reference input, not a
second runtime authority: the FontaineRepublic trade service
(`com.fontainerepublic.server.trade.api.TradeService`) remains the single
server-authoritative state machine.

## What was adapted

- `TradeItemValidator` — nested-container blacklist recursion; the blacklist
  is now supplied by the server-authoritative caller instead of a platform
  service loader.
- `TradeRules` — dimension/site policy predicate; lists are injected by the
  caller.
- `TradeMessages` — chat formatting helpers; re-branded to FR.
- **FR-TRADE-003-B** (container presentation, live in
  `com.fontainerepublic.common.trade` + `com.fontainerepublic.client.gui.trade`):
  - `TradeMenuType` / `TradeMenu` — the Secure Trade 54-slot container adapted
    so the two 27-slot offer regions are <em>phantom display</em> surfaces over
    the FR intent model (authorized by FR-TRADE-003-A §3, Human 2026-08-15).
  - `TradeSounds` — the six Secure Trade sound events under the
    `fontainerepublic` namespace (registered; no audio assets shipped).
  - `TradeScreen` — the Secure Trade screen adapted to the FR cards + the FR
    C2S intent surface (money / XP / ready / cancel).

## What was deliberately NOT copied

- <del>Secure Trade's custom `MenuType` and `SoundEvent` registrations</del> —
  this changed: FR-TRADE-003-A §3 (Human decision 2026-08-15) now authorizes a
  custom `MenuType` + `SoundEvent` because the FR client is required. They are
  registered in `com.fontainerepublic.common.trade.TradeMenuType` and
  `TradeSounds`.
- The independent `TradeSession`/`TradeSessionManager`/journal authority —
  FR keeps one trade authority (the existing `TradeService`).
- Memory-only escrow / history-before-settlement ordering and overflow drops —
  FR uses the intent model (nothing pre-escrowed, no drop).

Use vanilla sound events and commands/clickable chat for FR-absent clients.
