---
name: battlecode-2026-architect
description: "Generate, audit, and refactor Java bots for Battlecode 2026: Uneasy Alliances with engine-rule checks, sandbox restrictions, bounded memory, bytecode budgeting, and reproducible validation."
---

# Battlecode 2026: Uneasy Alliances - Engineering & Code Generation Skill

_Skill revision: 1.3.0. Rules baseline: supplied specs.md, changelog through engine v1.2.6. Skill and engine versions are independent_[cite: 26; policy: E1].

## 1. Purpose, Scope & Repository Integration

Use this skill to implement, review, and validate Java bot behavior against the project's pinned Battlecode 2026 engine. Apply bot sandbox, bytecode, and heap restrictions to code executing inside the game, including its dependencies; do not apply those restrictions to host-side tests, build tools, or match runners[cite: 18, 21, 22; policy: E1, E2].

Read the repository's `AGENTS.md` and the active ticket before changing code. Follow its instruction precedence and use `.ai/README.md` to locate supporting guidance. Keep architecture pending team agreement: this skill does not require particular handlers, module boundaries, or action ordering. Treat examples as implementation options and use the active ticket for strategy choices[policy: E4].

Use repository-root paths for `AGENTS.md`, `.ai/testing.md`, `.ai/style/java.md`, and `.ai/project-state.md`. Resolve `references/specs.md` relative to this `SKILL.md`. If repository guidance is unavailable, inspect existing equivalents and report the gap; continue independent work, but defer behavior that depends on unresolved rules or APIs. Do not invent commands or treat proposed policy as team-approved[policy: E1, E4].

### 1.1 Source Authority & API Verification

- **Local API Lookup:** Start with the Java sources and Javadoc in [references/common/](references/common/), relative to this skill. Search the requested method/type and read its declaration and surrounding comments rather than loading the entire directory. `RobotController.java` covers sensing and actions; `GameConstants.java` and `UnitType.java` cover constants and unit stats; `Clock.java` covers turn and execution budgets. Preserve upstream contents. Treat these sources as the working API reference unless a difference from the current engine is confirmed; record confirmed discrepancies in [engine-reference-provenance.md](references/engine-reference-provenance.md). These sources complement the specification, sandbox lists, method-cost table, and instrumented validation; `@battlecode.doc.costlymethod` does not supply a numerical bytecode cost.
- **References:** `[cite: N]` means original PDF page N of the specification now stored as **specs.md**, including the table of contents; keep it at `references/specs.md` relative to this file, and verify it is present when adopting the skill. `[policy: E1-E4]` denotes engineering guidance defined in Section 5, not an official Battlecode rule. Keep the Markdown specification with any redistributed skill[cite: 1, 26; policy: E1].
- **Version Gate:** Before coding, identify the pinned engine dependency and check its compatibility with this specification baseline through v1.2.6. Use `AGENTS.md` for repository discovery and `.ai/testing.md` for verified setup and commands; do not assume either records a verified engine version. If the engine differs, establish the applicable rule changes before relying on affected guidance; never silently substitute another year or current `master`. For API signatures and constants, use `references/common/`. For package permissions and method costs, consult the matching engine and `AllowedPackages.txt`, `DisallowedClasses.txt`, and `MethodCosts.txt`. If unavailable, label affected code unverified instead of inventing APIs[cite: 19, 20, 21, 22; policy: E1].
- **Conflicts:** Prefer explicit later changelog corrections over stale prose within this PDF. Verify remaining contradictions against the pinned engine and record the discrepancy; do not silently override tournament rules with an engine quirk. The PDF's page-21 package-list hyperlink points to **Battlecode 2025**; use the matching **2026** files instead[cite: 21, 23, 24, 25, 26; policy: E1].

---

## 2. Hard Platform Constraints & Sandboxing Rules

### 2.1 JVM Sandbox, State, & Disallowed APIs

_Each robot runs an independent copy of the code with isolated static state; sandbox violations are checked at runtime_[cite: 9, 21].

- **Inter-Robot State Isolation (CRITICAL):**
  - **Never use `static` variables to share data between robots**[cite: 9, 17]. Each robot has its own independent copy of static fields[cite: 9, 17]. All inter-robot communication _must_ use the Global Shared Array or Squeaks[cite: 17, 18].
- **Disallowed Concurrency / Reflection:**
  - **Explicit PDF bans:** `Object.wait()`, `Object.notify()`, `Object.notifyAll()`, `Class.forName()`, and `String.intern()`[cite: 21].
  - Use only allowed packages/classes, including dependencies; compilation does not prove sandbox legality. Avoid threads and reflection as project policy, and verify exact class restrictions in the pinned allow/deny lists rather than attributing an exhaustive class ban to this PDF[cite: 21; policy: E1, E2].
- **Restricted `System` Calls:**
  - Allowed `java.lang.System` methods: ONLY `System.out`, `System.arraycopy()`, and `System.getProperty("bc.testing.*")`[cite: 21].
  - `PrintStream` must not open files; unsupported `System` calls such as `System.currentTimeMillis()` are prohibited. Avoid file/network I/O and process control in bot code; check exact class permissions. Development tools outside the bot JVM are not subject to bot sandbox rules[cite: 21; policy: E1, E2]. Use `rc.getRoundNum()` and `Clock.getBytecodeNum()` for turn budgeting[cite: 20].
- **Execution, Heap Limits, & Top-Level Safety:**
  - Max heap space: **8 MB**[cite: 21].
  - Total Execution Time: A per-team limit aggregates all robots for the entire game and resets between games. Exceeding it causes resignation and loss of the **set**. Inspect the pinned game constants and Clock API for the limit/elapsed-time query; avoid enormous generated Java files or expensive operations even if bytecodes appear cheap[cite: 21, 22].
  - **Recovery Safety (policy):** Handle recoverable failures with bounded recovery and preserve normal turn completion. Catching `GameActionException`/recoverable `Exception`s with `Clock.yield()` in `finally` is one implementation option; preserve the repository's approved structure rather than prescribing an exception-boundary layout. Keep recovery bounded, invalidate stale plans, and do not retry the same illegal action or use exceptions as normal control flow. Do not catch `Throwable` to suppress sandbox failures. Unhandled exceptions may paralyze a robot[cite: 18, 20, 21; policy: E2].

### 2.2 Bytecode Economics & Instrumentation

_Bytecode instructions are tracked deterministically per turn_[cite: 19].

- **Turn Limits:**
  - **Baby Rat:** 17,500 bytecodes/turn[cite: 18].
  - **Rat King:** 20,000 bytecodes/turn[cite: 18].
- **Bytecode Overrun & State Desync (CRITICAL):**
  - If a bot exceeds its bytecode limit, computation pauses at the cutoff and resumes at that point next turn[cite: 18].
  - **Check-to-Action Proximity:** Never separate a condition check (e.g., `rc.canMove(dir)`) and its corresponding action (e.g., `rc.move(dir)`) with heavy computations. If execution is cut off between the check and the action, the game state may change before the action fires next turn, causing a `GameActionException` that must be handled[cite: 18].
  - Always yield explicitly via `Clock.yield()` at the end of each round loop[cite: 18].
- **Allocation Penalties:**
  - Array allocations (`NEWARRAY`, `ANEWARRAY`, `MULTIANEWARRAY`) cost bytecode **equal to the length of the instantiated array**[cite: 22].
  - **Policy:** Pre-allocate bounded reusable buffers and avoid repeated large arrays or growing collections in hot paths. Account for API-returned sensing arrays and necessary small objects rather than banning required allocations. Initialization is also bytecode-limited; stage large setup across turns if needed[cite: 18, 21, 22; policy: E2].
  - `System.arraycopy()` costs one bytecode per element. `java.util`, `java.math`, and the listed random/regex/string functions are instrumented; check the version-matched method-cost table before optimizing. Cheap bytecodes do not imply cheap heap or execution time[cite: 21, 22].
- **Exception Penalty:**
  - Throwing ANY Exception incurs a mandatory penalty of **500 bytecodes**[cite: 20]. Check state defensively before taking actions.

---

## 3. Game Mechanics & Balance (Specification Baseline Through v1.2.6)

### 3.1 Mode Transitions & Scoring

- **Cooperation vs. Backstabbing:**
  - Games start in **Cooperation Mode**[cite: 3].
  - **Backstab Trigger:** Enemy-rat biting, an enemy triggering your rat trap, or enemy ratnapping ends cooperation; do not confuse the trap owner with the victim when identifying the backstabbing team. The PDF places a trap-triggered transition immediately after the triggering rat's turn; query the engine state instead of inferring a delayed round boundary[cite: 3, 4, 13; policy: E1]. (Attacking cats or ratnapping same-team rats does NOT trigger backstab)[cite: 23].
  - Backstabbing cannot be undone for the remainder of the 2,000-round game[cite: 3, 4].
- **Victory & Auto-Loss:**
  - Matches are best of 3 games, each lasting at most 2,000 rounds. If one team loses all kings, it auto-loses that game; **if both teams lose all kings in the same round, use the point system instead**[cite: 3, 4].
  - In Cooperation: Defeating all cats ends the game immediately[cite: 4].
  - In Backstabbing: Defeating cats does NOT end the game; play continues until round 2,000 or until all kings of one team die[cite: 4].
- **Scoring Formulas (Rounds 1–2000):**
  - **Cooperation Score:** `round(0.5 * % damage to cat + 0.3 * % living rat kings + 0.2 * % cheese transferred)`[cite: 5]
  - **Backstabbing Score:** `round(0.3 * % damage to cat + 0.5 * % living rat kings + 0.2 * % cheese transferred)`[cite: 5]
  - **Share Denominators:** Each percentage compares your team with the combined total of both teams; king share uses living kings at game end. Cat damage share is 0 if neither team damaged cats. Cheese scoring counts **baby-rat transfers to kings**, not all global cheese gains. Verify percentage scaling, rounding, and unspecified zero-denominator cases against the pinned engine before implementing a score estimator[cite: 5; policy: E1].
  - **Tiebreakers:** (1) Global cheese stash, (2) Total living rats (Baby + Kings), (3) Uniform random[cite: 5].

### 3.2 Resources: Cheese & Dirt

- **Cheese:**
  - Starting Global Cheese: 2500 per team[cite: 8].
  - Cheese spawns symmetrically in a 9x9 square near mines with probability `1 - (1 - 0.01)^r` in 20-cheese batches; `r` is rounds since that mine last spawned cheese. It spawns only in cat-accessible locations and may spawn on dirt but not walls[cite: 8, 23, 26].
  - **Raw Cheese:** Picked up by Baby Rats in adjacent tiles[cite: 8]. Slows movement/actions. The PDF calls `0.01 * raw cheese` a multiplier, which is ambiguous at zero cheese; verify the full base-plus-penalty formula and rounding in the pinned engine rather than treating zero load as zero cooldown[cite: 8; policy: E1]. Dropped locally if carrier dies[cite: 8, 9].
  - Converted to team **Global Cheese** on delivery to a Rat King. Kings collect only cheese under their footprint, directly into the global stash. Spending consumes raw cheese first, then global cheese; pickup amount is selectable. Check the API for transfer/pickup ranges, amounts, and cooldowns[cite: 8, 26; policy: E1].
- **Dirt:**
  - Rats recover dug dirt into their team-wide stash; cats permanently remove dug dirt. Place dirt only from available stock on a tile without a robot, wall, dirt, cheese, or cheese mine, within legal vision/range[cite: 7, 8, 13].
  - Digging costs 5 cheese and 25 action cooldown[cite: 13].
  - Placing dirt costs 0 cheese and 25 action cooldown[cite: 13, 26].

### 3.3 Robot Specifications, Cooldowns & Sensing

- **Map & Geometry:** Maps are 20x20 through 60x60, with (0,0) at bottom left and increasing x East/y North; symmetry is rotational or horizontal/vertical reflection. Walls and dirt block movement; walls cannot be modified. Respect the entire unit footprint, map bounds, vision cone, and sensing preconditions. Seeing any occupied part can reveal a multi-tile unit[cite: 6, 7, 9, 24].
- **Turn Order:** Robots act in spawn order. Cats act after initial rats but before later-spawned rats/kings; do not assume cats always act last[cite: 9, 25].

_Cooldown decrement: All cooldowns decrease by 10 per round_[cite: 10]. _Movement, turning, and action have separate cooldowns; each is permitted only when its respective cooldown is < 10_[cite: 10].

- **Chirality (CRITICAL):** Map symmetry enforces robot chirality[cite: 9]. `sense` methods return arrays of locations/robots in an order dictated by the robot's chirality[cite: 9, 17]. Do not assume fixed Cartesian ordering.

#### A. Baby Rat

- **Stats:** 100 HP, 1x1 size, `sqrt(20)` vision radius in a 90-degree forward cone[cite: 6, 10].
- **Movement Cooldown:** Forward = 10, Strafe (non-facing) = 18, Turn = 10[cite: 10].
- **Combat (Bite):** Attack only a visible adjacent enemy rat or cat, never an ally. Base damage = 10; spending X cheese adds `ceil(sqrt(X))` damage. Verify action cost and the amount-aware legality check in the pinned API[cite: 10, 23, 24; policy: E1].
- **Ratnapping (Carrying):**
  - Pick up a **visible adjacent** baby rat facing away (unable to sense the carrier), with less health than the carrier, or on the same team. Carry at most one rat; picking up a carrier forces it to drop its passenger[cite: 10, 11].
  - Action cooldown: 20 to pick up, 20 to drop[cite: 11, 25].
  - Carried rats may only sense and squeak; no movement, turning, or other actions. Cat **pounce or movement** kills both passenger and carrier. After 10 rounds, auto-drop in front; if blocked, passenger and carrier **swap roles**. Manual drops require a legal adjacent tile; the passenger retains its original facing[cite: 11, 12, 25].
  - **Cooldown Rule:** Cannot ratnap enemy rat B until 2 turns after B hits an obstacle/ground if you were its most recent carrier[cite: 11, 25].
- **Throwing:**
  - Incurs 20 action cooldown[cite: 12, 24]. Requires at least 1 empty space in front of the thrower[cite: 12].
  - Travels forward 2 tiles/turn for up to 4 turns (8 tiles max)[cite: 12, 24].
  - Collision damage to thrown rat & target: `4 * (remaining tiles)`[cite: 12].
  - Landing stun: 10 HP loss; movement/action/turning cooldowns increase by 10 (natural) or 20 (collision). Map-edge landing incurs only the normal 10 HP loss; collateral targets are not stunned[cite: 12, 24].
  - Flying rats cannot act, collect cheese, be attacked, or trigger traps; they can sense/communicate and are impassable, detectable via `MapInfo`. Ground landings can trigger traps. Flying-rat collisions drop both rats with different landing penalties (see PDF p.12); hitting a cat kills the thrown rat and makes the cat sleep for 2 turns[cite: 12, 13, 26].
- **Traps:**
  - **Rat Trap:** Costs 20 cheese, 15 action cooldown (max 25 active per team)[cite: 13, 25]. Radius `sqrt(2)` trigger deals 50 damage + 30 **movement** cooldown[cite: 13, 25].
  - **Cat Trap:** Costs 10 cheese, 10 action cooldown (max 10 active per team)[cite: 13]. Radius `sqrt(2)` trigger deals 100 damage + 20 **movement** cooldown[cite: 13]. Placed during cooperation OR by the **backstabbed** team when elapsed rounds since backstab are `<= 100`; existing traps remain. Simultaneous cat-trap damage stacks, but stun does not[cite: 13, 26].
  - **Placement & Removal:** Validate placement through the matching API; the PDF excludes mines, walls, and dirt for rat traps. Allied rats are immune to allied traps; enemies/cats cannot see them. Traps are single-use. Remove visible adjacent allied traps without refund or cooldown. Never assume an unseen tile is trap-free[cite: 13; policy: E2].

#### B. Rat King

- **Stats:** 3x3 multi-tile unit, starting HP capped at 600, 360-degree vision cone with radius `sqrt(25)`[cite: 6, 14, 26].
- **Movement Cooldown:** 40 (any direction), Turn = 10[cite: 14].
- **Upkeep:** At round end, kings consume 2 cheese each in creation order. A king with insufficient available cheese takes 10 HP starvation damage and consumes none[cite: 14, 23].
- **Attack/Action Radius:** Attack distance squared `<= 8` from king center[cite: 24, 25]. Can dig/place dirt within distance `(3/2 + sqrt(2))` from center[cite: 7].
- **Spawning Baby Rats:**
  - **CRITICAL API AWARENESS:** Use `canBuildRat` and `buildRat` with the arguments required by the pinned API. **DO NOT USE** old names `canBuildRobot` or `buildRobot`[cite: 23].
  - Cost: `10 + 10 * floor(living_baby_rats / 4)` cheese[cite: 14].
  - Action cooldown: 10. Spawn on an empty adjacent location; newborn facing matches the parent king[cite: 14].
- **Formation:**
  - Upgraded from a baby rat surrounded by 7+ allied baby rats in a 3x3 footprint[cite: 14].
  - Costs 50 cheese[cite: 14]. Consume all baby rats in the footprint (**allied or enemy**) and absorb their raw cheese. Health is their summed health, capped at 600; retain the center rat's movement/action cooldowns. The footprint must be on-map, passable, and not intersect a king or cat. Trigger traps whose **trigger radius** touches the footprint[cite: 14, 23, 24, 26].
  - **Limits:** Max 5 kings per team[cite: 14]. No new kings created after round 1200 if team has `>= 2` living kings[cite: 14, 25].

#### C. NPC Cats

- **Stats:** 2x2 multi-tile unit, 4,000 HP, 20 move cooldown, 30 dig cooldown, 180-degree vision cone of `sqrt(17)`[cite: 15, 16, 23, 24].
- **Behavior:** Explore (waypoint BFS) -> Attack (8 turns) -> Explore. Explore ignores squeaks but can crush baby rats or attack blocking kings; Attack turns toward the first new squeak, then tries scratch (20 damage), pounce, movement, and digging in order. Mode changes occur on the next cat turn[cite: 15, 16, 17].
- **Threat Geometry:** Movement/pounce instantly kills baby rats and passengers, but cannot land on kings. Pounce validity uses the cat's corners/2x2 footprint, not a baby-rat center-distance test; pounces cross obstacles, incur 40 movement cooldown, and trigger cat traps only at landing. Kings can still be scratched[cite: 15, 16, 25].

### 3.4 Inter-Robot Communication

1. **Shared Array:** 64 slots, **indices 0-63**, each storing a value **0-1023**[cite: 17].
   - **Only Rat Kings can write (`rc.writeSharedArray()`)**[cite: 18].
   - All units can read (`rc.readSharedArray()`)[cite: 18].
2. **Squeaks:** Broadcasts `(messageContent, robotID, loc, roundNum)` within `sqrt(16)` radius[cite: 18].
   - At most **one squeak per rat per turn**; delivered to allied rats and cats in range, never the sender. Persists for 5 rounds. Cats turn toward new squeaks in attack mode[cite: 15, 17, 18].
   - **Protocol Policy:** Specify slot ownership, bit ranges, sentinel values, and freshness. One shared value holds only 10 bits: a 60x60 tile index needs 12 bits and full robot IDs (>=10,000) do not fit. Use a defined multi-slot/relative encoding; do not truncate silently. Cache reads only for their valid round, deduplicate squeaks using sender/round, and bound message processing[cite: 6, 9, 17, 18; policy: E2].

---

## 4. Code Generation Rules & Low-Bytecode Templates

### 4.1 Direction Tables & Geometry Guidance

_Cache direction enumeration once; prefer squared-distance integer comparisons and pinned radius constants over repeated square roots. Preserve chirality where tie-breaking depends on sense order_[cite: 9, 24, 25; policy: E2].

Treat this reusable direction table as an example, not a universal traversal or tie-breaking policy. Preserve the ordering required by the active ticket; do not infer a movement algorithm from this table[policy: E2, E4].

```java
// Per-robot cached table. final fixes the reference, not the elements; never mutate.
private static final Direction[] DIRECTIONS = {
    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
};
```

### 4.2 Bounded Computation & Memory

- **Budget Before Work:** Measure with `Clock.getBytecodeNum()` and the unit's current limit. Reserve a measured margin for legality checks, essential actions, recovery, and yield; there is no universal safe reserve. Bound search expansions, queue sizes, and iterations; choose a legal fallback when the budget is low. Recheck the round and refresh transient sensing after any yield or unexpected cutoff; never continue a stale plan blindly[cite: 18, 20; policy: E2].
- **Heap Budget:** Prefer flat primitive buffers sized from actual map dimensions (at most 3,600 tiles), capped queues, reset pointers, and generation stamps. Include array/object overhead, API results, and temporary allocations in an estimate comfortably below 8 MB **per robot**; static buffers are duplicated per robot. Avoid retained sensing snapshots, unbounded histories, recursion, boxing, streams, regex, and repeated debug string construction in hot paths unless profiling justifies them[cite: 6, 9, 21, 22; policy: E2].
- **Cache Validity:** Keep immutable walls separately from dynamic dirt, occupancy, cheese, and traps. Tag observations with round/freshness; invalidate caches after relevant movement, turning, actions, or type changes. Base behavior on the current robot type without requiring a particular dispatch structure; after king formation, refresh role, footprint, limits, and communication permissions rather than retaining the initial baby-rat role. Reuse sensing results within a valid decision window, but do not act on unsensed assumptions[cite: 6, 7, 9, 12, 13, 18; policy: E2].

### 4.3 Battlecode Validation & Supporting Guidance

- **Repository Workflow:** Use `AGENTS.md` for patch scope, context loading, approvals, and the evidence-based handoff. Use `.ai/style/java.md` for Java conventions and `.ai/project-state.md` for confirmed decisions and unfinished work. Do not create competing project-memory files[policy: E4].
- **Runtime Evidence:** Validate affected bot behavior in the instrumented engine when available; compilation and mocked controllers alone cannot establish sandbox legality, actual geometry, or runtime bytecode safety. Mark unavailable checks **not run** and affected claims **unverified**[cite: 18, 20, 21; policy: E3].
- **Repeatability:** Preserve established deterministic ordering and supported RNG seeds unless the task changes them; never use wall time for bot decisions. Match comparisons must fix engine/toolchain, bot revisions, map, opponent, side, and seed/configuration, and record exact commands and results. Exercise both sides/chiralities where relevant[cite: 9, 18, 21; policy: E3].
- **Select Relevant Scenarios:** Choose checks from the changed behavior and active ticket, not every mechanic in this skill. Candidate boundaries include movement/action/turning readiness, bytecode cutoffs, carried/flying states, king formation at rounds 1200/1201, cat-trap eligibility at backstab +100/+101, and shared-array index/value bounds. Test the externally expected behavior, not a copy of the implementation[cite: 10, 11, 12, 14, 18, 20, 21, 26; policy: E3].
- **Measure Relevant Worst Cases:** Where the change warrants it, exercise large maps, dense sensing, blocked paths, and long matches. Record peak observed turn bytecodes, cutoffs, exceptions, and available heap/execution-time evidence; observed measurements are not universal bounds. Gate and bound diagnostics; a win rate alone is not a compliance check[cite: 18, 20, 21, 22; policy: E2, E3].
- **Testing Guide Transition:** Put verified commands, environment setup, match-record format, scenario catalog, and evidence locations in `.ai/testing.md`. Until that guide exists and contains this coverage, retain the repeatability/scenario/measurement guidance above. After migration, replace procedural duplication with a direct reference while preserving this skill's runtime-evidence requirement and game-rule citations[policy: E3, E4].

---

## 5. Reference Index & Engineering Policy Provenance

- **Rules Source:** Supplied `specs.md`, converted from the 26-page PDF, changelog through v1.2.6. Page map: 3-5 modes/scoring; 6-8 map/resources; 9-10 execution/sensing/cooldowns; 10-14 rat actions; 15-17 cats; 17-18 communication; 18-19 bytecodes; 20 diagnostics/API; 21 sandbox/heap/time; 22 instrumentation; 23-26 amendments. Numeric citations retain the original PDF page numbers, not Markdown line numbers or external citation IDs[cite: 1, 26].
- **Known Verification Points:** The PDF conflicts on king starting HP (p.14 versus v1.2.1 on p.26), cat vision (p.15 versus v1.0.6 on p.23), and cat-trap availability (p.13 versus v1.2.2 on p.26). Cat action cooldown also conflicts (p.15 says scratch 15; v1.1.4 on p.24 changes action cooldown to 30); verify the pinned action-specific cost. Apply the cited later corrections. Confirm the raw-cheese cooldown formula (p.8), zero-denominator score behavior (p.5), action costs and package restrictions in the project's pinned engine; these are not resolved by this summary[cite: 5, 8, 13, 14, 15, 20, 21, 23, 24, 26; policy: E1].
- **E1 - Source & Version Discipline:** Engineering policy derived from the PDF's versioned amendments and its pointers to complete API, package, and cost documentation. Require traceable facts and explicit uncertainty; this policy is not itself a tournament rule[cite: 19, 20, 21, 22, 23, 24, 25, 26].
- **E2 - Runtime Safety & Efficiency:** Engineering recommendations derived from per-robot isolation, bytecode interruption, exception penalties, heap/time limits, and allocation costs. Specific data structures, caching rules, and reserve sizes are implementation choices to measure, not engine mandates[cite: 9, 18, 20, 21, 22].
- **E3 - Reproducible Validation:** Engineering policy based on the spec's repeatable-match model, debugging guidance, and runtime-only sandbox failures. Scenario selection is a review recommendation, not an official test suite[cite: 18, 20, 21].
- **E4 - Repository Integration & Review Status:** Follow `AGENTS.md` §§1-5 and `.ai/README.md` §§1-6 for shared workflow and document ownership. The user's review directions (2026-10-12) defer architecture to team agreement and request removal of duplicated workflow guidance. Treat E1-E3 as engineering recommendations proposed for team review until adopted; cited engine rules remain constraints regardless of policy adoption. Record approval status in `.ai/project-state.md`. These references establish project intent, not tournament rules.
