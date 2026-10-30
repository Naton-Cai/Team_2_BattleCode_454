package ratplayer;

import static org.junit.Assert.assertEquals;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ratplayer.navigation.Navigation;
import support.TestRobotController;

/**
 * Controller checks for baby-rat dirt collection through {@link RobotPlayer#playTurn}.
 *
 * <p>These tests record which {@code removeDirt} and {@code attack} calls happen. They do not show
 * what the engine allows. Named cases are in {@code .ai/test-cases.md}.
 */
public class BabyRatDirtTest {
  private static final MapLocation ORIGIN = new MapLocation(5, 5);
  private static final MapLocation FORWARD = ORIGIN.add(Direction.NORTH);
  private static final MapLocation LEFT = ORIGIN.add(Direction.NORTHWEST);
  private static final MapLocation RIGHT = ORIGIN.add(Direction.NORTHEAST);

  @Before
  public void setUp() {
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    Trap.backstabCounter = 0;
    Navigation.resetFollow();
  }

  @After
  public void tearDown() {
    Config.reset();
  }

  /**
   * Arrange: forward tile is the only legal dig. Act: one {@code playTurn}. Assert: one dig at
   * (5,6) and no other {@code removeDirt}.
   */
  @Test
  public void digsForwardDirtWhenAllowed() {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirtAt(FORWARD);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals("removeDirt:" + FORWARD, controller.calls().get(0));
  }

  /**
   * Forward is preferred, then left, then right. Two cases share that order: a refused forward tile
   * falls through once, and a refused left tile falls through once more.
   */
  @Test
  public void digsLeftThenRightWhenForwardRefused() {
    TestRobotController leftCase = dirtController();
    leftCase.setCanRemoveDirtAt(LEFT);
    RobotPlayer.playTurn(leftCase);
    assertEquals(1, countCalls(leftCase, "removeDirt:"));
    assertEquals("removeDirt:" + LEFT, leftCase.calls().get(0));

    RobotPlayer.turnCount = 0;
    TestRobotController rightCase = dirtController();
    rightCase.setCanRemoveDirtAt(RIGHT);
    RobotPlayer.playTurn(rightCase);
    assertEquals(1, countCalls(rightCase, "removeDirt:"));
    assertEquals("removeDirt:" + RIGHT, rightCase.calls().get(0));
  }

  /**
   * Arrange: every in-cone tile refuses {@code canRemoveDirt}, forward movement is allowed. Assert:
   * no dig, and the movement step still runs.
   */
  @Test
  public void doesNotDigWhenNothingDiggable() {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirt(false);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals(1, controller.moveCount());
  }

  /**
   * Global cheese after paying the dig cost must stay at the configured reserve. Raw cheese is 0, so
   * the whole cost comes from the stash: reserve + cost digs, one cheese below does not.
   */
  @Test
  public void reserveBoundary() {
    int reserve = Config.MIN_CHEESE_RESERVE_FOR_DIG;
    int cost = GameConstants.DIG_DIRT_CHEESE_COST;

    TestRobotController atReserve = dirtController();
    atReserve.setRawCheese(0);
    atReserve.setGlobalCheese(reserve + cost);
    atReserve.setCanRemoveDirt(true);
    RobotPlayer.playTurn(atReserve);
    assertEquals(1, countCalls(atReserve, "removeDirt:"));

    RobotPlayer.turnCount = 0;
    TestRobotController belowReserve = dirtController();
    belowReserve.setRawCheese(0);
    belowReserve.setGlobalCheese(reserve + cost - 1);
    belowReserve.setCanRemoveDirt(true);
    RobotPlayer.playTurn(belowReserve);
    assertEquals(0, countCalls(belowReserve, "removeDirt:"));
  }

  /**
   * Raw cheese is spent first. With raw cheese covering the cost, global cheese can sit exactly at
   * the reserve and the dig still happens.
   */
  @Test
  public void rawCheesePaysFirst() {
    TestRobotController controller = dirtController();
    controller.setRawCheese(GameConstants.DIG_DIRT_CHEESE_COST);
    controller.setGlobalCheese(Config.MIN_CHEESE_RESERVE_FOR_DIG);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
  }

  /**
   * Default {@link Config#BABY_ACTION_ORDER} is attack then dig. A legal cat bite consumes the
   * action, so dirt on the same tile is not dug.
   */
  @Test
  public void attackBeatsDigByDefault() {
    TestRobotController controller = dirtControllerWithForwardCat();
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "attack:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
  }

  /**
   * Swapping the configured order makes the same setup dig instead of biting.
   */
  @Test
  public void digFirstWhenOrderSwapped() {
    Config.BABY_ACTION_ORDER =
        new Config.BabyAction[] {Config.BabyAction.DIG_DIRT, Config.BabyAction.ATTACK};
    TestRobotController controller = dirtControllerWithForwardCat();
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * An action left out of {@link Config#BABY_ACTION_ORDER} never runs, even when its legality check
   * would succeed.
   */
  @Test
  public void omittedActionNeverRuns() {
    Config.BABY_ACTION_ORDER = new Config.BabyAction[] {Config.BabyAction.ATTACK};
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "removeDirt:"));
  }

  /**
   * A {@code GameActionException} from {@code removeDirt} reaches {@link RobotPlayer#playTurn} and
   * is not retried that turn. Clearing the failure lets the next turn dig once.
   */
  @Test
  public void digFailureContainedAtTurnBoundary() {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirt(true);
    controller.setRemoveDirtFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test dig failure"));

    RobotPlayer.playTurn(controller);

    assertEquals(1, RobotPlayer.turnCount);
    assertEquals(1, countCalls(controller, "removeDirt:"));

    controller.setRemoveDirtFailure(null);
    RobotPlayer.playTurn(controller);

    assertEquals(2, RobotPlayer.turnCount);
    assertEquals(2, countCalls(controller, "removeDirt:"));
  }

  private static TestRobotController dirtController() {
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(1000);
    controller.setCooperation(true);
    controller.setForwardAllowed(true);
    return controller;
  }

  private static TestRobotController dirtControllerWithForwardCat() {
    TestRobotController controller = dirtController();
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
