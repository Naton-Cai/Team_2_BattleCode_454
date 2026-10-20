package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import ratplayer.navigation.Navigation;
import support.TestRobotController;

/**
 * Tests that greedy, DistBug, and BFS must all pass the same way.
 *
 * <p>In these maps the robot cannot always walk straight at the goal. A wall or dirt may block a
 * neighbor, another robot may sit on a passable tile, movement or turning may be on cooldown, or
 * the robot may already be standing on the goal. The robot has not seen a wall this turn, so DistBug
 * does not hug a wall and BFS does not search extra tiles. All three then pick the next legal step
 * the same way greedy does.
 *
 * <p>DistBug tests for a wall it has seen live in {@link DistBugPathTest}. These tests tell the mock
 * which steps are allowed. They do not prove engine collision.
 *
 * <p>JUnit 4 runs the inherited {@code @Test} methods on each concrete subclass.
 *
 * <p>Diagrams are north-up, +x east. {@code R} rat, {@code G} goal, {@code #} blocked, {@code .}
 * open, {@code ?} blocked tile the robot has not seen, {@code O} occupied passable.
 */
abstract class CommonPathingTest {

  abstract Config.PathingStrategy strategy();

  @Before
  public void resetRobotLocalState() {
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    BabyRat.kingLoc = null;
    BabyRat.cheeseTile = null;
    BabyRat.pickedUpCheese = false;
    BabyRat.kingChecked = false;
    Config.reset();
    Navigation.resetFollow();
  }

  /**
   * Walls, dirt, occupancy, or movement cooldown can make every neighboring step illegal. A 1x1
   * baby rat can be boxed that way; a king more often is, because its 3x3 footprint needs every
   * destination tile clear. Turning uses a separate cooldown, so the robot can still face toward
   * the goal.
   *
   * <pre>
   *   | # | # | # |   |   |
   *   | # | R | # |   | G |     faces south; no step; turn east
   *   | # | # | # |   |   |
   * </pre>
   */
  @Test
  public void turnsTowardGoalWhenEveryNeighborIsIllegal() throws Exception {
    // Arrange: goal east, no legal step, turning toward east is allowed.
    TestRobotController controller = at(5, 5);
    controller.setDirection(Direction.SOUTH);
    controller.setCanMove(false);
    controller.setTurnAllowed(true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: no move, one turn to face east toward the goal.
    assertEquals(Arrays.asList("turn:EAST"), controller.calls());
    assertEquals(1, controller.turnCount());
  }

  /**
   * When both movement and turning are on cooldown, the robot cannot step or change facing, so it
   * waits.
   *
   * <pre>
   *   | # | # | # |   |   |
   *   | # | R | # |   | G |     no step and no turn
   *   | # | # | # |   |   |
   * </pre>
   */
  @Test
  public void waitsWhenMovementAndTurningAreOnCooldown() throws Exception {
    // Arrange: same boxed map, but turning is also on cooldown.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setTurnAllowed(false);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: neither action.
    assertTrue(controller.calls().isEmpty());
  }

  /**
   * A wall or dirt row sits north of the rat, but the rat has not seen it this turn. North,
   * northwest, and northeast are blocked. West and east are open. Greedy tries north, then
   * northwest, then northeast, then west before east, so the rat steps west even though the opening
   * to the east is closer to the goal.
   *
   * <pre>
   *   |   | G |   |
   *   |   |   |   |
   *   | # | # | # |     wall north of the rat, not seen this turn
   *   |   | R |   |     step west, not east
   * </pre>
   */
  @Test
  public void slidesWestAlongANorthernWall() throws Exception {
    // Arrange: wall north of the rat has not been seen this turn; west and east are open.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.WEST, true);
    controller.setDirectionAllowed(Direction.EAST, true);
    MapLocation goal = new MapLocation(5, 8);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: left-hand slide, not the eastern opening.
    assertEquals(Arrays.asList("move:WEST"), controller.calls());
  }

  /**
   * After that west step, the goal just north of the wall is now northeast. North and northeast are
   * still blocked, and east is the tile the rat left, so it walks back east. A farther northern
   * goal would keep trying north and slide west again.
   *
   * <pre>
   *   t1                  t2
   *   |   | G |   |      |   | G |   |
   *   | # | # | # |      | # | # | # |
   *   |   | R |   | W->  | R |   |   | ->E
   * </pre>
   */
  @Test
  public void pingPongsAlongANorthernWall() throws Exception {
    // Arrange: wall north of the rat has not been seen this turn; after the west step the goal is
    // northeast.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.WEST, true);
    controller.setDirectionAllowed(Direction.EAST, true);
    MapLocation goal = new MapLocation(5, 7);

    // Act: one step, then the next turn from the new tile.
    Navigation.stepToward(controller, goal, strategy());
    controller.setLocation(new MapLocation(4, 5));
    Navigation.stepToward(controller, goal, strategy());

    // Assert: west then back east.
    assertEquals(Arrays.asList("move:WEST", "move:EAST"), controller.calls());
  }

  /**
   * A one-tile corridor with the way forward and both sides blocked. The only open neighbor is
   * behind the rat. Because the rat has not seen a wall, it walks away from the goal rather than
   * waiting.
   *
   * <pre>
   *   | # | # | # |   |   |
   *   |   | R | # |   | G |     only west is open; step west
   *   | # | # | # |   |   |
   * </pre>
   */
  @Test
  public void deadEndWalksAwayFromGoal() throws Exception {
    // Arrange: goal east, only west is open, and the blocked tiles have not been seen this turn.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.WEST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: the rear step.
    assertEquals(Arrays.asList("move:WEST"), controller.calls());
  }

  /**
   * Another robot sits on the passable tile to the east. That is not a wall, so DistBug must not
   * start hugging it. Every strategy takes the next open step.
   *
   * <pre>
   *   |   |   |   |   |   |
   *   |   | R | O |   | G |     O occupied; northeast open; step northeast
   *   |   |   |   |   |   |
   * </pre>
   */
  @Test
  public void sidestepsAnOccupiedPassableNeighbor() throws Exception {
    // Arrange: another robot occupies the east tile; northeast is empty; no wall has been seen.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.NORTHEAST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: northeast, not a wait.
    assertEquals(Arrays.asList("move:NORTHEAST"), controller.calls());
  }

  /**
   * Standing on the cheese tile is legal. Pickup can still be refused this turn by action cooldown,
   * so the helper is asked to step toward a goal it already occupies. It must not move or turn. A
   * living king's 3x3 footprint cannot share that center with a baby rat.
   *
   * <pre>
   *   |   |   |   |
   *   |   |R/G|   |     R and G share the tile; no action
   *   |   |   |   |
   * </pre>
   */
  @Test
  public void doesNotMoveWhenAlreadyOnTheGoal() throws Exception {
    // Arrange: current tile is the goal, steps would otherwise be legal.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(true);
    MapLocation goal = new MapLocation(5, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: no action.
    assertTrue(controller.calls().isEmpty());
  }

  static TestRobotController at(int x, int y) {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.BABY_RAT);
    controller.setLocation(new MapLocation(x, y));
    return controller;
  }
}
