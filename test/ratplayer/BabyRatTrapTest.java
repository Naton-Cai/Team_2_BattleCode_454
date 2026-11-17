package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Test;
import support.TestRobotController;

/** Tests visible cat detection and the one-trap-per-turn placement policy. */
public class BabyRatTrapTest {
  private static final MapLocation ORIGIN = new MapLocation(10, 10);

  @After
  public void resetBackstabbingState() {
    Trap.startofBackStabbing = 0;
    Config.reset();
  }

  /** With no cats, detection is empty and placement makes no action call. */
  @Test
  public void noCatsProducesNoTrap() throws Exception {
    TestRobotController controller = readyController();

    assertEquals(0, Trap.detectCats(controller).length);
    assertEquals(false, Trap.placeTrap(controller));

    assertEquals(0, controller.calls().size());
  }

  /** A cat four tiles north gets a trap on the adjacent tile toward it. */
  @Test
  public void placesAdjacentCatTrapTowardVisibleCat() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});

    assertEquals(true, Trap.placeTrap(controller));

    assertEquals(1, controller.calls().size());
    assertEquals("placeCatTrap:[10, 11]", controller.calls().get(0));
  }

  /** Multiple visible cats still produce at most one placement in a turn. */
  @Test
  public void twoCatsStillPlaceExactlyOneTrap() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(
        new RobotInfo[] {catAt(new MapLocation(10, 14)), catAt(new MapLocation(14, 10))});

    assertEquals(true, Trap.placeTrap(controller));

    assertEquals(1, controller.calls().size());
  }

  /** Insufficient cheese prevents placement; equality with the reserve threshold allows it. */
  @Test
  public void cheeseReserveBoundaryControlsTrapPlacement() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});
    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 9);
    assertEquals(false, Trap.placeTrap(controller));
    assertEquals(0, controller.calls().size());

    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 10);
    assertEquals(true, Trap.placeTrap(controller));
    assertEquals(1, controller.calls().size());
  }

  /** Baby Rat loads the absolute backstabbing start round from shared memory. */
  @Test
  public void countsTurnsWhileBackstabbingIsActive() throws Exception {
    TestRobotController controller = readyController();
    controller.setRoundNum(10);
    BabyRat.run(controller);
    assertEquals(0, Trap.startofBackStabbing);

    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    controller.setSharedArrayValue(Trap.BACKSTABSLOT, Trap.encodeRound(7));
    BabyRat.run(controller);
    assertEquals(7, Trap.startofBackStabbing);
  }

  /** The first king to observe backstabbing publishes the current absolute round. */
  @Test
  public void recordsBackstabbingStartRoundWhenSharedSlotIsEmpty() throws Exception {
    TestRobotController controller = readyController();
    controller.setRoundNum(24);
    controller.setCooperation(false);

    Trap.writeStartofBackStabbing(controller);

    // The writer keeps the exact round locally; the slot holds it in two-round units.
    assertEquals(24, Trap.startofBackStabbing);
    assertEquals("writeSharedArray:" + Trap.BACKSTABSLOT + ":12", controller.calls().get(0));
  }

  @Test
  public void lateBackstabRoundFitsSharedSlotAndDecodesWithinOneRound() throws Exception {
    // Regression: backstab at round 1981 used to write 1981 into a 0..1023 slot. The engine threw
    // every king turn, freezing all kings until round 2000. The fake does not enforce the limit,
    // so assert the written value directly. Boundary rounds cover 1, the old limit, and game end.
    for (int round : new int[] {1, 2, 1023, 1024, 1981, 1999, 2000}) {
      TestRobotController controller = readyController();
      controller.setRoundNum(round);
      controller.setCooperation(false);

      Trap.writeStartofBackStabbing(controller);

      int stored = controller.readSharedArray(Trap.BACKSTABSLOT);
      assertTrue("round " + round, stored >= 1 && stored <= GameConstants.COMM_ARRAY_MAX_VALUE);
      // A baby reading the slot recovers the start round exactly or one round early.
      Trap.startofBackStabbing = 0;
      Trap.updateStartofBackStabbing(controller);
      assertTrue("round " + round, round - Trap.startofBackStabbing <= 1);
      assertTrue("round " + round, Trap.startofBackStabbing <= round);
    }
  }

  @Test
  public void kingReadsExistingBackstabRoundWithoutMistakingMineDataForIt() throws Exception {
    // Slot 45 now contains mine data. Both king and baby readers must use slot 51.
    TestRobotController controller = readyController();
    controller.setCooperation(false);
    controller.setSharedArrayValue(45, 33);
    controller.setSharedArrayValue(51, Trap.encodeRound(7));

    Trap.writeStartofBackStabbing(controller);

    assertEquals(7, Trap.startofBackStabbing);
    assertTrue(controller.calls().isEmpty());
    Trap.startofBackStabbing = 0;
    Trap.updateStartofBackStabbing(controller);
    assertEquals(7, Trap.startofBackStabbing);
  }

  /** Baby Rat skips the configured trap action before its 100-turn gate. */
  @Test
  public void doesNotTryTrapBeforeOneHundredBackstabbingTurns() throws Exception {
    TestRobotController controller = readyController();
    Config.BABY_ACTION_ORDER = new Config.BabyAction[] {Config.BabyAction.TRAP};
    controller.setRoundNum(1);
    controller.setSharedArrayValue(Trap.BACKSTABSLOT, 1);
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {ratAt(new MapLocation(10, 14))});
    controller.setCanPlaceRatTrap(true);
    advanceTurns(controller, 49);
    BabyRat.run(controller);

    assertEquals(50, controller.getRoundNum());
    assertEquals(0, trapPlacementCount(controller));
  }

  /** After 100 rounds since backstabbing began, Baby Rat places an opponent-rat trap. */
  @Test
  public void placesRatTrapAtTwoHundredBackstabbingTurns() throws Exception {
    TestRobotController controller = readyController();
    Config.BABY_ACTION_ORDER = new Config.BabyAction[] {Config.BabyAction.TRAP};
    controller.setRoundNum(1);
    controller.setSharedArrayValue(Trap.BACKSTABSLOT, 1);
    controller.setCanPlaceRatTrap(true);
    advanceTurns(controller, 99);
    controller.setRoundNum(101);
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {ratAt(new MapLocation(10, 12))});

    BabyRat.run(controller);

    assertEquals(101, controller.getRoundNum());
    assertEquals(1, trapPlacementCount(controller));
    assertEquals(true, controller.calls().contains("placeRatTrap:[10, 11]"));
  }

  private static TestRobotController readyController() {
    Trap.startofBackStabbing = 0;
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setRoundNum(1);
    controller.setCooperation(true);
    controller.setActionReady(true);
    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 10);
    controller.setCanPlaceCatTrap(true);
    controller.setCanPlaceRatTrap(true);
    return controller;
  }

  private static RobotInfo catAt(MapLocation location) {
    return new RobotInfo(
        9, Team.NEUTRAL, UnitType.CAT, UnitType.CAT.health, location, Direction.SOUTH, 0, 0, null);
  }

  private static RobotInfo ratAt(MapLocation location) {
    return new RobotInfo(
        10,
        Team.B,
        UnitType.BABY_RAT,
        UnitType.BABY_RAT.health,
        location,
        Direction.SOUTH,
        0,
        0,
        null);
  }

  private static void advanceTurns(TestRobotController controller, int turns) throws Exception {
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    for (int turn = 0; turn < turns; turn++) {
      controller.setRoundNum(controller.getRoundNum() + 1);
      BabyRat.run(controller);
    }
  }

  private static int trapPlacementCount(TestRobotController controller) {
    int placements = 0;
    for (String call : controller.calls()) {
      if (call.startsWith("placeCatTrap:") || call.startsWith("placeRatTrap:")) {
        placements++;
      }
    }
    return placements;
  }
}
