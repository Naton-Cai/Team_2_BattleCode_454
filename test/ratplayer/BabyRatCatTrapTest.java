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
public class BabyRatCatTrapTest {
  private static final MapLocation ORIGIN = new MapLocation(10, 10);

  @After
  public void resetBackstabbingState() {
    BabyRat.turnsSinceBackstab = 0;
  }

  /** With no cats, detection is empty and placement makes no action call. */
  @Test
  public void noCatsProducesNoTrap() throws Exception {
    TestRobotController controller = readyController();

    assertEquals(0, Cats.detectCats(controller).length);
    BabyRat.placeCatTrap(controller);

    assertEquals(0, controller.calls().size());
  }

  /** A cat four tiles north gets a trap on the adjacent tile toward it. */
  @Test
  public void placesAtMidpointTowardVisibleCat() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});

    BabyRat.placeCatTrap(controller);

    assertEquals(1, controller.calls().size());
    assertEquals("placeCatTrap:[10, 11]", controller.calls().get(0));
  }

  /** Multiple visible cats still produce at most one placement in a turn. */
  @Test
  public void twoCatsStillPlaceExactlyOneTrap() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(
        new RobotInfo[] {catAt(new MapLocation(10, 14)), catAt(new MapLocation(14, 10))});

    BabyRat.placeCatTrap(controller);

    assertEquals(1, controller.calls().size());
  }

  /** A low cheese balance or an expired backstabbing window prevents trap placement. */
  @Test
  public void reserveAndExpiredBackstabbingWindowPreventPlacement() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});
    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 9);
    BabyRat.placeCatTrap(controller);
    assertEquals(0, controller.calls().size());

    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 10);
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    BabyRat.turnsSinceBackstab = 100;
    BabyRat.placeCatTrap(controller);
    assertEquals(0, controller.calls().size());
  }

  /** Baby rat turns increment the counter only while backstabbing mode is active. */
  @Test
  public void countsTurnsWhileBackstabbingIsActive() throws Exception {
    TestRobotController controller = readyController();
    BabyRat.run(controller);
    assertEquals(0, BabyRat.turnsSinceBackstab);

    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    BabyRat.run(controller);
    assertEquals(1, BabyRat.turnsSinceBackstab);
  }

  /** A backstabbed rat may still place a trap 50 rounds after it observes backstabbing. */
  @Test
  public void placesTrapFiftyRoundsAfterBackstabbingStarts() throws Exception {
    TestRobotController controller = readyController();
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    advanceTurns(controller, 50);

    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});
    BabyRat.placeCatTrap(controller);

    assertEquals(50, BabyRat.turnsSinceBackstab);
    assertEquals(1, trapPlacementCount(controller));
    assertEquals(true, controller.calls().contains("placeCatTrap:[10, 11]"));
  }

  /** A backstabbed rat may not place a trap 200 rounds after it observes backstabbing. */
  @Test
  public void doesNotPlaceTrapTwoHundredRoundsAfterBackstabbingStarts() throws Exception {
    TestRobotController controller = readyController();
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    advanceTurns(controller, 200);

    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});
    BabyRat.placeCatTrap(controller);

    assertEquals(200, BabyRat.turnsSinceBackstab);
    assertEquals(0, trapPlacementCount(controller));
  }

  private static TestRobotController readyController() {
    BabyRat.turnsSinceBackstab = 0;
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setCooperation(true);
    controller.setActionReady(true);
    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 10);
    controller.setCanPlaceCatTrap(true);
    return controller;
  }

  private static RobotInfo catAt(MapLocation location) {
    return new RobotInfo(
        9, Team.NEUTRAL, UnitType.CAT, UnitType.CAT.health, location, Direction.SOUTH, 0, 0, null);
  }

  private static void advanceTurns(TestRobotController controller, int turns) throws Exception {
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.B);
    for (int turn = 0; turn < turns; turn++) {
      BabyRat.run(controller);
    }
  }

  private static int trapPlacementCount(TestRobotController controller) {
    int placements = 0;
    for (String call : controller.calls()) {
      if (call.startsWith("placeCatTrap:")) {
        placements++;
      }
    }
    return placements;
  }
}
