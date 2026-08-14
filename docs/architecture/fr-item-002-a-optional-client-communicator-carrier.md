# FR-ITEM-002-A — Optional-Client Communicator Carrier Architecture

> **Task ID:** FR-ITEM-002-A
> **Status:** Design Candidate — not Human Approval
> **Implementation Status:** Not authorized
> **Supersedes if approved:** FR-ITEM-001-A custom-item registration decision
> **Purpose:** Restore Forge-without-FR connection compatibility while retaining
> the Message Water Mirror as the in-world mobile entry.

## 1. Runtime Finding

The 2026-08-14 Level-3 test proved that a Forge 1.20.1 client without
FontaineRepublic cannot join the dedicated server. The handshake reports the
missing item registry entry `fontainerepublic:communicator`.

The failure chain is deterministic:

1. `FontaineRepublic` registers `FRItems` on the common mod event bus;
2. `FRItems` adds `fontainerepublic:communicator` to `ForgeRegistries.ITEMS`;
3. the server sends a registry snapshot containing that item;
4. an FR-absent Forge client has no matching item entry and is disconnected.

`displayTest="IGNORE_SERVER_VERSION"` and the FR network predicate accepting
`NetworkRegistry.ABSENT` cannot waive registry equality. The channel is already
optional; the public item registry is the blocker.

## 2. Goals Preserved

- installed FR client: the Water Mirror acts as a Chinese-first mobile UI;
- Forge client without FR: can join and use every separately approved command
  path, receiving no FR packet;
- server remains authoritative for identity, permissions and business state;
- the Water Mirror remains issued under Hydro Archon/server-console control;
- client presentation never becomes a source of identity or authority;
- phone-only features may remain unavailable to an FR-absent client where Human
  explicitly chose that boundary, such as the personal land-rights list.

## 3. Compatibility Scope

The required target is:

```text
Forge 47.4.18 client without FontaineRepublic -> accepted
```

A truly vanilla, non-Forge client reports `ACCEPTVANILLA`, not `ABSENT`. Current
`NetworkProtocol.serverAccepts` does not accept it. Pure-vanilla compatibility
is outside this candidate and requires a separate Human decision and test line.

## 4. Carrier Decision

### 4.1 No public custom registry entry

FR-ITEM-002 removes the common `DeferredRegister<Item>` entry. The Water Mirror
uses a vanilla `minecraft:clock` carrier in Minecraft 1.20.1.

The carrier is recognized by bounded NBT under one namespaced compound:

```text
FontaineRepublicDevice:
  Schema: int
  Kind: "message_water_mirror"
  DeviceId: canonical UUID string
  OwnerUuid: canonical player UUID string
  IssuedAt: long
  KeyId: bounded string
  Signature: bounded byte array / lowercase hex
CustomModelData: int
display.Name / Lore: presentation only
```

Minecraft 1.20.1 uses `ItemStack` NBT; post-1.20.5 data components are not used.

The encoded contract is deliberately singular and bounded:

- `KeyId` is printable ASCII, 1 through 32 bytes;
- `Signature` is exactly 32 raw bytes from HMAC-SHA-256; hexadecimal and
  alternate encodings are rejected rather than accepted as a second form;
- UUIDs use the lowercase canonical hyphenated representation;
- `CustomModelData` must equal the one configured Water Mirror model value;
- presentation name is at most 64 Unicode code points and lore is at most 8
  lines of 128 code points each;
- unknown authoritative fields, oversized values and duplicate semantic forms
  fail closed.

### 4.2 Server validation

One `CommunicatorAuthenticator` performs every authoritative carrier check:

1. item ID is the selected vanilla carrier;
2. the device compound has exactly the supported schema and bounded fields;
3. kind, UUIDs, timestamp and key identifier are canonical;
4. HMAC verifies a versioned canonical byte sequence in this exact order:
   fixed domain separator, carrier resource ID, schema, kind, device UUID,
   owner UUID, issued time and key ID. Variable-length UTF-8 fields use a
   fixed-width length prefix; integers use fixed-width big-endian encoding;
5. the authenticated player UUID equals `OwnerUuid` where ownership is required.

The signing key is server-only and is never written into the stack or sent to
the client. `KeyId` resolves through a bounded server-side key map to exactly
one verification key; unknown, duplicate or disabled identifiers fail closed.
Exactly one key is active for issuance. Older keys may remain verification-only
during an explicitly configured reissue period. Because devices have no
automatic expiry, retiring an old key intentionally invalidates every remaining
device signed by it and therefore requires an audited reissue plan; rotation
must never silently brick all devices. A client marker, name, lore or
`CustomModelData` alone is never an authoritative device.

### 4.3 Authority boundary

The Water Mirror controls access to mobile presentation and features that Human
explicitly made device-only. It does not grant citizenship, office, land rights,
money authority or emergency power.

Copying a signed stack for the same owner may duplicate the UX carrier but does
not duplicate business authority. A copy used by another player fails owner
binding. Full revocation, transfer and duplicate-serial enforcement would need
a persistent device registry and therefore a separately aligned new runtime
module; FR-ITEM-002 does not silently create that module.

## 5. Issuance

The existing `/give ... fontainerepublic:communicator` path is retired.

A scoped command adapter may issue the signed vanilla carrier only for:

- strict local dedicated-server console; or
- the configured Hydro Archon player, after server-side verification.

The command generates `DeviceId`, signs the canonical fields, writes display
NBT and creates one carrier. The command is audited without exposing signing
key material.

Key rotation rules must be explicit. Removing an old key invalidates all stacks
signed by it; keeping an old verification key preserves them.

## 6. Command and Mobile Entry Separation

Device checks must not be embedded blindly in every domain service.

```text
Command adapter -------------------+
                                   +--> authoritative domain service
Signed-device C2S / interaction ---+
                |
                +--> CommunicatorAuthenticator
```

- command adapters apply the Human-approved command policy;
- mobile adapters require an authenticated carrier;
- both converge on the same server-authoritative service;
- domain services revalidate identity, permission, revision and business
  invariants at the final mutation boundary.

This separation makes an FR-absent command path possible without making a fake
phone authoritative.

## 7. Client Presentation

FR clients may render the vanilla clock as the Water Mirror through a
`CustomModelData` predicate. A server resource pack may provide the same look to
FR-absent clients.

If `assets/minecraft/models/item/clock.json` is overridden, every vanilla clock
time/angle override must be preserved. A client that has neither FR resources
nor the resource pack sees a named clock; functionality and identity remain
unchanged.

No new FR item, block, menu or sound registry entry may be introduced solely
for this presentation.

## 8. Interaction Routing

The client interaction router is hand-aware and deterministic:

1. a valid main-hand Water Mirror has priority for its approved air action;
2. an unrelated usable offhand item must not suppress that action;
3. a main-hand non-device keeps normal Minecraft behavior;
4. an offhand-only Water Mirror follows an explicit action rule and must not
   duplicate a main-hand action;
5. entity, block and air entry paths use the same hand-resolution policy;
6. when an FR block action claims the interaction, it consumes the original
   block activation; otherwise vanilla behavior passes through.

Every device-originated C2S request is reauthenticated on the server. Client
hand routing is UX, not authority.

## 9. Existing-World Migration

Removing the registry entry is unsafe without an explicit migration decision.

### Option A — Alpha invalidation and reissue (recommended now)

- remap the missing old item ID to `minecraft:clock` so the world loads;
- old stacks become ordinary clocks and do not pass device authentication;
- reissue signed Water Mirrors to approved players;
- record the compatibility break in release notes.

The missing-mapping handler is registered only on the common/server world-load
migration path and must not become a client-side authority hook. Option A is not
accepted until an isolated save copy proves remapping for offline `player.dat`,
unloaded chunks, containers and item entities as each is decoded. Tests must
also prove that legacy NBT and `CustomModelData` may survive the remap only as
presentation residue: the unsigned clock is rejected by authentication even if
it still looks like a Water Mirror. Any decode path that produces an unknown or
fallback item blocks release rather than silently deleting the stack.

### Option B — Two-release online migration

Keep the old registry for one release, replace loaded player/container/entity
stacks with signed carriers, then remove the registry. Unloaded chunks and
offline player data remain a coverage risk.

### Option C — Offline full-save migration

Back up and transform player data, region containers and item entities before
startup. This gives the broadest preservation and the largest operational risk.

FR-ITEM-002 records Option A as the Alpha recommendation but does not authorize
it until Human confirms the migration choice.

## 10. Security and Failure Rules

- malformed, unknown-version, wrong-owner or invalid-signature carriers fail
  closed;
- no fallback to display name, lore, item name or `CustomModelData`;
- no raw signing key or full signature in normal logs;
- failed issuance creates no usable carrier;
- server reload/key rotation cannot silently reinterpret an old stack;
- business actions never trust NBT-provided UUIDs over the authenticated player;
- FR-absent clients receive no FR packets.

## 11. Validation Gate

Implementation must prove:

- Forge-without-FR joins successfully with channel `ABSENT`;
- current FR client joins and renders/uses the signed carrier;
- invalid marker, signature and owner are rejected;
- unrelated clocks remain ordinary clocks;
- main-hand device works with a shield in the offhand without duplicate opens;
- claimed block interactions do not also toggle/open the block;
- approved command paths work without an FR client and receive no FR packet;
- old registry migration follows the Human-selected option;
- the selected remap covers offline player data, unloaded containers and item
  entities and rejects visually ambiguous legacy stacks as unsigned;
- Dedicated Server loads no client class.

## 12. Implementation Staging

1. Human confirms compatibility and migration decisions.
2. Introduce carrier codec/authenticator and issuance tests without removing the
   old registry.
3. Migrate duplicated registry-key predicates to the authenticator or to
   command/mobile adapter boundaries.
4. Implement the selected old-stack migration.
5. Remove `FRItems` registration and custom item ID.
6. Run matching-client, FR-absent Forge client and restart evidence cycles.

## 13. Non-Goals

- pure-vanilla client support;
- a new DeviceRegistry runtime module;
- device transfer/revocation policy;
- citizenship, office or business permission changes;
- Secure Trade integration;
- physical ID cards or expiring credentials.

## 14. Decision Gate

Before implementation Human must confirm:

1. compatibility target remains Forge-without-FR, not pure vanilla;
2. old custom Water Mirrors use Alpha invalidation/reissue or full preservation;
3. signed device remains a mobile/entry credential, not independent business
   authority.

Approval of this document would authorize only a separately scoped
implementation task.
