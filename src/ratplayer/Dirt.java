package ratplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Shared dirt digging and placement. {@code canRemoveDirt} and {@code canPlaceDirt} encode unit
 * range, vision, occupancy, and cooldown. Baby rats still pick the three in-cone tiles to dig;
 * kings scan build range {@code r² <= 8} from center.
 */
final class Dirt {
  private Dirt() {}

  /**
   * King dig order from center: the eight spawn-ring tiles clockwise from north, then the other
   * in-range tiles. Spawn-ring first so a blocked north spawn can be cleared next if spawn fails.
   */
  static final int[] KING_DIG_DX = {
    0, 2, 2, 2, 0, -2, -2, -2, 1, 2, 2, 1, -1, -2, -2, -1, 0, 1, 1, 1, 0, -1, -1, -1
  };

  static final int[] KING_DIG_DY = {
    2, 2, 0, -2, -2, -2, 0, 2, 2, 1, -1, -2, -2, -1, 1, 2, 1, 1, 0, -1, -1, -1, 0, 1
  };

  static boolean reserveAllowsDig(RobotController rc) {
    int globalAfterPay =
        rc.getGlobalCheese() - Math.max(0, GameConstants.DIG_DIRT_CHEESE_COST - rc.getRawCheese());
    return globalAfterPay >= Config.MIN_CHEESE_RESERVE_FOR_DIG;
  }

  static boolean tryRemove(RobotController rc, MapLocation location) throws GameActionException {
    if (!rc.canRemoveDirt(location)) {
      return false;
    }
    rc.removeDirt(location);
    return true;
  }

  static boolean tryPlace(RobotController rc, MapLocation location) throws GameActionException {
    if (!rc.canPlaceDirt(location)) {
      return false;
    }
    rc.placeDirt(location);
    return true;
  }

  static boolean digInVisionCone(RobotController rc) throws GameActionException {
    if (!reserveAllowsDig(rc)) {
      return false;
    }
    Direction direction = rc.getDirection();
    MapLocation center = rc.getLocation();
    if (tryRemove(rc, center.add(direction))) {
      return true;
    }
    if (tryRemove(rc, center.add(direction.rotateLeft()))) {
      return true;
    }
    return tryRemove(rc, center.add(direction.rotateRight()));
  }

  static boolean digAroundKing(RobotController rc) throws GameActionException {
    if (!reserveAllowsDig(rc)) {
      return false;
    }
    MapLocation center = rc.getLocation();
    for (int i = 0; i < KING_DIG_DX.length; i++) {
      MapLocation tile = new MapLocation(center.x + KING_DIG_DX[i], center.y + KING_DIG_DY[i]);
      if (tryRemove(rc, tile)) {
        return true;
      }
    }
    return false;
  }
}
