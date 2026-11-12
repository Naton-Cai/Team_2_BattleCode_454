package ratplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import ratplayer.navigation.Navigation;

/** One explicitly requested formation; only shared slot 52 is reserved. */
final class KingFormation {
  static final int STATE = 52;
  // BIRTH: kind[31:27], index[26:24], rendezvous[23:12], recipient tile[11:0].
  static final int BIRTH = 0x60000000;
  private static final int BIRTH_MASK = 0xf8000000;
  // Other messages: kind[31:25], group[24:15], index[14:12], location[11:0].
  static final int READY = 0x68000000;
  static final int ARRIVED = 0x6a000000;
  static final int HELLO = 0x6c000000;
  static final int ASSIGN = 0x6e000000;
  private static final int KIND_MASK = 0xfe000000;
  private static final int[] DX = {0, 1, 1, 1, 0, -1};
  private static final int[] DY = {1, 1, 0, -1, -1, -1};

  private static boolean requested;
  private static boolean ownsGroup;
  private static int recruited;
  private static int pendingIndex = -1;
  private static int pendingId;
  private static boolean assignmentChecked;
  private static int firstTurn = -1;
  private static MapLocation birthLocation;
  static int index = -1;
  static int group;
  private static int parentId;
  static MapLocation anchor;
  static MapLocation center;
  private static final int[] followerIds = new int[6];
  private static final int[] arrivalRounds = new int[6];

  private KingFormation() {}

  /** Arms this king only. No gameplay policy calls this automatically. */
  static void request() {
    if (!ownsGroup || recruited == 7) {
      requested = true;
    }
  }

  /** A nonzero token locks the team to one group. All other metadata stays robot-local. */
  static void prepare(RobotController rc) throws GameActionException {
    if (ownsGroup && rc.readSharedArray(STATE) != group) {
      ownsGroup = false;
    }
    if (requested && !ownsGroup && rc.readSharedArray(STATE) == 0) {
      group = rc.getRoundNum() % 1023 + 1;
      rc.writeSharedArray(STATE, group);
      ownsGroup = true;
      requested = false;
      recruited = 0;
    }
  }

  static boolean hasPendingBirth() {
    return pendingIndex >= 0;
  }

  /** Retry a failed birth squeak using the full recipient ID, then a metadata response. */
  static boolean flushBirth(RobotController rc) throws GameActionException {
    if (pendingIndex < 0) {
      return true;
    }
    for (Message message : rc.readSqueaks(-1)) {
      if ((message.getBytes() & KIND_MASK) == HELLO
          && messageGroup(message.getBytes()) == group
          && message.getSenderID() == pendingId
          && fresh(rc, message)
          && rc.getLocation().distanceSquaredTo(message.getSource())
              <= GameConstants.SQUEAK_RADIUS_SQUARED) {
        // The invited baby waits in place, so this current/previous-round source is still valid.
        if (rc.squeak(packet(ASSIGN, group, pendingIndex, anchor))) {
          finishBirth();
          return true;
        }
        return false;
      }
    }
    // Negative packets are addressed invitations: sign bit + all 31 positive engine-ID bits.
    rc.squeak(Integer.MIN_VALUE | pendingId);
    return false;
  }

  static void born(RobotController rc, MapLocation location) throws GameActionException {
    if (!ownsGroup || recruited >= 7) {
      return;
    }
    pendingIndex = recruited;
    pendingId = rc.senseRobotAtLocation(location).getID();
    if (pendingIndex == 0) {
      anchor = location;
    }
    // Sent immediately after build: the newborn is inside squeak range and receives this packet.
    if (rc.squeak(BIRTH | pendingIndex << 24 | pack(anchor) << 12 | pack(location))) {
      finishBirth();
    }
  }

  private static void finishBirth() {
    recruited++;
    pendingIndex = -1;
  }

  /** Only the chosen rat's first king turn releases its matching reservation. */
  static void promoted(RobotController rc) throws GameActionException {
    if (BabyRat.role == BabyRole.SPAWNER && index == 0) {
      if (group != 0 && rc.readSharedArray(STATE) == group) {
        rc.writeSharedArray(STATE, 0);
      }
      BabyRat.role = null;
    }
  }

  static void assign(RobotController rc) throws GameActionException {
    if (assignmentChecked) {
      return;
    }
    if (firstTurn < 0) {
      firstTurn = rc.getRoundNum();
      birthLocation = rc.getLocation();
    }
    int active = rc.readSharedArray(STATE);
    if (active == 0) {
      assignmentChecked = true;
      return;
    }
    for (Message message : rc.readSqueaks(-1)) {
      int payload = message.getBytes();
      int slot = (payload >>> 24) & 7;
      if ((payload & BIRTH_MASK) == BIRTH
          && slot <= 6
          && message.getRound() == firstTurn - 1
          && unpack(payload).equals(birthLocation)
          && message.getSource().distanceSquaredTo(birthLocation)
              <= GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED) {
        group = active;
        parentId = message.getSenderID();
        accept(slot, unpack(payload >>> 12));
        return;
      }
      if (payload < 0 && (payload & Integer.MAX_VALUE) == rc.getID() && fresh(rc, message)) {
        // Exact-ID retry can recover even if this newborn already took an ordinary baby turn.
        parentId = message.getSenderID();
        group = active;
        BabyRat.role = BabyRole.SPAWNER;
      }
      if ((payload & KIND_MASK) == ASSIGN
          && index < 0
          && BabyRat.role == BabyRole.SPAWNER
          && message.getSenderID() == parentId
          && messageGroup(payload) == group
          && group == active
          && fresh(rc, message)) {
        slot = (payload >>> 12) & 7;
        if (slot <= 6) {
          accept(slot, unpack(payload));
          return;
        }
      }
    }
    if (BabyRat.role == BabyRole.SPAWNER && index < 0) {
      rc.squeak(packet(HELLO, group, 0, rc.getLocation()));
    }
  }

  private static void accept(int slot, MapLocation rendezvous) {
    index = slot;
    anchor = rendezvous;
    BabyRat.role = BabyRole.SPAWNER;
    assignmentChecked = true;
  }

  static void run(RobotController rc) throws GameActionException {
    if (index < 0) {
      return; // An explicitly invited retry recipient waits for its parent's metadata.
    }
    if (index == 0) {
      runChosen(rc);
      return;
    }
    if (center == null) {
      for (Message message : rc.readSqueaks(-1)) {
        int payload = message.getBytes();
        int kind = payload & KIND_MASK;
        int slot = (payload >>> 12) & 7;
        // Only one group is active. Traveling members forward READY; settled members' existing
        // ARRIVED messages also advertise the center, without another squeak or shared record.
        boolean leader = kind == READY && slot == 0;
        boolean member =
            slot >= 1
                && slot <= 6
                && (kind == READY
                    || (kind == ARRIVED
                        && message.getSource().equals(target(unpack(payload), slot))));
        if ((leader || member) && messageGroup(payload) == group && fresh(rc, message)) {
          center = unpack(message.getBytes());
          break;
        }
      }
      if (center == null) {
        Navigation.stepToward(rc, anchor, Config.PathingStrategy.DIST_BUG);
        return;
      }
    }
    MapLocation target = target(center, index);
    Navigation.stepToward(rc, target, Config.PathingStrategy.DIST_BUG);
    if (rc.getLocation().equals(target)) {
      rc.squeak(packet(ARRIVED, group, index, center));
    } else {
      rc.squeak(packet(READY, group, index, center));
    }
  }

  private static void runChosen(RobotController rc) throws GameActionException {
    if (center == null) {
      MapInfo[] infos = rc.senseNearbyMapInfos();
      MapLocation[] kings = new MapLocation[KingRegistry.CAPACITY];
      for (int id = 0; id < kings.length; id++) {
        kings[id] = KingRegistry.read(rc, id, rc.getRoundNum());
      }
      RobotInfo[] robots = rc.senseNearbyRobots();
      int bestClearance = -1;
      int bestOpen = -1;
      int best = Integer.MAX_VALUE;
      for (MapInfo info : infos) {
        MapLocation candidate = info.getMapLocation();
        int distance = anchor.distanceSquaredTo(candidate);
        // Keep the ready broadcast reachable from the fixed birth rendezvous (squeak r²=16).
        if (distance > GameConstants.SQUEAK_RADIUS_SQUARED || !validSite(rc, candidate)) {
          continue;
        }
        int clearance = kingClearance(candidate, kings, robots);
        int open = openApproachTiles(candidate, infos);
        if (center != null) {
          if (clearance != bestClearance) {
            if (clearance < bestClearance) {
              continue;
            }
          } else if (open != bestOpen) {
            if (open < bestOpen) {
              continue;
            }
          } else if (distance != best) {
            if (distance > best) {
              continue;
            }
          } else if (candidate.x > center.x
              || (candidate.x == center.x && candidate.y >= center.y)) {
            continue;
          }
        }
        center = candidate;
        best = distance;
        bestClearance = clearance;
        bestOpen = open;
      }
      if (center == null) {
        Direction facing = rc.getDirection().rotateRight();
        if (rc.canTurn(facing)) {
          rc.turn(facing);
        }
        return;
      }
    }
    Navigation.stepToward(rc, center, Config.PathingStrategy.DIST_BUG);
    if (!rc.getLocation().equals(center)) {
      return;
    }
    rc.squeak(packet(READY, group, 0, center));
    for (Message message : rc.readSqueaks(-1)) {
      int payload = message.getBytes();
      int slot = (payload >>> 12) & 7;
      if ((payload & KIND_MASK) == ARRIVED
          && slot >= 1
          && slot <= 6
          && unpack(payload).equals(center)
          && message.getSource().equals(target(center, slot))
          && messageGroup(payload) == group
          && fresh(rc, message)
          && (followerIds[slot - 1] == 0 || followerIds[slot - 1] == message.getSenderID())) {
        followerIds[slot - 1] = message.getSenderID();
        arrivalRounds[slot - 1] = message.getRound();
      }
    }
    for (int i = 0; i < 6; i++) {
      if (followerIds[i] == 0 || arrivalRounds[i] < rc.getRoundNum() - 1) {
        return;
      }
    }
    // Do not knowingly absorb a nonmember on either unused tile (spec v1.2.6 p.14).
    for (RobotInfo robot : rc.senseNearbyRobots()) {
      if (Math.abs(robot.getLocation().x - center.x) > 1
          || Math.abs(robot.getLocation().y - center.y) > 1) {
        continue;
      }
      boolean member = false;
      for (int id : followerIds) {
        member |= id == robot.getID();
      }
      if (!member) {
        return;
      }
    }
    if (rc.canBecomeRatKing()) {
      rc.becomeRatKing();
    }
  }

  private static boolean validSite(RobotController rc, MapLocation candidate)
      throws GameActionException {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        MapLocation tile = candidate.translate(dx, dy);
        if (!rc.onTheMap(tile)
            || !rc.canSenseLocation(tile)
            || !rc.senseMapInfo(tile).isPassable()) {
          return false;
        }
        RobotInfo robot = rc.senseRobotAtLocation(tile);
        if (robot != null && robot.getID() != rc.getID()) {
          return false;
        }
      }
    }
    return true;
  }

  /** Prefer the greatest available separation from known king footprints. */
  private static int kingClearance(MapLocation candidate, MapLocation[] kings, RobotInfo[] robots) {
    int clearance = Integer.MAX_VALUE;
    for (MapLocation king : kings) {
      if (king != null) {
        clearance =
            Math.min(
                clearance,
                Math.max(Math.abs(candidate.x - king.x), Math.abs(candidate.y - king.y)));
      }
    }
    for (RobotInfo robot : robots) {
      if (robot.getType() == UnitType.RAT_KING) {
        clearance =
            Math.min(
                clearance,
                Math.max(
                    Math.abs(candidate.x - robot.getLocation().x),
                    Math.abs(candidate.y - robot.getLocation().y)));
      }
    }
    return clearance;
  }

  /** Count only known passable perimeter tiles; unseen terrain is not evidence of an approach. */
  private static int openApproachTiles(MapLocation candidate, MapInfo[] infos) {
    int count = 0;
    for (MapInfo info : infos) {
      MapLocation tile = info.getMapLocation();
      if (info.isPassable()
          && Math.max(Math.abs(candidate.x - tile.x), Math.abs(candidate.y - tile.y)) == 2) {
        count++;
      }
    }
    return count;
  }

  static MapLocation target(MapLocation origin, int slot) {
    return slot == 0 ? origin : origin.translate(DX[slot - 1], DY[slot - 1]);
  }

  static int pack(MapLocation location) {
    return location.x | location.y << 6;
  }

  static MapLocation unpack(int payload) {
    return new MapLocation(payload & 63, (payload >>> 6) & 63);
  }

  static int packet(int kind, int token, int slot, MapLocation location) {
    return kind | token << 15 | slot << 12 | pack(location);
  }

  private static int messageGroup(int payload) {
    return (payload >>> 15) & 1023;
  }

  private static boolean fresh(RobotController rc, Message message) {
    return message.getRound() >= rc.getRoundNum() - 1 && message.getRound() <= rc.getRoundNum();
  }

  /** Reset robot-local state between host-side controller scenarios. */
  static void reset() {
    requested = false;
    ownsGroup = false;
    recruited = 0;
    pendingIndex = -1;
    assignmentChecked = false;
    firstTurn = -1;
    birthLocation = null;
    index = -1;
    group = 0;
    parentId = 0;
    anchor = null;
    center = null;
    java.util.Arrays.fill(followerIds, 0);
    java.util.Arrays.fill(arrivalRounds, 0);
    BabyRat.role = null;
  }
}
