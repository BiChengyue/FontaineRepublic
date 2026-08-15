# Secure Trade direct copy + FR money leg (FR-TRADE-004)

This package is a **verbatim port** of
[Navielon/SecureTrade](https://github.com/Navielon/SecureTrade) (MIT,
Copyright (c) 2026 Secure Trade Mod Authors), upstream commit
`add98b377ffc39e5d73789a874a08c5e369790ce`, archived under
`docs/third_party/securetrade/` (LICENSE + NOTICES.md). The package name is
changed from `com.securetrade` to `com.fontainerepublic.trade.securetrade`
and the sound/texture namespace from `securetrade` to `fontainerepublic`.

## What was copied verbatim

- `menu/TradeMenuType` — custom `fontainerepublic:trade_menu` MenuType holder
- `menu/TradeMenu` — 54-slot shared container (two 27-slot escrow offer
  regions + player inventory/hotbar), real draggable slots
- `menu/TradeSession` / `menu/TradeSessionManager` — escrow model: items
  physically move into the offer cells; both sides ready -> atomic swap
- `client/TradeScreen` — the real draggable 54-slot screen + XP slider
- `SecureTradeSounds`, `XPMath`, `TradeCommand`, `TradeRules`,
  `TradeItemValidator`, `TradeMessages`, `TradeLogger`,
  `TradeHistoryManager`
- `network/*` packets + `network/TradeNetwork` (own sub-channel
  `fontainerepublic:trade`, protocol "1")
- `forge/TradeConfig`, `forge/ForgePlatformHelper`

## FR money leg (FR-TRADE-004)

- `TradeSession` carries `player1Money`/`player2Money` (pending +
  synchronous offer fields, exactly like the XP leg).
- `FRSettlementBridge` routes settlement through FR
  `EconomyService.executeTradeSettlement` (offer legs + 5% payer tax,
  `ConfigManager.tradeTaxRatePercent()`, fail closed), resolves both parties
  through `SubjectRegistryService`, and writes an authoritative
  `AuditService` record (FINANCE "trade-settled").
- `TradeScreen` adds a money offer input + both sides' money display.

## Deviations from upstream (documented)

- `Services.PLATFORM` is bound directly by the FR mod bootstrap instead of
  via `ServiceLoader` (FR is a single-module Forge build; no
  `META-INF/services` multi-loader split).
- `TradeNetwork` lives on a FR-namespaced sub-channel
  (`fontainerepublic:trade`) rather than `securetrade:main`.
