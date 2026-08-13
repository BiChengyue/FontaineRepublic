# Decision Log (ADR)

> This log records decisions that affect architecture, module boundaries, persistence strategy, network design, and development order. Implementation details and routine choices are not recorded.

## ADR-001: Server Authority Principle

- **Date**: Phase 0 initialization
- **Decision**: All persistent data must be managed by the server. The client must never decide economy state, permissions, land ownership, political state, or persistent data.
- **Rejected alternatives**: Client-authoritative model, hybrid sync model
- **Reason**: Prevents cheating, ensures data integrity, aligns with Minecraft server mod conventions
- **Status**: **Accepted** — permanently binding

---

## ADR-002: Development Order

- **Date**: Phase 0 initialization
- **Decision**: Development must follow: Core → Data → Network → Business → GUI.
- **Rejected alternatives**: Parallel module development, GUI-first approach, feature-driven ordering
- **Reason**: Data-first prevents rework. GUI-last prevents UI changes from driving data design. Core-first establishes infrastructure needed by all modules.
- **Status**: **Accepted** — permanently binding

---

## ADR-003: SavedData as Primary Persistence

- **Date**: Phase 0 Step 3
- **Decision**: Use Minecraft SavedData / NBT as the primary data persistence mechanism. JSON backup/export deferred to Alpha 0.2.
- **Rejected alternatives**: JSON-only storage, SQLite database, external database
- **Reason**: SavedData is natively supported by Forge, auto-saves with world data, requires no additional dependencies. External databases add deployment complexity unnecessary in Alpha.
- **Status**: **Accepted** — applies to Alpha 0.1

---

## ADR-004: GUI Development Prohibition

- **Date**: Phase 0 initialization
- **Decision**: GUI systems must not be implemented before data model and business logic are complete.
- **Rejected alternatives**: GUI-first development, parallel GUI and data development
- **Reason**: GUI-first causes rework when data models change. Data models must stabilize before UI design.
- **Status**: **Accepted** — permanently binding

---

## ADR-005: JSON Backup Deferred

- **Date**: Phase 0 Step 3
- **Decision**: JSON backup/export is not implemented in Phase 0. Scheduled for Alpha 0.2.
- **Rejected alternatives**: Implement JSON export alongside SavedData in Phase 0
- **Reason**: Phase 0 is a foundation phase. JSON export is an operational convenience, not a core requirement. Premature implementation adds maintenance burden.
- **Status**: **Accepted** — applies to Phase 0

---

## ADR-006: Two-AI Collaboration Model

- **Date**: Phase 0 initialization
- **Decision**: Use two distinct AI roles — ChatGPT for architecture/design/review, Claude Code for implementation.
- **Rejected alternatives**: Single AI for all tasks, human-only review
- **Reason**: Separation of concerns improves quality. Architect AI reviews before implementation AI builds. Reduces blind spots from a single AI's context limits.
- **Status**: **Active with proposed supersession** — ADR-010 is proposed to supersede this model, pending Human confirmation.

---

## ADR-007: Forge Lifecycle Events for Module Management

- **Date**: Phase 0 Step 1
- **Decision**: Modules are initialized and shut down via Forge lifecycle events (FMLCommonSetupEvent, ServerStartingEvent, ServerStoppingEvent). CoreManager uses priority-ordered TreeMap for module ordering.
- **Rejected alternatives**: Classpath scanning, annotation-based discovery, manual module list
- **Reason**: Direct lifecycle control, deterministic ordering, no reflection overhead.
- **Status**: **Accepted**

---

## ADR-008: IModule Interface Design

- **Date**: Phase 0 Step 1
- **Decision**: IModule interface uses `getName()`, `init()`, `shutdown()`, and `default int getPriority() { return 50; }`. No MinecraftServer reference in init/shutdown signature.
- **Rejected alternatives**: Passing MinecraftServer as parameter, using Minecraft.getInstance()
- **Reason**: Keep core interfaces framework-agnostic. Potential network client should not depend on server classes.
- **Status**: **Accepted**

---

## ADR-009: Phase 0 Module Isolation

- **Date**: Phase 0 initialization
- **Decision**: Core modules (core/) must not depend on future business modules (citizen, land, economy, etc.). Business modules may depend on core APIs.
- **Rejected alternatives**: All modules equally dependent, core as a pure utility library
- **Reason**: Core must remain stable as business modules evolve. Business modules can depend on core, but not vice versa.
- **Status**: **Accepted** — permanently binding

---

## ADR-010: Three-AI Collaboration Model

- **Date**: 2026-07-26
- **Decision**: Extend the Two-AI model (ADR-006) to a Three-AI model. Retains ADR-006's Design/Implementation Separation principle, and introduces additional governance principles. Extends by adding Codex as Repository Principal Engineer for independent repository-level review.
- **Rejected alternatives**: Single-AI model, two-AI with human-only review, fixed-role assignment regardless of task
- **Reason**: Three-AI model provides independent validation at each stage. Task-role allocation allows flexible distribution based on workload and expertise. Separation of review and approval prevents single-AI blind spots. Human authority prevents AI-driven scope creep.
- **Status**: **Pending Human Approval**
