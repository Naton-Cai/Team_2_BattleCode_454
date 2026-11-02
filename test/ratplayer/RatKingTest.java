package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import support.TestRobotController;

/** Decision and publication tests. Canned legality answers do not establish engine geometry. */
public class RatKingTest {
  private TestRobotController rc;

  @Before
  public void setUp() {
    // Each scenario starts with a fresh robot, including its publication flag and RNG.
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    RatKing.locationPublished = false;
    RatKing.resetHome();
    Trap.startofBackStabbing = 0;
    rc = new TestRobotController();
    rc.setUnitType(UnitType.RAT_KING);
    rc.setLocation(new MapLocation(10, 10));
    rc.setMapWidth(60);
    rc.setMapHeight(60);
    rc.setMovementReady(true);
    rc.setCooperation(true);
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
  public void mapsSeededChoiceOntoInwardWeightedAlternativesInCompassOrder() throws Exception {
    // At (10,10) on 60x60, NE gets closer to center; S and NW do not.
    // Explicit slots verify the 3:1 weights and compass order without mirroring the algorithm.
    for (int seed = 0; seed < 20; seed++) {
      setUp();
      RobotPlayer.rng.setSeed(seed);
      rc.setDirectionAllowed(Direction.NORTHEAST, true);
      rc.setDirectionAllowed(Direction.SOUTH, true);
      rc.setDirectionAllowed(Direction.NORTHWEST, true);
      Direction[] expected = {
        Direction.NORTHEAST,
        Direction.NORTHEAST,
        Direction.NORTHEAST,
        Direction.SOUTH,
        Direction.NORTHWEST
      };
      RatKing.move(rc);
      assertEquals(expected[new Random(seed).nextInt(5)], rc.getDirection());
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

  @After
  public void restoreConfig() {
    Config.reset();
    RatKing.resetHome();
  }

  @Test
  public void looseCheeseNeverRedirectsExplorationOrHomeHolding() throws Exception {
    // Arrange an eastern pile but open northward exploration; repeat to reproduce fixation.
    rc.setForwardAllowed(true);
    rc.setDirectionAllowed(Direction.EAST, true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(14, 10, 20)});
    for (int turn = 0; turn < 3; turn++) {
      RobotPlayer.playTurn(rc);
    }
    assertEquals(new MapLocation(10, 13), rc.getLocation());
    assertEquals(0, rc.turnCount());
    assertNull(RatKing.home);
    // Once a nearby empty mine becomes home, even legally reachable distant cheese cannot lure it.
    rc.setNearbyMapInfos(new MapInfo[] {mine(10, 14), cheese(14, 13, 50)});
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 14), RatKing.home);
    assertEquals(new MapLocation(10, 13), rc.getLocation());
    assertNextRandomUnchanged();
  }

  @Test
  public void simultaneousFirstMinesUseDistanceThenCoordinatesAndHomePersists() throws Exception {
    // Reverse sense order and vary both tie coordinates; an empty home stays home.
    for (MapInfo[] mines :
        List.of(
            new MapInfo[] {mine(10, 12), mine(8, 10), mine(15, 10)},
            new MapInfo[] {mine(15, 10), mine(8, 10), mine(10, 12)})) {
      setUp();
      rc.setNearbyMapInfos(mines);
      RobotPlayer.playTurn(rc);
      assertEquals(new MapLocation(8, 10), RatKing.home);
      rc.setNearbyMapInfos(new MapInfo[] {mine(10, 10)});
      RobotPlayer.playTurn(rc);
      assertEquals(new MapLocation(8, 10), RatKing.home);
    }
    setUp();
    rc.setNearbyMapInfos(new MapInfo[] {mine(10, 12), mine(10, 8)});
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 8), RatKing.home);
  }

  @Test
  public void earlierSharedRecordBeatsCloserMineDiscoveredThisTurn() throws Exception {
    // A new king adopts an existing committed mine before publishing its own discovery.
    CheeseMines.publish(rc, new MapLocation(40, 40));
    CheeseMines.publish(rc, new MapLocation(11, 10));
    rc.setNearbyMapInfos(new MapInfo[] {mine(10, 10)});
    rc.setMovementReady(false);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(40, 40), RatKing.home);
  }

  @Test
  public void radiusBoundaryHoldsAt16AndTravelsAt17WithDelayedTurn() throws Exception {
    rc.setNearbyMapInfos(new MapInfo[] {mine(14, 10)});
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    RobotPlayer.playTurn(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    // Move the fixture one tile south: squared distance is now 17, just outside radius four.
    rc.setLocation(new MapLocation(10, 9));
    RobotPlayer.playTurn(rc);
    assertEquals(Direction.EAST, rc.getDirection());
    assertEquals(0, rc.moveCount());
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(11, 9), rc.getLocation());
    RobotPlayer.playTurn(rc);
    assertEquals(1, rc.moveCount());
  }

  @Test
  public void stallsOnTenthReadyTurnThenRetriesAfterTenExplorationTurns() throws Exception {
    // A distant committed mine with no legal steps must not hold the king forever.
    CheeseMines.publish(rc, new MapLocation(40, 40));
    for (int turn = 1; turn <= 9; turn++) {
      RobotPlayer.playTurn(rc);
      assertEquals(turn, RatKing.stalledTurns);
      assertEquals(new MapLocation(40, 40), RatKing.home);
    }
    rc.setMovementReady(false);
    RobotPlayer.playTurn(rc);
    assertEquals(9, RatKing.stalledTurns);
    rc.setMovementReady(true);
    RobotPlayer.playTurn(rc);
    assertNull(RatKing.home);
    assertEquals(10, RatKing.retryTurnsLeft);
    for (int turn = 0; turn < 9; turn++) {
      RobotPlayer.playTurn(rc);
      assertNull(RatKing.home);
    }
    rc.setMovementReady(false);
    RobotPlayer.playTurn(rc);
    assertEquals(1, RatKing.retryTurnsLeft);
    rc.setMovementReady(true);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(40, 40), RatKing.home);
    assertNull(RatKing.deferredHome);
    assertEquals(0, RatKing.stalledTurns);
  }

  @Test
  public void stalledHomeChoosesNearestAlternativeWithCoordinateTies() throws Exception {
    CheeseMines.publish(rc, new MapLocation(40, 40));
    CheeseMines.publish(rc, new MapLocation(10, 20));
    CheeseMines.publish(rc, new MapLocation(0, 10));
    for (int turn = 0; turn < 10; turn++) {
      RobotPlayer.playTurn(rc);
    }
    assertEquals(new MapLocation(0, 10), RatKing.home);
    assertEquals(0, RatKing.stalledTurns);
  }

  @Test
  public void turnOnlyDecisionsCountAsStallsAndSuccessfulProgressResetsThem() throws Exception {
    CheeseMines.publish(rc, new MapLocation(30, 10));
    rc.setDirectionAllowed(Direction.EAST, true);
    RobotPlayer.playTurn(rc);
    assertEquals(1, RatKing.stalledTurns);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(11, 10), rc.getLocation());
    assertEquals(0, RatKing.stalledTurns);
    assertEquals(361, RatKing.bestHomeDistance);
  }

  @Test
  public void occupancyWaitsAndFreshLegalityAllowsProgressWithoutChangingHome() throws Exception {
    CheeseMines.publish(rc, new MapLocation(30, 10));
    rc.setCanSenseLocation(true);
    rc.setPassable(true);
    RobotPlayer.playTurn(rc);
    assertEquals(1, RatKing.stalledTurns);
    assertEquals(0, rc.turnCount());
    rc.setDirectionAllowed(Direction.EAST, true);
    RobotPlayer.playTurn(rc);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(11, 10), rc.getLocation());
    assertEquals(new MapLocation(30, 10), RatKing.home);
  }

  @Test
  public void escapePausesStallAndRetryAndDoesNotForceNormalMovement() throws Exception {
    CheeseMines.publish(rc, new MapLocation(40, 40));
    for (int turn = 0; turn < 9; turn++) {
      RobotPlayer.playTurn(rc);
    }
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 15)});
    for (int turn = 0; turn < 3; turn++) {
      RobotPlayer.playTurn(rc);
      assertEquals(9, RatKing.stalledTurns);
    }
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(10, RatKing.retryTurnsLeft);
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 15)});
    RobotPlayer.playTurn(rc);
    assertEquals(10, RatKing.retryTurnsLeft);
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(9, RatKing.retryTurnsLeft);
  }

  @Test
  public void escapeDiscoveriesAreComparedFromFinalPositionAndOldOnesAreExcluded()
      throws Exception {
    // Establish an old home and a second previously seen mine; neither is a new escape discovery.
    rc.setNearbyMapInfos(new MapInfo[] {mine(14, 10), mine(10, 14)});
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 14), RatKing.home);
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
    rc.setNearbyMapInfos(new MapInfo[] {mine(5, 10), mine(15, 10)});
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 14), RatKing.home);
    // Simulate displacement during a prolonged escape. The best candidate changes with position.
    rc.setLocation(new MapLocation(16, 10));
    rc.setMovementReady(false);
    rc.setNearbyRobots(new RobotInfo[0]);
    rc.setNearbyMapInfos(new MapInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(15, 10), RatKing.home);
    assertEquals(0, RatKing.stalledTurns);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(15, 10), RatKing.home);
  }

  @Test
  public void equalOrFartherEscapeMineRetainsHomeAndCandidateTiesUseCoordinates() throws Exception {
    for (int newX : new int[] {6, 5}) {
      setUp();
      rc.setNearbyMapInfos(new MapInfo[] {mine(14, 10)});
      RobotPlayer.playTurn(rc);
      rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
      rc.setNearbyMapInfos(new MapInfo[] {mine(newX, 10)});
      RobotPlayer.playTurn(rc);
      rc.setNearbyRobots(new RobotInfo[0]);
      rc.setNearbyMapInfos(new MapInfo[0]);
      RobotPlayer.playTurn(rc);
      assertEquals(new MapLocation(14, 10), RatKing.home);
    }
    setUp();
    CheeseMines.publish(rc, new MapLocation(40, 40));
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
    rc.setNearbyMapInfos(new MapInfo[] {mine(12, 10), mine(8, 10), mine(10, 8)});
    RobotPlayer.playTurn(rc);
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(8, 10), RatKing.home);
  }

  @Test
  public void postMoveEscapeDiscoveryRehomesAndBabyReadsActualPublishedCenter() throws Exception {
    // A spy supplies different pre/post-move visibility and makes the fake's direct move displace.
    rc = spy(rc);
    when(rc.senseNearbyMapInfos()).thenReturn(new MapInfo[] {mine(14, 10)});
    RobotPlayer.playTurn(rc);
    rc.setCanMove(true);
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
    doAnswer(
            invocation -> {
              rc.setLocation(rc.getLocation().add(invocation.getArgument(0)));
              return null;
            })
        .when(rc)
        .move(Direction.SOUTH);
    when(rc.senseNearbyMapInfos()).thenReturn(new MapInfo[0], new MapInfo[] {mine(10, 5)});
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 9), rc.getLocation());
    assertPosition(10, 9);
    assertEquals(new MapLocation(14, 10), RatKing.home);
    rc.setMovementReady(false);
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 5), RatKing.home);
    assertPosition(10, 9);
    // The reader shares team memory, but its goal is the actual center, not either mine.
    rc.setUnitType(UnitType.BABY_RAT);
    rc.setLocation(new MapLocation(10, 2));
    rc.setRawCheese(Config.RAW_CHEESE_MINIMUM + 1);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 9), BabyRat.kingLoc);
  }

  @Test
  public void previouslyPublishedButLocallyNewMineCanReplaceHomeDuringEscape() throws Exception {
    CheeseMines.publish(rc, new MapLocation(40, 40));
    CheeseMines.publish(rc, new MapLocation(10, 12));
    RobotPlayer.playTurn(rc);
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
    rc.setNearbyMapInfos(new MapInfo[] {mine(10, 12)});
    RobotPlayer.playTurn(rc);
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 12), RatKing.home);
  }

  @Test
  public void lowNavigationBudgetPreservesPublicationPickupAndSpawning() throws Exception {
    // Optional navigation must give its budget to the rest of the complete king turn.
    CheeseMines.publish(rc, new MapLocation(40, 40));
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(new MapInfo[] {cheese(10, 10, 5)});
    rc.setCanPickUpCheese(true);
    rc.setGlobalCheese(1000);
    rc.setActionReady(true);
    rc.setCanBuildRat(true);
    try (MockedStatic<Clock> clock = mockStatic(Clock.class)) {
      clock.when(Clock::getBytecodeNum).thenReturn(10000);
      RobotPlayer.playTurn(rc);
    }
    assertPosition(10, 10);
    assertEquals(0, rc.moveCount());
    assertEquals(0, RatKing.stalledTurns);
    assertTrue(rc.calls().contains("pickUpCheese:[10, 10]"));
    assertTrue(rc.calls().stream().anyMatch(call -> call.startsWith("buildRat:")));
  }

  @Test
  public void localMinesRemainUsableWhenSharedRegistryHasNoCapacity() throws Exception {
    // Discovery tracking must not depend on a successful append to the team's finite registry.
    Config.MAX_CHEESE_MINES = 0;
    rc.setNearbyMapInfos(new MapInfo[] {mine(14, 10)});
    RobotPlayer.playTurn(rc);
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
    rc.setNearbyMapInfos(new MapInfo[] {mine(10, 8)});
    RobotPlayer.playTurn(rc);
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 8), RatKing.home);
  }

  @Test
  public void escapeMovementImprovementResetsStallBeforeNormalNavigation() throws Exception {
    CheeseMines.publish(rc, new MapLocation(40, 10));
    for (int turn = 0; turn < 9; turn++) {
      RobotPlayer.playTurn(rc);
    }
    rc.setNearbyRobots(new RobotInfo[] {cat(5, 10)});
    RobotPlayer.playTurn(rc);
    // Displacement models a successful flee step while the fake's direct move is otherwise canned.
    rc.setLocation(new MapLocation(11, 10));
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(40, 10), RatKing.home);
    assertEquals(1, RatKing.stalledTurns);
    assertEquals(841, RatKing.bestHomeDistance);
  }

  @Test
  public void deferredHomeWaitsUntilEscapeEndsBeforeAdoptingDiscovery() throws Exception {
    CheeseMines.publish(rc, new MapLocation(40, 40));
    for (int turn = 0; turn < 10; turn++) {
      RobotPlayer.playTurn(rc);
    }
    assertNull(RatKing.home);
    rc.setNearbyRobots(new RobotInfo[] {cat(10, 16)});
    rc.setNearbyMapInfos(new MapInfo[] {mine(10, 12)});
    RobotPlayer.playTurn(rc);
    assertNull(RatKing.home);
    assertEquals(10, RatKing.retryTurnsLeft);
    rc.setNearbyRobots(new RobotInfo[0]);
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(10, 12), RatKing.home);
    assertNull(RatKing.deferredHome);
  }

  @Test
  public void stalledHomeCanUseLocallyKnownAlternateOutsideFullRegistry() throws Exception {
    Config.MAX_CHEESE_MINES = 0;
    rc.setNearbyMapInfos(new MapInfo[] {mine(14, 10), mine(10, 30)});
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(14, 10), RatKing.home);
    rc.setLocation(new MapLocation(10, 0));
    rc.setNearbyMapInfos(new MapInfo[0]);
    for (int turn = 0; turn < 10; turn++) {
      RobotPlayer.playTurn(rc);
    }
    assertEquals(new MapLocation(10, 30), RatKing.home);
  }

  @Test
  public void invalidSettingsAreRejectedAndResetRestoresAgreedValues() throws Exception {
    Config.KING_HOME_RADIUS = -1;
    assertThrows(IllegalArgumentException.class, () -> RatKing.move(rc));
    Config.reset();
    Config.KING_STALL_TURNS = 0;
    assertThrows(IllegalArgumentException.class, () -> RatKing.move(rc));
    Config.reset();
    Config.KING_RETRY_TURNS = -1;
    assertThrows(IllegalArgumentException.class, () -> RatKing.move(rc));
    Config.reset();
    assertEquals(4, Config.KING_HOME_RADIUS);
    assertEquals(10, Config.KING_STALL_TURNS);
    assertEquals(10, Config.KING_RETRY_TURNS);
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

  private static MapInfo mine(int x, int y) {
    return new MapInfo(new MapLocation(x, y), true, null, false, false, 0, TrapType.NONE, true);
  }

  private static RobotInfo cat(int x, int y) {
    return new RobotInfo(
        3, Team.NEUTRAL, UnitType.CAT, 4000, new MapLocation(x, y), Direction.SOUTH, 0, 0, null);
  }

  private static MapInfo cheese(int x, int y, int amount) {
    return new MapInfo(
        new MapLocation(x, y), true, null, false, false, amount, TrapType.NONE, false);
  }
}
