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

/**
 * Coordinates one chosen baby and six fodder babies without adding an automatic expansion policy.
 *
 * <p>Normal flow: king request -> seven birth squeaks -> chosen selects/occupies a center -> fodder
 * fills assigned tiles -> chosen promotes. RatKing calls the king-side helpers; BabyRat calls
 * assign/run before its ordinary behavior. Each helper returns to RobotPlayer's shared turn loop.
 *
 * <p>Every static field below is robot-local. Only slot 52 is shared: zero means free, a nonzero
 * token reserves the team to one formation. IDs, assignments, and coordinates travel in squeaks or
 * stay local. A dead member/parent or permanently blocked site can leave the reservation held;
 * expiry, replacement, and relocation are not implemented here.
 */
final class KingFormation {
  // A group token, not a robot ID, birth count, or heartbeat; slots 53-63 remain available.
  static final int STATE = 52;
  // BIRTH fits both coordinates in one squeak; its recipient reads the group token from STATE.
  // BIRTH: kind[31:27], index[26:24], rendezvous[23:12], recipient tile[11:0].
  static final int BIRTH = 0x60000000;
  private static final int BIRTH_MASK = 0xf8000000;
  // Other messages: kind[31:25], group[24:15], index[14:12], location[11:0].
  static final int READY = 0x68000000;
  static final int ARRIVED = 0x6a000000;
  static final int HELLO = 0x6c000000;
  static final int ASSIGN = 0x6e000000;
  private static final int KIND_MASK = 0xfe000000;

  // Slots 1-6 map to N, NE, E, SE, S, SW around chosen slot 0. W/NW stay unused.
  private static final int[] DX = {0, 1, 1, 1, 0, -1};
  private static final int[] DY = {1, 1, 0, -1, -1, -1};

  // Recruiting king's state: count only delivered assignments, retaining a failed birth for retry.
  private static boolean requested;
  private static boolean ownsGroup;
  private static int recruited;
  private static int pendingIndex = -1;
  private static int pendingId;

  // Baby's state: keep its original birth tile/turn so later messages cannot reassign an older rat.
  private static boolean assignmentChecked;
  private static int firstTurn = -1;
  private static MapLocation birthLocation;
  // -1 waits for retry metadata, 0 is chosen, 1-6 are fodder; all use BabyRole.SPAWNER.
  static int index = -1;
  static int group;
  private static int parentId;
  // anchor is the chosen's birth rendezvous; center is the committed upgrade footprint's middle.
  static MapLocation anchor;
  static MapLocation center;
  // Chosen's arrival ledger: pin each slot to one sender and require recent reports before merging.
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
    // The previous chosen may have released our reservation on its first king turn.
    if (ownsGroup && rc.readSharedArray(STATE) != group) {
      ownsGroup = false;
    }
    if (requested && !ownsGroup && rc.readSharedArray(STATE) == 0) {
      // Reserve before building. Zero stays available as "free"; the token is not an expiry round.
      group = rc.getRoundNum() % 1023 + 1;
      rc.writeSharedArray(STATE, group);
      ownsGroup = true;
      requested = false;
      recruited = 0;
    }
  }

  /** RatKing checks this before another spawn, even if action cooldown or cheese blocks a birth. */
  static boolean hasPendingBirth() {
    return pendingIndex >= 0;
  }

  /** Retry a failed birth squeak using the full recipient ID, then a metadata response. */
  static boolean flushBirth(RobotController rc) throws GameActionException {
    if (pendingIndex < 0) {
      return true;
    }
    // Accept only a fresh, matching HELLO from the exact invited baby.
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

  /** Called only after buildRat succeeds; failed builds never consume a chosen/fodder index. */
  static void born(RobotController rc, MapLocation location) throws GameActionException {
    // Other kings and this king's eighth/later births keep ordinary baby behavior.
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

  /** Advance only after BIRTH or retry ASSIGN is sent; no acknowledgment is needed on normal births. */
  private static void finishBirth() {
    recruited++;
    pendingIndex = -1;
  }

  /** Only the chosen rat's first king turn releases its matching reservation. */
  static void promoted(RobotController rc) throws GameActionException {
    if (BabyRat.role == BabyRole.SPAWNER && index == 0) {
      // Avoid clearing a different group's reservation if the shared token no longer matches ours.
      if (group != 0 && rc.readSharedArray(STATE) == group) {
        rc.writeSharedArray(STATE, 0);
      }
      BabyRat.role = null;
    }
  }

  /** Accept a birth assignment once, or complete the exact-ID retry before doing baby-role work. */
  static void assign(RobotController rc) throws GameActionException {
    if (assignmentChecked) {
      return;
    }
    if (firstTurn < 0) {
      firstTurn = rc.getRoundNum();
      birthLocation = rc.getLocation();
    }
    int active = rc.readSharedArray(STATE);
    // With no active request, this is an ordinary baby; future requests recruit future newborns.
    if (active == 0) {
      assignmentChecked = true;
      return;
    }
    for (Message message : rc.readSqueaks(-1)) {
      int payload = message.getBytes();
      int slot = (payload >>> 24) & 7;
      // Normal path: only a packet from this baby's birth round/tile may assign its index.
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
      // Retry path: accept metadata only from the parent that invited this exact engine ID.
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
    // HELLO uses Message's sender ID to identify this baby; its payload need not contain the ID.
    if (BabyRat.role == BabyRole.SPAWNER && index < 0) {
      rc.squeak(packet(HELLO, group, 0, rc.getLocation()));
    }
  }

  /** Freeze the baby's role/index; later group messages can supply a center, not a new role. */
  private static void accept(int slot, MapLocation rendezvous) {
    index = slot;
    anchor = rendezvous;
    BabyRat.role = BabyRole.SPAWNER;
    assignmentChecked = true;
  }

  /** Spawner-only turn: wait for metadata, lead the formation, or travel to a fodder tile. */
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
        // Regroup near the birth rendezvous until the chosen or a relay supplies the fixed center.
        Navigation.stepToward(rc, anchor, Config.PathingStrategy.DIST_BUG);
        return;
      }
    }
    MapLocation target = target(center, index);
    Navigation.stepToward(rc, target, Config.PathingStrategy.DIST_BUG);
    // Check the new position before claiming arrival; otherwise forward the center while traveling.
    if (rc.getLocation().equals(target)) {
      rc.squeak(packet(ARRIVED, group, index, center));
    } else {
      rc.squeak(packet(READY, group, index, center));
    }
  }

  /** Select once, occupy the center, then wait for all six members and engine upgrade legality. */
  private static void runChosen(RobotController rc) throws GameActionException {
    if (center == null) {
      // The expensive scoring stays here: committed formations reuse their center on later turns.
      MapInfo[] infos = rc.senseNearbyMapInfos();
      // Registry adds allies outside our cone; sensing also covers visible enemy kings.
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
        // Compare priorities in order, without weights: king separation, openness, distance, x/y.
        // Birth-relative distance and x/y ties make ranking independent of sense-array order.
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
        // Inspect another part of the cone next turn; do not invent a site on unsensed terrain.
        Direction facing = rc.getDirection().rotateRight();
        if (rc.canTurn(facing)) {
          rc.turn(facing);
        }
        return;
      }
    }
    // Announce READY only after the chosen occupies the center.
    Navigation.stepToward(rc, center, Config.PathingStrategy.DIST_BUG);
    if (!rc.getLocation().equals(center)) {
      return;
    }
    // Reannounce every turn so late or separated followers can discover the center through relays.
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
    // Require a fresh report from each assigned sender; old reports are not permanent occupancy.
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
    // Reports coordinate the group; the engine still decides cheese, cooldown, footprint, and caps.
    // Return through BabyRat afterward; next-turn current-type dispatch runs normal king behavior.
    if (rc.canBecomeRatKing()) {
      rc.becomeRatKing();
    }
  }

  /** All nine tiles must be known, passable, and empty except for the chosen rat itself. */
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
    // Chebyshev distance matches square footprints; maximize the distance to the nearest king.
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

  /** Stable indices prevent followers from repeatedly competing for whichever tile looks empty. */
  static MapLocation target(MapLocation origin, int slot) {
    return slot == 0 ? origin : origin.translate(DX[slot - 1], DY[slot - 1]);
  }

  // A coordinate is x[5:0], y[11:6]; six bits per axis cover the engine's 60x60 maximum map.
  static int pack(MapLocation location) {
    return location.x | location.y << 6;
  }

  static MapLocation unpack(int payload) {
    return new MapLocation(payload & 63, (payload >>> 6) & 63);
  }

  /** Encode READY/ARRIVED/HELLO/ASSIGN; BIRTH has its own two-coordinate layout above. */
  static int packet(int kind, int token, int slot, MapLocation location) {
    return kind | token << 15 | slot << 12 | pack(location);
  }

  private static int messageGroup(int payload) {
    return (payload >>> 15) & 1023;
  }

  // Earlier robots act before later members: last round's report must remain usable this round.
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
