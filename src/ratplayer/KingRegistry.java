package ratplayer;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/** Shared king locations, with robot-local state for each publisher. */
final class KingRegistry {
  static final int CAPACITY = GameConstants.MAX_NUMBER_OF_RAT_KINGS;
  static final int FIRST_MINE_SLOT = 9;
  static final int FIRST_EXTRA_KING_SLOT = 29;
  private static final int ROUND_BLOCK = 512;
  private static final int PENDING_ROUND_BLOCK = 4;
  private static final int COORDINATE_BLOCK = 64;

  private int id = -1;
  private int lastRound = -1;
  private int pendingStamp;
  private MapLocation lastCenter;

  int lastRound() {
    return lastRound;
  }

  static int slot(int id) {
    return id < 3 ? 3 * id : FIRST_EXTRA_KING_SLOT + 3 * (id - 3);
  }

  boolean publish(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    if (id >= 0) {
      int slot = slot(id);
      int stamp = rc.readSharedArray(slot + 2);
      // A king that missed heartbeats must not overwrite a slot another king reclaimed.
      if (stamp > ROUND_BLOCK) {
        if (stamp != pendingStamp) {
          id = -1;
        }
      } else if (stamp != 0) {
        int packedY = rc.readSharedArray(slot + 1);
        if (decodeRound(stamp, packedY) != lastRound
            || lastCenter.x != rc.readSharedArray(slot)
            || lastCenter.y != packedY % COORDINATE_BLOCK) {
          id = -1;
        }
      }
    }
    if (id < 0) {
      for (int candidate = 0; candidate < CAPACITY; candidate++) {
        if (available(rc, slot(candidate), round)) {
          id = candidate;
          break;
        }
      }
      if (id < 0) {
        return false;
      }
    }

    int slot = slot(id);
    MapLocation center = rc.getLocation();
    // Reserve the slot during a failed write without exposing a partial center. The pending stamp
    // stores the absolute round in four-round buckets, which fits alongside the committed stamps.
    int stamp = ROUND_BLOCK + 1 + round / PENDING_ROUND_BLOCK;
    rc.writeSharedArray(slot + 2, stamp);
    pendingStamp = stamp;
    rc.writeSharedArray(slot, center.x);
    rc.writeSharedArray(slot + 1, center.y + COORDINATE_BLOCK * (round / ROUND_BLOCK));
    rc.writeSharedArray(slot + 2, round % ROUND_BLOCK + 1);
    pendingStamp = 0;
    lastRound = round;
    lastCenter = center;
    return true;
  }

  private static int decodeRound(int stamp, int packedY) {
    return packedY / COORDINATE_BLOCK * ROUND_BLOCK + stamp - 1;
  }

  private static boolean available(RobotController rc, int slot, int now)
      throws GameActionException {
    int stamp = rc.readSharedArray(slot + 2);
    if (stamp > ROUND_BLOCK) {
      int bucketStart = (stamp - ROUND_BLOCK - 1) * PENDING_ROUND_BLOCK;
      return now > bucketStart + PENDING_ROUND_BLOCK;
    }
    return stamp == 0 || !fresh(decodeRound(stamp, rc.readSharedArray(slot + 1)), now);
  }

  private static boolean fresh(int written, int now) {
    // A later-acting king may not have taken its turn yet.
    return written >= 0 && written <= now && now - written <= 1;
  }

  static MapLocation read(RobotController rc, int id, int round) throws GameActionException {
    return read(rc, id, round, true);
  }

  /** Legacy cheese return follows the last committed primary center, regardless of age. */
  static MapLocation readPrimary(RobotController rc) throws GameActionException {
    return read(rc, 0, 0, false);
  }

  private static MapLocation read(RobotController rc, int id, int round, boolean requireFresh)
      throws GameActionException {
    int slot = slot(id);
    int stamp = rc.readSharedArray(slot + 2);
    if (stamp == 0 || stamp > ROUND_BLOCK) {
      return null;
    }
    int packedY = rc.readSharedArray(slot + 1);
    if (requireFresh && !fresh(decodeRound(stamp, packedY), round)) {
      return null;
    }
    int x = rc.readSharedArray(slot);
    int y = packedY % COORDINATE_BLOCK;
    return x < rc.getMapWidth() && y < rc.getMapHeight() ? new MapLocation(x, y) : null;
  }
}
