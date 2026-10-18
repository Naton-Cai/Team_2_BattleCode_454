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
 * Current {@link ratplayer.navigation.GreedyPath} behavior on engine-legal stuck layouts. The mock
 * answers {@code canMove}; it does not prove collision geometry. DistBug and visible BFS are not
 * covered here.
 */
public class GreedyPathTest {

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
   * {@code canMove} is false for every direction when movement cooldown is at least 10, when all
   * eight neighbors are walls or dirt, or when all eight are occupied. A 1x1 baby rat can be boxed
   * that way; a king more often is, because its 3x3 footprint needs every destination tile clear.
   * Turning is a separate cooldown, so the engine can still allow a facing change.
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
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: no move, one turn onto the goal heading.
    assertEquals(Arrays.asList("turn:EAST"), controller.calls());
    assertEquals(1, controller.turnCount());
  }

  /**
   * When movement and turning cooldowns are both raised, every {@code canMove} is false and
   * {@code canTurn} is false, so greedy must wait.
   */
  @Test
  public void waitsWhenMovementAndTurningAreOnCooldown() throws Exception {
    // Arrange: same boxed geometry, but turning is also refused.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setTurnAllowed(false);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: neither action.
    assertTrue(controller.calls().isEmpty());
  }

  /**
   * An east-west wall or dirt row north of the rat. North, northwest, and northeast are impassable.
   * West and east are open ground. Greedy tries north, then northwest, then northeast, then west
   * before east, so it slides west even when the opening is to the east.
   */
  @Test
  public void slidesWestAlongANorthernWall() throws Exception {
    // Arrange: wall along y = 6, side steps open.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.WEST, true);
    controller.setDirectionAllowed(Direction.EAST, true);
    MapLocation goal = new MapLocation(5, 8);

    // Act.
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: left-hand slide, not the eastern opening.
    assertEquals(Arrays.asList("move:WEST"), controller.calls());
  }

  /**
   * After that west step, a goal just north of the wall is northeast ({@code dy = 2}, {@code dx =
   * 1}). North and northeast are still the wall, and east is the tile the rat left, so greedy walks
   * back east. A farther northern goal would keep heading north and slide west again.
   */
  @Test
  public void pingPongsAlongANorthernWall() throws Exception {
    // Arrange: wall along y = 6, goal at (5, 7) so the second heading is northeast.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.WEST, true);
    controller.setDirectionAllowed(Direction.EAST, true);
    MapLocation goal = new MapLocation(5, 7);

    // Act: one step, then the next turn from the new tile.
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);
    controller.setLocation(new MapLocation(4, 5));
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: west then back east.
    assertEquals(Arrays.asList("move:WEST", "move:EAST"), controller.calls());
  }

  /**
   * A one-tile corridor with a wall in front and walls on both sides. The only legal neighbor is
   * behind the rat. Greedy walks opposite the goal rather than waiting.
   */
  @Test
  public void deadEndWalksAwayFromGoal() throws Exception {
    // Arrange: goal east, only west is a passable empty tile.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.WEST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: the rear step.
    assertEquals(Arrays.asList("move:WEST"), controller.calls());
  }

  /**
   * Occupancy blocks {@code canMove} on a passable tile. Another robot on the east neighbor is not
   * a wall, but greedy only sees a refused east step and takes the next open probe.
   */
  @Test
  public void sidestepsAnOccupiedPassableNeighbor() throws Exception {
    // Arrange: east occupied, northeast empty and passable.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.NORTHEAST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: northeast, not a wait.
    assertEquals(Arrays.asList("move:NORTHEAST"), controller.calls());
  }

  /**
   * Standing on the cheese tile is legal. Pickup can still be refused this turn by action cooldown,
   * so greedy is asked to step toward a goal it already occupies. {@code directionTo} is {@code
   * CENTER}, so it must not move or turn. A living king's 3x3 footprint cannot share that center
   * with a baby rat.
   */
  @Test
  public void doesNotMoveWhenAlreadyOnTheGoal() throws Exception {
    // Arrange: current tile is the goal, steps would otherwise be legal.
    TestRobotController controller = at(5, 5);
    controller.setCanMove(true);
    MapLocation goal = new MapLocation(5, 5);

    // Act.
    Navigation.stepToward(controller, goal, Config.PathingStrategy.GREEDY);

    // Assert: no action.
    assertTrue(controller.calls().isEmpty());
  }

  private static TestRobotController at(int x, int y) {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.BABY_RAT);
    controller.setLocation(new MapLocation(x, y));
    return controller;
  }
}
