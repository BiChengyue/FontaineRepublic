# # FontaineRepublic Project Rules

## Project Identity

Project:

FontaineRepublic

Minecraft Version:

1.20.1

Forge Version:

47.4.18

Mod ID:

fontainerepublic

Branch:

develop

Architecture:

v2.7

Roadmap:

v1.1

Phase0 Technical Design:

v1.1

---

# Role

You are the implementation engineer.

Your responsibilities:

- implement approved designs

- verify technical correctness

- report risks

- maintain project state

Follow:

- Architecture v2.7

- Development Roadmap v1.1

- Phase0 Technical Design v1.1

---

# Current Progress

Current Phase:

Phase 0 Step 3 - Data Persistence Framework

Completed:

- Forge Project Initialization

- C1 Module Lifecycle Framework

- C2 Config Manager

- C3 Core System Initialization

Current:

- Data Persistence Framework

Next:

- Data Layer Review

- Alpha 0.1 preparation

Always read this section before starting work.

Never assume previous progress from memory.

---

# Scope Control

Do not implement future modules before their phase.

Do not add features because they seem useful.

If a feature belongs to a later phase:

- record suggestion

- do not implement

---

# Phase 0 Restrictions

Allowed:

- Core framework

- Configuration system

- Lifecycle management

- Data persistence foundation

Forbidden:

- Citizen module

- Land module

- Economy module

- Government module

- Justice module

- GUI systems

- Network features

JSON backup/export:

Not implemented in Phase 0.

Scheduled for Alpha 0.2.

---

# Allowed Without Confirmation

You may:

- create files

- edit source code

- run Gradle commands

- run build commands

- run approved verification commands

---

# Ask Before

Request confirmation before:

- changing architecture documents

- changing roadmap documents

- adding dependencies

- changing Forge/Minecraft version

- deleting important files

- changing module boundaries

- changing core interfaces

Never:

- git reset --hard

- git push --force

- destructive branch operations

without explicit approval.

---

# Bidirectional Correction Mechanism

The implementation agent is not only an executor.

For every approved task:

## Before implementation

Read:

- [CLAUDE.md](http://CLAUDE.md)

- docs/architecture/

- docs/roadmap/

- relevant design documents

Confirm:

- task scope

- affected modules

- architecture constraints

---

## After implementation

Provide:

# Implementation Assessment

## 1. Task Understanding

Explain:

- implementation goal

- allowed scope

- excluded scope

## 2. Technical Evaluation

Evaluate:

- implementation approach

- Forge/Minecraft compatibility

- maintainability

## 3. Risk Analysis

Report:

- bugs

- compatibility risks

- architecture risks

- future maintenance risks

## 4. Architecture Consistency

Check:

- module boundaries

- dependencies

- server authority principle

## 5. Suggestions

Provide improvements.

---

# Architecture Impact Check

Before every commit output:

## Architecture Impact

Check:

- [architecture.md](http://architecture.md)

- roadmap documents

- [CLAUDE.md](http://CLAUDE.md)

- core interfaces

- module boundaries

- dependency structure

Rules:

- Code changes inside approved scope are allowed.

- Architecture changes require approval.

- Never silently change design principles.

If architecture impact exists:

stop and request review.

---

# Project Progress Maintenance

[CLAUDE.md](http://CLAUDE.md) is the project state record.

After every milestone update:

- Current Phase

- Completed Tasks

- Current Task

- Next Task

Milestone completion must keep:

Code state

=

Documentation state

=

Git history state

---

# Verification Strategy

Use layered verification.

## Level 1: Code Review

Check:

- logic correctness

- architecture consistency

- API usage

## Level 2: Build Verification

Run:

./gradlew build

## Level 3: Runtime Verification

Required for:

- Forge lifecycle

- Server events

- SavedData

- Networking

- World interaction

Prefer:

Dedicated server runtime verification

over creating unit test infrastructure.

Do not create test frameworks unless explicitly approved.

---

# Verification and Fix Separation

During verification:

If a bug is discovered:

1. Record the issue.

2. Finish current verification report.

3. Create a separate fix task.

Do not mix:

verification

+

unrelated fixes

---

# Temporary File Rules

During testing:

Avoid creating unnecessary:

- temporary Java classes

- test frameworks

- runtime files

Never automatically delete:

- run/world

- server data

- important project files

without confirmation.

---

# Git Workflow

After completing an approved task:

1. Run:

./gradlew build

2. Review changed files.

3. Update [CLAUDE.md](http://CLAUDE.md) if milestone completed.

4. Execute:

git add .

git commit -m "type(scope): description"

git push origin develop

Before push output:

## Git Summary

Commit Message:

...

Changed Files:

...

Build Result:

...

Affected Modules:

...

Architecture Impact:

...

Implementation Assessment:

...

---

# Minecraft Architecture Rules

Maintain:

Server authority.

Client must not decide:

- economy state

- permissions

- land ownership

- political state

- persistent data

Core modules must not depend on future business modules.

---

# Data Rules

All persistent data must use:

Minecraft SavedData / NBT

Business modules access data through approved Core APIs.

Do not directly manipulate other modules' storage.

