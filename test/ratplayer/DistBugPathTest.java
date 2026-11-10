package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.TrapType;
import java.util.Arrays;
import org.junit.Test;
import ratplayer.navigation.Navigation;
import support.TestRobotController;

/**
 * Tests DistBug when the robot has seen a wall and starts or stops following it. Maps where it has
 * not seen a wall live in {@link CommonPathingTest}; DistBug must match greedy there.
 *
 * <p>Diagrams are north-up, +x east. {@code R} rat, {@code G} goal, {@code #} wall the robot has
 * seen, {@code ?} blocked tile the robot has not seen, {@code O} occupied passable, {@code .} open.
 */
public class DistBugPathTest extends CommonPathingTest {

  @Override
  Config.PathingStrategy strategy() {
    return Config.PathingStrategy.DIST_BUG;
  }

  /**
   * The rat has seen a wall on the tile it wants. DistBug starts following that wall. The rat faces
   * into the wall so the first open left-hand step is northeast, not a greedy left/right pair that
   * could walk back east.
   *
   * <pre>
   *   |   |   |   |   |   |
   *   |   | R | # |   | G |     seen wall; faces east; follow northeast
   *   |   |   |   |   |   |
   * </pre>
   */
  @Test
  public void distBugFollowsSensedWallInsteadOfBlockedHeading() throws Exception {
    // Arrange: goal east, wall on (6, 5) that the rat has seen, only the two side steps are legal.
    TestRobotController controller = at(5, 5);
    controller.setDirection(Direction.EAST);
    controller.setNearbyMapInfos(new MapInfo[] {wall(6, 5)});
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.NORTHEAST, true);
    controller.setDirectionAllowed(Direction.SOUTHEAST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: one northeast follow step, and DistBug is still following on the next turn.
    assertEquals(Arrays.asList("move:NORTHEAST"), controller.calls());
    assertTrue(Navigation.following);
    assertTrue(Navigation.followLeft);
    assertEquals(goal, Navigation.followGoal);
    assertEquals(9, Navigation.hitDistanceSq);
  }

  /**
   * The tile toward the goal is blocked, but the rat has not seen it this turn. DistBug must not
   * start following a wall it cannot see. Greedy may still take an open side step.
   *
   * <pre>
   *   |   |   |   |   |   |
   *   |   | R | ? |   | G |     blocked but not seen; greedy northeast; no follow
   *   |   |   |   |   |   |
   * </pre>
   */
  @Test
  public void distBugDoesNotFollowWhenBlockedTileIsUnsensed() throws Exception {
    // Arrange: east is blocked and has not been seen this turn; northeast is open.
    TestRobotController controller = at(5, 5);
    controller.setNearbyMapInfos(new MapInfo[0]);
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.NORTHEAST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: greedy northeast, and DistBug does not start following.
    assertEquals(Arrays.asList("move:NORTHEAST"), controller.calls());
    assertFalse(Navigation.following);
  }

  /**
   * The rat has seen the east tile and it is passable. Another robot or cooldown is blocking the
   * step, not a wall, so DistBug must not start following.
   *
   * <pre>
   *   |   |   |   |   |   |
   *   |   | R | O |   | G |     another robot; greedy northeast; no follow
   *   |   |   |   |   |   |
   * </pre>
   */
  @Test
  public void distBugDoesNotFollowWhenSensedTileIsPassable() throws Exception {
    // Arrange: east is occupied but passable, northeast is open.
    TestRobotController controller = at(5, 5);
    controller.setNearbyMapInfos(new MapInfo[] {passable(6, 5)});
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.NORTHEAST, true);
    MapLocation goal = new MapLocation(8, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: greedy northeast, not a new wall-follow.
    assertEquals(Arrays.asList("move:NORTHEAST"), controller.calls());
    assertFalse(Navigation.following);
  }

  /**
   * The rat is already following a wall. It leaves only when the way toward the goal is open and it
   * is closer than when it first hit the wall. That is the DistBug rule that stops walking around a
   * U-shape forever.
   *
   * <pre>
   *   already following, first hit was farther
   *   |   | R |   |   | G |     closer now and east open; walk toward the goal
   * </pre>
   */
  @Test
  public void distBugLeavesFollowWhenCloserAndHeadingIsOpen() throws Exception {
    // Arrange: already following a wall from farther away, now at (5, 5) with an open east step.
    TestRobotController controller = at(5, 5);
    MapLocation goal = new MapLocation(8, 5);
    Navigation.following = true;
    Navigation.followLeft = true;
    Navigation.followGoal = goal;
    Navigation.hitDistanceSq = 50;
    controller.setCanMove(false);
    controller.setDirectionAllowed(Direction.EAST, true);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: walk east toward the goal and stop following the wall.
    assertEquals(Arrays.asList("move:EAST"), controller.calls());
    assertFalse(Navigation.following);
  }

  private static MapInfo wall(int x, int y) {
    return new MapInfo(new MapLocation(x, y), false, null, true, false, 0, TrapType.NONE, false);
  }

  private static MapInfo passable(int x, int y) {
    return new MapInfo(new MapLocation(x, y), true, null, false, false, 0, TrapType.NONE, false);
  }
}
