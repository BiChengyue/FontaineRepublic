# FontaineRepublic Institution Physical Interaction and Mobile Boundary v1.0

> **Task ID:** FR-INST-001-A  
> **Status:** Design Candidate — Pending Independent Review and Human Approval  
> **Project:** FontaineRepublic  
> **Platform:** Minecraft Forge 1.20.1 / Forge 47.4.18 / Java 17  
> **Scope:** Cross-module interaction boundary for Parliament, Government, Court, and Central Bank  
> **Implementation Status:** Not authorized

---

## 1. Purpose

This document defines where and how players may perform authoritative actions
for FontaineRepublic's four state institutions:

1. Parliament;
2. Government;
3. Court;
4. Central Bank.

The institutional names map to the existing module plan as follows:

| Institution | Existing/future module boundary |
|---|---|
| Parliament | `Parliament` module |
| Government | `Government` module |
| Court | Existing `Justice` module name |
| Central Bank | Institutional authority within `Economy` unless a later approved design creates a separate module |

This document does not rename an existing module or authorize a new one.

The four institutions must exist as meaningful places in the Minecraft world.
Their authoritative work and formal public-service procedures must not be
reduced to menus or commands that can be used from anywhere.

The optional FontaineRepublic client experience is treated as a **mobile
device**. It improves access to information, communication, preparation, and
approved personal services. It does not become a portable parliament,
government office, courtroom, or central-bank workplace.

This design is an interaction-boundary amendment. It does not define political
rules, legal doctrine, monetary policy, building appearance, or gameplay
content.

---

## 2. Normative Language

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are
normative requirements.

- **Authoritative institutional action:** an action that changes official
  institutional data, creates a legally or administratively effective record,
  exercises public office, or completes a formal state procedure.
- **Institution facility:** a server-registered physical building or bounded
  area belonging to one of the four institutions.
- **Institution terminal:** a server-registered block, entity, or anchored
  interaction point inside a valid institution facility.
- **On-site context:** short-lived server-side proof that a player directly
  interacted with a valid institution terminal while physically present at the
  corresponding facility.
- **Mobile capability:** an action intentionally allowed without an on-site
  context.
- **Preparation capability:** an action that prepares, drafts, schedules, or
  previews a formal procedure but does not complete it.

---

## 3. Core Decisions

### 3.1 Physical Institution Rule

Every authoritative institutional action MUST require:

1. a registered institution facility;
2. a registered terminal belonging to that facility;
3. direct server-observed interaction with that terminal;
4. continued player presence at the facility;
5. normal identity, role, permission, and business validation.

Player role, OP level, possession of an item, an open GUI, or knowledge of a
command MUST NOT independently satisfy the physical institution rule.

### 3.2 Mobile Device Rule

The optional client experience MAY provide:

- public information and news;
- personal notifications and reminders;
- player-to-player and approved group communication;
- appointment and scheduling tools;
- drafts and form preparation;
- personal account information;
- approved routine personal banking operations;
- navigation and facility information.

The optional client experience MUST NOT independently authorize or complete an
on-site institutional action.

### 3.3 Server Authority

The server remains the sole authority for:

- player identity;
- facility registration;
- terminal identity;
- player location and dimension;
- on-site context issuance and validity;
- institutional roles and permissions;
- authoritative institutional mutations.

A client-provided facility ID, position, terminal ID, distance, session token,
or claim of physical presence MUST be treated only as untrusted input.

### 3.4 Optional Client Parity

Installing the FontaineRepublic client features MUST NOT grant additional
institutional authority.

Players without optional client features MUST be able to access core
institutional procedures through standard Minecraft interaction plus command
or chat feedback. They remain subject to the same physical, identity, role,
and business validation as players with client features.

---

## 4. Capability Classes

Every future institutional action MUST be assigned exactly one primary
interaction class.

| Class | Meaning | Physical presence |
|---|---|---|
| `REMOTE_INFORMATION` | Read-only public or personal information | Not required |
| `REMOTE_PERSONAL_SERVICE` | Explicitly approved personal service with no exercise of public office | Not required |
| `REMOTE_PREPARATION` | Draft, appointment, preview, or pre-filled material without official effect | Not required |
| `ONSITE_PUBLIC_SERVICE` | Formal public procedure completed with an institution | Required |
| `ONSITE_OFFICIAL_DUTY` | Exercise of institutional office or state authority | Required |
| `EMERGENCY_RECOVERY` | Exceptional restoration or continuity mechanism | Special audited authority |

An action MUST NOT be implicitly treated as remote merely because it has a
command, GUI button, or network packet.

If an action has both preparation and completion stages, the preparation MAY
be remote while completion MUST use the appropriate on-site class.

---

## 5. Institutional Capability Matrix

This matrix defines boundary categories, not final political or business
rules. Future module designs must refine individual actions without weakening
these boundaries.

### 5.1 Parliament

| Capability | Class |
|---|---|
| View public agenda, bills, notices, and published voting results | `REMOTE_INFORMATION` |
| Receive meeting reminders or public consultation notices | `REMOTE_INFORMATION` |
| Draft a proposal without submitting it | `REMOTE_PREPARATION` |
| Prepare comments or supporting material | `REMOTE_PREPARATION` |
| Formally submit a parliamentary proposal | `ONSITE_OFFICIAL_DUTY` |
| Exercise committee or chamber authority | `ONSITE_OFFICIAL_DUTY` |
| Participate in formal debate as an institutional act | `ONSITE_OFFICIAL_DUTY` |
| Cast an official parliamentary vote | `ONSITE_OFFICIAL_DUTY` |
| Approve a state project or other parliamentary decision | `ONSITE_OFFICIAL_DUTY` |

The mobile client may inform and prepare. It MUST NOT function as a remote
parliamentary chamber.

### 5.2 Government

| Capability | Class |
|---|---|
| View departments, policies, notices, procedures, and service status | `REMOTE_INFORMATION` |
| Receive administrative notifications | `REMOTE_INFORMATION` |
| Schedule an appointment or prepare an application | `REMOTE_PREPARATION` |
| View the status of one's own submitted matter | `REMOTE_INFORMATION` |
| Formally submit or confirm an administrative application | `ONSITE_PUBLIC_SERVICE` |
| Issue a permit, certificate, appointment, or administrative decision | `ONSITE_OFFICIAL_DUTY` |
| Exercise departmental authority or official workflow approval | `ONSITE_OFFICIAL_DUTY` |
| Sign an authoritative government record | `ONSITE_OFFICIAL_DUTY` |

The mobile client may be a service guide and appointment tool. It MUST NOT
become a portable government office.

### 5.3 Court

| Capability | Class |
|---|---|
| View public judgments, hearing schedules, and public case information | `REMOTE_INFORMATION` |
| View one's own case status and receive summons or notices | `REMOTE_INFORMATION` |
| Prepare a filing, appeal, statement, or evidence index | `REMOTE_PREPARATION` |
| Formally file or accept a case | `ONSITE_PUBLIC_SERVICE` |
| Formally submit evidence into the court record | `ONSITE_PUBLIC_SERVICE` |
| Conduct a hearing or exercise judge, clerk, or other court authority | `ONSITE_OFFICIAL_DUTY` |
| Issue a judgment, order, or legally effective court record | `ONSITE_OFFICIAL_DUTY` |
| Formally lodge or accept an appeal | `ONSITE_PUBLIC_SERVICE` |

The mobile client may communicate schedules and prepare material. It MUST NOT
become a portable courtroom or create a legally effective court record by
itself.

### 5.4 Central Bank

| Capability | Class |
|---|---|
| View personal balance, transaction history, and account notifications | `REMOTE_INFORMATION` |
| Routine personal transfer | `REMOTE_PERSONAL_SERVICE` |
| Routine personal deposit or withdrawal approved for mobile use | `REMOTE_PERSONAL_SERVICE` |
| View published central-bank information | `REMOTE_INFORMATION` |
| Prepare an appointment or official request | `REMOTE_PREPARATION` |
| Change treasury or state-account balances | `ONSITE_OFFICIAL_DUTY` |
| Exercise currency, issuance, reserve, settlement, or other central-bank authority | `ONSITE_OFFICIAL_DUTY` |
| Freeze, unfreeze, correct, or administratively alter an account | `ONSITE_OFFICIAL_DUTY` |
| Approve exceptional or institution-level transfers | `ONSITE_OFFICIAL_DUTY` |

"Routine personal deposit or withdrawal" is a boundary decision, not yet a
currency-model decision. A future Economy design must define whether it moves
funds between a wallet, cash item, account, or another representation.

OP level alone MUST NOT authorize central-bank duties from an arbitrary
location.

---

## 6. Facility and Terminal Model

### 6.1 Facility Registration

An institution facility MUST have a stable server-side identity and at least:

- `facilityId`;
- institution type;
- dimension;
- bounded physical region;
- lifecycle state;
- registered terminals;
- revision or equivalent change marker.

Suggested lifecycle states are:

- `ACTIVE` — normal institutional actions allowed;
- `SUSPENDED` — public information remains available, mutations blocked;
- `RELOCATING` — old facility blocked while controlled relocation occurs;
- `DISABLED` — facility invalid or administratively withdrawn.

The exact persistence model belongs to the future Land/City/Institution design
and is not implemented by this task.

### 6.2 Terminal Registration

An institution terminal MUST be anchored to a registered facility and MUST
have:

- a stable terminal identity;
- institution type;
- dimension and block/entity position;
- allowed capability set;
- lifecycle state;
- integrity information sufficient to reject replacement or cloning.

A handheld item with the same name or NBT MUST NOT automatically become an
institution terminal. If an item participates in a procedure, the server must
observe its use at a valid anchored terminal inside the correct facility.

### 6.3 Building and Terminal Validation

Before issuing an on-site context, the server MUST verify:

1. facility exists and is `ACTIVE`;
2. terminal exists and is active;
3. facility and terminal institution types match;
4. terminal is still located in the facility's registered region;
5. player is in the same dimension;
6. player is within the configured interaction distance;
7. the interaction was observed by the logical server;
8. requested action belongs to the terminal's allowed capability set.

---

## 7. On-Site Context

### 7.1 Purpose

The on-site context prevents commands, GUI screens, or packets from bypassing
physical presence.

It is a short-lived server-side runtime fact, not authoritative persistent
business data and not a client-owned credential.

### 7.2 Conceptual Fields

An on-site context should bind at least:

- player UUID;
- institution type;
- facility ID;
- terminal ID;
- allowed action or action category;
- issue time;
- expiry time;
- facility/terminal revision at issue time;
- source dimension and position.

This is a conceptual contract, not authorization to create a class or API.

### 7.3 Validity

The server MUST revalidate the context at the final mutation boundary; this
final mutation-time validation remains mandatory even when the server also
detects absence through periodic checks and events.

The server MUST detect that a player has left the facility through bounded
periodic presence checks and through lifecycle and location events (for
example, movement beyond the permitted distance, dimension change, logout,
death, or server shutdown).

Leaving the permitted facility or terminal distance immediately invalidates
the on-site context. Returning to the facility MUST NOT restore the old
context; the player MUST interact with a registered terminal again before a
new on-site context may be issued.

The context becomes invalid when any of the following occurs:

- expiry;
- player leaves the permitted facility or terminal distance;
- player changes dimension;
- player logs out;
- player dies;
- facility or terminal is suspended, disabled, moved, destroyed, or revised;
- action does not match the context capability;
- institutional role or business permission changes.

Keeping a GUI open MUST NOT keep an on-site context valid.

### 7.4 Runtime Storage

Any future active-context registry must be server-run scoped, bounded, and
cleared on shutdown. It MUST NOT become a second authoritative database or a
static global player cache.

---

## 8. Authoritative Request Flow

### 8.1 On-Site Action

```text
Player physically interacts with registered terminal
    ↓
Logical server validates facility + terminal + position
    ↓
Server issues short-lived on-site context
    ↓
Command / standard interaction / optional GUI submits action intent
    ↓
Institution access boundary revalidates on-site context
    ↓
Institution Service validates identity + role + business rules
    ↓
Repository / SavedData mutation
    ↓
Audit record + bounded feedback
```

### 8.2 Remote Mobile Action

```text
Command / chat / optional mobile GUI submits remote action intent
    ↓
Capability registry confirms action is remote-enabled
    ↓
Institution Service validates identity + business rules
    ↓
Repository / SavedData mutation when applicable
    ↓
Audit record + bounded feedback
```

The mobile route MUST NOT accept an on-site action merely because the player
has sufficient role or OP permission.

---

## 9. Command Boundary

### 9.1 Commands Remain an Interface

Commands remain a supported first-generation interface and a compatibility
path for players without optional client features.

"Command path available" means the interface remains usable. It does not mean
every action is valid from every location.

### 9.2 Location-Sensitive Commands

For an on-site action, a command MUST:

1. parse syntax;
2. call the same application/service path as all other interfaces;
3. require a valid on-site context;
4. fail without mutation when the context is missing or stale;
5. return a clear message directing the player to the correct institution and
   terminal.

Dynamic physical validation MUST NOT live only in Brigadier `.requires()`.
The final mutation boundary must revalidate it.

### 9.3 Administrative Commands

Diagnostic commands MAY remain remotely available to authorized operators if
they are read-only.

Commands that perform institutional mutations MUST follow the same on-site
rule as GUI and standard interaction. OP level is not a general bypass.

Emergency commands are governed separately by Section 13.

---

## 10. Optional Client as Mobile Device

### 10.1 Allowed Responsibilities

The optional client MAY provide:

- news and announcement feeds;
- messaging and notification presentation;
- personal account views and approved mobile banking screens;
- calendars, appointments, and reminders;
- maps and directions to institution facilities;
- form drafting and pre-filling;
- display of public institutional records;
- presentation of an active on-site workflow after server-observed terminal
  interaction.

### 10.2 Forbidden Responsibilities

The optional client MUST NOT:

- create or persist authoritative institutional state;
- assert that the player is inside a facility;
- issue its own trusted on-site context;
- keep an expired on-site workflow alive;
- convert an on-site action into a remote action;
- bypass terminal capability, role, permission, or business validation;
- provide exclusive access to a core institutional procedure.

### 10.3 On-Site GUI

An optional GUI MAY open after server-observed interaction with a valid
terminal. The GUI is presentation only.

Every mutation submitted from that GUI MUST re-enter the same server-side
validation path used by commands and standard Minecraft interaction.

---

## 11. Players Without Optional Client Features

Players without optional client features MUST be able to:

- right-click or otherwise use the same registered institution terminal;
- receive bounded chat or command feedback;
- enter required arguments through commands, chat prompts, books, containers,
  or another standard Minecraft mechanism;
- complete the same core procedure when all server-side rules pass.

They MUST NOT be required to send a FontaineRepublic custom packet.

Their reduced presentation quality MUST NOT reduce their legal,
administrative, parliamentary, judicial, or financial rights.

### 11.1 Impact on the Existing Presentation Model

Books, containers, and similar standard Minecraft input mechanisms refine the
existing "command + chat" presentation model defined in architecture.md
(Section 5.1, core constraint, line 616; Section 5.2, "missing optional
client" interaction mode, line 625). They remain input and presentation
channels only.

They MUST NOT become a new source of authority and MUST NOT bypass registered
terminal interaction, on-site context validation, or any other server-side
check.

---

## 12. Security and Abuse Cases

Future implementation and review must explicitly cover:

| Abuse case | Required response |
|---|---|
| Client sends a fake facility or terminal ID | Ignore claim; resolve and validate server-side |
| Client replays an old action or context | Reject expired or already-consumed context where single-use policy applies |
| Player opens GUI and leaves building | Reject final action |
| Player changes dimension or dies | Invalidate context |
| Terminal block is copied or renamed | Reject unregistered copy |
| Registered terminal is destroyed or moved | Suspend terminal and invalidate contexts |
| Player gains OP away from institution | Do not grant on-site authority |
| Role changes while GUI is open | Revalidate role and reject if no longer valid |
| Packet and command paths disagree | Service/access boundary remains authoritative |
| Concurrent institution mutations occur | Use module revision/transaction controls |

"Already-consumed context" rejection applies where a future policy defines
single-use behavior for on-site contexts; this design does not decide the
single-use policy (see Open Question 19.4). Expired or otherwise invalid
contexts are rejected regardless of that decision.

---

## 13. Damage, Relocation, and Emergency Recovery

### 13.1 Normal Failure

If a required facility or terminal is invalid, authoritative actions MUST fail
closed. Read-only information MAY remain available.

The failure message SHOULD identify the unavailable institution without
revealing sensitive internal state.

### 13.2 Relocation

Relocation MUST be an explicit server-side administrative process. It SHOULD:

1. suspend the old facility;
2. invalidate old terminal contexts;
3. register and validate the new facility;
4. activate new terminals;
5. leave an audit trail.

Copying or rebuilding blocks MUST NOT silently relocate institutional
authority.

### 13.3 Emergency Recovery

Emergency recovery exists to restore institutional availability, not to make
normal institutional work remotely convenient.

Emergency recovery MUST require explicit elevated authority and MUST record:

- actor UUID;
- time;
- reason;
- affected institution/facility/terminal;
- before and after state;
- operation result.

An emergency operation that directly performs an institutional business
mutation requires a separate, explicit policy decision. It is not authorized
by this design.

---

## 14. Audit Requirements

Every successful authoritative institutional action SHOULD produce an audit
record sufficient to identify:

- actor UUID;
- institution and action type;
- facility and terminal;
- execution time;
- relevant business record identifier;
- result and resulting revision.

Rejected attempts SHOULD be logged when they indicate:

- spoofed or stale context;
- unauthorized institutional action;
- invalid facility/terminal state;
- repeated replay or bypass attempts.

Routine user mistakes SHOULD receive bounded feedback without excessive log
noise.

---

## 15. Dependency Boundary

### 15.1 Future Shared Access Boundary

The four institution modules SHOULD depend on one shared institution-access
boundary rather than implementing position and terminal checks independently.

Conceptually:

```text
Parliament / Government / Court / Central Bank action
    ↓
Shared Institution Access Boundary
    ↓
Facility Directory + Terminal Directory + Server Player State
    ↓
Module Service
```

This design does not authorize a concrete interface, module, or class name.

### 15.2 Land and City Integration

Facility geometry and public-building registration naturally relate to future
Land and City modules. To avoid circular ownership:

- Land/City may own spatial registration data;
- the shared institution-access boundary may consume that data through a
  service contract;
- Parliament, Government, Court, and Central Bank modules must not directly
  inspect Land/City NBT;
- business modules must not duplicate building-region data.

Until the required spatial contracts exist, production on-site institutional
workflows MUST NOT be implemented with temporary hard-coded coordinates.

---

## 16. Impact on Existing Architecture

This design intentionally refines the following existing statements.

### 16.1 Optional Client Contract

Existing rule (architecture.md:616, Section 5.1 core constraint;
architecture.md:625, Section 5.2 "missing optional client" interaction mode):

> All core functions are completed through commands and standard Minecraft
> interaction.

Refined meaning:

> All core functions remain accessible without optional client features, but
> on-site functions require standard Minecraft interaction with a registered
> institution terminal and cannot be completed by a remote command alone.

### 16.2 Command Availability

Existing rule (architecture.md:665, Section 5.4 development principle 6):

> Command paths must always remain available.

Refined meaning:

> The command interface remains available for compatible workflows and clear
> feedback. Availability does not bypass location, terminal, role, or business
> validation.

### 16.3 Commands as First-Generation UI

Commands remain the first-generation UI. They are not the source of authority.
On-site commands are a textual continuation of a valid physical interaction.

### 16.4 Economy Candidate

Human approval of FR-INST-001-A permits preparation of a separately scoped
revision to FR-ECO-001-A that reflects the constraints below. It does not
automatically modify or approve FR-ECO-001-A. It does not authorize Economy,
Central Bank, command, facility, GUI, packet, or any other implementation.

The current Economy candidate requires revision before implementation:

- personal `balance`, `history`, routine `pay`, and Human-approved personal
  deposit/withdraw capabilities may be remote/mobile;
- treasury and central-bank authority mutations require an active Central Bank
  facility and appropriate terminal;
- `.requires(OP level 2)` is insufficient for central-bank mutations;
- command, standard interaction, and future GUI must share the same service
  and institution-access validation path;
- Economy implementation staging must not add central-bank mutation commands
  before the required on-site access contract exists.

### 16.5 Future Government, Parliament, and Justice Designs

Their command and GUI designs must classify every action using Section 4 and
must not introduce remote completion of authoritative institutional work.

---

## 17. Validation Requirements for Future Implementation

Any future implementation claiming compliance must verify at least:

### 17.1 Positive Paths

- correct player at correct terminal can execute allowed action;
- optional client GUI and no-client interaction reach the same result;
- approved mobile actions work away from facilities;
- all four institution types are correctly isolated.

### 17.2 Negative Paths

- command from outside facility is rejected without mutation;
- GUI opened on-site but submitted after leaving is rejected;
- player leaves, returns to the facility, and submits with the old on-site
  context is rejected;
- wrong terminal or wrong institution is rejected;
- copied/renamed item is rejected;
- wrong dimension is rejected;
- expired/replayed context is rejected;
- OP without on-site context is rejected for institutional mutation;
- no-client path cannot bypass validation;
- optional-client packet cannot spoof facility presence.

### 17.3 Lifecycle Paths

- logout, death, dimension change, shutdown, terminal destruction, facility
  suspension, and relocation invalidate active contexts;
- server restart does not restore stale contexts;
- normal facility data persists through the authoritative storage path;
- emergency recovery is audited.

### 17.4 Regression Paths

- remote information remains accessible;
- approved personal mobile banking remains accessible;
- read-only operator diagnostics remain available;
- command and optional-client capability contracts remain compatible;
- no static global player cache or client-authoritative copy is introduced.

---

## 18. Non-Goals

This design does not define or authorize:

- building layouts, architectural style, or construction materials;
- Citizen implementation;
- Economy implementation;
- Government implementation;
- Parliament implementation;
- Court/Justice implementation;
- Land or City implementation;
- GUI, screen, HUD, or packet implementation;
- political rules, elections, constitutions, or legislative procedure;
- legal standards, sentences, or judicial doctrine;
- monetary policy, taxation, interest, markets, or commercial banking;
- hard-coded institution coordinates;
- emergency business-action policy.

---

## 19. Open Design Questions

These questions must be answered by later scoped designs and are not blockers
to approving this boundary:

1. Which module owns facility records before and after Land/City exists?
2. Which standard Minecraft terminal forms are permitted?
3. What is the exact distance and timeout policy for each workflow, and what
   concrete leave-detection mechanism applies (bounded periodic presence
   checks, lifecycle/location events, or a defined combination)?
4. Are on-site contexts single-use for all mutations or only selected actions?
5. What concrete value movement does personal deposit/withdraw represent?
6. Which Central Bank information is public, personal, official, or secret?
7. Which preparation artifacts persist, and which module owns them?
8. What emergency continuity procedure applies when a facility is unavailable?

---

## 20. Decision Summary

If approved, FontaineRepublic adopts the following project-level boundary:

1. Parliament, Government, Court, and Central Bank are physical institutions.
2. Their authoritative work and formal procedures require valid physical
   facility and terminal interaction.
3. The optional client is a mobile information, communication, preparation,
   and approved personal-service tool.
4. Commands and GUI remain interfaces but never create institutional authority.
5. Players without optional client features retain equal access through
   standard Minecraft interaction and command/chat presentation.
6. One shared server-authoritative institution-access boundary prevents
   duplicated location logic and interface bypass.
7. Existing Economy and future institutional module designs must conform before
   implementation.

Approval of this document authorizes architecture alignment only. It does not
authorize implementation.
