package ratplayer;

import static org.junit.Assert.assertEquals;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/**
 * Controller checks for the king low-health cat block-and-flee policy through {@link
 * RobotPlayer#playTurn}.
 *
 * <p>These tests record {@code placeDirt}, {@code buildRat}, {@code removeDirt}, and movement
 * calls. They do not show what the engine allows. Named cases are in {@code .ai/test-cases.md}.
 */
public class RatKingCatAvoidanceTest {
  private static final MapLocation ORIGIN = new MapLocation(10, 10);
  private static final MapLocation NORTH_RING = ORIGIN.translate(0, 2);
  private static final MapLocation DISTANT_NORTH = new MapLocation(10, 16);

  @Before
  public void setUp() {
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    RatKing.locationPublished = false;
    RatKing.resetHome();
  }

  @After
  public void tearDown() {
    Config.reset();
    RatKing.locationPublished = false;
    RatKing.resetHome();
  }

  /**
   * A king at the flee threshold with a visible cat spends the action on a spawn-ring dirt block,
   * not spawn or dig, and steps away from the cat.
   *
   * <p>Arrange: health at {@link Config#KING_CAT_FLEE_HEALTH}, cat at (10,16), spawn and dig would
   * otherwise be legal. Act: one {@code playTurn}. Assert: one dirt at (10,12), no spawn, no dig,
   * one south step.
   */
  @Test
  public void lowHealthVisibleCatPlacesDirtAndFlees() {
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanBuildRat(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals("placeDirt:" + NORTH_RING, firstCall(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "buildRat:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
    assertEquals(0, controller.moveCount());
  }

  /**
   * Health one above the threshold keeps today's spawn/dig/move path even when a cat is visible.
   *
   * <p>Arrange: health {@link Config#KING_CAT_FLEE_HEALTH} + 1, same cat, dig legal, spawn refused.
   * Act: one {@code playTurn}. Assert: no dirt, one dig, wander forward.
   */
  @Test
  public void healthAboveThresholdKeepsDigAndWander() {
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH + 1);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(1, controller.moveCount());
    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));
  }

  /**
   * Low health without a cat is not this policy. Generic digging still runs.
   *
   * <p>Arrange: health at {@link Config#KING_CAT_FLEE_HEALTH}, no nearby robots, dig legal. Act:
   * one {@code playTurn}. Assert: one dig, no {@code placeDirt}.
   */
  @Test
  public void lowHealthWithoutCatStillDigs() {
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setCanPlaceDirt(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(1, controller.moveCount());
  }

  /**
   * The shared bite-then-block policy does not wait on {@link Config#KING_CAT_FLEE_HEALTH}. Kings
   * do not bite, so a visible cat still spends the action on dirt and a reverse step.
   *
   * <p>Arrange: {@code BITE_THEN_BLOCK_AND_FLEE}, health {@link Config#KING_CAT_FLEE_HEALTH} + 1,
   * cat at (10,16), spawn and dig would otherwise be legal. Act: one {@code playTurn}. Assert: one
   * dirt at (10,12), no spawn, no dig, one south step.
   */
  @Test
  public void biteThenBlockAndFleeIgnoresHealthThreshold() {
    Config.KING_CAT_RESPONSE_STRATEGY = Config.CatResponseStrategy.BITE_THEN_BLOCK_AND_FLEE;
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH + 1);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanBuildRat(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals("placeDirt:" + NORTH_RING, firstCall(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "buildRat:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
  }

  /**
   * {@link Config.CatResponseStrategy#NONE} restores the pre-feature policy.
   *
   * <p>Arrange: strategy {@code NONE}, health at {@link Config#KING_CAT_FLEE_HEALTH}, visible cat,
   * dig and dirt both legal. Act: one {@code playTurn}. Assert: one dig, no dirt, wander forward.
   */
  @Test
  public void noneStrategyKeepsDigAndWander() {
    Config.KING_CAT_RESPONSE_STRATEGY = Config.CatResponseStrategy.NONE;
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(1, controller.moveCount());
    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));
  }

  /**
   * A blocked opposite heading still spends the action on the spawn-ring dirt block and takes the
   * first greedy sidestep. Spawn and dig stay skipped.
   *
   * <p>Arrange: health at {@link Config#KING_CAT_FLEE_HEALTH}, cat at (10,16), south refused, other
   * directions open, spawn and dig would otherwise be legal. Act: one {@code playTurn}. Assert: one
   * dirt at (10,12), no spawn, no dig, one southeast step, no south step.
   */
  @Test
  public void blockedOppositeHeadingSidestepsAndStillPlacesDirt() {
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanBuildRat(true);
    controller.setCanRemoveDirt(true);
    controller.setDirectionAllowed(Direction.SOUTH, false);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals("placeDirt:" + NORTH_RING, firstCall(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "buildRat:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals("move:" + Direction.SOUTHEAST, firstCall(controller, "move:"));
    assertEquals(1, countCalls(controller, "move:"));
  }

  /**
   * A {@code GameActionException} from {@code placeDirt} reaches {@link RobotPlayer#playTurn} and
   * is not retried that turn. Clearing the failure lets the next turn place once.
   *
   * <p>Arrange: health at {@link Config#KING_CAT_FLEE_HEALTH}, visible cat, first turn throws. Act:
   * two {@code playTurn}s, with the failure cleared between them. Assert: one dirt attempt per
   * turn and a normal return.
   */
  @Test
  public void placeDirtFailureContainedAtTurnBoundary() {
    TestRobotController controller = avoidanceController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setPlaceDirtFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test king place dirt"));

    RobotPlayer.playTurn(controller);

    assertEquals(1, RobotPlayer.turnCount);
    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));

    controller.setPlaceDirtFailure(null);
    RobotPlayer.playTurn(controller);

    assertEquals(2, RobotPlayer.turnCount);
    assertEquals(2, countCalls(controller, "placeDirt:"));
  }

  private static TestRobotController avoidanceController() {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.RAT_KING);
    controller.setLocation(ORIGIN);
    // This fixture is in the interior, so normal cat-policy controls keep forward momentum.
    controller.setMapWidth(60);
    controller.setMapHeight(60);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(1000);
    controller.setActionReady(true);
    controller.setCurrentRatCost(10);
    controller.setCanBuildRat(false);
    controller.setMovementReady(true);
    controller.setForwardAllowed(true);
    controller.setCanMove(true);
    return controller;
  }

  private static RobotInfo catAt(MapLocation location) {
    return new RobotInfo(
        9, Team.NEUTRAL, UnitType.CAT, UnitType.CAT.health, location, Direction.SOUTH, 0, 0, null);
  }

  private static String firstCall(TestRobotController controller, String prefix) {
    for (String call : controller.calls()) {
      if (call.startsWith(prefix)) {
        return call;
      }
    }
    return null;
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
