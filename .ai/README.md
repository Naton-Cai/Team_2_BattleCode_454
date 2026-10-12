# Battlecode 2026 Shared Development Guidance

This directory gives teammates and development models a shared starting point for our Java Battlecode 2026 bot. It keeps project knowledge in version control so work can continue across developers, models, and sessions without relying on private chat history.

Place this file at `.ai/README.md`. Paths shown below are relative to the repository root. This is the guidance index; the root project README should cover general project setup and usage.

## 1. Start here

1. Read the root `AGENTS.md` and any applicable directory-level instructions.
2. Read the active feature ticket, including its scope, acceptance criteria, and test cases.
3. Use the index below to load only the guidance needed for the task.
4. Inspect the relevant implementation, tests, and build configuration before making changes.

`AGENTS.md` defines the working rules and conflict policy. Follow its precedence over conflicting plugin or skill guidance, subject to the host tool's higher-priority instructions, permissions, and explicit user instructions. This README supports that policy; it does not replace it.

## 2. Project context

We are developing the bot incrementally through small features with explicit success checks. Initial feature work concerns basic Rat King movement and spawning Baby Rats using cheese. Each active ticket determines the behavior to implement; mentioning a feature here does not mean it is implemented or verified.

Architecture is pending team agreement. Earlier proposals for separate entity handlers, module boundaries, or action ordering are not requirements. Preserve the current structure unless the active task authorizes a change.

The starting code shared during planning was a Java `RobotPlayer` scaffold and a JUnit sanity test. That test alone does not validate gameplay. Inspect the current repository to establish what has changed since that baseline; do not reconstruct the code from conversation history.

## 3. Guidance index

These are the intended locations. This index does not establish that every document has been added or reviewed.

| Path | Purpose | Read when |
| --- | --- | --- |
| `AGENTS.md` | Repository working rules, instruction precedence, and evidence requirements. | Starting any development task. |
| `.ai/README.md` | Project context and navigation. | Starting a task or onboarding. |
| `.ai/skills/battlecode-2026/SKILL.md` | Battlecode constraints and implementation/validation guidance, with source references. | Changing or reviewing bot behavior. |
| `.ai/style/java.md` | Agreed Java conventions and examples. | Editing Java. |
| `.ai/testing.md` | Verified environment, build, test, and match instructions. | Planning or running validation. |
| `.ai/project-state.md` | Confirmed decisions, unfinished work, and unresolved questions. | Resuming work or checking decision status. |

Locate the versioned specification through the Battlecode skill's references. Check that its rules and API guidance match the engine dependency used by the repository.

If a guide is missing, follow the missing-context procedure in `AGENTS.md`: inspect existing equivalents, report the gap, and continue independent work. Never invent commands or claim a missing guide was read.

## 4. Keep information in the right place

| Information | Canonical location |
| --- | --- |
| Feature purpose, scope, acceptance criteria, estimate, and planned QA | Active feature ticket, such as the team's Trello card. |
| Engine rules and their specification references | Battlecode skill and its supporting references. |
| Verified commands, prerequisites, and expected outputs | `.ai/testing.md`. |
| Team decisions and current handoff state | `.ai/project-state.md`, with a date and ticket/commit reference when available. |
| Actual behavior and executable checks | Source code and tests. |

If a model cannot access a ticket, provide its current text or an accessible reference. Do not assume access to Trello, previous chats, or another assistant's memory.

Keep proposed, implemented, and verified work distinct. For example, clockwise movement selection is a ticket-level strategy choice; the engine's movement legality is a game constraint. Record policy decisions separately from cited engine rules.

## 5. Work efficiently and leave evidence

Read relevant sections and files as needed instead of loading every guide, source file, or replay. Keep summaries short and link to detailed evidence. Use the handoff format in `AGENTS.md` to report changes, acceptance status, validation, and limits.

A build checks that the code compiles. Focused tests check specific expected behavior. A match runner executes the bot inside the Battlecode engine, where gameplay and runtime constraints can be checked. Use the verified commands in `.ai/testing.md`; these checks provide different evidence and do not substitute for one another.

For repeatable comparisons, retain the code revision, instruction revision, relevant environment and match inputs, exact commands, and results as required by `AGENTS.md`. Shared instructions improve consistency across models; acceptance checks determine whether the result is correct.

## 6. Maintain this directory

Review guidance changes alongside the feature that requires them. Cite specification pages and versions for game-rule changes, and ticket or decision references for team policies. Update this index when guidance moves or is added.

Keep shared guidance here and have tool-specific entry files reference it. Document loading depends on the assistant being used; do not assume that placing files in `.ai/` automatically loads them. Avoid competing copies of project memory or instructions.

Before adopting this index, confirm that the referenced guides and specification can be located, and record the actual engine version and working commands. These details have not been verified against the team's repository during preparation of this document.
