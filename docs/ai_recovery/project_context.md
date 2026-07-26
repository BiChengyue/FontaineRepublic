# FontaineRepublic Project Context

## Project Identity

- **Project name**: FontaineRepublic
- **Chinese name**: 枫丹共和国核心系统
- **Type**: Minecraft Forge server core mod
- **Goal**: Provide a nation simulation/RPG framework for the FontaineRepublic Minecraft server
- **Scope**: Player identity, land ownership, national economy, city planning, government system, judicial system, AI-assisted governance

## Technology Stack

| Component | Version |
|-----------|---------|
| Minecraft | 1.20.1 |
| Forge | 47.4.18 |
| Java | 17 |
| Gradle | 8.5 |
| Mod ID | fontainerepublic |

## Architecture Principles

### Server Authority

All core data is managed by the server. The client is only responsible for display, input, and requests. The client must never decide game state (economy, permissions, land ownership, political state, persistent data).

Data flow: `Client → Network Packet → Server Logic → Data Storage`

### Modular Design

The system is divided into functional modules. Modules remain independent, communicate through defined interfaces, and must not directly modify other modules' internal data.

### Data-First Development

Development order: `Data Model → Business Logic → Network Sync → GUI`

GUI must never be developed before data.

### Module List

```
Core ─┬─ Citizen
      ├─ Land
      ├─ Audit
      ├─ Economy
      ├─ Resource
      ├─ City
      ├─ Government
      ├─ Parliament
      ├─ Justice
      ├─ AI
      └─ Client
```

## Data Storage Strategy

- **Alpha stage**: Minecraft SavedData / NBT (+ deferred JSON backup from Alpha 0.2)
- **Future**: Database upgrade possible as needed
- **Phase 0**: No JSON backup/export. Postponed to Alpha 0.2.

## AI Collaboration Model

Two-AI workflow:

| Role | AI | Responsibility |
|------|----|---------------|
| Architect/Reviewer | ChatGPT | Architecture design, technical design review, code review |
| Implementer | Claude Code | Implementation, build verification, state maintenance |

**Key rule**: The architect approves designs; the implementer builds to spec. The implementer may raise risks but must not change approved scope without re-review.

## Development Phases

| Phase | Content |
|-------|---------|
| Alpha 0.1 | Core, Citizen, Land, Audit |
| Alpha 0.2 | Economy (Account, Treasury, Market) |
| Alpha 0.3 | Resource (Storage, Production) |
| Alpha 0.4 | City (District, Building) |
| Alpha 0.5+ | Government, Parliament, Justice |
| Beta | AI enhancement |

## Git Workflow

- **Branch model**: main (stable) → develop (active) → feature/* (single features)
- **Commit convention**: `type(scope): description` (e.g., `feat(core): add module lifecycle framework`)
- **Flow**: Design → Task assignment → Implementation → Architecture review → Test server verification → Merge
- **Push**: Feature branches pushed to `origin/develop` after completion

## Development Governance

- Architect approves design before implementation
- Implementation engineer must verify scope and architecture constraints before coding
- After each task: provide Implementation Assessment, Architecture Impact Check
- Project state is maintained in CLAUDE.md and must stay in sync with code and git history

## Forbidden Practices

1. GUI-first development
2. Client-side core data storage
3. Direct cross-module internal data manipulation
4. Premature complexity for hypothetical future needs
5. Adding large systems without architecture review
6. Mixing verification with unrelated fixes
