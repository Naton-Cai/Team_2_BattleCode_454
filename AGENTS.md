# Battlecode 2026 Repository Instructions

Develop small, reviewable Java bot changes that satisfy the active task and the pinned Battlecode engine. Optimize for correct, reproducible outcomes and total effort per accepted change.

## 1. Load only the context needed

All paths below are relative to the repository root, regardless of your working directory. Read applicable directory-level instructions before editing files there. Respect the instruction precedence of your host tool; report material conflicts instead of silently choosing a convenient interpretation.

Use `.ai/` as the canonical source of shared project guidance:

| File | Read when |
| --- | --- |
| `.ai/README.md` | Starting a task; use its index to locate relevant guidance. |
| `.ai/skills/battlecode-2026/SKILL.md` | Implementing, reviewing, or testing bot behavior. |
| `.ai/style/java.md` | Editing Java. |
| `.ai/testing.md` | Selecting build, test, or match commands. |
| `.ai/project-state.md` | Continuing work that depends on previous decisions or unfinished changes. |

Read supporting skill references only as needed. Do not recursively load all of `.ai/`, the entire specification, or unrelated source files. If native skill loading is unavailable, read the relevant `SKILL.md` directly; do not assume a particular vendor's tools or commands exist.

If a listed guide is absent, use an existing equivalent identified by repository instructions. Otherwise report the gap and inspect relevant build/source files. Continue independent work; defer only the affected behavior when a material rule or API remains unresolved. Ask only when the missing information cannot be established from available sources. Never claim an unread or missing guide was applied.

## 2. Establish facts before changing code

- Inspect the current branch, working-tree changes, relevant code, tests, and build configuration. Preserve unrelated user/team changes.
- Identify the engine dependency, Java toolchain, source/test roots, and existing wrapper or runner. Derive commands from the repository; do not guess Gradle tasks, package paths, dependencies, or API signatures.
- Use the active ticket's outcome, scope, invariants, and acceptance criteria as the implementation contract. If criteria are absent, propose concrete success checks from the request; clarify only material ambiguity. For a substantial task, briefly state the intended change and verification before editing. Make routine implementation choices autonomously and record meaningful assumptions.
- Treat current source as evidence of implemented behavior, the ticket as intended behavior, and version-matched official specifications/API documentation as engine authority. Comments, old chats, and handoff notes can be stale.
- The supplied specification covers amendments through engine v1.2.6; this does not establish the repository's installed version. Check compatibility. Resolve conflicting prose through explicit amendments and matching engine evidence; flag unresolved rule/engine discrepancies before relying on them.

## 3. Preserve the turn architecture

- Keep the shared infinite loop, exception boundary, and end-of-turn `Clock.yield()` in `RobotPlayer`. Put the yield in its existing `finally` path.
- Dispatch using the current unit type each turn. Keep Rat King and Baby Rat behavior separate; a baby may become a king.
- Entity handlers coordinate behavior. Movement, spawning, and other helpers perform bounded work and return; they do not own an infinite loop or yield independently.
- For the initial movement/spawning features, the king handler attempts movement before spawning. A normal blocked or unavailable move must still allow the spawning phase. Recalculate position-dependent decisions after movement.
- Movement, turning, and action readiness are independent. Consult current engine state rather than assuming one unavailable capability prevents the others.
- Keep unexpected failures recoverable at the turn boundary; do not retry an illegal action indefinitely or suppress sandbox failures.

These are design requirements, not a claim that the handlers already exist. Do not introduce an architectural refactor during an unrelated task. Follow a newer explicit team-approved design change and update conflicting guidance in the same scoped change.

## 4. Keep behavior and resource use bounded

- Read and enforce the relevant skill constraints. Verify action legality immediately before execution; account for complete unit footprints and stale observations after movement or round changes.
- Static fields are robot-local, not shared team memory. Use supported game communication for coordination.
- Reuse bounded buffers and direction tables. Avoid repeated large allocations, unbounded searches, and verbose turn-time logging. Required API allocations are not automatically prohibited.
- Budget for the complete robot turn, including setup, sensing, actions, diagnostics, recovery, and yielding. Measure expensive paths and leave headroom; do not claim safety from source size alone.
- Keep game constants separate from strategy parameters. Clockwise movement, cheese reserves, and population caps are project policies, not engine rules. Use the active ticket's agreed values; do not silently tune them or present proposals as implemented behavior.

## 5. Implement and verify the task

1. Make the smallest coherent patch. Preserve public behavior outside the ticket, existing interfaces where practical, and the configured test framework. Ask before a major redesign or unrequested dependency/toolchain change.
2. Add meaningful tests for changed behavior with explicit setup and expected outcomes. Test boundary conditions and failure paths. For bug fixes, reproduce the original failure when practical. Never weaken acceptance criteria or disable failing checks merely to obtain a pass.
3. Compile and run the relevant existing tests using verified repository commands. Run gameplay changes in the instrumented engine when available: compilation and mocked controllers cannot establish sandbox legality, actual collision geometry, or runtime bytecode behavior.
4. Exercise affected integration paths, such as movement followed by spawning, current-type dispatch, and normal turn completion. Expand testing only to resolve a concrete risk or meet an existing gate.
5. Review the final diff for unrelated changes, stale documentation, and unsupported claims. Correct failures introduced by the patch. Report pre-existing failures separately; do not repair unrelated behavior without scope agreement.

For match comparisons, record baseline/candidate commits (or a saved patch for uncommitted changes), engine/toolchain, opponent, map, side, supported seed/configuration, and exact commands. Hold inputs fixed and exercise both sides where relevant. Preserve established deterministic ordering and RNG seeds unless the task changes them. Keep full logs/replays outside routine model context; return summaries and relevant excerpts. When evaluating different models, also retain the task prompt, instruction revision, model/version and settings when exposed, and total attempts against the same acceptance checks.

## 6. Handoff with evidence

Finish implementation tasks with a concise report:

- **Changed:** Files, behavior, and reason.
- **Acceptance:** Criteria satisfied, unmet, or unverified.
- **Validation:** Exact commands, outcomes, relevant measurements, and evidence paths.
- **Limits:** Checks not run and why; remaining risks or blockers.

Do not report tests as passed unless executed, equate a sanity test with feature coverage, or claim improved survival/win rate without match evidence. Mark blocked validation as **not run**; do not declare the feature fully verified.

Update existing project notes only when decisions or handoff state materially change. Distinguish proposed, implemented, and verified work. Cite the specification page and version for rule changes. Keep shared instructions canonical in `.ai/`; tool-specific entry files should reference them rather than maintain competing copies. Do not create parallel memory files or rewrite unrelated guides automatically.

For documentation-only tasks, validate the changed document and its references; runtime tests are unnecessary unless runtime behavior also changes.

Game-rule references: the versioned `specs.pdf` accompanying the Battlecode skill, physical pp. 9-10 (execution/state/cooldowns), 14 (king actions), 18 (bytecodes), and 20-22 (exceptions/sandbox/resources). Turn-handler structure and workflow requirements are team policy, not engine mandates.
