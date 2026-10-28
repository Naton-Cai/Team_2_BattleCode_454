# Test cases

Canonical catalog of feature test cases. Record new cases here, not in `.ai/project-state.md`. Older catalogs already in project state stay there until they are moved.

`.ai/testing.md` owns commands and evidence procedures. The task contract owns required behavior. This file lists named checks: class, setup, and expected outcome. Status is **proposed** until the test exists and has been run.

## Baby Rat cheese-chase bytecodes

Status: **implemented and run** (2026-10-20), except the two visible BFS tests, which are ignored.

### Engine tests

These tests are in `ratplayer.BabyRatBytecodeEngineTest`. They run the real sandboxed bot loop through `PlayerControlProvider` and read `InternalRobot.getBytecodesUsed()` after each turn. That count is the whole turn, not one pathing helper. Every observed turn also checks that the baby is alive, no exception is logged, and the turn uses less than 16,500 of the 17,500 baby rat limit. Each turn prints its bytecodes, and the cone-edge tests also print turns, peak, and sum.

The fixture is a 20x20 map with one allied baby facing north at (8,8) and an enemy king at (17,17). The game is put in backstab mode so the missing cats do not end it.

The sandbox loads its own copy of `Config` from the compiled classes. Assigning `Config.BABY_PATH_TO_CHEESE_STRATEGY` from a test does not reach the robot, so each test runs whatever value is compiled. Strategy-specific tests first check that compiled value and fail with setup instructions if it does not match.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `adjacentCheesePickupLeavesBytecodeHeadroom` | 5 cheese on the forward tile (8,9). Any cheese strategy. | The pile is picked up in one turn and the rat holds at least 5 raw cheese. Measured: 1,704. |
| `coneEdgeCheeseWithVisibleWallUsesGreedy` | Compiled strategy `GREEDY` (the default). Wall at (8,10), 5 cheese at (8,12) on the edge of the vision cone. Runs until pickup, at most 20 turns. | The pile is collected and the second step is (7,10). Measured: path 8,9 → 7,10 → 8,11, 4 turns, peak 1,827, sum 3,991. |
| `coneEdgeCheeseWithVisibleWallUsesVisibleBfs` | **Ignored.** Same map, compiled strategy `VISIBLE_BFS`. | The pile is collected and the second step is (9,10). Measured with BFS compiled in: path 8,9 → 9,10 → 9,11 → 9,12, 4 turns, peak 13,412, sum 30,206. |
| `openFieldCheeseChaseLeavesBytecodeHeadroom` | **Ignored.** Compiled strategy `VISIBLE_BFS`. Open map, 5 cheese at (8,12). Three turns. | Each turn steps north to (8,9), (8,10), then (8,11), and the cheese is untouched. Measured with BFS compiled in: 13,124 / 8,676 / 6,223. |

The two visible BFS tests are `@Ignore`d because cheese chase uses greedy. To run them, set `Config.BABY_PATH_TO_CHEESE_STRATEGY` and its `reset()` value to `VISIBLE_BFS` by hand, remove the `@Ignore`, run, then revert both. While `VISIBLE_BFS` is compiled in, the greedy test fails its strategy check, so revert before trusting greedy numbers.

Mock comparisons of greedy, DistBug, and visible BFS stepping are in `GreedyPathTest`, `DistBugPathTest`, and `VisibleBfsPathTest`, still documented in `.ai/project-state.md`.
