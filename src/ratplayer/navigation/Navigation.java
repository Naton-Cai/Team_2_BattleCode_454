package ratplayer.navigation;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import ratplayer.Config;

/**
 * Shared one-step pathing switch and buffers. Algorithm bodies live in {@link GreedyPath}, {@link
 * DistBugPath}, and {@link VisibleBfsPath}.
 *
 * <p>Uses only {@link RobotController}, so a baby rat or king can call {@link #stepToward}. {@code
 * canMove} already applies that unit's footprint. Static follow and BFS buffers are robot-local
 * copies, not team memory.
 */
public final class Navigation {
  private Navigation() {}

  /** DistBug follow state. Persists across turns until the goal changes. */
  public static boolean following = false;

  public static boolean followLeft = true;
  public static int hitDistanceSq = Integer.MAX_VALUE;
  public static MapLocation followGoal = null;

  /** Reused probe order for one greedy step. */
  static final Direction[] stepCandidates = new Direction[8];

  static final Direction[] MOVE_DIRECTIONS = {
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST
  };

  /**
   * Visible-tile BFS buffers. 96 is above a king's 360-degree disk of radius squared 25 and a baby
   * rat's 90-degree cone of radius squared 20.
   */
  static final int MAX_VISIBLE_TILES = 96;

  static final MapLocation[] bfsTiles = new MapLocation[MAX_VISIBLE_TILES];
  static final int[] bfsQueue = new int[MAX_VISIBLE_TILES];
  static final int[] bfsParent = new int[MAX_VISIBLE_TILES];
  static final boolean[] bfsVisited = new boolean[MAX_VISIBLE_TILES];

  /**
   * One step toward {@code goal} using {@code strategy}. Default greedy is the 8-direction probe.
   * Standing on the goal does not move or turn.
   */
  public static void stepToward(
      RobotController rc, MapLocation goal, Config.PathingStrategy strategy)
      throws GameActionException {
    switch (strategy) {
      case DIST_BUG:
        DistBugPath.stepToward(rc, goal);
        return;
      case VISIBLE_BFS:
        resetFollow();
        VisibleBfsPath.stepToward(rc, goal);
        return;
      case GREEDY:
      default:
        resetFollow();
        GreedyPath.stepToward(rc, goal);
    }
  }

  /**
   * A tile is an obstacle only when it is off the known map or present in this turn's sense
   * results and impassable. Unlisted tiles stay unknown and must not start follow.
   */
  static boolean isKnownObstacle(RobotController rc, MapInfo[] infos, MapLocation loc) {
    if (isOffMap(rc, loc)) {
      return true;
    }
    MapInfo info = findSensedInfo(infos, loc);
    return info != null && !info.isPassable();
  }

  static boolean isOffMap(RobotController rc, MapLocation loc) {
    int width = rc.getMapWidth();
    int height = rc.getMapHeight();
    if (width <= 0 || height <= 0) {
      return false;
    }
    return loc.x < 0 || loc.y < 0 || loc.x >= width || loc.y >= height;
  }

  static MapInfo findSensedInfo(MapInfo[] infos, MapLocation loc) {
    for (int i = 0; i < infos.length; i++) {
      if (infos[i].getMapLocation().equals(loc)) {
        return infos[i];
      }
    }
    return null;
  }

  public static void resetFollow() {
    following = false;
    followLeft = true;
    hitDistanceSq = Integer.MAX_VALUE;
    followGoal = null;
  }
}
