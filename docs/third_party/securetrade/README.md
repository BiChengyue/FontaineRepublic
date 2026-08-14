# Secure Trade — Third-Party Source Archive (FR-TRADE-002-A)

This directory archives the exact upstream revision that FR-TRADE-002-A
(FontaineRepublic Secure Trade controlled adaptation) reviews and adapts.

## Upstream identification

- Project: Secure Trade (Minecraft Forge/Fabric player-trade mod)
- Repository: https://github.com/Navielon/SecureTrade
- Modrinth: https://modrinth.com/mod/secure-trade (project `ptgA3PQG`)
- Branch: `version/1.20.1`
- Exact commit (archived): `add98b377ffc39e5d73789a874a08c5e369790ce`
- Commit title: "Release Secure Trade 1.2.1 maintenance fixes" (2026-06-20)
- License: MIT — "Copyright (c) 2026 Secure Trade Mod Authors"

## Contents

- `LICENSE` — the upstream MIT permission notice, verbatim, at the exact
  commit (`git show add98b3...:LICENSE`).
- `secure-trade-add98b377ffc39e5d73789a874a08c5e369790ce.zip` —
  `git archive` of the full tree at the exact commit (all tracked source,
  resources, scripts and the LICENSE).

## Adaptation boundary (per docs/architecture/fr-trade-002-a-...)

FontaineRepublic adapts the following upstream *concepts* only, re-implemented
in its own module/service/command structure (no upstream source is copied
verbatim):

- request expiry, cooldown and mutual-request auto-accept;
- two 27-slot offer areas;
- item and XP offers;
- offer changes clearing both ready states;
- both-ready countdown;
- item blacklist and distance/dimension policy (config hooks);
- disconnect cancellation and history presentation.

Explicitly NOT copied: Secure Trade mod bootstrap, MenuType/sound registry
objects, strict network channel, independent request/session/history authority,
memory-only escrow, history-before-settlement ordering, overflow drops, and
direct XP mutation without recovery. No upstream endorsement is implied.
