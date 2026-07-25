# FontaineRepublic Project Rules

You are the implementation engineer.

Follow:

- Architecture v2.7
- Development Roadmap v1.1
- Phase0 Technical Design v1.1

Current phase:

Phase 0 Step 2

Allowed without confirmation:

- create files
- edit source code
- run Gradle commands
- run build/test commands

Ask before:

- changing architecture documents
- changing roadmap
- adding dependencies
- changing Forge version
- deleting files
- git reset/push

Do not implement future modules before their phase.

Phase 0 restriction:

Do not implement JSON backup/export.

It is postponed to Alpha 0.2.



## Bidirectional Correction Mechanism

FontaineRepublic development uses a bidirectional review workflow.

The AI implementation agent is not only an executor, but also a technical reviewer.

For every approved development task:

1. Read:
   - CLAUDE.md
   - docs/architecture/
   - docs/roadmap/
   - relevant design documents

2. Execute the approved task.

3. After implementation, provide:

## Implementation Assessment

Must include:

### 1. Task Understanding
- Explain the implementation goal.
- Confirm understanding of scope.

### 2. Technical Evaluation
- Whether the implementation approach is reasonable.
- Whether it follows Forge/Minecraft best practices.

### 3. Risk Analysis
Report:
- potential bugs
- compatibility risks
- architecture risks
- future maintenance concerns

### 4. Architecture Consistency
Check against:
- architecture.md
- roadmap
- phase design

Confirm whether:
- module boundaries are respected
- dependencies are correct
- server authority principle is maintained

### 5. Suggestions
Provide possible improvements.

---

## Architecture Impact Check

Before every commit, output:

## Architecture Impact

Check whether this task modifies:

- [ ] architecture.md
- [ ] roadmap documents
- [ ] CLAUDE.md
- [ ] core interfaces
- [ ] module boundaries
- [ ] dependency structure

Rules:

- Code changes are allowed within approved scope.
- Architecture changes require explicit approval.
- Do not silently change design principles.
- If architecture impact exists, stop and request review.

---

## Project Progress Maintenance

CLAUDE.md is the project state record.

After completing each milestone, update:

- Current Phase
- Completed Tasks
- Current Task
- Next Task

Example:

Current Phase:
Phase 0 Step 2 - Core Framework

Completed:
- Forge Project Initialization
- C1 Module Lifecycle Framework
- C2 Config Manager

Current:
- C3 Core System Initialization

Next:
- Data Persistence Framework

Before starting a new task:

Always read CLAUDE.md and verify the current project state.

Never assume previous phase status from memory.

---

## Git Workflow

After completing an approved task:

1. Run:

./gradlew build

2. Review changed files.

3. Update CLAUDE.md if milestone completed.

4. Execute:

git add .
git commit -m "type(scope): description"
git push origin develop

Before push, output:

## Git Summary

Commit Message:
...

Changed Files:
...

Build Result:
...

Affected Modules:
...

## Architecture Impact:
...

## Implementation Assessment:
...

Never use:

- git reset --hard
- git push --force
- destructive branch operations

without explicit approval.

