package ratplayer;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import java.util.Arrays;

/** Visible-only routes for the king's 3×3 footprint. Does not execute game actions. */
final class KingNavigation {
  private KingNavigation() {}

  enum Purpose {
    CHEESE,
    HOME,
    RETREAT
  }

  private static final int KING_SEARCH_BYTECODE_CEILING = 15000;

  enum RouteResult {
    CONFIRMED,
    BLOCKED,
    UNCONFIRMED
  }

  static RouteResult routeResult;
  // King vision r²=25 fits in an 11×11 grid, rebuilt for every search.
  private static final int GRID_WIDTH = 11;
  private static final int GRID_RADIUS = 5;
  private static final int GRID_SIZE = GRID_WIDTH * GRID_WIDTH;
  private static final int GRID_CENTER = GRID_SIZE / 2;
  private static final byte UNKNOWN = 0;
  private static final byte CLEAR = 1;
  private static final byte BLOCKED = 2;
  // One bit per tile in each row; these are rebuilt from current sensing only.
  private static final int[] clearRows = new int[GRID_WIDTH];
  private static final int[] blockedRows = new int[GRID_WIDTH];
  private static final int[] queue = new int[GRID_SIZE];
  private static final int[] goalDistances = new int[GRID_SIZE];
  private static final Direction[] firstSteps = new Direction[GRID_SIZE];

  /**
   * Returns a first step, or null when already at the goal or no usable route is found.
   * HOME/RETREAT can return confirmed local progress without confirming the complete route;
   * routeResult reports whether the complete goal was reached by this bounded search.
   */
  static Direction findRoute(
      RobotController rc,
      MapInfo[] infos,
      RobotInfo[] robots,
      MapLocation goal,
      MapLocation home,
      Purpose purpose) {
    int goalRadiusSquared =
        purpose == Purpose.CHEESE
            ? GameConstants.CHEESE_PICK_UP_RADIUS_SQUARED
            : Config.KING_HOME_RADIUS_SQUARED;
    boolean stayHome = purpose == Purpose.CHEESE && home != null;
    boolean allowPartial = purpose != Purpose.CHEESE;
    routeResult = RouteResult.UNCONFIRMED;
    MapLocation current = rc.getLocation();
    if (current.distanceSquaredTo(goal) <= goalRadiusSquared) {
      routeResult = RouteResult.CONFIRMED;
      return null;
    }
    if (Clock.getBytecodeNum() >= KING_SEARCH_BYTECODE_CEILING) {
      return null;
    }
    Arrays.fill(clearRows, 0);
    Arrays.fill(blockedRows, 0);
    Arrays.fill(firstSteps, null);
    for (MapInfo info : infos) {
      if (Clock.getBytecodeNum() >= KING_SEARCH_BYTECODE_CEILING) {
        return null;
      }
      MapLocation location = info.getMapLocation();
      int x = location.x - current.x + GRID_RADIUS;
      int y = location.y - current.y + GRID_RADIUS;
      if (x >= 0 && x < GRID_WIDTH && y >= 0 && y < GRID_WIDTH) {
        if (info.isPassable() && info.flyingRobot() == null) {
          clearRows[y] |= 1 << x;
        } else {
          blockedRows[y] |= 1 << x;
        }
      }
    }
    for (RobotInfo robot : robots) {
      if (robot.getID() == rc.getID()) {
        continue;
      }
      // The engine's type helper includes the cat's bottom-left footprint.
      for (MapLocation part : robot.getType().getAllTypeLocations(robot.getLocation())) {
        if (Clock.getBytecodeNum() >= KING_SEARCH_BYTECODE_CEILING) {
          return null;
        }
        int index = gridIndex(part, current);
        if (index >= 0) {
          blockedRows[index / GRID_WIDTH] |= 1 << (index % GRID_WIDTH);
        }
      }
    }
    int head = 0;
    int tail = 1;
    queue[0] = GRID_CENTER;
    firstSteps[GRID_CENTER] = Direction.CENTER;
    int best = GRID_CENTER;
    int bestDistance = current.distanceSquaredTo(goal);
    goalDistances[GRID_CENTER] = bestDistance;
    int safety = purpose == Purpose.RETREAT ? catDistance(current, robots) : Integer.MAX_VALUE;
    boolean flee = safety != Integer.MAX_VALUE;
    int width = rc.getMapWidth();
    int height = rc.getMapHeight();
    int preferred = 0;
    Direction towardGoal = current.directionTo(goal);
    while (RobotPlayer.directions[preferred] != towardGoal) {
      preferred++;
    }
    boolean unknownBoundary = false;
    while (head < tail) {
      // Leave the rest of the turn for movement, publication, pickup, spawning, and yield.
      if (Clock.getBytecodeNum() >= KING_SEARCH_BYTECODE_CEILING) {
        if (allowPartial && best != GRID_CENTER) {
          return firstSteps[best];
        }
        return null;
      }
      // Examine the most promising frontier position first. Breadth-first expansion
      // exhausted the measured budget on detours before reaching a collection position.
      int closest = head;
      for (int i = head + 1; i < tail; i++) {
        if (goalDistances[queue[i]] < goalDistances[queue[closest]]) {
          closest = i;
        }
      }
      int index = queue[closest];
      queue[closest] = queue[head++];
      for (int offset = 0;
          offset < RobotPlayer.directions.length
              && Clock.getBytecodeNum() < KING_SEARCH_BYTECODE_CEILING;
          offset++) {
        Direction direction = RobotPlayer.directions[(preferred + offset) % 8];
        int x = index % GRID_WIDTH + direction.dx;
        int y = index / GRID_WIDTH + direction.dy;
        if (x <= 0 || x >= GRID_WIDTH - 1 || y <= 0 || y >= GRID_WIDTH - 1) {
          unknownBoundary = true;
          continue;
        }
        int next = x + y * GRID_WIDTH;
        if (firstSteps[next] != null) {
          continue;
        }
        // CENTER marks evaluated non-route positions as well as the start.
        firstSteps[next] = Direction.CENTER;
        MapLocation location = current.translate(x - GRID_RADIUS, y - GRID_RADIUS);
        if ((stayHome && location.distanceSquaredTo(home) > Config.KING_HOME_RADIUS_SQUARED)
            || (flee && catDistance(location, robots) <= safety)) {
          continue;
        }
        byte footprint = footprintAt(next, location, width, height);
        if (footprint == UNKNOWN) {
          unknownBoundary = true;
          continue;
        }
        if (footprint == BLOCKED) {
          continue;
        }
        Direction first = index == GRID_CENTER ? direction : firstSteps[index];
        firstSteps[next] = first;
        queue[tail++] = next;
        int distance = location.distanceSquaredTo(goal);
        goalDistances[next] = distance;
        if (distance <= goalRadiusSquared) {
          routeResult = RouteResult.CONFIRMED;
          return first;
        }
        if (distance < bestDistance) {
          best = next;
          bestDistance = distance;
        }
      }
    }
    routeResult =
        unknownBoundary || Clock.getBytecodeNum() >= KING_SEARCH_BYTECODE_CEILING
            ? RouteResult.UNCONFIRMED
            : RouteResult.BLOCKED;
    if (allowPartial && best != GRID_CENTER) {
      // This is a confirmed local path only, not a confirmed route to the distant mine.
      return firstSteps[best];
    }
    return null;
  }

  private static int gridIndex(MapLocation location, MapLocation current) {
    int x = location.x - current.x + GRID_RADIUS;
    int y = location.y - current.y + GRID_RADIUS;
    return x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_WIDTH ? -1 : x + y * GRID_WIDTH;
  }

  private static byte footprintAt(int index, MapLocation location, int width, int height) {
    if (location.x < 1 || location.y < 1 || location.x >= width - 1 || location.y >= height - 1) {
      return BLOCKED;
    }
    // Three row masks check all nine tiles at once. Repeated tile loops exhausted
    // the measured search budget before a simple visible detour could be found.
    int x = index % GRID_WIDTH;
    int y = index / GRID_WIDTH;
    int footprint = 7 << (x - 1);
    if (((blockedRows[y - 1] | blockedRows[y] | blockedRows[y + 1]) & footprint) != 0) {
      return BLOCKED;
    }
    int clear = clearRows[y - 1] & clearRows[y] & clearRows[y + 1];
    return (clear & footprint) == footprint ? CLEAR : UNKNOWN;
  }

  static int catDistance(MapLocation location, RobotInfo[] robots) {
    int distance = Integer.MAX_VALUE;
    for (RobotInfo robot : robots) {
      if (robot.getType() == UnitType.CAT) {
        distance = Math.min(distance, location.distanceSquaredTo(robot.getLocation()));
      }
    }
    return distance;
  }
}
