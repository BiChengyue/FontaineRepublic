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

## What was deliberately NOT copied

- Secure Trade's custom `MenuType` (trade_menu) and `SoundEvent`
  registrations — importing those synchronized registries would re-create the
  optional-client (no-FR client join) failure. FR registers no custom menu or
  sound; the GUI is an adapted `Screen` over the vanilla surface.
- The independent `TradeSession`/`TradeSessionManager`/journal authority —
  FR keeps one trade authority (the existing `TradeService`).
- Memory-only escrow / history-before-settlement ordering and overflow drops —
  FR uses the intent model (nothing pre-escrowed, no drop).

Use vanilla sound events and commands/clickable chat for FR-absent clients.
