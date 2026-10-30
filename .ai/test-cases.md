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


## Baby Rat dirt collection

### Controller tests

These tests go in a new `ratplayer.BabyRatDirtTest`. They call `RobotPlayer.playTurn` with a `TestRobotController`, which already has `setDirt`, `setCanRemoveDirt`, and `setRemoveDirtFailure`. Setup and teardown call `Config.reset()`. Unless a test says otherwise, the rat is at (5,5) facing north, has 0 raw cheese and 1,000 global cheese, and has no adjacent robot.

These tests check which calls happen and in what order. They do not show what the engine allows.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `digsForwardDirtWhenAllowed` | Digging is allowed on the forward tile (5,6). | One `removeDirt:(5,6)`, and no other dig. |
| `digsLeftThenRightWhenForwardRefused` | Case 1: forward is refused and left (4,6) is allowed. Case 2: forward and left are refused and right (6,6) is allowed. | Case 1 makes one dig at (4,6). Case 2 makes one dig at (6,6). |
| `doesNotDigWhenNothingDiggable` | All three tiles are refused. | No `removeDirt` call, and the movement step still runs. |
| `reserveBoundary` | Raw cheese 0 with global cheese 805, then with global cheese 804. Forward is diggable. | 805 digs, because 805 - 5 = 800 meets the configured reserve. 804 does not dig. |
| `rawCheesePaysFirst` | Raw cheese 5, global cheese exactly 800, forward diggable. | One dig, because the cost comes out of raw cheese. |
| `attackBeatsDigByDefault` | A cat can be bitten on the forward tile and dirt is diggable. The order is the default. | One bite and no dig. |
| `digFirstWhenOrderSwapped` | Same setup as above with `BABY_ACTION_ORDER = {DIG_DIRT, ATTACK}`. | One dig and no bite. |
| `omittedActionNeverRuns` | `BABY_ACTION_ORDER = {ATTACK}`, no biteable target, forward diggable. | No `removeDirt` call. |
| `digFailureContainedAtTurnBoundary` | `removeDirt` throws a `GameActionException` on the first turn and is cleared before the second turn. | The first turn returns normally from `playTurn` with one dig attempt and no retry. The second turn makes one dig. |

### Engine tests

These tests run the real sandboxed bot loop, following `BabyRatBytecodeEngineTest`. They place dirt with `world.setDirt`. The sandbox loads the compiled `Config` values, so these tests use the starting values.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineDigIncreasesTeamDirtAndCharges5Cheese` | One dirt tile directly ahead of the rat. Global cheese is above the reserve plus 5. | After one turn, the tile is passable, the team dirt stash is up by 1, and team cheese is down by 5 plus any king upkeep in the fixture. |
| `engineRespectsCooldownBetweenDigs` | Dirt on the forward tile and on the left tile. | The second dig waits until the action cooldown has dropped below 10. Neither turn logs an exception. |
| `engineDigTurnBytecodeHeadroom` | Same fixture as the first engine test. | The peak bytecodes on a dig turn leave at least 1,000 under the 17,500 baby rat limit. Measured: 1,851. |

## Rat King dirt collection

Kings share `Dirt` and `Config.MIN_CHEESE_RESERVE_FOR_DIG` with baby rats. Spawn still uses the action first. These tests refuse or wall spawn so digging is the action under test.

### Controller tests

These tests go in `ratplayer.RatKingDirtTest`. They call `RobotPlayer.playTurn` with a `TestRobotController` set to `UnitType.RAT_KING`. Setup and teardown call `Config.reset()` and clear `RatKing.locationPublished`. Unless a test says otherwise, the king is at (10,10) facing north, has 0 raw cheese and 1,000 global cheese, action ready, spawn refused, and movement allowed.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `digsNorthSpawnRingWhenAllowed` | Digging is allowed on the north spawn-ring tile (10,12). | One `removeDirt` at (10,12), and no `buildRat`. |
| `digsNortheastWhenNorthRefused` | North is refused and northeast (12,12) is allowed. | One dig at (12,12). |
| `doesNotDigWhenNothingDiggable` | All tiles are refused. | No `removeDirt`, and the movement step still runs. |
| `reserveBoundary` | Raw cheese 0 with global cheese `reserve + 5`, then `reserve + 4`. | The first digs; the second does not. |
| `rawCheesePaysFirst` | Raw cheese 5, global cheese exactly the reserve. | One dig. |
| `spawnBeatsDigWhenBuildLegal` | `canBuildRat` is true and dirt is diggable. | One `buildRat` and no dig. |
| `digFailureContainedAtTurnBoundary` | `removeDirt` throws on the first turn and is cleared before the second. | First turn returns from `playTurn` with one dig attempt. Second turn makes one dig. |

### Engine tests

These tests are in `ratplayer.RatKingDirtEngineTest`. The king starts at (8,8). The eight spawn-ring tiles are walls so spawning cannot take the action. Dirt is on in-range tiles that are not spawn candidates: (9,10) then (10,9). Movement and turning are held. The sandbox uses compiled `Config` values.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineDigIncreasesTeamDirtAndCharges5Cheese` | Dirt at (9,10), 1000 global cheese. | After one turn, the tile is passable, team dirt is up by 1, no baby is spawned, and cheese is down by 5 plus king upkeep (2). |
| `engineRespectsCooldownBetweenDigs` | Dirt at (9,10) and (10,9). | The second dig waits until action cooldown is below 10. No baby is spawned. |
| `engineDigTurnBytecodeHeadroom` | Same fixture as the first engine test. | Peak bytecodes on a dig turn leave at least 1,000 under the 20,000 king limit. Measured: 4,320. |

## Baby Rat cat block-and-flee

Status: **implemented and run** (2026-10-30). Engine bytecode evidence is **run**. Match evidence is **not run**.

Requirements are in `.ai/tasks/baby-rat-cat-avoidance.md`. Default `Config.BABY_CAT_RESPONSE_STRATEGY` is `BITE_THEN_BLOCK_AND_FLEE`.

### Controller tests

These tests are in `ratplayer.BabyRatCatAvoidanceTest`. They call `RobotPlayer.playTurn` with a `TestRobotController`. Setup and teardown call `Config.reset()` and clear `BabyRat.attackedThisTurn`. Unless a test says otherwise, the rat is at (5,5) facing north, has 0 raw cheese and 1,000 global cheese, forward and greedy movement are allowed, and cat-trap placement is left refused.

These tests check which calls happen. They do not show what the engine allows.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `distantCatPlacesDirtAndFleesInsteadOfDigging` | Cat at (5,9). `canPlaceDirt` and `canRemoveDirt` both true. | One `placeDirt` at (5,6), no bite, no dig, one `move:SOUTH`. |
| `adjacentCatBiteSkipsDirtAndKeepsWander` | Cat on the forward tile (5,6), bite legal, same cat also in `senseNearbyRobots`. | One bite, no `placeDirt`, wander `moveForward`, no south flee. |
| `noCatsStillDigsWhenAllowed` | No nearby robots, forward dig legal. | One `removeDirt`, no `placeDirt`, wander still runs. |
| `refusedDirtStillFleesAndSkipsDig` | Cat at (5,9), `canPlaceDirt` false, dig would otherwise be legal. | No dirt, no dig, one `move:SOUTH`. |
| `twoCatsUseNearestForDirtAndFlee` | East cat at (9,5) listed first, closer north cat at (5,8) second. | Dirt at (5,6) and a south step. No west step. |
| `placeDirtFailureContainedAtTurnBoundary` | Cat at (5,9), `placeDirt` throws on the first turn and is cleared before the second. | First turn returns from `playTurn` with one dirt attempt and no flee. Second turn places dirt once more. |
| `noneStrategyKeepsDigAndWander` | `BABY_CAT_RESPONSE_STRATEGY = NONE`, cat at (5,9), dig and dirt both legal. | One dig, no dirt, wander forward, no south flee. |

### Engine tests

These tests are in `ratplayer.BabyRatCatAvoidanceEngineTest`. They run the real sandboxed bot loop through `PlayerControlProvider` and read `InternalRobot.getBytecodesUsed()` after each turn. The sandbox loads compiled `Config` values. The fixture is a 20x20 map with a north-facing allied baby at (8,8), a frozen cat whose 2x2 starts at (8,12), and an enemy king at (17,17). `LiveMap` cat waypoints are required for `InternalRobot` construction. The game is put in backstab mode. The baby limit is 17,500; each observed turn must keep 1,000 headroom.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineCatBlockTurnBytecodeHeadroom` | Compiled strategy `BITE_THEN_BLOCK_AND_FLEE`. One dirt in the team stash. Cat movement/action/turning held. | After one turn, dirt is on (8,9), the baby is at (8,7), and the turn leaves at least 1,000 under the 17,500 baby-rat limit. Measured: 2,685. |

## Rat King low-health cat block-and-flee

Status: **implemented and run** (2026-10-30). Engine bytecode evidence is **run**. Match evidence is **not run**.

Requirements are in `.ai/tasks/rat-king-cat-avoidance.md`. Default `Config.KING_CAT_RESPONSE_STRATEGY` is `BLOCK_AND_FLEE_WHEN_LOW_HEALTH`. `Config.KING_CAT_FLEE_HEALTH` starts at 20.

### Controller tests

These tests are in `ratplayer.RatKingCatAvoidanceTest`. They call `RobotPlayer.playTurn` with a `TestRobotController` set to `UnitType.RAT_KING`. Setup and teardown call `Config.reset()` and clear `RatKing.locationPublished`. Unless a test says otherwise, the king is at (10,10) facing north, spawn is refused, and forward and directional movement are allowed.

These tests check which calls happen. They do not show what the engine allows.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `lowHealthVisibleCatPlacesDirtAndFlees` | Health 20, cat at (10,16), spawn and dig would be legal. | One `placeDirt` at (10,12), no spawn, no dig, one `move:SOUTH`. |
| `healthAboveThresholdKeepsDigAndWander` | Health 21, same cat, dig legal. | No dirt, one dig, wander forward. |
| `lowHealthWithoutCatStillDigs` | Health 20, no nearby robots, dig legal. | One dig, no `placeDirt`. |
| `noneStrategyKeepsDigAndWander` | `KING_CAT_RESPONSE_STRATEGY = NONE`, health 20, cat visible, dig legal. | One dig, no dirt, wander forward. |
| `placeDirtFailureContainedAtTurnBoundary` | Health 20, cat visible, `placeDirt` throws on the first turn and is cleared before the second. | First turn returns from `playTurn` with one dirt attempt and no flee. Second turn places dirt once more. |

### Engine tests

These tests are in `ratplayer.RatKingCatAvoidanceEngineTest`. They run the real sandboxed bot loop. The fixture is a 20x20 map with a north-facing allied king at (8,8), a frozen cat whose 2x2 starts at (8,12), and an enemy king at (17,17). King health is set to 20 with `InternalRobot.addHealth` because LiveMap bodies do not apply `RobotInfo` health. The king limit is 20,000; each observed turn must keep 1,000 headroom.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineLowHealthCatBlockBytecodeHeadroom` | Compiled strategy `BLOCK_AND_FLEE_WHEN_LOW_HEALTH`. Health 20. One dirt in the team stash. Cat movement/action/turning held. | After one turn, dirt is on (8,10), the king is at (8,7), and the turn leaves at least 1,000 under the 20,000 king limit. Measured: 4,290. |
