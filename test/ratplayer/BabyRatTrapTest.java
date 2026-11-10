package ratplayer;

import static org.junit.Assert.assertEquals;

import battlecode.common.Direction;
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
    controller.setSharedArrayValue(Trap.BACKSTABSLOT, 7);
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

    assertEquals(24, Trap.startofBackStabbing);
    assertEquals("writeSharedArray:" + Trap.BACKSTABSLOT + ":24", controller.calls().get(0));
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
