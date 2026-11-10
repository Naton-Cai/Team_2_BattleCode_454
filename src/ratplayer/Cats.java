package ratplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Shared cat sensing and block heading. Each unit still applies its own cat-response gate.
 */
final class Cats {
  private Cats() {}

  /** Returns the cats currently visible to this robot. */
  static RobotInfo[] detectCats(RobotController rc) {
    RobotInfo[] visibleRobots = rc.senseNearbyRobots();
    int catCount = 0;
    for (int i = 0; i < visibleRobots.length; i++) {
      if (visibleRobots[i].getType() == UnitType.CAT) {
        catCount++;
      }
    }
    RobotInfo[] cats = new RobotInfo[catCount];
    int nextCat = 0;
    for (int i = 0; i < visibleRobots.length; i++) {
      if (visibleRobots[i].getType() == UnitType.CAT) {
        cats[nextCat++] = visibleRobots[i];
      }
    }
    return cats;
  }

  /**
   * Nearest visible cat by squared distance. Equal distances keep the earlier sense result.
   */
  static RobotInfo nearestVisibleCat(RobotController rc) {
    RobotInfo[] cats = detectCats(rc);
    MapLocation origin = rc.getLocation();
    RobotInfo nearest = null;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = 0; i < cats.length; i++) {
      int distance = origin.distanceSquaredTo(cats[i].getLocation());
      if (nearest == null || distance < bestDistance) {
        nearest = cats[i];
        bestDistance = distance;
      }
    }
    return nearest;
  }

  /**
   * Places dirt on the first Chebyshev step outside this unit's footprint toward {@code
   * catLocation}.
   */
  static void placeDirtTowardCat(RobotController rc, MapLocation catLocation)
      throws GameActionException {
    MapLocation origin = rc.getLocation();
    int stepX = Integer.compare(catLocation.x - origin.x, 0);
    int stepY = Integer.compare(catLocation.y - origin.y, 0);
    if (stepX == 0 && stepY == 0) {
      return;
    }
    int step = 1 + (rc.getType().size - 1) / 2;
    Dirt.tryPlace(rc, new MapLocation(origin.x + step * stepX, origin.y + step * stepY));
  }

  /** Opposite heading from {@code catLocation}, or {@link Direction#CENTER} if none. */
  static Direction awayFrom(RobotController rc, MapLocation catLocation) {
    return rc.getLocation().directionTo(catLocation).opposite();
  }
}
