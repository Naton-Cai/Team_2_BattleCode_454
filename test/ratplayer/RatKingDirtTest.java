package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/**
 * Controller checks for king dirt collection through {@link RobotPlayer#playTurn}.
 *
 * <p>Spawn uses the same action cooldown, so these tests refuse {@code canBuildRat} unless the case
 * is spawn-beats-dig. Named cases are in {@code .ai/test-cases.md}.
 */
public class RatKingDirtTest {
  private static final MapLocation ORIGIN = new MapLocation(10, 10);
  private static final MapLocation NORTH = ORIGIN.translate(0, 2);
  private static final MapLocation NORTHEAST = ORIGIN.translate(2, 2);

  @Before
  public void setUp() {
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    RatKing.locationPublished = false;
  }

  @After
  public void tearDown() {
    Config.reset();
  }

  /**
   * Arrange: spawn is refused, north spawn-ring tile is the only legal dig. Act: one {@code
   * playTurn}. Assert: one dig at (10,12).
   */
  @Test
  public void digsNorthSpawnRingWhenAllowed() {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirtAt(NORTH);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertTrue(controller.calls().contains("removeDirt:" + NORTH));
    assertEquals(0, countCalls(controller, "buildRat:"));
  }

  /**
   * King dig order matches spawn: north, then northeast. A refused north tile falls through once.
   */
  @Test
  public void digsNortheastWhenNorthRefused() {
    TestRobotController controller = dirtController();
    controller.setCanRemoveDirtAt(NORTHEAST);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertTrue(controller.calls().contains("removeDirt:" + NORTHEAST));
  }

  /**
   * Arrange: every tile refuses {@code canRemoveDirt}, forward movement is allowed. Assert: no dig,
   * and the movement step still runs.
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
   * the whole cost comes from the stash.
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
    RatKing.locationPublished = false;
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
   * Spawning uses the action first. When a spawn is legal, dirt on the north ring is not dug.
   */
  @Test
  public void spawnBeatsDigWhenBuildLegal() {
    TestRobotController controller = dirtController();
    controller.setCanBuildRat(true);
    controller.setAdjacentLocation(NORTH);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "buildRat:"));
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
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test king dig failure"));

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
    controller.setUnitType(UnitType.RAT_KING);
    controller.setLocation(ORIGIN);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(1000);
    controller.setActionReady(true);
    controller.setCurrentRatCost(10);
    controller.setCanBuildRat(false);
    controller.setMovementReady(true);
    controller.setForwardAllowed(true);
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
