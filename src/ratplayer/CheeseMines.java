package ratplayer;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

/** Team mine records, independent of visibility and robot-local initialization. */
public final class CheeseMines {
  // Reserve 0–8 for three x/y/valid king records; only 0–2 are currently implemented.
  static final int FIRST_SLOT = 9;
  static final int MAX_CAPACITY = (GameConstants.SHARED_ARRAY_SIZE - FIRST_SLOT) / 2;

  private CheeseMines() {}

  private static int capacity() {
    if (Config.MAX_CHEESE_MINES < 0 || Config.MAX_CHEESE_MINES > MAX_CAPACITY) {
      throw new IllegalArgumentException("Mine cap must be between 0 and " + MAX_CAPACITY);
    }
    return Config.MAX_CHEESE_MINES;
  }

  /** Returns an exact committed coordinate, or null for an empty/incomplete record. */
  public static MapLocation read(RobotController rc, int index) throws GameActionException {
    if (index < 0 || index >= capacity()) {
      throw new IllegalArgumentException("Mine index outside configured capacity");
    }
    int slot = FIRST_SLOT + 2 * index;
    int committedY = rc.readSharedArray(slot + 1);
    if (committedY == 0) {
      return null;
    }
    int x = rc.readSharedArray(slot);
    int y = committedY - 1;
    if (x >= rc.getMapWidth() || y >= rc.getMapHeight()) {
      return null;
    }
    return new MapLocation(x, y);
  }

  // Called only for directly sensed mine flags. Full registries retain the first discoveries.
  static void publish(RobotController rc, MapLocation location) throws GameActionException {
    int limit = capacity();
    if (rc.getType() != UnitType.RAT_KING) {
      return;
    }
    int empty = -1;
    for (int i = 0; i < limit; i++) {
      MapLocation known = read(rc, i);
      if (location.equals(known)) {
        return;
      }
      if (known == null && empty == -1) {
        empty = i;
      }
    }
    if (empty == -1) {
      return;
    }
    int slot = FIRST_SLOT + 2 * empty;
    // y+1 is the commit marker, preserving (0,0). Never modify committed records.
    // A failed x/y append leaves y zero; any later king can reuse that slot safely.
    rc.writeSharedArray(slot, location.x);
    rc.writeSharedArray(slot + 1, location.y + 1);
  }
}
