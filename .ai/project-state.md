# Project state

## Test cases

Controller tests call `playTurn` or `RatKing.move` with a `TestRobotController` and return. They do not call `run`, because that method loops on `Clock.yield()`. The controller supplies the answers for one scenario; a true `canMoveForward` does not establish that the engine would allow the step. Engine tests run the real bot loop in the sandbox and inspect the world after yielding. The `% 100` type print in `RobotPlayer` is not covered by a focused test.

`ratplayer.RobotPlayer.playTurn` dispatches on the current unit type each turn. `TestRobotController` starts as `UnitType.BABY_RAT` unless a test changes it. King tests reset the robot-local publication flag and RNG between scenarios.

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

Validated on 2026-10-17 with engine 1.2.6, OpenJDK 21.0.12.1, and Gradle 8.10: `./gradlew classes test build verify -Pteam=ratplayer` passed all 47 tests, including the 20 existing tests. Line coverage was 86.15%; peak observed full king turn was 3,191 of 20,000 bytecodes. Both smoke-match sides completed without logged bot exceptions. Detailed commands, revisions, logs, and limits are retained in `matches/rat-king-movement-validation/validation-record.md` alongside the existing evidence. That directory is Git-ignored.


Pickup follow-up, 2026-10-17: pickup now scans fresh map information after movement and collects all sensed positive cheese tiles permitted by `canPickUpCheese`, rather than only the cached center. `currentLocation` remains initialized and refreshed by location publication. `./gradlew classes test build verify -Pteam=ratplayer` passed all 51 tests (21 king controller tests, 10 king engine tests, and all 20 existing tests). Peak observed king turn was 5,308 of 20,000 bytecodes. On `DefaultSmall` against `examplefuncsplayer`, ratplayer lost as A at round 1018 and won as B at round 1310; neither match logged bot exceptions. These two matches do not establish a win-rate improvement. Replays: `matches/rat-king-pickup-all-a.bc26` and `matches/rat-king-pickup-all-b.bc26`. Commands, logs, test XML, and candidate source are retained in the Git-ignored `matches/rat-king-pickup-validation/` directory.
