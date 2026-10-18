package ratplayer.navigation;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Seek the goal until a known wall, dirt, or map edge blocks the heading. Follow with a fixed
 * hand until the heading is open and the unit is closer than at the hit.
 *
 * <p>Follow state lives on {@link Navigation}. Falls back to {@link GreedyPath} while seeking.
 */
final class DistBugPath {
  private DistBugPath() {}

  static void stepToward(RobotController rc, MapLocation goal) throws GameActionException {}
}
