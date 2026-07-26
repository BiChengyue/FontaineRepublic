# Incident Log

## Incident-001: AI Conversation Interruption

- **Date**: Phase 0 Step 3 wrap-up
- **Impact**: Medium — lost mid-task context during Data Persistence Framework wrap-up

### Description

During investigation of `DataManager.saveAll()` shutdown persistence behavior, the AI conversation was interrupted (context window limit reached). The session was resumed in a new conversation using the conversation summary mechanism.

### What Was Lost

- Partial progress in decompiling Minecraft/Forge JARs
- Mid-analysis state for DimensionDataStorage shutdown behavior
- Shell history from verification attempts

### What Preserved Continuity

- **Git**: All committed code intact
- **CLAUDE.md**: Provided project phase, scope, and rules
- **Conversation summary**: Allowed the new session to resume from the correct state
- **Project files on disk**: All source files, build config, docs preserved

### Root Cause

AI conversation context window limit. The session accumulated too much context (code review, multiple verification attempts, shell output) and triggered compaction.

### Lessons Learned

1. Long verification sessions accumulate excessive context — consider breaking into focused sub-tasks
2. Shell command output (especially decompilation attempts) filled context quickly
3. Task separation (verify vs. fix) is correct but verification itself can bloat context

### Improvements Applied

- Established Verification Strategy: layered (Code Review → Build → Runtime), prefer simple over elaborate
- Verification and Fix Separation rule: record, finish report, separate fix task
- Temp File Rules: avoid unnecessary temporary Java classes, test frameworks, runtime files

### Follow-Up Actions

- [x] Create AI recovery document system (docs/ai_recovery/)
- [ ] Future: set context budget warnings for long-running tasks

---

## Incident-002: ServerStartingEvent Timing Bug

- **Date**: Phase 0 Step 3 initialization
- **Impact**: Low — caught during verification before commit

### Description

Initial implementation used `ServerAboutToStartEvent` for `DataManager.init()`. This caused a `NullPointerException` because `server.overworld()` returns null at that point (worlds not yet loaded).

### Root Cause

Incorrect Forge lifecycle event selection. `ServerAboutToStartEvent` fires before worlds are loaded. `ServerStartingEvent` fires after world initialization.

### Resolution

Changed from `ServerAboutToStartEvent` to `ServerStartingEvent`.

### Lessons Learned

- Forge lifecycle events have specific ordering constraints
- `ServerStartingEvent` = worlds loaded, safe to access overworld
- Always verify event timing against Forge lifecycle documentation

---

## Incident-003: ConfigManager Empty Spec

- **Date**: Phase 0 Step 2
- **Impact**: Low — caught during compilation

### Description

`ConfigManager` used `builder.comment()` with no subsequent config entries, causing `IllegalStateException: Non-empty comment when empty expected` from ForgeConfigSpec.

### Root Cause

ForgeConfigSpec does not allow comments without corresponding config entries in an otherwise empty spec.

### Resolution

Removed the `comment()` call. Empty config spec is valid as long as no orphaned comments exist.

### Lessons Learned

- Empty ForgeConfigSpec.Builder is valid; adding comments without entries is not
- Phase 0 spec is intentionally empty (business entries added in later phases)
