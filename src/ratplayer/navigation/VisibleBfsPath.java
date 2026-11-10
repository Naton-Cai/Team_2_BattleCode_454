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
 * <p>Uses {@link Navigation}'s reusable BFS buffers. Neighbor lookup is an 11x11 offset grid filled
 * during collect, not a linear scan of {@code bfsTiles}.
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
    for (int i = 0; i < Navigation.bfsIndexGrid.length; i++) {
      Navigation.bfsIndexGrid[i] = -1;
    }
    int count = 0;
    count = recordVisibleTile(count, current);
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
      count = recordVisibleTile(count, loc);
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
    int cx = Navigation.bfsX[0];
    int cy = Navigation.bfsY[0];
    while (head < tail) {
      int index = Navigation.bfsQueue[head];
      head += 1;
      if (index == targetIndex) {
        break;
      }
      int fromX = Navigation.bfsX[index];
      int fromY = Navigation.bfsY[index];
      for (int d = 0; d < Navigation.MOVE_DX.length; d++) {
        int neighbor =
            indexAt(fromX + Navigation.MOVE_DX[d], fromY + Navigation.MOVE_DY[d], cx, cy);
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

  static int recordVisibleTile(int count, MapLocation loc) {
    Navigation.bfsTiles[count] = loc;
    Navigation.bfsX[count] = loc.x;
    Navigation.bfsY[count] = loc.y;
    int ox = loc.x - Navigation.bfsX[0] + Navigation.GRID_RADIUS;
    int oy = loc.y - Navigation.bfsY[0] + Navigation.GRID_RADIUS;
    if (ox >= 0 && ox < Navigation.GRID_WIDTH && oy >= 0 && oy < Navigation.GRID_WIDTH) {
      Navigation.bfsIndexGrid[ox + Navigation.GRID_WIDTH * oy] = count;
    }
    return count + 1;
  }

  static int indexAt(int x, int y, int cx, int cy) {
    int ox = x - cx + Navigation.GRID_RADIUS;
    int oy = y - cy + Navigation.GRID_RADIUS;
    if (ox < 0 || ox >= Navigation.GRID_WIDTH || oy < 0 || oy >= Navigation.GRID_WIDTH) {
      return -1;
    }
    return Navigation.bfsIndexGrid[ox + Navigation.GRID_WIDTH * oy];
  }
}
