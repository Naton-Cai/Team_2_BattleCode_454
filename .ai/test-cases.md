# Test cases

Canonical catalog of feature test cases. Record new cases here, not in `.ai/project-state.md`. Older catalogs already in project state stay there until they are moved.

`.ai/testing.md` owns commands and evidence procedures. The task contract owns required behavior. This file lists named checks: class, setup, and expected outcome. Status is **proposed** until the test exists and has been run.

## Rat King ID engine scenarios

Implemented and run with engine 1.2.6 in `ratplayer.RatKingEngineTest`. The 20×20
`RatKingMovementFixture` uses seed 6147, the active bot on team A, and an inactive opponent.

| Test | Setup and expected outcome |
| --- | --- |
| `promotedKingPublishesIndependentlyWhileCarrierUsesPrimary` | Promote a second king through the engine API. Both kings publish independently and the carrier still delivers to ID 0. |
| `replacementReclaimsPrimaryIdWithoutCarrierSwitchingToSurvivor` | Kill ID 0 with a reachable ID 1. After two rounds the primary lookup returns null and the carrier keeps its cheese. A replacement claims ID 0 and the carrier can deliver to it. |
| `fiveKingsPublishWithoutOverwritingFullMineRegistry` | Run five stationary allied kings for three rounds with all 18 mine records filled. Every ID refreshes in slots 0–14; all mine records and the backstab round at slot 51 survive. |

The scenarios check for bot exceptions and enforce full-turn bytecode headroom. The exact
validation command was `./gradlew classes test spotlessCheck`. Results are in
`build/test-results/test/TEST-ratplayer.RatKingEngineTest.xml`.

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

Requirements are in `.ai/tasks/baby-rat-cat-avoidance.md`. Default `Config.BABY_CAT_RESPONSE_STRATEGY` is `BITE_THEN_BLOCK_AND_FLEE`. `Config.BABY_CAT_FLEE_HEALTH` starts at 20. Baby and king both use `Config.CatResponseStrategy`.

### Controller tests

These tests are in `ratplayer.BabyRatCatAvoidanceTest`. They call `RobotPlayer.playTurn` with a `TestRobotController`. Setup and teardown call `Config.reset()` and clear `BabyRat.attackedThisTurn`. Unless a test says otherwise, the rat is at (5,5) facing north, has 0 raw cheese and 1,000 global cheese, forward and greedy movement are allowed, and cat-trap placement is left refused.

These tests check which calls happen. They do not show what the engine allows.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `distantCatPlacesDirtAndFleesInsteadOfDigging` | Cat at (5,9). `canPlaceDirt` and `canRemoveDirt` both true. | One `placeDirt` at (5,6), no bite, no dig, one `move:SOUTH`. |
| `adjacentCatBiteSkipsDirtAndKeepsWander` | Cat on the forward tile (5,6), bite legal, same cat also in `senseNearbyRobots`. | One bite, no `placeDirt`, wander `moveForward`, no south flee. |
| `noCatsStillDigsWhenAllowed` | No nearby robots, forward dig legal. | One `removeDirt`, no `placeDirt`, wander still runs. |
| `refusedDirtStillFleesAndSkipsDig` | Cat at (5,9), `canPlaceDirt` false, dig would otherwise be legal. | No dirt, no dig, one `move:SOUTH`. |
| `alliedTrapOnBlockTileSkipsDirtAndStillFlees` | Cat at (5,9), dirt legal, sensed tile is an allied `CAT_TRAP`. | No `placeDirt`, one `move:SOUTH`. |
| `twoCatsUseNearestForDirtAndFlee` | East cat at (9,5) listed first, closer north cat at (5,8) second. | Dirt at (5,6) and a south step. No west step. |
| `placeDirtFailureContainedAtTurnBoundary` | Cat at (5,9), `placeDirt` throws on the first turn and is cleared before the second. | First turn returns from `playTurn` with one dirt attempt and no flee. Second turn places dirt once more. |
| `lowHealthStrategyPlacesDirtAndFlees` | `BABY_CAT_RESPONSE_STRATEGY = BLOCK_AND_FLEE_WHEN_LOW_HEALTH`, health at `BABY_CAT_FLEE_HEALTH` (20), cat at (5,9), dig legal. | One `placeDirt` at (5,6), no dig, one `move:SOUTH`. |
| `lowHealthStrategyAboveThresholdKeepsDigAndWander` | Same strategy, health `BABY_CAT_FLEE_HEALTH + 1`, cat at (5,9), dig legal. | One dig, no dirt, wander forward. |
| `noneStrategyKeepsDigAndWander` | `BABY_CAT_RESPONSE_STRATEGY = NONE`, cat at (5,9), dig and dirt both legal. | One dig, no dirt, wander forward, no south flee. |

### Engine tests

These tests are in `ratplayer.BabyRatCatAvoidanceEngineTest`. They run the real sandboxed bot loop through `PlayerControlProvider` and read `InternalRobot.getBytecodesUsed()` after each turn. The sandbox loads compiled `Config` values. The fixture is a 20x20 map with a north-facing allied baby at (8,8), a frozen cat whose 2x2 starts at (8,12), and an enemy king at (17,17). `LiveMap` cat waypoints are required for `InternalRobot` construction. The game is put in backstab mode. The baby limit is 17,500; each observed turn must keep 1,000 headroom.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineCatBlockTurnBytecodeHeadroom` | Compiled strategy `BITE_THEN_BLOCK_AND_FLEE`. One dirt in the team stash. Cat movement/action/turning held. | After one turn, dirt is on (8,9), the baby is at (8,7), and the turn leaves at least 1,000 under the 17,500 baby-rat limit. Measured: 2,734. |

## Rat King low-health cat block-and-flee

Status: **implemented and run** (2026-10-30). Engine bytecode evidence is **run**. Match evidence is **not run**.

Requirements are in `.ai/tasks/rat-king-cat-avoidance.md`. Default `Config.KING_CAT_RESPONSE_STRATEGY` is `BLOCK_AND_FLEE_WHEN_LOW_HEALTH`. `Config.KING_CAT_FLEE_HEALTH` starts at 600. King and baby both use `Config.CatResponseStrategy`.

### Controller tests

These tests are in `ratplayer.RatKingCatAvoidanceTest`. They call `RobotPlayer.playTurn` with a `TestRobotController` set to `UnitType.RAT_KING`. Setup and teardown call `Config.reset()` and clear `RatKing.locationPublished`. Unless a test says otherwise, the king is at (10,10) facing north, spawn is refused, and forward and directional movement are allowed.

These tests check which calls happen. They do not show what the engine allows.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `lowHealthVisibleCatPlacesDirtAndFlees` | Health at `KING_CAT_FLEE_HEALTH` (600), cat at (10,16), spawn and dig would be legal. | One `placeDirt` at (10,12), no spawn, no dig, one `move:SOUTH`. |
| `healthAboveThresholdKeepsDigAndWander` | Health `KING_CAT_FLEE_HEALTH + 1`, same cat, dig legal. | No dirt, one dig, wander forward. |
| `lowHealthWithoutCatStillDigs` | Health at `KING_CAT_FLEE_HEALTH`, no nearby robots, dig legal. | One dig, no `placeDirt`. |
| `biteThenBlockAndFleeIgnoresHealthThreshold` | `KING_CAT_RESPONSE_STRATEGY = BITE_THEN_BLOCK_AND_FLEE`, health `KING_CAT_FLEE_HEALTH + 1`, cat at (10,16), spawn and dig would be legal. | One `placeDirt` at (10,12), no spawn, no dig, one `move:SOUTH`. |
| `noneStrategyKeepsDigAndWander` | `KING_CAT_RESPONSE_STRATEGY = NONE`, health at `KING_CAT_FLEE_HEALTH`, cat visible, dig legal. | One dig, no dirt, wander forward. |
| `blockedOppositeHeadingSidestepsAndStillPlacesDirt` | Health at `KING_CAT_FLEE_HEALTH` (600), cat at (10,16), south refused, other directions open, spawn and dig would be legal. | One `placeDirt` at (10,12), no spawn, no dig, one `move:SOUTHEAST`, no `move:SOUTH`. |
| `placeDirtFailureContainedAtTurnBoundary` | Health at `KING_CAT_FLEE_HEALTH`, cat visible, `placeDirt` throws on the first turn and is cleared before the second. | First turn returns from `playTurn` with one dirt attempt and no flee. Second turn places dirt once more. |

### Engine tests

These tests are in `ratplayer.RatKingCatAvoidanceEngineTest`. They run the real sandboxed bot loop. The fixture is a 20x20 map with a north-facing allied king at (8,8), a frozen cat whose 2x2 starts at (8,12), and an enemy king at (17,17). King health is set to `KING_CAT_FLEE_HEALTH` (600) with `InternalRobot.addHealth` because LiveMap bodies do not apply `RobotInfo` health. The king limit is 20,000; each observed turn must keep 1,000 headroom.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineLowHealthCatBlockBytecodeHeadroom` | Compiled strategy `BLOCK_AND_FLEE_WHEN_LOW_HEALTH`. Health at `KING_CAT_FLEE_HEALTH` (600). One dirt in the team stash. Cat movement/action/turning held. | After one turn, dirt is on (8,10), the king is at (8,7), and the turn leaves at least 1,000 under the 20,000 king limit. Measured: 4,288. |

## Rat King mine home and bounded navigation

Status: **implemented and run** (2026-11-01). Radius 4, stall threshold 10,
exploration retry interval 10. Engine 1.2.6. Evidence: `matches/king-home-20261101/`
(local ignored artifacts); implementation summary in `.ai/project-state.md`.

### Controller tests (`ratplayer.RatKingTest`)

The controller cases reset home/discovery state, configuration, and RNG between robots.
Canned legality answers establish decisions, not actual collision geometry.

| Test | Setup and expected outcome |
| --- | --- |
| `looseCheeseNeverRedirectsExplorationOrHomeHolding` | Repeated eastern cheese cannot redirect northward exploration or lure the king away from an empty home mine. |
| `simultaneousFirstMinesUseDistanceThenCoordinatesAndHomePersists` | Reverse sensing order and exercise x/y ties; first home remains despite later discoveries. |
| `earlierSharedRecordBeatsCloserMineDiscoveredThisTurn` | A pre-existing committed record initializes home before the king publishes this turn's local discoveries. |
| `radiusBoundaryHoldsAt16AndTravelsAt17WithDelayedTurn` | Hold at squared distance 16; turn at 17 and move on a later turn; then hold inside the area. |
| `stallsOnTenthReadyTurnThenRetriesAfterTenExplorationTurns` | Block all steps; nine failed navigation turns retain home, tenth defers it, tenth exploration turn restores it. Cooldown turns pause both counts. |
| `stalledHomeChoosesNearestAlternativeWithCoordinateTies` | Tenth failed turn selects the nearest other shared mine with x/y tie breaking. |
| `turnOnlyDecisionsCountAsStallsAndSuccessfulProgressResetsThem` | Required turning increments the counter; a closer successful move resets it. |
| `occupancyWaitsAndFreshLegalityAllowsProgressWithoutChangingHome` | Passable but occupied terrain causes a bounded wait; after clearance, fresh checks permit progress. |
| `escapePausesStallAndRetryAndDoesNotForceNormalMovement` | Active cat handling pauses both counters even when no escape is possible. |
| `escapeDiscoveriesAreComparedFromFinalPositionAndOldOnesAreExcluded` | Choose new escape discoveries by final position, retaining home during escape and adopting on the first unhandled cooldown turn. |
| `equalOrFartherEscapeMineRetainsHomeAndCandidateTiesUseCoordinates` | Strict improvement is required over old home; equal new-candidate distances use x/y ordering. |
| `postMoveEscapeDiscoveryRehomesAndBabyReadsActualPublishedCenter` | Post-move sensing reveals a mine; after adoption a loaded baby reads the relocated center rather than either mine. |
| `previouslyPublishedButLocallyNewMineCanReplaceHomeDuringEscape` | Another king's record does not exclude a mine newly encountered locally during escape. |
| `lowNavigationBudgetPreservesPublicationPickupAndSpawning` | Mock the optional-navigation cutoff; essential publication, pickup, and spawning continue without adding a stall. |
| `localMinesRemainUsableWhenSharedRegistryHasNoCapacity` | A zero-capacity shared registry does not prevent local initial home selection or escape replacement. |
| `escapeMovementImprovementResetsStallBeforeNormalNavigation` | A flee displacement improving home distance resets the old nine-turn stall before normal navigation resumes. |
| `deferredHomeWaitsUntilEscapeEndsBeforeAdoptingDiscovery` | A paused retry retains its ten turns during escape; the closer discovery replaces home only afterward. |
| `stalledHomeCanUseLocallyKnownAlternateOutsideFullRegistry` | A locally seen alternate remains usable for stall replacement even with no shared capacity. |
| `invalidSettingsAreRejectedAndResetRestoresAgreedValues` | Reject negative radius and nonpositive delays; reset restores 4/10/10. |

Existing publication-failure recovery, current-type dispatch, pickup, spawning, dirt,
and cat-response checks still run. Their setups now reset king home state.

### Instrumented engine tests

Each fixture observes the real robot loop after yield, checks actual center publication,
rejects exception logs, and requires full-turn use below 19,000 of 20,000 bytecodes.

| Class / test | Setup and expected outcome |
| --- | --- |
| `RatKingEngineTest.visibleCheeseDoesNotRedirectExploration` | Visible eastern cheese leaves northward exploration unchanged. |
| `RatKingEngineTest.mineHomeHoldsAtRadiusAndStillPicksUpCheese` | Empty mine at distance four anchors the king while footprint cheese is picked up and distant cheese remains. |
| `RatKingEngineTest.fullFootprintDetourInitiallyMovesAwayThenReachesHomeArea` | A three-tile wall forces a farther first step; bounded wall following reaches the home radius and holds. |
| `RatKingEngineTest.homeTravelWaitsForOffCenterRobotThenUsesFreshLegality` | A baby blocks the destination footprint corner; the king waits, then progresses when it is removed. |
| `RatKingEngineTest.distantHomeMakesProgressWithoutAssumingUnseenTerrainClear` | A shared mine beyond vision directs legal local progress with delayed turning. |
| `RatKingCatAvoidanceEngineTest.escapeDiscoversCloserMineAndBabyTracksRelocatedKing` | North cat forces southward escape, revealing a southern mine; after threat removal the king adopts that area and a loaded baby steps toward its actual updated center. Peak: 11,229 bytecodes. |

The existing mine-publication fixture now places the initial mine in the direction of
home travel, so its move still reveals the second mine without relying on wandering
away from home. Existing edge, diagonal, dirt, trapped, cooldown, dense sensing,
spawning, and cat tests retain their engine checks.


## Rat King inward-biased initial fallback

Status: **implemented and run** (2026-11-01). User-approved edge margin 3, per-heading
inward weight 3, and preserved open inward momentum. Bias is initial-search-only;
deferred-home retry retains its uniform fallback. Evidence and matched six-game
comparison: `matches/king-center-bias-20261101/README.md` (local ignored artifacts).

### Controller tests (`ratplayer.RatKingCenterBiasTest`)

| Test | Setup and expected outcome |
| --- | --- |
| `openInteriorForwardKeepsMomentumEvenWhenHeadingAwayFromCenter` | Interior forward stays unchanged and consumes no random draw. |
| `edgeBoundaryAtThreeBiasesButFourKeepsForward` | At center x=4 continue forward; at x=3 apply weighted selection and turn before moving. |
| `weightedSlotsKeepThreeToOneOddsAndPermitOutwardMoves` | Forty varied reproducible seeds map to explicit E,E,E,W slots; both inward and outward outcomes occur, and the next open inward step makes no extra draw. |
| `biasAppliesAtAllFourEdges` | Each edge gives the inward alternative its configured weight. |
| `blockedInwardStepsStillAllowAnOutwardEscape` | With no inward legal step, turn and move outward rather than freezing. |
| `evenSizedMapUsesTrueCenterAndTreatsEqualDistanceAsNeutral` | On 20x20, S from (1,10) is neutral relative to (9.5,9.5); only E receives the bonus. |
| `retryIntervalKeepsUniformFallbackDespiteBeingNearEdge` | A deferred home disables the initial-search bias; seed 0 selects uniform W instead of weighted E. |
| `cooldownAtEdgeDoesNotTurnMoveOrConsumeRandomness` | Movement cooldown skips exploration even when legal alternatives are canned true. |
| `homeHoldingAtEdgeTakesPriorityOverInwardExploration` | A nearby mine becomes home and holds without a bias draw. |
| `activeFleeAtEdgeDoesNotRunOrConsumeRandomnessForNormalExploration` | Existing cat response handles the turn; no normal move, turn, or random draw occurs. |
| `unavailableTurnAndChangedLegalityNeverForceAForwardMove` | Refused turning waits; after turning, a newly blocked heading is rechecked before moving. |

`RatKingTest.mapsSeededChoiceOntoInwardWeightedAlternativesInCompassOrder` replaces
the old uniform initial-selection expectation with explicit NE,NE,NE,S,NW slots.
Cat-response controls declare map dimensions so their origin is a real interior.

### Engine tests (`ratplayer.RatKingEngineTest`)

| Test | Setup and expected outcome |
| --- | --- |
| `edgeSearchTurnsInwardThenMovesWithoutRerolling` | A 3-tile-high E/W corridor leaves outward forward legal; seed 6147 selects E, turns, then advances inward with proper cooldown and publication. |
| `denseEdgeSelectionAndOtherKingWorkLeaveBytecodeHeadroom` | Edge exploration plus dense loose cheese, pickup, publication and spawning complete below 19,000. |

Existing edge/full-footprint, mine-home, post-flee home replacement, recovery, pickup,
and lifecycle checks still run. Full suite: 321 passed, 0 failed, 2 existing skips.

## Rat King backstab attack when unable to flee

Status: **implemented and run** (2026-11-02). Engine bytecode evidence is **run**. Match evidence
is **not run**.

A king bites an opposing baby rat or rat king on the 16-tile attack ring (`r² <= 8`) only while
`getBackstabbingTeam()` is set and the king cannot step this turn (movement not ready, or every
heading illegal). Cat flee still wins when a step is legal. Rat bites spend no cheese.

### Controller tests (`ratplayer.RatKingAttackTest`)

These tests call `RobotPlayer.playTurn` with a `TestRobotController` set to `UnitType.RAT_KING`.
Setup and teardown call `Config.reset()` and clear `RatKing.locationPublished`. Unless a test says
otherwise, the king is at (10,10) facing north, movement is not ready, spawn is refused, and
backstabbing has already started.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `attacksEnemyBabyWhenUnableToFleeDuringBackstabbing` | Enemy baby at (10,12), spawn otherwise legal. | One `attack:(10,12):0`, no spawn. |
| `attacksEnemyBabyOnKnightRingTile` | Enemy baby at (11,12). | One bite at (11,12). |
| `attacksEnemyKingWhenUnableToFlee` | Enemy king at (10,12). | One zero-cheese bite. |
| `doesNotAttackEnemyRatDuringCooperation` | Cooperation, enemy baby on the ring. | No attack. |
| `doesNotAttackWhenKingCanFlee` | Movement ready, headings legal, enemy on the ring. | No attack; wander `moveForward`. |
| `successfulCatFleeSkipsAttack` | Health 600, cat at (10,16), movement legal, enemy on the ring. | Dirt and south flee; no attack. |
| `blockedCatFleeAttacksEnemyRat` | Health 600, visible cat, movement not ready, dirt refused, enemy on the ring. | One bite; no flee step. |
| `doesNotAttackAlliedRat` | Allied baby on the ring. | No attack. |
| `doesNotAttackEnemyOutsideAttackRing` | Enemy baby at (10,13). | No attack. |
| `spawnStillRunsWhenUnableToFleeWithoutEnemy` | No nearby robot, spawn legal. | One `buildRat`, no attack. |
| `attackFailureContainedAtTurnBoundary` | First `attack` throws; failure cleared before the second turn. | One attempt per turn; second turn bites. |
| `attacksWhenEveryHeadingIsBlocked` | Movement ready, every `canMove` false, enemy on the ring. | One bite; no move. |

### Engine tests (`ratplayer.RatKingAttackEngineTest`)

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineBlockedKingBitesEnemyBabyAndLeavesBytecodeHeadroom` | King at (8,8) with movement/turning held, Team B baby at (9,10), spawn ring walled, backstab on. | Baby loses 10 HP, king stays put, no allied spawn, at least 1,000 headroom under 20,000. Measured: 6,631. |

## Rat King last-resort bite of an adjacent cat

Status: **implemented and run** (2026-11-09). Engine bytecode evidence is **run**. Match evidence is
**not run**.

Requirements are in `.ai/tasks/rat-king-adjacent-cat-attack.md`. A king bites a cat on the attack
ring (`r² <= 8`) only when it cannot step and the spawn-ring tile toward that cat accepts neither
dirt nor a cat trap. The bite spends no cheese and runs in cooperation. A legal step, placeable
dirt, or a placeable cat trap skips it. Enemy-rat bites still take the action first.

### Controller tests (`ratplayer.RatKingAttackTest`)

Same fixture as the backstab-attack tests, except the cat cases use cooperation, health at
`KING_CAT_FLEE_HEALTH`, dirt refused, and cat trap refused unless a test says otherwise.

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `attacksAdjacentCatWhenStuckAndCannotBlock` | Cooperation, movement not ready, cat at (10,12), spawn otherwise legal. | One `attack:(10,12):0`, no spawn, no cat trap. |
| `attacksAdjacentCatOnKnightRingTile` | Cat at (11,12). | One bite at (11,12). |
| `doesNotAttackDistantCatWhenStuck` | Cat at (10,16). | No attack. |
| `doesNotAttackAdjacentCatWhenKingCanStep` | Movement ready, cat at (10,12). | A step, no attack. |
| `doesNotAttackAdjacentCatWhenDirtCanBePlaced` | Stuck, `canPlaceDirt` true. | One `placeDirt`, no attack. |
| `doesNotAttackAdjacentCatWhenCatTrapCanBePlaced` | Stuck, `canPlaceCatTrap` true. | No attack and no `placeCatTrap`. |
| `adjacentCatAttackFailureContainedAtTurnBoundary` | First cat bite throws; failure cleared before the second turn. | One attempt per turn; second turn bites. |

### Engine tests (`ratplayer.RatKingAttackEngineTest`)

| Test | Setup | Expected outcome |
| --- | --- | --- |
| `engineStuckKingBitesAdjacentCatAndLeavesBytecodeHeadroom` | King at (8,8) with movement held, cat 2x2 anchored at (9,10), backstab on. | Cat loses 10 HP, king stays put, at least 1,000 headroom under 20,000. Measured: 9,725. |

## Requested formation population-cap exemption (2026-11-11)

KingFormationTest adds requestedFormationBypassesCapForOnlyItsSevenBirths (ordinary capped birth
refused; chosen plus six above-cap births allowed; eighth refused),
capExemptionPreservesReserveAtEqualityAndRechecksRisingCosts (one below/equal reserve plus current
price, both before claiming and during recruitment), and
waitingRequestCannotBypassCapWhileAnotherKingOwnsTheGroup (no competing above-cap births).

KingFormationEngineTest.requestedFormationExceedsFullPopulationOnlyWhileFundedAndRecruiting seeds
32 ordinary babies away from the footprint. It verifies the cap before request, one-below/equal
actual-price reserve boundaries, a funding pause and resume, seven additional members, real
promotion, surviving original babies, and capped ordinary spawning afterward. Slot canaries and
complete-turn budget gates remain active. Engine 1.2.6, SpawnerFormationFixture seed 6147, side A;
observed peaks baby 9,730 / king 9,257, first promoted-king turn by round 14.

./gradlew spotlessApply classes test spotlessCheck: 270 passed, two manual BFS cases skipped.
A real jail side-B/request-100 match previously assigned zero members due to the cap; the fix
assigned all seven and promoted at round 241. Full evidence under matches/spawner-cap-20261111/.

## Prefunding experiment reverted (2026-11-11)

Restored the staged-recruitment tests listed in Requested formation population-cap exemption.
Removed prefunding-only tests and restored first-birth/per-birth reserve equality coverage.
./gradlew classes test spotlessCheck passed: 270 passed, two existing manual BFS tests skipped.
Formation tests and production code match the saved staged/cap-exempt baseline exactly.
The discarded prefunding tests, results, and comparison replays remain archived under
matches/spawner-prefund-20261111/; they do not describe current behavior.
