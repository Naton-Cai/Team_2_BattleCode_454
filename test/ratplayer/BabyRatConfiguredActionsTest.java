package ratplayer;

import static org.junit.Assert.assertEquals;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

public class BabyRatConfiguredActionsTest {
  private static final MapLocation ORIGIN = new MapLocation(5, 5);
  private static final MapLocation FORWARD = ORIGIN.add(Direction.NORTH);

  @Before
  public void setUp() {
    Config.reset();
  }

  @After
  public void tearDown() {
    Config.reset();
  }

  @Test
  public void defaultOrderAttacksThenSkipsDig() throws Exception {
    TestRobotController controller = controllerWithForwardCatAndDirt();

    BabyRat.tryConfiguredActions(controller);

    assertEquals(1, countCalls(controller, "attack:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
  }

  @Test
  public void swappedOrderDigsThenSkipsAttack() throws Exception {
    Config.BABY_ACTION_ORDER =
        new Config.BabyAction[] {Config.BabyAction.DIG_DIRT, Config.BabyAction.ATTACK};
    TestRobotController controller = controllerWithForwardCatAndDirt();

    BabyRat.tryConfiguredActions(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "attack:"));
  }

  @Test
  public void omittedDigNeverRuns() throws Exception {
    Config.BABY_ACTION_ORDER = new Config.BabyAction[] {Config.BabyAction.ATTACK};
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirt(true);

    BabyRat.tryConfiguredActions(controller);

    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "attack:"));
  }

  @Test
  public void emptyOrderRunsNeitherAction() throws Exception {
    Config.BABY_ACTION_ORDER = new Config.BabyAction[0];
    TestRobotController controller = controllerWithForwardCatAndDirt();

    BabyRat.tryConfiguredActions(controller);

    assertEquals(0, countCalls(controller, "attack:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
  }

  @Test
  public void digsWhenAttackDoesNotSucceed() throws Exception {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirt(true);

    BabyRat.tryConfiguredActions(controller);

    assertEquals(0, countCalls(controller, "attack:"));
    assertEquals(1, countCalls(controller, "removeDirt:"));
  }

  private static TestRobotController dirtController() {
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(1000);
    controller.setCooperation(true);
    return controller;
  }

  private static TestRobotController controllerWithForwardCatAndDirt() {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirt(true);
    controller.setCanSenseRobotAtLocation(true);
    controller.setRobotAtLocation(
        new RobotInfo(
            2,
            Team.NEUTRAL,
            UnitType.CAT,
            UnitType.CAT.health,
            FORWARD,
            Direction.SOUTH,
            0,
            0,
            null));
    controller.setCanAttackWithCheese(true);
    return controller;
  }

  private static int countCalls(TestRobotController controller, String prefix) {
    int count = 0;
    for (String call : controller.calls()) {
      if (call.startsWith(prefix)) {
        count++;
      }
    }
    return count;
  }
}
