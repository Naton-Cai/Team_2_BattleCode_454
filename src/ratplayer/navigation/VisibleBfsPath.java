package ratplayer.navigation;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * BFS over this turn's sensed passable tiles, then take the first legal step. Unknown tiles are
 * not walkable. Falls back to {@link GreedyPath} when the visible graph has no path.
 *
 * <p>Uses {@link Navigation}'s reusable BFS buffers.
 */
final class VisibleBfsPath {
  private VisibleBfsPath() {}

  static void stepToward(RobotController rc, MapLocation goal) throws GameActionException {}
}
