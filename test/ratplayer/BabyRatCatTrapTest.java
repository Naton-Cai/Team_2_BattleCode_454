package ratplayer;

import static org.junit.Assert.assertEquals;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.Test;
import support.TestRobotController;

/** Tests visible cat detection and the one-trap-per-turn placement policy. */
public class BabyRatCatTrapTest {
  private static final MapLocation ORIGIN = new MapLocation(10, 10);

  /** With no cats, detection is empty and placement makes no action call. */
  @Test
  public void noCatsProducesNoTrap() throws Exception {
    TestRobotController controller = readyController();

    assertEquals(0, BabyRat.detectCats(controller).length);
    BabyRat.placeCatTrap(controller);

    assertEquals(0, controller.calls().size());
  }

  /** A cat four tiles north gets a trap at the midpoint between it and the baby rat. */
  @Test
  public void placesAtMidpointTowardVisibleCat() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});

    BabyRat.placeCatTrap(controller);

    assertEquals(1, controller.calls().size());
    assertEquals("placeCatTrap:[10, 12]", controller.calls().get(0));
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

  /** Preserve the configured global cheese reserve and honor engine phase legality. */
  @Test
  public void reserveAndBackstabberPhasePreventPlacement() throws Exception {
    TestRobotController controller = readyController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(new MapLocation(10, 14))});
    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 9);
    BabyRat.placeCatTrap(controller);
    assertEquals(0, controller.calls().size());

    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 10);
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.A);
    BabyRat.placeCatTrap(controller);
    assertEquals(0, controller.calls().size());
  }

  private static TestRobotController readyController() {
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
}
