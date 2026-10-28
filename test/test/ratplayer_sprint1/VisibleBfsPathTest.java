package ratplayer_sprint1;

import static org.junit.Assert.assertEquals;

import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.TrapType;
import java.util.Arrays;
import org.junit.Test;
import ratplayer_sprint1.navigation.Navigation;
import support.TestRobotController;

/**
 * Tests BFS on tiles the robot can see. Maps where BFS has nothing extra to search live in {@link
 * CommonPathingTest}; BFS must match greedy there. DistBug wall-follow is in {@link
 * DistBugPathTest}.
 *
 * <p>Diagrams are north-up, +x east. {@code R} rat, {@code G} goal, {@code #} wall the robot has
 * seen, {@code ?} tile the robot has not seen, {@code .} open.
 */
public class VisibleBfsPathTest extends CommonPathingTest {

  @Override
  Config.PathingStrategy strategy() {
    return Config.PathingStrategy.VISIBLE_BFS;
  }

  /**
   * BFS may only walk tiles the robot saw this turn. The northern shortcut looks open, but those
   * tiles were never seen, so the first step must be the southern detour around a wall the robot
   * did see.
   *
   * <pre>
   *   | ? | ? | ? |     not seen this turn (greedy shortcut)
   *   | R | # | G |     wall east of the rat, seen this turn
   *   |   |   |   |     southern corridor the robot can see; first step southeast
   * </pre>
   */
  @Test
  public void visibleBfsTakesSensedDetourInsteadOfUnsensedShortcut() throws Exception {
    // Arrange: wall east of the rat, only a southern corridor has been seen this turn.
    TestRobotController controller = at(5, 5);
    controller.setNearbyMapInfos(
        new MapInfo[] {
          wall(6, 5), passable(5, 4), passable(6, 4), passable(7, 4), passable(7, 5),
        });
    controller.setCanMove(true);
    MapLocation goal = new MapLocation(7, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: first BFS step is southeast around the wall, not east into it or northeast.
    assertEquals(Arrays.asList("move:SOUTHEAST"), controller.calls());
  }

  /**
   * BFS cannot invent a path through tiles the robot has not seen, even when those steps look open.
   * With only the current tile seen, BFS matches greedy and walks east.
   *
   * <pre>
   *   | ? | ? | ? |
   *   | R | ? | G |     only R is seen; greedy east, not a made-up BFS path
   *   | ? | ? | ? |
   * </pre>
   */
  @Test
  public void visibleBfsDoesNotInventUnsensedTiles() throws Exception {
    // Arrange: goal two tiles east, no neighboring tiles have been seen this turn.
    TestRobotController controller = at(5, 5);
    controller.setNearbyMapInfos(new MapInfo[] {passable(5, 5)});
    controller.setCanMove(true);
    MapLocation goal = new MapLocation(7, 5);

    // Act.
    Navigation.stepToward(controller, goal, strategy());

    // Assert: greedy walks east. No made-up path through the unseen tile in between.
    assertEquals(Arrays.asList("move:EAST"), controller.calls());
  }

  private static MapInfo wall(int x, int y) {
    return new MapInfo(new MapLocation(x, y), false, null, true, false, 0, TrapType.NONE, false);
  }

  private static MapInfo passable(int x, int y) {
    return new MapInfo(new MapLocation(x, y), true, null, false, false, 0, TrapType.NONE, false);
  }
}
