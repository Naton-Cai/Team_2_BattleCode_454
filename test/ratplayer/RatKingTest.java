package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/** Decision and publication tests. Canned legality answers do not establish engine geometry. */
public class RatKingTest {
  private TestRobotController rc;

  @Before
  public void setUp() {
    // Each scenario starts with a fresh robot, including its publication flag and RNG.
    resetKingState();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    RatKing.locationPublished = false;
    rc = new TestRobotController();
    rc.setUnitType(UnitType.RAT_KING);
    rc.setMapWidth(60);
    rc.setMapHeight(60);
    rc.setLocation(new MapLocation(10, 10));
    rc.setMovementReady(true);
    rc.setSimulateMovement(true);
  }

  @After
  public void resetKingState() {
    Config.reset();
    RatKing.home = null;
    RatKing.retreatMine = null;
    RatKing.retreating = false;
    RatKing.catFreeTurns = 0;
  }

  @Test
  public void recordsEmptyMinesOnCooldownButNotLooseCheese() throws Exception {
    // Mine presence is independent of cheese quantity; discovery must run on cooldown turns.
    rc.setMapWidth(60);
    rc.setMapHeight(60);
    rc.setMovementReady(false);
    rc.setNearbyMapInfos(
        new MapInfo[] {
          new MapInfo(new MapLocation(0, 0), true, null, false, false, 0, TrapType.NONE, true),
          cheese(14, 10, 20)
        });
    RobotPlayer.playTurn(rc);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(0, 0), CheeseMines.read(rc, 0));
    assertEquals(null, CheeseMines.read(rc, 1));
    // Losing visibility never clears an earlier discovery.
    rc.setNearbyMapInfos(new MapInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(0, 0), CheeseMines.read(rc, 0));
  }

  @Test
  public void publishesInitialAndMovedCentersBeforeSpawning() throws Exception {
    // Arrange an open path and an affordable spawn below the population cap.
    rc.setForwardAllowed(true);
    rc.setGlobalCheese(1000);
    rc.setActionReady(true);
    rc.setCurrentRatCost(10);
    rc.setCanBuildRat(true);
    rc.setAdjacentLocation(new MapLocation(10, 12));
    rc.writeSharedArray(63, 777);
    rc.calls().clear();

    RobotPlayer.playTurn(rc);

    assertEquals(new MapLocation(10, 11), rc.getLocation());
    assertPosition(10, 11);
    assertEquals(777, rc.readSharedArray(63));
    assertEquals(
        List.of(
            "writeSharedArray:2:0",
            "writeSharedArray:0:10",
            "writeSharedArray:1:10",
            "writeSharedArray:2:1",
            "moveForward",
            "writeSharedArray:2:0",
            "writeSharedArray:0:10",
            "writeSharedArray:1:11",
            "writeSharedArray:2:1"),
        rc.calls().subList(0, 9));
    assertTrue(rc.calls().get(9).startsWith("buildRat:"));
    assertNextRandomUnchanged();
  }

  @Test
  public void continuesInEveryFacingAndPublishesEachStep() throws Exception {
    for (Direction facing : RobotPlayer.directions) {
      setUp();
      rc.setDirection(facing);
      rc.setForwardAllowed(true);
      RatKing.move(rc);
      RatKing.move(rc);
      MapLocation expected = new MapLocation(10, 10).add(facing).add(facing);
      assertEquals(expected, rc.getLocation());
      assertPosition(expected.x, expected.y);
      assertEquals(2, rc.moveCount());
      assertEquals(0, rc.turnCount());
      assertNextRandomUnchanged();
    }
  }

  @Test
  public void cooldownWaitsWithoutRedirectingEvenWithCheese() throws Exception {
    rc.setMovementReady(false);
    rc.setCanMove(true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(14, 10, 20)});
    RatKing.move(rc);
    assertPosition(10, 10);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void turnsToOnlyOpenAlternativeAndMovesOnFollowingTurn() throws Exception {
    rc.setDirectionAllowed(Direction.EAST, true);
    RatKing.move(rc);
    assertEquals(List.of(Direction.EAST), rc.turns());
    assertEquals(0, rc.moveCount());
    assertPosition(10, 10);
    RatKing.move(rc);
    assertEquals(1, rc.moveCount());
    assertPosition(11, 10);
  }

  @Test
  public void mapsSeededChoiceOntoOnlyLegalAlternativesInCompassOrder() throws Exception {
    // Repeat with several seeds: expected choices index the explicit N-clockwise candidate order.
    for (int seed = 0; seed < 20; seed++) {
      setUp();
      RobotPlayer.rng.setSeed(seed);
      rc.setDirectionAllowed(Direction.NORTHEAST, true);
      rc.setDirectionAllowed(Direction.SOUTH, true);
      rc.setDirectionAllowed(Direction.NORTHWEST, true);
      Direction[] expected = {Direction.NORTHEAST, Direction.SOUTH, Direction.NORTHWEST};
      RatKing.move(rc);
      assertEquals(expected[new Random(seed).nextInt(3)], rc.getDirection());
      assertEquals(0, rc.moveCount());
    }
  }

  @Test
  public void allBlockedWaitsWithoutRandomDrawOrRepeatedPublication() throws Exception {
    RatKing.move(rc);
    int initialCalls = rc.calls().size();
    RatKing.move(rc);
    assertEquals(initialCalls, rc.calls().size());
    assertEquals(0, rc.turnCount());
    assertEquals(0, rc.moveCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void rejectsUnavailableTurnAndRechecksNewlyBlockedDirection() throws Exception {
    rc.setDirectionAllowed(Direction.EAST, true);
    rc.setTurnAllowed(false);
    RatKing.move(rc);
    assertEquals(0, rc.turnCount());
    rc.setTurnAllowed(true);
    RatKing.move(rc);
    rc.setDirectionAllowed(Direction.EAST, false);
    RatKing.move(rc);
    assertEquals(1, rc.turnCount());
    assertEquals(0, rc.moveCount());
    assertPosition(10, 10);
  }

  @Test
  public void cheeseRouteTurnsThenMovesAndRechecksVanishedTarget() throws Exception {
    // A complete visible disk is needed to establish hypothetical 3×3 footprints.
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(visibleCheese(14, 10));
    RatKing.move(rc);
    assertEquals(Direction.EAST, rc.getDirection());
    assertEquals(0, rc.moveCount());
    RatKing.move(rc);
    assertPosition(11, 10);
    rc.setNearbyMapInfos(visibleCheese(11, 14));
    RatKing.move(rc);
    assertEquals(Direction.NORTH, rc.getDirection());
    rc.setNearbyMapInfos(new MapInfo[0]);
    RatKing.move(rc);
    assertPosition(11, 11);
    assertNextRandomUnchanged();
  }

  @Test
  public void nearestCheeseAndCoordinateTiesIgnoreSensingOrder() throws Exception {
    // Equal distances choose lowest x, then lowest y, independent of sensing order.
    rc.setCanMove(true);
    MapInfo[] infos = visibleCheese(8, 10, 10, 12, 10, 8);
    rc.setNearbyMapInfos(infos);
    RatKing.move(rc);
    assertEquals(Direction.WEST, rc.getDirection());
    setUp();
    rc.setCanMove(true);
    Collections.reverse(Arrays.asList(infos));
    rc.setNearbyMapInfos(infos);
    RatKing.move(rc);
    assertEquals(Direction.WEST, rc.getDirection());
    setUp();
    rc.setCanMove(true);
    rc.setNearbyMapInfos(visibleCheese(10, 12, 10, 8));
    RatKing.move(rc);
    assertEquals(Direction.SOUTH, rc.getDirection());
  }

  @Test
  public void alreadyCollectibleCheeseHoldsWithoutTravelOrRandomDraw() throws Exception {
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(visibleCheese(11, 11));
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void uncertainNearestTargetHoldsWithoutTryingAnotherCheese() throws Exception {
    // Sparse observations cannot certify a footprint, even though the fake allows a move.
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(12, 10, 1), cheese(6, 10, 1)});
    RatKing.move(rc);
    assertEquals(KingNavigation.RouteResult.UNCONFIRMED, KingNavigation.routeResult);
    assertPosition(10, 10);
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void unavailableRouteTurnWaitsEvenWithOpenForward() throws Exception {
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setTurnAllowed(false);
    rc.setNearbyMapInfos(visibleCheese(12, 10));
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertPosition(10, 10);
  }

  @Test
  public void visibleDetourAvoidsBlockedDirectStep() {
    // The northwestern cheese is reachable around the off-center wall. Compass ordering
    // searches around it rather than requiring a direct northwestern step.
    MapInfo[] infos = visibleCheese(7, 13);
    block(infos, 9, 12);
    Direction step = search(infos, new MapLocation(7, 13));
    assertEquals(KingNavigation.RouteResult.CONFIRMED, KingNavigation.routeResult);
    assertEquals(Direction.WEST, step);
    assertTrue(rc.calls().isEmpty());
    assertNextRandomUnchanged();
  }

  @Test
  public void completeFootprintRejectsAnEnclosedCheeseAndOffCenterObstacles() {
    MapInfo[] infos = visibleCheese(13, 10);
    // Ring immediately outside the king's footprint: every center-only route looks open.
    for (int x = 8; x <= 12; x++) {
      for (int y = 8; y <= 12; y++) {
        if (x == 8 || x == 12 || y == 8 || y == 12) {
          block(infos, x, y);
        }
      }
    }
    Direction step = search(infos, new MapLocation(13, 10));
    assertEquals(KingNavigation.RouteResult.BLOCKED, KingNavigation.routeResult);
    assertEquals(null, step);
  }

  @Test
  public void collectionGoalAcceptsOffsetAndFreshObservationsReplaceOldRoute() {
    // One eastward step puts diagonal cheese inside pickup radius; center-on-cheese is unnecessary.
    Direction step = search(visibleCheese(12, 11), new MapLocation(12, 11));
    assertEquals(KingNavigation.RouteResult.CONFIRMED, KingNavigation.routeResult);
    assertTrue(rc.getLocation().add(step).distanceSquaredTo(new MapLocation(12, 11)) <= 2);
    step = search(new MapInfo[0], new MapLocation(12, 11));
    assertEquals(KingNavigation.RouteResult.UNCONFIRMED, KingNavigation.routeResult);
    assertEquals(null, step);
  }

  @Test
  public void occupiedOffCenterFootprintBlocksHypotheticalRoute() {
    MapInfo[] infos = visibleCheese(12, 10);
    // A robot on the cheese blocks every legal collection footprint, including positions
    // whose center is empty. The search must not consider center-tile passability sufficient.
    RobotInfo baby = robot(UnitType.BABY_RAT, 12, 10);
    Direction step =
        KingNavigation.findRoute(
            rc,
            infos,
            new RobotInfo[] {baby},
            new MapLocation(12, 10),
            null,
            KingNavigation.Purpose.CHEESE);
    assertTrue(KingNavigation.routeResult != KingNavigation.RouteResult.CONFIRMED);
    assertEquals(null, step);
  }

  @Test
  public void knownMineBecomesHomeAndIdleKingHolds() throws Exception {
    // Discovery is not needed here: a different king could have committed this coordinate.
    CheeseMines.publish(rc, new MapLocation(12, 10));
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(visibleCheese());
    RatKing.move(rc);
    assertEquals(new MapLocation(12, 10), RatKing.home);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void homeSelectionUsesDistanceThenCoordinatesAndRemainsStable() throws Exception {
    CheeseMines.publish(rc, new MapLocation(12, 10));
    CheeseMines.publish(rc, new MapLocation(8, 10));
    RatKing.move(rc);
    assertEquals(new MapLocation(8, 10), RatKing.home);
    rc.setLocation(new MapLocation(12, 10));
    RatKing.move(rc);
    assertEquals(new MapLocation(8, 10), RatKing.home);
  }

  @Test
  public void distantHomeUsesConfirmedLocalProgressWithoutClaimingCompleteRoute() throws Exception {
    CheeseMines.publish(rc, new MapLocation(30, 10));
    rc.setCanMove(true);
    rc.setNearbyMapInfos(visibleCheese());
    RatKing.move(rc);
    assertEquals(Direction.EAST, rc.getDirection());
    assertEquals(KingNavigation.RouteResult.UNCONFIRMED, KingNavigation.routeResult);
    assertPosition(10, 10); // Turning does not move.
  }

  @Test
  public void homeCollectionStaysInRadiusAndIgnoresOutsideCheese() throws Exception {
    RatKing.home = rc.getLocation();
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(visibleCheese(15, 10));
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    rc.setNearbyMapInfos(visibleCheese(14, 10));
    RatKing.move(rc);
    assertEquals(Direction.EAST, rc.getDirection());
  }

  @Test
  public void everyVisibleCatOverridesCheeseAndNoMineEscapeMovesAway() throws Exception {
    RatKing.home = rc.getLocation();
    rc.setNearbyMapInfos(visibleCheese(13, 10));
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    rc.setCanMove(true);
    RatKing.move(rc);
    assertTrue(RatKing.retreating);
    assertEquals(0, RatKing.catFreeTurns);
    assertTrue(
        rc.getLocation().add(rc.getDirection()).distanceSquaredTo(new MapLocation(14, 10)) > 16);
    assertEquals(0, rc.moveCount());
    assertEquals(new MapLocation(10, 10), RatKing.home);
  }

  @Test
  public void saferMineIsPreferredAndOriginalHomeSurvivesRetreat() throws Exception {
    RatKing.home = rc.getLocation();
    CheeseMines.publish(rc, new MapLocation(3, 10));
    CheeseMines.publish(rc, new MapLocation(14, 10));
    rc.setNearbyMapInfos(visibleCheese());
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    rc.setCanMove(true);
    RatKing.move(rc);
    assertEquals(new MapLocation(3, 10), RatKing.retreatMine);
    assertEquals(new MapLocation(10, 10), RatKing.home);
    assertTrue(rc.getLocation().add(rc.getDirection()).x < 10);
  }

  @Test
  public void multipleCatsUseNearestThreatAndNoSafeStepHolds() throws Exception {
    RatKing.home = rc.getLocation();
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10), robot(UnitType.CAT, 6, 10)});
    rc.setDirectionAllowed(Direction.EAST, true);
    // East moves toward one cat, so it cannot be selected merely to flee the other.
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
  }

  @Test
  public void fiveCatFreeTurnsIncludingCooldownPermitReturnExactlyAtThreshold() throws Exception {
    RatKing.home = new MapLocation(2, 10);
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    rc.setMovementReady(false);
    RatKing.move(rc);
    rc.setNearbyRobots(new RobotInfo[0]);
    for (int turn = 1; turn <= 4; turn++) {
      RatKing.move(rc);
      assertTrue(RatKing.retreating);
      assertEquals(turn, RatKing.catFreeTurns);
    }
    rc.setMovementReady(true);
    rc.setCanMove(true);
    rc.setNearbyMapInfos(visibleCheese());
    RatKing.move(rc);
    assertFalse(RatKing.retreating);
    assertEquals(5, RatKing.catFreeTurns);
    assertEquals(Direction.WEST, rc.getDirection());
  }

  @Test
  public void catReappearanceResetsDelayAndInterruptsReturn() throws Exception {
    RatKing.home = new MapLocation(2, 10);
    rc.setMovementReady(false);
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    RatKing.move(rc);
    rc.setNearbyRobots(new RobotInfo[0]);
    RatKing.move(rc);
    RatKing.move(rc);
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    RatKing.move(rc);
    assertEquals(0, RatKing.catFreeTurns);
    assertTrue(RatKing.retreating);
    Config.KING_CAT_FREE_TURNS_BEFORE_RETURN = 1;
    rc.setNearbyRobots(new RobotInfo[0]);
    RatKing.move(rc);
    assertFalse(RatKing.retreating);
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    RatKing.move(rc);
    assertTrue(RatKing.retreating);
    assertEquals(new MapLocation(2, 10), RatKing.home);
  }

  @Test
  public void zeroDelayStillRetreatsWhileCatVisibleAndConfigResetRestoresDefaults()
      throws Exception {
    Config.KING_CAT_FREE_TURNS_BEFORE_RETURN = 0;
    rc.setMovementReady(false);
    rc.setNearbyRobots(new RobotInfo[] {robot(UnitType.CAT, 14, 10)});
    RatKing.move(rc);
    assertTrue(RatKing.retreating);
    rc.setNearbyRobots(new RobotInfo[0]);
    RatKing.move(rc);
    assertFalse(RatKing.retreating);
    Config.KING_HOME_RADIUS_SQUARED = 1;
    Config.reset();
    assertEquals(5, Config.KING_CAT_FREE_TURNS_BEFORE_RETURN);
    assertEquals(16, Config.KING_HOME_RADIUS_SQUARED);
  }

  @Test
  public void negativeKingConfigIsRejectedBeforeActions() {
    Config.KING_CAT_FREE_TURNS_BEFORE_RETURN = -1;
    org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> RatKing.move(rc));
    Config.reset();
    Config.KING_HOME_RADIUS_SQUARED = -1;
    org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> RatKing.move(rc));
    assertTrue(rc.calls().isEmpty());
  }

  @Test
  public void exhaustedSearchBudgetIsUnconfirmedAndHasNoSideEffects() {
    // A full-turn absolute ceiling reserves work for pickup/spawning rather than overrunning.
    try (org.mockito.MockedStatic<Clock> clock = org.mockito.Mockito.mockStatic(Clock.class)) {
      clock.when(Clock::getBytecodeNum).thenReturn(15000);
      Direction step = search(visibleCheese(13, 10), new MapLocation(13, 10));
      assertEquals(KingNavigation.RouteResult.UNCONFIRMED, KingNavigation.routeResult);
      assertEquals(null, step);
      assertTrue(rc.calls().isEmpty());
    }
  }

  private Direction search(MapInfo[] infos, MapLocation goal) {
    return KingNavigation.findRoute(
        rc, infos, new RobotInfo[0], goal, null, KingNavigation.Purpose.CHEESE);
  }

  private MapInfo[] visibleCheese(int... coordinates) {
    List<MapInfo> infos = new ArrayList<>();
    MapLocation center = rc.getLocation();
    for (int dx = -5; dx <= 5; dx++) {
      for (int dy = -5; dy <= 5; dy++) {
        if (dx * dx + dy * dy > 25) {
          continue;
        }
        int amount = 0;
        for (int i = 0; i < coordinates.length; i += 2) {
          if (center.x + dx == coordinates[i] && center.y + dy == coordinates[i + 1]) {
            amount = 1;
          }
        }
        infos.add(cheese(center.x + dx, center.y + dy, amount));
      }
    }
    return infos.toArray(new MapInfo[0]);
  }

  private static void block(MapInfo[] infos, int x, int y) {
    for (int i = 0; i < infos.length; i++) {
      if (infos[i].getMapLocation().equals(new MapLocation(x, y))) {
        infos[i] =
            new MapInfo(new MapLocation(x, y), false, null, true, false, 0, TrapType.NONE, false);
      }
    }
  }

  private static RobotInfo robot(UnitType type, int x, int y) {
    return new RobotInfo(
        200, Team.NEUTRAL, type, 100, new MapLocation(x, y), Direction.NORTH, 0, 0, null);
  }

  @Test
  public void publishesZeroAndLargestLegalCentersWithoutTouchingOtherSlots() throws Exception {
    // Zero tests encoding; real 3x3 king centers must leave a one-tile map margin.
    for (MapLocation center :
        List.of(new MapLocation(0, 0), new MapLocation(1, 1), new MapLocation(58, 58))) {
      setUp();
      rc.setLocation(center);
      RatKing.move(rc);
      assertPosition(center.x, center.y);
      for (int slot = 3; slot < 64; slot++) {
        assertEquals(0, rc.readSharedArray(slot));
      }
    }
  }

  @Test
  public void failedMoveLeavesActualPublishedPositionAndBoundaryRecovers() throws Exception {
    rc.setForwardAllowed(true);
    rc.setMoveFailure(failure());
    RobotPlayer.playTurn(rc);
    assertPosition(10, 10);
    assertEquals(1, rc.moveCount());
    assertEquals(4, writes());
    rc.setMoveFailure(null);
    RobotPlayer.playTurn(rc);
    assertPosition(10, 11);
  }

  @Test
  public void repairsEveryPossibleFailedPostMoveWriteBeforeMovingAgain() throws Exception {
    // First publish successfully, then inject a failure at each of the four post-move writes.
    for (int write = 1; write <= 4; write++) {
      setUp();
      RatKing.move(rc);
      rc.setForwardAllowed(true);
      rc.failSharedWrite(write, failure());
      RobotPlayer.playTurn(rc);
      assertEquals(new MapLocation(10, 11), rc.getLocation());
      assertFalse(RatKing.locationPublished);
      if (write > 1) {
        assertEquals(0, rc.readSharedArray(2));
      }
      rc.calls().clear();
      rc.setForwardAllowed(false);
      RobotPlayer.playTurn(rc);
      assertPosition(10, 11);
      assertTrue(RatKing.locationPublished);
      assertEquals(4, writes());
    }
  }

  @Test
  public void initialPublicationFailureStopsMovementAndRetriesNextTurn() throws Exception {
    rc.setForwardAllowed(true);
    rc.failSharedWrite(2, failure());
    RobotPlayer.playTurn(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.readSharedArray(2));
    RobotPlayer.playTurn(rc);
    assertPosition(10, 11);
  }

  @Test
  public void currentTypeDispatchInitializesKingAfterBabyTurn() throws Exception {
    rc.setUnitType(UnitType.BABY_RAT);
    rc.setForwardAllowed(true);
    RobotPlayer.playTurn(rc);
    assertEquals(0, writes());
    rc.setUnitType(UnitType.RAT_KING);
    RobotPlayer.playTurn(rc);
    assertPosition(10, 12);
    assertEquals(8, writes());
  }

  @Test
  public void kingTurnAndSensingFailuresReachExistingRecoveryBoundary() throws Exception {
    rc.setDirectionAllowed(Direction.EAST, true);
    rc.setTurnFailure(failure());
    RobotPlayer.playTurn(rc);
    assertPosition(10, 10);
    assertEquals(Direction.NORTH, rc.getDirection());
    rc.setTurnFailure(null);
    rc.setForwardFailure(new IllegalStateException("test forward check"));
    RobotPlayer.playTurn(rc);
    assertEquals(0, rc.moveCount());
  }

  @Test
  public void picksUpEveryLegalSensedCheeseTileAndIgnoresEmptyTiles() throws Exception {
    // The controller grants pickup legality; check all positive tiles, not just the center.
    rc.setCanPickUpCheese(true);
    MapLocation east = new MapLocation(11, 10);
    MapLocation west = new MapLocation(9, 10);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(11, 10, 2), cheese(9, 10, 3), cheese(10, 11, 0)});
    RatKing.pickUpCheese(rc);
    assertEquals(List.of("pickUpCheese:" + east, "pickUpCheese:" + west), rc.calls());
  }

  @Test
  public void skipsCheeseWhenEngineRefusesPickup() throws Exception {
    // Covers out-of-range or otherwise refused pickups without attempting an illegal action.
    rc.setCanPickUpCheese(false);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(11, 10, 2)});
    RatKing.pickUpCheese(rc);
    assertTrue(rc.calls().isEmpty());
  }

  private void assertPosition(int x, int y) throws Exception {
    assertEquals(x, rc.readSharedArray(0));
    assertEquals(y, rc.readSharedArray(1));
    assertEquals(1, rc.readSharedArray(2));
  }

  private long writes() {
    return rc.calls().stream().filter(call -> call.startsWith("writeSharedArray:")).count();
  }

  private static void assertNextRandomUnchanged() {
    assertEquals(new Random(6147).nextInt(), RobotPlayer.rng.nextInt());
  }

  private static GameActionException failure() {
    return new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test failure");
  }

  private static MapInfo cheese(int x, int y, int amount) {
    return new MapInfo(
        new MapLocation(x, y), true, null, false, false, amount, TrapType.NONE, false);
  }
}
