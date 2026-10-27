package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/** Decision and publication tests. Canned legality answers do not establish engine geometry. */
public class RatKingTest {
  private TestRobotController rc;

  @Before
  public void setUp() {
    // Each scenario starts with a fresh robot, including its publication flag and RNG.
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    RatKing.locationPublished = false;
    rc = new TestRobotController();
    rc.setUnitType(UnitType.RAT_KING);
    rc.setLocation(new MapLocation(10, 10));
    rc.setMovementReady(true);
    rc.setSimulateMovement(true);
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
  public void cheeseOverridesOpenForwardThenMovesAndForgetsVanishedTarget() throws Exception {
    rc.setForwardAllowed(true);
    rc.setDirectionAllowed(Direction.EAST, true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(14, 10, 20)});
    RatKing.move(rc);
    assertEquals(Direction.EAST, rc.getDirection());
    assertEquals(0, rc.moveCount());
    RatKing.move(rc);
    assertPosition(11, 10);
    // A newly sensed target wins; the previous east target is no longer remembered.
    rc.setNearbyMapInfos(new MapInfo[] {cheese(11, 14, 1)});
    rc.setDirectionAllowed(Direction.NORTH, true);
    RatKing.move(rc);
    assertEquals(Direction.NORTH, rc.getDirection());
    rc.setNearbyMapInfos(new MapInfo[0]);
    RatKing.move(rc);
    assertPosition(11, 11);
    assertNextRandomUnchanged();
  }

  @Test
  public void nearestCheeseAndCoordinateTiesIgnoreSensingOrder() throws Exception {
    MapInfo[] targets = {
      cheese(15, 10, 100), cheese(10, 12, 1), cheese(8, 10, 1), cheese(10, 8, 1)
    };
    rc.setCanMove(true);
    rc.setNearbyMapInfos(targets);
    RatKing.move(rc);
    assertEquals(Direction.WEST, rc.getDirection());
    setUp();
    rc.setCanMove(true);
    rc.setNearbyMapInfos(new MapInfo[] {targets[3], targets[2], targets[1], targets[0]});
    RatKing.move(rc);
    assertEquals(Direction.WEST, rc.getDirection());
    // Same x: the lower y is selected, so SOUTH beats NORTH.
    setUp();
    rc.setCanMove(true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(10, 12, 1), cheese(10, 8, 1)});
    RatKing.move(rc);
    assertEquals(Direction.SOUTH, rc.getDirection());
  }

  @Test
  public void ignoresEmptyMineAndCenterTargetUsesNormalMovement() throws Exception {
    rc.setForwardAllowed(true);
    rc.setCanMove(true);
    rc.setNearbyMapInfos(
        new MapInfo[] {
          new MapInfo(new MapLocation(11, 10), true, null, false, false, 0, TrapType.NONE, true),
          cheese(10, 10, 1)
        });
    RatKing.move(rc);
    assertEquals(0, rc.turnCount());
    assertPosition(10, 11);
    assertNextRandomUnchanged();
  }

  @Test
  public void blockedCheeseFallsBackToForwardRatherThanSecondTarget() throws Exception {
    rc.setForwardAllowed(true);
    rc.setDirectionAllowed(Direction.EAST, false);
    rc.setDirectionAllowed(Direction.WEST, true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(12, 10, 1), cheese(6, 10, 1)});
    RatKing.move(rc);
    assertEquals(Direction.NORTH, rc.getDirection());
    assertPosition(10, 11);
    assertNextRandomUnchanged();
  }

  @Test
  public void blockedCheeseAndForwardUseOpenAlternative() throws Exception {
    rc.setDirectionAllowed(Direction.SOUTH, true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(12, 10, 1)});
    RatKing.move(rc);
    assertEquals(Direction.SOUTH, rc.getDirection());
    assertPosition(10, 10);
    assertEquals(0, rc.moveCount());
  }

  @Test
  public void unavailableCheeseTurnWaitsEvenWithOpenForward() throws Exception {
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setTurnAllowed(false);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(12, 10, 1)});
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertPosition(10, 10);
    assertNextRandomUnchanged();
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
