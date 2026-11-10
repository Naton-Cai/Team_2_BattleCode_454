package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/** Canned legality verifies weighted decisions; engine fixtures verify actual footprints/costs. */
public class RatKingCenterBiasTest {
  private TestRobotController rc;

  @Before
  public void setUp() {
    Config.reset();
    RatKing.resetHome();
    RatKing.locationPublished = false;
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    Trap.startofBackStabbing = 0;
    rc = new TestRobotController();
    rc.setUnitType(UnitType.RAT_KING);
    rc.setLocation(new MapLocation(10, 10));
    rc.setMapWidth(60);
    rc.setMapHeight(60);
    rc.setDirection(Direction.NORTH);
    rc.setMovementReady(true);
    rc.setSimulateMovement(true);
  }

  @After
  public void restore() {
    Config.reset();
    RatKing.resetHome();
  }

  @Test
  public void openInteriorForwardKeepsMomentumEvenWhenHeadingAwayFromCenter() throws Exception {
    rc.setDirection(Direction.WEST);
    rc.setForwardAllowed(true);
    rc.setDirectionAllowed(Direction.EAST, true);
    RatKing.move(rc);
    assertEquals(new MapLocation(9, 10), rc.getLocation());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void edgeBoundaryAtThreeBiasesButFourKeepsForward() throws Exception {
    rc.setLocation(new MapLocation(4, 10));
    rc.setDirection(Direction.WEST);
    rc.setForwardAllowed(true);
    rc.setDirectionAllowed(Direction.EAST, true);
    RatKing.move(rc);
    assertEquals(new MapLocation(3, 10), rc.getLocation());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
    RobotPlayer.rng.setSeed(0); // nextInt(4)=2 selects E from [E,E,E,W].
    RatKing.move(rc);
    assertEquals(new MapLocation(3, 10), rc.getLocation());
    assertEquals(Direction.EAST, rc.getDirection());
    assertEquals(1, rc.moveCount());
    assertEquals(1, rc.turnCount());
  }

  @Test
  public void weightedSlotsKeepThreeToOneOddsAndPermitOutwardMoves() throws Exception {
    // Explicit E,E,E,W slots verify weights and compass ordering across reproducible seeds.
    Direction[] choices = {Direction.EAST, Direction.EAST, Direction.EAST, Direction.WEST};
    Random seeds = new Random(2026);
    boolean selectedInward = false;
    boolean selectedOutward = false;
    for (int attempt = 0; attempt < 40; attempt++) {
      long seed = seeds.nextLong();
      setUp();
      rc.setLocation(new MapLocation(3, 10));
      rc.setDirection(Direction.WEST);
      rc.setForwardAllowed(true);
      rc.setDirectionAllowed(Direction.EAST, true);
      RobotPlayer.rng.setSeed(seed);
      Random expected = new Random(seed);
      Direction selected = choices[expected.nextInt(4)];
      RatKing.move(rc);
      assertEquals(selected, rc.getDirection());
      if (selected == Direction.EAST) {
        selectedInward = true;
        assertEquals(new MapLocation(3, 10), rc.getLocation());
        RatKing.move(rc); // An open inward heading must move without another random draw.
        assertEquals(new MapLocation(4, 10), rc.getLocation());
      } else {
        selectedOutward = true;
        assertEquals(new MapLocation(2, 10), rc.getLocation());
      }
      assertEquals(expected.nextInt(), RobotPlayer.rng.nextInt());
    }
    assertTrue("Seed sample must exercise the inward slots", selectedInward);
    assertTrue("The outward slot must retain nonzero probability", selectedOutward);
  }

  @Test
  public void biasAppliesAtAllFourEdges() throws Exception {
    MapLocation[] locations = {
      new MapLocation(3, 20),
      new MapLocation(56, 20),
      new MapLocation(20, 3),
      new MapLocation(20, 56)
    };
    Direction[] outward = {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH};
    for (int i = 0; i < locations.length; i++) {
      setUp();
      rc.setLocation(locations[i]);
      rc.setDirection(outward[i]);
      rc.setForwardAllowed(true);
      rc.setDirectionAllowed(outward[i].opposite(), true);
      RobotPlayer.rng.setSeed(0);
      RatKing.move(rc);
      assertEquals(outward[i].opposite(), rc.getDirection());
      assertEquals(locations[i], rc.getLocation());
    }
  }

  @Test
  public void blockedInwardStepsStillAllowAnOutwardEscape() throws Exception {
    rc.setLocation(new MapLocation(3, 10));
    rc.setDirectionAllowed(Direction.WEST, true);
    RatKing.move(rc);
    assertEquals(Direction.WEST, rc.getDirection());
    assertEquals(0, rc.moveCount());
    RatKing.move(rc);
    assertEquals(new MapLocation(2, 10), rc.getLocation());
  }

  @Test
  public void evenSizedMapUsesTrueCenterAndTreatsEqualDistanceAsNeutral() throws Exception {
    // Center is (9.5,9.5): S from (1,10) leaves distance unchanged, E decreases it.
    // Rounding center down to (9,9) would incorrectly give S the inward bonus.
    Direction[] slots = {Direction.EAST, Direction.EAST, Direction.EAST, Direction.SOUTH};
    Random seeds = new Random(2026);
    for (int attempt = 0; attempt < 20; attempt++) {
      long seed = seeds.nextLong();
      setUp();
      rc.setMapWidth(20);
      rc.setMapHeight(20);
      rc.setLocation(new MapLocation(1, 10));
      rc.setDirection(Direction.WEST);
      rc.setDirectionAllowed(Direction.SOUTH, true);
      rc.setDirectionAllowed(Direction.EAST, true);
      RobotPlayer.rng.setSeed(seed);
      RatKing.move(rc);
      assertEquals(slots[new Random(seed).nextInt(4)], rc.getDirection());
    }
  }

  @Test
  public void retryIntervalKeepsUniformFallbackDespiteBeingNearEdge() throws Exception {
    RatKing.deferredHome = new MapLocation(40, 40);
    RatKing.retryTurnsLeft = 10;
    rc.setLocation(new MapLocation(3, 10));
    rc.setDirectionAllowed(Direction.EAST, true);
    rc.setDirectionAllowed(Direction.WEST, true);
    RobotPlayer.rng.setSeed(0); // nextInt(2)=1 chooses W; biased E,E,E,W would choose E.
    RatKing.move(rc);
    assertEquals(Direction.WEST, rc.getDirection());
    assertEquals(9, RatKing.retryTurnsLeft);
  }

  @Test
  public void cooldownAtEdgeDoesNotTurnMoveOrConsumeRandomness() throws Exception {
    rc.setLocation(new MapLocation(3, 10));
    rc.setMovementReady(false);
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void homeHoldingAtEdgeTakesPriorityOverInwardExploration() {
    rc.setLocation(new MapLocation(3, 10));
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setNearbyMapInfos(
        new MapInfo[] {
          new MapInfo(new MapLocation(4, 10), true, null, false, false, 0, TrapType.NONE, true)
        });
    RobotPlayer.playTurn(rc);
    assertEquals(new MapLocation(4, 10), RatKing.home);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertNextRandomUnchanged();
  }

  @Test
  public void activeFleeAtEdgeDoesNotRunOrConsumeRandomnessForNormalExploration() {
    rc.setLocation(new MapLocation(3, 10));
    rc.setCanMove(true);
    rc.setForwardAllowed(true);
    rc.setNearbyRobots(
        new RobotInfo[] {
          new RobotInfo(
              3,
              Team.NEUTRAL,
              UnitType.CAT,
              4000,
              new MapLocation(0, 10),
              Direction.EAST,
              0,
              0,
              null)
        });
    RobotPlayer.playTurn(rc);
    assertTrue(rc.calls().contains("move:EAST"));
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    assertNull(RatKing.home);
    assertNextRandomUnchanged();
  }

  @Test
  public void unavailableTurnAndChangedLegalityNeverForceAForwardMove() throws Exception {
    rc.setLocation(new MapLocation(3, 10));
    rc.setDirection(Direction.WEST);
    rc.setForwardAllowed(true);
    rc.setDirectionAllowed(Direction.EAST, true);
    rc.setTurnAllowed(false);
    RobotPlayer.rng.setSeed(0);
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(0, rc.turnCount());
    rc.setTurnAllowed(true);
    RobotPlayer.rng.setSeed(0);
    RatKing.move(rc);
    rc.setDirectionAllowed(Direction.EAST, false);
    RatKing.move(rc);
    assertEquals(0, rc.moveCount());
    assertEquals(1, rc.turnCount());
  }

  private static void assertNextRandomUnchanged() {
    assertEquals(new Random(6147).nextInt(), RobotPlayer.rng.nextInt());
  }
}
