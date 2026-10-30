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
import ratplayer.navigation.Navigation;
import support.TestRobotController;

/**
 * Controller checks for the bite-then-block-and-flee cat policy through {@link
 * RobotPlayer#playTurn}.
 *
 * <p>These tests record {@code placeDirt}, {@code attack}, {@code removeDirt}, and movement calls.
 * They do not show what the engine allows. Named cases are in {@code .ai/test-cases.md}.
 */
public class BabyRatCatAvoidanceTest {
  private static final MapLocation ORIGIN = new MapLocation(5, 5);
  private static final MapLocation FORWARD = ORIGIN.add(Direction.NORTH);
  private static final MapLocation DISTANT_NORTH = new MapLocation(5, 9);
  private static final MapLocation NEAR_NORTH = new MapLocation(5, 8);
  private static final MapLocation FAR_EAST = new MapLocation(9, 5);

  @Before
  public void setUp() {
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    Trap.startofBackStabbing = 0;
    BabyRat.attackedThisTurn = false;
    Navigation.resetFollow();
  }

  @After
  public void tearDown() {
    Config.reset();
    BabyRat.attackedThisTurn = false;
  }

  /**
   * A cat four tiles north is visible but not on the three adjacent bite tiles. Digging would also
   * be legal, so this checks that the action is reserved for the dirt block and that movement goes
   * south, away from the cat, instead of wandering forward.
   *
   * <p>Arrange: distant north cat, {@code canPlaceDirt} and {@code canRemoveDirt} both true, greedy
   * and forward movement allowed. Act: one {@code playTurn}. Assert: one dirt at (5,6), no bite, no
   * dig, one south step.
   */
  @Test
  public void distantCatPlacesDirtAndFleesInsteadOfDigging() {
    TestRobotController controller = avoidanceController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals("placeDirt:" + FORWARD, controller.calls().get(0));
    assertEquals(0, countCalls(controller, "attack:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
    assertEquals(0, controller.moveCount());
  }

  /**
   * Option C keeps today's bite when a cat is on the forward tile. The same cat is also in {@code
   * senseNearbyRobots}, so the flee path would fire if a bite did not suppress it.
   *
   * <p>Arrange: adjacent north cat that {@code canAttack} accepts. Act: one {@code playTurn}.
   * Assert: one bite, no dirt, wander forward rather than flee south.
   */
  @Test
  public void adjacentCatBiteSkipsDirtAndKeepsWander() {
    TestRobotController controller = avoidanceController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(FORWARD)});
    controller.setCanSenseRobotAtLocation(true);
    controller.setRobotAtLocation(catAt(FORWARD));
    controller.setCanAttackWithCheese(true);
    controller.setCanPlaceDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "attack:"));
    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(1, controller.moveCount());
    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));
  }

  /**
   * With no cats the default action order still digs. This is the control that skip-dig is tied to
   * a visible cat, not to the strategy enum alone.
   *
   * <p>Arrange: no nearby robots, forward dig legal. Act: one {@code playTurn}. Assert: one dig, no
   * {@code placeDirt}.
   */
  @Test
  public void noCatsStillDigsWhenAllowed() {
    TestRobotController controller = avoidanceController();
    controller.setCanRemoveDirt(true);
    controller.setCanPlaceDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(1, controller.moveCount());
  }

  /**
   * Flee is independent of a successful dirt drop. If the engine refuses {@code canPlaceDirt}, the
   * rat still walks away and still skips generic digging so the action stays unused.
   *
   * <p>Arrange: distant north cat, dirt refused, dig would otherwise be legal. Act: one {@code
   * playTurn}. Assert: no dirt, no dig, one south step.
   */
  @Test
  public void refusedDirtStillFleesAndSkipsDig() {
    TestRobotController controller = avoidanceController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(false);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "removeDirt:"));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
  }

  /**
   * Two visible cats: the far east cat is first in the sense array so a scan that kept sense order
   * would block east and flee west. Nearest-by-squared-distance must still pick the closer north
   * cat.
   *
   * <p>Arrange: east cat at r²=16 listed first, north cat at r²=9 second. Act: one {@code
   * playTurn}. Assert: dirt at (5,6) and a south step, not (6,5) or west.
   */
  @Test
  public void twoCatsUseNearestForDirtAndFlee() {
    TestRobotController controller = avoidanceController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(FAR_EAST), catAt(NEAR_NORTH)});
    controller.setCanPlaceDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals("placeDirt:" + FORWARD, controller.calls().get(0));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
    assertEquals(0, countCalls(controller, "move:" + Direction.WEST));
  }

  /**
   * A {@code GameActionException} from {@code placeDirt} reaches {@link RobotPlayer#playTurn} and
   * is not retried that turn. Clearing the failure lets the next turn place once.
   *
   * <p>Arrange: distant north cat, dirt allowed, first turn throws. Act: two {@code playTurn}s, with
   * the failure cleared between them. Assert: one dirt attempt per turn and a normal return.
   */
  @Test
  public void placeDirtFailureContainedAtTurnBoundary() {
    TestRobotController controller = avoidanceController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setPlaceDirtFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test place dirt failure"));

    RobotPlayer.playTurn(controller);

    assertEquals(1, RobotPlayer.turnCount);
    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));

    controller.setPlaceDirtFailure(null);
    RobotPlayer.playTurn(controller);

    assertEquals(2, RobotPlayer.turnCount);
    assertEquals(2, countCalls(controller, "placeDirt:"));
  }

  /**
   * {@link Config.CatResponseStrategy#NONE} restores the pre-feature policy: a visible cat does not
   * skip digging, place dirt, or replace wander.
   *
   * <p>Arrange: strategy {@code NONE}, distant north cat, dig and dirt both legal. Act: one {@code
   * playTurn}. Assert: one dig, no dirt, wander forward.
   */
  @Test
  public void noneStrategyKeepsDigAndWander() {
    Config.BABY_CAT_RESPONSE_STRATEGY = Config.CatResponseStrategy.NONE;
    TestRobotController controller = avoidanceController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanRemoveDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "removeDirt:"));
    assertEquals(0, countCalls(controller, "placeDirt:"));
    assertEquals(1, controller.moveCount());
    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));
  }

  private static TestRobotController avoidanceController() {
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(1000);
    controller.setCooperation(true);
    controller.setForwardAllowed(true);
    controller.setCanMove(true);
    return controller;
  }

  private static RobotInfo catAt(MapLocation location) {
    return new RobotInfo(
        9, Team.NEUTRAL, UnitType.CAT, UnitType.CAT.health, location, Direction.SOUTH, 0, 0, null);
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
