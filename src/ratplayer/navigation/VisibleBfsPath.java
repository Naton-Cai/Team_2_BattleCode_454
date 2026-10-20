package ratplayer.navigation;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
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

  static void stepToward(RobotController rc, MapLocation goal) throws GameActionException {
    MapLocation current = rc.getLocation();
    if (current.directionTo(goal) == Direction.CENTER) {
      return;
    }

    int count = collectVisiblePassableTiles(rc, current);
    if (count <= 1) {
      GreedyPath.stepToward(rc, goal);
      return;
    }

    int targetIndex = visibleBfsTargetIndex(count, current, goal);
    if (targetIndex <= 0) {
      GreedyPath.stepToward(rc, goal);
      return;
    }

    int nextIndex = visibleBfsFirstStep(count, targetIndex);
    if (nextIndex < 0) {
      GreedyPath.stepToward(rc, goal);
      return;
    }

    Direction step = current.directionTo(Navigation.bfsTiles[nextIndex]);
    if (step != Direction.CENTER && rc.canMove(step)) {
      rc.move(step);
      return;
    }
    GreedyPath.stepToward(rc, goal);
  }

  static int collectVisiblePassableTiles(RobotController rc, MapLocation current) {
    int count = 0;
    Navigation.bfsTiles[count] = current;
    count += 1;
    MapInfo[] infos = rc.senseNearbyMapInfos();
    for (int i = 0; i < infos.length && count < Navigation.MAX_VISIBLE_TILES; i++) {
      MapInfo info = infos[i];
      if (!info.isPassable()) {
        continue;
      }
      MapLocation loc = info.getMapLocation();
      if (loc.equals(current)) {
        continue;
      }
      Navigation.bfsTiles[count] = loc;
      count += 1;
    }
    return count;
  }

  /**
   * Goal index when that tile was sensed and is passable; otherwise the closest sensed passable
   * tile. Index 0 is the current location.
   */
  static int visibleBfsTargetIndex(int count, MapLocation current, MapLocation goal) {
    int closestIndex = 0;
    int bestDistance = current.distanceSquaredTo(goal);
    for (int i = 0; i < count; i++) {
      if (Navigation.bfsTiles[i].equals(goal)) {
        return i;
      }
      int distance = Navigation.bfsTiles[i].distanceSquaredTo(goal);
      if (distance < bestDistance) {
        closestIndex = i;
        bestDistance = distance;
      }
    }
    return closestIndex;
  }

  /** Index of the first step toward {@code targetIndex}, or -1 if no path exists. */
  static int visibleBfsFirstStep(int count, int targetIndex) {
    for (int i = 0; i < count; i++) {
      Navigation.bfsVisited[i] = false;
      Navigation.bfsParent[i] = -1;
    }
    int head = 0;
    int tail = 0;
    Navigation.bfsQueue[tail] = 0;
    tail += 1;
    Navigation.bfsVisited[0] = true;
    while (head < tail) {
      int index = Navigation.bfsQueue[head];
      head += 1;
      if (index == targetIndex) {
        break;
      }
      MapLocation from = Navigation.bfsTiles[index];
      for (int d = 0; d < Navigation.MOVE_DIRECTIONS.length; d++) {
        MapLocation next = from.add(Navigation.MOVE_DIRECTIONS[d]);
        int neighbor = indexOfTile(count, next);
        if (neighbor < 0 || Navigation.bfsVisited[neighbor]) {
          continue;
        }
        Navigation.bfsVisited[neighbor] = true;
        Navigation.bfsParent[neighbor] = index;
        Navigation.bfsQueue[tail] = neighbor;
        tail += 1;
      }
    }
    if (!Navigation.bfsVisited[targetIndex] || Navigation.bfsParent[targetIndex] < 0) {
      return -1;
    }
    int step = targetIndex;
    while (Navigation.bfsParent[step] != 0) {
      step = Navigation.bfsParent[step];
    }
    return step;
  }

  static int indexOfTile(int count, MapLocation loc) {
    for (int i = 0; i < count; i++) {
      if (Navigation.bfsTiles[i].equals(loc)) {
        return i;
      }
    }
    return -1;
  }
}
