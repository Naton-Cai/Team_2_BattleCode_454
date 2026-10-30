# Project state

## Dirt collection (baby rats 2026-10-28, kings 2026-10-29)

Requirements are in `.ai/tasks/baby-rat-dirt-collection.md`. Named tests are in `.ai/test-cases.md`.
`Dirt` holds the shared reserve check and `removeDirt` call. Baby rats still use
`Config.BABY_ACTION_ORDER` (attack then dig) and the three in-cone tiles. Kings spawn first; if
spawn does not happen they scan build range `r² <= 8` starting at the north spawn-ring tile.
Both use `Config.MIN_CHEESE_RESERVE_FOR_DIG` (800). Placing dirt to block cats remains deferred.

`RatKingDirtTest` covers spawn-ring order, reserve/raw-cheese payment, spawn-beats-dig, and
turn-boundary recovery. King engine tests wall the spawn ring so digging is the action under test.
Match evidence is **not run**.

## Baby Rat cat block-and-flee (implemented 2026-10-30)

Requirements are in `.ai/tasks/baby-rat-cat-avoidance.md`. Named tests are in `.ai/test-cases.md`.
Default `Config.BABY_CAT_RESPONSE_STRATEGY` is `BITE_THEN_BLOCK_AND_FLEE`. Adjacent in-cone cats
are still bitten; a visible cat with no bite this turn places dirt on the first Chebyshev step
toward the nearest cat and takes one greedy step the other way. Generic digging is skipped while
that strategy sees a cat so the action stays free for `placeDirt`. Kings do not use this policy.

`BabyRatCatAvoidanceTest` covers distant-cat dirt and flee, bite-suppresses-flee, skip-dig,
nearest-cat choice, `NONE` opt-out, and turn-boundary recovery.
`BabyRatCatAvoidanceEngineTest.engineCatBlockTurnBytecodeHeadroom` ran in engine 1.2.6: dirt on
(8,9), flee to (8,7), measured 2,685 bytecodes (1,000 headroom under 17,500). Match evidence is
**not run**.

## Baby Rat attacks (implemented 2026-10-18)

`BabyRat.run` picks up cheese, checks sensed nearby robots for a legal bite, then moves. It always
considers cats and considers opposing baby rats or rat kings only while `getBackstabbingTeam()` is
non-null. Cat bites spend up to seven cheese in cooperation mode or three in backstabbing mode;
rat bites spend none. `BabyRatAttackTest` covers target selection and cheese spend. Controller tests
verify calls and target selection, not damage or instrumented engine behavior.

Engine 1.2.6 references and the supplied specification v1.2.6 identify `getBackstabbingTeam()` as
the cooperation-state check. The attack only senses the forward tile and its left/right neighbors
when `canSenseRobotAtLocation()` permits each, then checks bite legality with `canAttack`. `classes`,
`spotlessCheck`,
and the six focused attack tests passed. The focused Gradle command skips the aggregate 70% coverage
gate because a class-only run cannot meet the whole-bundle threshold. No instrumented scenario was
run, so the 17,500-bytecode headroom and in-engine bite damage remain unverified.

The full test run completed 77 tests with five failures in existing `RobotPlayerTest` expectations for
raw-cheese threshold behavior. The current uncommitted `Config.RAW_CHEESE_MINIMUM = 20` change
explains those expectation mismatches; it predates this attack change and was left intact.

## Test cases

Controller tests call `playTurn` or `RatKing.move` with a `TestRobotController` and return. They do not call `run`, because that method loops on `Clock.yield()`. The controller supplies the answers for one scenario; a true `canMoveForward` or `canMove` does not establish that the engine would allow the step. Engine tests run the real bot loop in the sandbox and inspect the world after yielding. The `% 100` type print in `RobotPlayer` is not covered by a focused test.

`ratplayer.RobotPlayer.playTurn` dispatches on the current unit type each turn. `TestRobotController` starts as `UnitType.BABY_RAT` unless a test changes it. King tests reset the robot-local publication flag and RNG between scenarios. Cheese collection, king return, and pathing configs for this branch are specified in `.ai/tasks/baby-rat-movement.md`, not here.

### BabyRat

- `movesForwardWhenAllowed`: when forward is allowed, the turn calls `moveForward` once and does not turn. `turnCount` becomes 1.
- `movesForwardInEachFacing`: for each facing except `CENTER`, a forward move calls `moveForward` once, does not turn, and leaves that facing in place. The test sets the facing.
- `turnsOnceWhenForwardIsBlocked`: when forward is blocked and a turn is allowed, the turn calls `turn` once with one of the eight facings and does not move.
- `skipsTurnWhenForwardIsBlockedAndTurnIsRefused`: a refused turn calls neither action. Allowing a turn on the next call then records one facing turn and still does not move.
- `turnsTwiceWhenForwardStaysBlocked`: two blocked turns with turning allowed record two facing turns and no move.
- `movesOnceBetweenBlockedTurns`: a blocked turn, a forward move, then another blocked turn records one move and two facing turns.
- `containsGameActionExceptionFromMove`: a `GameActionException` from `moveForward` stays inside `playTurn`. The move was attempted and no turn was attempted.
- `containsGameActionExceptionFromTurn`: a `GameActionException` from `turn` stays inside `playTurn`. The turn was attempted and no move was attempted.
- `containsUnexpectedExceptionFromForwardCheck`: a `RuntimeException` from `canMoveForward` stays inside `playTurn`. Neither action runs.

Shared checks that every helper must match live in abstract `ratplayer.CommonPathingTest`. `GreedyPathTest`, `DistBugPathTest`, and `VisibleBfsPathTest` each run those inherited cases for one `PathingStrategy`. These tests tell the mock which steps are allowed; they do not prove engine collision. In those maps the robot has not seen a wall this turn, so DistBug does not hug a wall and BFS does not search extra tiles. All three pick the next legal step the same way greedy does.

### Baby Rat pathing helpers (implemented 2026-10-19)

`DistBugPath` and `VisibleBfsPath` now implement the bodies behind `Navigation.stepToward`. Follow
state and BFS buffers stay on `Navigation`. King return uses DistBug
(`BABY_PATH_TO_KING_STRATEGY`) because the published king is often outside the 90-degree cone.
Cheese chase uses greedy (`BABY_PATH_TO_CHEESE_STRATEGY`, changed from visible BFS on 2026-10-20)
because the cone-edge engine comparison collected the pile in the same 4 turns for about an eighth
of the bytecodes. Visible BFS remains available. The 11x11 BFS neighbor grid and
`BabyRatBytecodeEngineTest` are in this change. Named cases for those engine tests are in
`.ai/test-cases.md`. DistBug follow/leave and BFS tests for tiles the robot can see live on those
helper classes. Shared tests for maps where the robot has not seen a wall are inherited from
`CommonPathingTest`. Match evidence for greedy cheese chase, DistBug, and visible BFS beyond the
bytecode engine tests is **not run**.

DistBug tests for a wall the robot has seen are in `ratplayer.DistBugPathTest`, which also inherits
the shared `CommonPathingTest` cases:

- `distBugFollowsSensedWallInsteadOfBlockedHeading`: a wall the robot has seen on the desired tile starts follow and takes the left-hand open step.
- `distBugDoesNotFollowWhenBlockedTileIsUnsensed`: a blocked tile the robot has not seen does not start follow; greedy may still side-step.
- `distBugDoesNotFollowWhenSensedTileIsPassable`: another robot or cooldown on a passable tile is not a wall.
- `distBugLeavesFollowWhenCloserAndHeadingIsOpen`: follow ends when the way toward the goal is open and the rat is closer than when it first hit the wall.

BFS tests for tiles the robot can see are in `ratplayer.VisibleBfsPathTest`, which also inherits the
shared `CommonPathingTest` cases:

- `visibleBfsTakesSensedDetourInsteadOfUnsensedShortcut`: BFS walks the southern corridor the robot can see and does not use a northern shortcut it has not seen.
- `visibleBfsDoesNotInventUnsensedTiles`: with only the current tile seen, BFS matches greedy.

### Baby Rat cheese pickup

The following focused tests are in `ratplayer.BabyRatCheesePickupTest`. They use a mocked `RobotController` with shared cheese state to check pickup decisions; they do not validate engine action legality.

- `picksUpOneAdjacentPile`: collects and clears one positive visible pile.
- `doesNothingWhenNoAdjacentCheeseExists`: makes no pickup attempt when sensing finds no cheese.
- `collectsPilesFromAllAdjacentTiles`: when several piles are visible, collects only the closest (ties keep the earlier sense result) and leaves the others because the return threshold is then crossed.
- `skipsOffMapNeighborsAtCorner`: collects the sensed on-map pile at a corner.
- `secondRatDoesNotAttemptToClaimAlreadyCollectedPile`: a second sequential rat does not claim cheese already removed from the shared map state.

### BabyRat attack
- `attacksAdjacentNorthCatWhenFacingNortheast`: cat on adjacent north `(5,6)`; expect one zero-cheese
  bite there. Adding left to the forward tile would look two north and miss this square.
- `attacksAdjacentEastCatWhenFacingNortheast`: cat on adjacent east `(6,5)`; expect one zero-cheese
  bite there. Adding right to the forward tile would look two east and miss this square.

### RatKing

The following tests are in `ratplayer.RatKingTest` unless another class is named. Shared slots 0 and 1 store the center coordinates; slot 2 stores validity.

- `ratKingRunsSpawnBehaviorWhenCheeseIsAvailable`: with movement ready, enough cheese, and a legal spawn location, the king moves once and runs the existing spawning behavior. This integration test is in `RobotPlayerTest`.
- `publishesInitialAndMovedCentersBeforeSpawning`: publishes the initial center, moves once, then publishes the actual new center before spawning. Unreserved shared slots stay unchanged.
- `continuesInEveryFacingAndPublishesEachStep`: for every compass facing, two open turns move twice in that direction and publish each new center without turning.
- `cooldownWaitsWithoutRedirectingEvenWithCheese`: movement cooldown prevents moving and turning even when cheese is sensed. Initial location is published and the RNG is unchanged.
- `turnsToOnlyOpenAlternativeAndMovesOnFollowingTurn`: turns toward the only open direction without moving that turn, then moves and publishes on the next turn.
- `mapsSeededChoiceOntoOnlyLegalAlternativesInCompassOrder`: fixed seeds select only legal alternatives in the existing compass order, without moving that turn.
- `allBlockedWaitsWithoutRandomDrawOrRepeatedPublication`: two fully blocked turns neither move nor turn. Only the initial location is published and the RNG is unchanged.
- `rejectsUnavailableTurnAndRechecksNewlyBlockedDirection`: a refused turn does nothing; a later allowed turn succeeds. If that direction then becomes blocked, the king does not move.
- `cheeseOverridesOpenForwardThenMovesAndForgetsVanishedTarget`: turns toward sensed cheese despite an open forward path, moves on the next turn, and reselects from current sensing instead of retaining vanished targets.
- `nearestCheeseAndCoordinateTiesIgnoreSensingOrder`: selects nearest cheese by squared distance, breaking ties by lowest x then y regardless of sensing order.
- `ignoresEmptyMineAndCenterTargetUsesNormalMovement`: an empty mine is not a target. Cheese at the current center allows normal forward movement without turning toward `CENTER`.
- `blockedCheeseFallsBackToForwardRatherThanSecondTarget`: when the direct cheese step is blocked, continues forward instead of selecting a farther cheese target.
- `blockedCheeseAndForwardUseOpenAlternative`: when cheese and forward steps are blocked, turns toward an open alternative without moving.
- `unavailableCheeseTurnWaitsEvenWithOpenForward`: a refused cheese-directed turn leaves the king stationary even when forward is open. The RNG is unchanged.
- `publishesZeroAndLargestLegalCentersWithoutTouchingOtherSlots`: coordinates round-trip through the shared record, validity becomes 1, and unreserved slots stay unchanged. Zero tests encoding, not legal king placement.
- `failedMoveLeavesActualPublishedPositionAndBoundaryRecovers`: a move exception stays inside `playTurn` and leaves the actual position published. Movement succeeds on a later turn.
- `repairsEveryPossibleFailedPostMoveWriteBeforeMovingAgain`: a failure at each publication write marks robot-local publication incomplete. The next turn republishes the actual location; failures after invalidation leave shared validity 0.
- `initialPublicationFailureStopsMovementAndRetriesNextTurn`: failed initial publication prevents movement. The next turn repairs the record before moving.
- `currentTypeDispatchInitializesKingAfterBabyTurn`: a Baby Rat turn does not publish. After the controller type changes to king, the next turn publishes initial and moved centers.
- `kingTurnAndSensingFailuresReachExistingRecoveryBoundary`: turn and forward-check exceptions stay inside `playTurn`; no failed turn changes facing and no failed forward check moves the king.

- `picksUpEveryLegalSensedCheeseTileAndIgnoresEmptyTiles`: checks all currently sensed positive cheese tiles and picks up every engine-approved location, including tiles away from the center.
- `skipsCheeseWhenEngineRefusesPickup`: a refused pickup produces no pickup call.

### RatKing engine tests

These tests are in `ratplayer.RatKingEngineTest`. Each observed turn checks that the king remains alive, no bot exception is logged, and the valid shared location matches its actual center.

- `openTravelPublishesActualCentersAndWaitsThroughCooldown`: moves on round one, waits through movement cooldown without changing facing, and resumes on round five.
- `offCenterFootprintWallBlocksForwardAndOnlyTurns`: a wall at a destination corner blocks movement even though the destination center is clear. The king turns first and moves on the following turn.
- `diagonalCornerAndMapEdgeUseFullFootprintLegality`: a diagonal step beyond the map edge is rejected. The king turns toward a legal direction and moves next turn.
- `occupiedFootprintTileAndTurningCooldownPreventIllegalActions`: a robot at the destination corner blocks forward movement. Turning cooldown causes waiting until turning becomes legal.
- `trappedKingPublishesAndKeepsFacingAcrossTurns`: a wall ring blocks every step. The king publishes its center and remains stationary with unchanged facing across turns.
- `dirtBlocksCheeseStepAndKingFallsBackToOpenForward`: dirt blocks the cheese-directed footprint, so the king continues along its open forward path.
- `sensedCheeseSteersThenMovesWithImmediatePublication`: the king turns toward real sensed cheese, waits until the next turn to move, and publishes its actual new center.
- `denseSensedCheeseScanAndOtherKingWorkLeaveBytecodeHeadroom`: dense loose cheese and existing spawning execute across eight turns with at least 1,000 bytecodes of headroom per observed king turn.

- `picksUpMultipleTilesAroundNewFootprintButLeavesDistantCheese`: after movement, collects two tiles within legal pickup range, leaves distant cheese untouched, and increases global cheese by the collected amount minus upkeep.
- `cooldownsStillAllowEngineApprovedPickup`: movement and action cooldowns leave the king stationary while engine-approved cheese pickup still succeeds in engine 1.2.6.


The following tests are in `ratplayer.RatKingSpawningTest`. They use Mockito with engine-consistent prices; real engine scenarios below establish actual population changes and legality. Spawning reads `Config.MAX_BABY_RATS` and `Config.MIN_CHEESE_RESERVE` directly; setup and teardown call `Config.reset()` to isolate configuration changes.

- `allowsEachPriceBelowCapAndChecksLegalityImmediately`: prices 10, 20, 30, and 40 each permit exactly one spawn, with the legality query immediately before the build.
- `preservesReserveAtEqualityAndRefusesOneBelow`: with 15 babies, 839 global cheese blocks spawning; 840 allows one spawn leaving 800 before upkeep.
- `stopsAtCapAndReplacesSeveralUnseenLossesOverLaterTurns`: prices at/above the cap block recruitment; later lower prices permit replacements on separate calls and the restored cap stops recruitment again.
- `readinessAndBlockedCandidateReturnWithoutBuilding`: action cooldown skips the cost query; a refused candidate produces no build; later legal placement can succeed. Off-map candidates use this same refusal branch; a separate real off-map spawn fixture was not run.
- `triesEightDirectionsClockwiseAndStopsAtFirstLegalSpawn`: exercises each possible first legal candidate in N, NE, E, SE, S, SW, W, NW order, verifies the exact legality-check prefix, and confirms exactly one build with no further checks. Candidates are two direction steps from the current king center. Mock approval checks ordering; it does not establish engine legality.
- `allBlockedChecksEightTilesWithoutBuilding`: all eight candidates are refused; checks the full clockwise sequence and confirms no build.
- `supportsEightZeroAndIndependentReserveLimits`: an eight-baby cap stops at price 30 and permits price 20; zero disables spawning; a changed reserve of 900 is checked at 919/920 cheese.
- `rejectsUnsupportedConfigWithoutActions`: negative caps, caps 15/17, and a negative reserve fail explicitly without a build.
- `pickupFundsSpawnDespiteMovementCooldownThroughCurrentTypeDispatch`: the normal king dispatch picks up cheese before reading the balance and can spawn while movement is cooling down.
- `buildFailureRecoversOnNextTurnWithoutDisablingSpawns`: an unexpected build exception reaches the existing turn boundary; a later turn can attempt spawning again.

Existing `RobotPlayerTest.ratKingRunsSpawnBehaviorWhenCheeseIsAvailable` and `RatKingTest.publishesInitialAndMovedCentersBeforeSpawning` now arrange global cheese, action readiness, and an actual engine-consistent price. They continue to check current-type dispatch, movement/publication before spawning, and bounded turn decisions.

The following added tests are in `ratplayer.RatKingEngineTest`. They run the actual sandboxed bot loop and verify normal completion/yield, live king, correct shared position, no logged exception, and full-turn bytecode headroom.

- `reachesSixteenStopsAndReplacesDeathOutsideVision`: starts with 15 real allied babies, reaches 16, stops, removes a baby outside king vision, replaces it, then replaces two further losses over separate turns and stops again. The fixture frees an occupied north candidate with a legal engine move before testing the second replacement; clockwise fallback is covered separately.
- `engineReserveBoundaryAndPickupFundOneReplacement`: 839 cheese prevents a spawn; after upkeep leaves 837, pickup of 3 funds a cost-40 spawn. The balance is 800 after payment and 798 after king upkeep.
- `engineActionCooldownThenBlockedNorthFallsBackClockwise`: action cooldown initially prevents spawning. With the king stationary at (8,8), dirt blocks north at (8,10); the next turn spawns northeast at (10,10). Removing the dirt allows a second baby at north on the following turn. This confirms diagonal spawn legality in engine 1.2.6 despite the local API comment describing a radius of two.
- `movedKingSpawnsNorthOfActualNewCenter`: the king moves from (8,8) to (8,9), then creates a baby at (8,11), using its actual new location.

#### Global shared-array layout (default `MAX_CHEESE_MINES = 18`)

64 entries total; each entry holds 0–1023. Only kings write; all rats can read.

| Slots | Entries | Purpose / encoding | Status |
| --- | ---: | --- | --- |
| 0–2 | 3 | Existing king location: x, y, valid (`1` = published) | Active single-record protocol |
| 3–5 | 3 | Reserved for a second king location | Reserved; not implemented |
| 6–8 | 3 | Reserved for a third king location | Reserved; not implemented |
| 9–44 | 36 | Mines 1–18: two entries each, x then y+1 | Allocated |
| 45 | 1 | Tracks Start of backstabbing round to track how long after the first backstab can we still place cattraps | Allocated |
| 46–63 | 19 | Available for future uses or increased mine capacity | Free |

**Budget: 3 active king entries + 6 reserved king entries + 36 mine entries + 19 free = 64.**
A mine's second entry is zero until committed; y+1 commits its exact coordinate.
