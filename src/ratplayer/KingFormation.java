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
 * Spawning a new rat king on request.
 *
 * <p>How it works:
 *
 * <ol>
 *   <li>A king calls {@link #request()}. Its next baby is born as the "chosen".
 *   <li>The chosen picks a 3x3 spot (the "site"), stands in the middle, and squeaks RECRUIT every
 *       turn.
 *   <li>Normal babies that hear it become "fodder": they walk to any free tile around the middle
 *       and wait there.
 *   <li>Every turn the chosen asks the engine whether it can become a king. The engine checks the
 *       7 rats, 50 cheese and empty footprint for us (spec v1.2.6 p.14), so we never count.
 * </ol>
 *
 * <p>Uses no shared-array slots. Every static field below belongs to one robot only; robots share
 * information only through squeaks.
 */
final class KingFormation {
  // ---- Squeak format ----
  // A squeak is a message tag plus a packed tile: tile x in bits 0-5, tile y in bits 6-11.
  private static final int TILE_MASK = (1 << 12) - 1;

  /** King to newborn: "the baby born on this tile is the chosen." */
  static final int CHOSEN = 1 << 12;

  /** Chosen to everyone nearby: "come fill the tiles around this site." */
  static final int RECRUIT = 2 << 12;

  // ---- Geometry ----
  // Two 3x3 squares overlap when their centers are this many steps apart or fewer.
  private static final int OVERLAP = 2;
  private static final Direction[] NORTH_EAST_SOUTH_WEST = {
    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
  };

  // Kings and cats the chosen must keep its site away from; refilled every chosen turn.
  private static final int MAX_HAZARDS = 16;
  private static final MapLocation[] hazards = new MapLocation[MAX_HAZARDS];
  private static int hazardCount;

  // ---- King state ----
  private static boolean requested; // The next baby this king builds becomes the chosen.
  private static int retriesLeft; // Automatic re-requests left if the chosen dies.
  private static int watchedId; // The chosen this king is watching, or 0.
  private static int watchedBirth; // Round the watched chosen was born.
  private static int lastSeen; // Last round this king could see the chosen.
  private static MapLocation lastSeenAt; // Where it was then.

  // ---- Baby state (chosen and fodder) ----
  private static boolean birthChecked; // Each baby checks for a CHOSEN squeak only on its 1st turn.
  static MapLocation site; // Middle of the 3x3 being built.
  private static int chosenSince; // Chosen: round it became the chosen.
  private static int siteRound; // Chosen: round it picked the current site.
  private static MapLocation rejectedSite; // Chosen: last site it gave up on; skipped once.
  private static int leaderId; // Fodder: the chosen it is following.
  private static int heardRound; // Fodder: last round it heard that chosen.
  private static int ignoredId; // A chosen whose ring was already full,
  private static int ignoreUntil; // ignored until this round.

  private KingFormation() {}

  // =============================== King side ===============================

  /** Make this king's next baby the chosen. Not called by any strategy code yet. */
  static void request() {
    requested = true;
    retriesLeft = Config.FORMATION_RETRIES;
  }

  /** While a request is waiting, the king may build one baby past the normal baby cap. */
  static boolean requested() {
    return requested;
  }

  /** Called right after the king builds a baby: tell it that it is the chosen. */
  static void born(RobotController rc, MapLocation location) throws GameActionException {
    // If the squeak fails (one squeak per turn), keep the request for the next baby.
    if (requested && rc.squeak(CHOSEN | pack(location))) {
      requested = false;
      watchedId = rc.senseRobotAtLocation(location).getID();
      watchedBirth = rc.getRoundNum();
      lastSeen = watchedBirth;
      lastSeenAt = location;
    }
  }

  /**
   * Every king turn: if the chosen clearly died, request a new one (at most FORMATION_RETRIES
   * times).
   *
   * <p>"Clearly died" means: last round the king saw it close by, and now it is gone. A baby moves
   * one tile per round, so from that close a living chosen would still be in the king's view. If it
   * was farther away, it may just have walked out of view, so we do nothing; the chosen's own
   * timeout handles that case.
   */
  static void watch(RobotController rc) throws GameActionException {
    if (watchedId == 0) {
      return;
    }
    int now = rc.getRoundNum();
    if (rc.canSenseRobot(watchedId)) {
      RobotInfo chosen = rc.senseRobot(watchedId);
      boolean becameKing = chosen.getType() == UnitType.RAT_KING;
      boolean gaveUp = now - watchedBirth > Config.FORMATION_TIMEOUT_ROUNDS;
      if (becameKing || gaveUp) {
        watchedId = 0;
      } else {
        lastSeen = now;
        lastSeenAt = chosen.getLocation();
      }
      return;
    }
    boolean seenLastRound = lastSeen == now - 1;
    boolean wasClose =
        rc.getLocation().distanceSquaredTo(lastSeenAt) <= Config.FORMATION_LOST_DISTANCE_SQUARED;
    if (!seenLastRound || !wasClose) {
      return; // It may just have walked out of view.
    }
    watchedId = 0;
    if (retriesLeft > 0) {
      retriesLeft--;
      requested = true;
    }
  }

  // ========================= Baby side: role changes =========================

  /** Start of every baby turn: become the chosen, join or follow a chosen, or quit. */
  static void assign(RobotController rc) throws GameActionException {
    int now = rc.getRoundNum();
    if (!birthChecked) {
      birthChecked = true;
      if (heardChosenSqueak(rc, now)) {
        BabyRat.role = BabyRole.CHOSEN;
        chosenSince = now;
        return;
      }
    }
    if (BabyRat.role == BabyRole.CHOSEN) {
      boolean timedOut = now - chosenSince > Config.FORMATION_TIMEOUT_ROUNDS;
      if (timedOut || kingLimitReached(rc)) {
        release(); // Back to normal baby work this same turn.
      }
      return;
    }
    listen(rc, now);
  }

  /** A newborn has not moved yet, so a CHOSEN squeak naming its tile can only mean this baby. */
  private static boolean heardChosenSqueak(RobotController rc, int now) {
    MapLocation here = rc.getLocation();
    for (Message message : rc.readSqueaks(-1)) {
      int payload = message.getBytes();
      if (tagOf(payload) == CHOSEN
          && isRecent(now, message)
          && unpack(payload).equals(here)
          && message.getSource().distanceSquaredTo(here)
              <= GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED) {
        return true;
      }
    }
    return false;
  }

  /** Normal babies join the first chosen they hear; fodder keep up with their chosen's site. */
  private static void listen(RobotController rc, int now) {
    for (Message message : rc.readSqueaks(-1)) {
      int payload = message.getBytes();
      if (tagOf(payload) != RECRUIT || !isRecent(now, message)) {
        continue;
      }
      int sender = message.getSenderID();
      boolean ignored = sender == ignoredId && now < ignoreUntil;
      if (BabyRat.role == null && !ignored) {
        BabyRat.role = BabyRole.FODDER;
        leaderId = sender;
      }
      if (sender == leaderId && message.getRound() >= heardRound) {
        site = unpack(payload); // The chosen may have moved to a new site.
        heardRound = message.getRound();
      }
    }
    // The chosen went quiet: it became a king, died or gave up. Go back to normal work.
    if (BabyRat.role == BabyRole.FODDER && now - heardRound > Config.FORMATION_LISTEN_ROUNDS) {
      release();
    }
  }

  // =============================== Fodder turn ===============================

  /** Wait on a tile next to the site, or walk to the nearest free one. Leave if none are needed. */
  static void runFodder(RobotController rc) throws GameActionException {
    MapLocation here = rc.getLocation();
    if (steps(here, site) == 1) {
      return; // Already next to the site: wait. The chosen decides when there are enough.
    }
    MapLocation nearestFree = null;
    int nearestDistance = Integer.MAX_VALUE;
    int alliesWaiting = 0;
    for (Direction direction : RobotPlayer.directions) {
      MapLocation tile = site.add(direction);
      if (!rc.onTheMap(tile)) {
        continue;
      }
      if (rc.canSenseLocation(tile)) {
        if (!rc.sensePassability(tile)) {
          continue;
        }
        if (rc.isLocationOccupied(tile)) {
          RobotInfo robot = rc.senseRobotAtLocation(tile);
          if (robot.getTeam() == rc.getTeam() && robot.getType() == UnitType.BABY_RAT) {
            alliesWaiting++;
          }
          continue;
        }
      }
      // A tile we cannot see yet still counts as free; we will see it as we get closer.
      int distance = here.distanceSquaredTo(tile);
      if (distance < nearestDistance) {
        nearestFree = tile;
        nearestDistance = distance;
      }
    }
    // Enough babies are already waiting (more would just be absorbed), or there is no free tile.
    if (nearestFree == null || alliesWaiting >= Config.FORMATION_FODDER_NEEDED) {
      ignoredId = leaderId;
      ignoreUntil = rc.getRoundNum() + Config.FORMATION_IGNORE_ROUNDS;
      release();
      return;
    }
    Navigation.stepToward(rc, nearestFree, Config.PathingStrategy.DIST_BUG);
  }

  // =============================== Chosen turn ===============================

  /** Keep a usable site, walk onto it, become a king as soon as the engine allows, and recruit. */
  static void runChosen(RobotController rc) throws GameActionException {
    // Try first: babies may already surround the chosen before it reaches its site.
    if (tryBecomeKing(rc)) {
      return;
    }
    int now = rc.getRoundNum();
    collectHazards(rc);
    if (siteFailed(rc, now)) {
      rejectedSite = site;
      site = null; // Fodder follow the new site from the next RECRUIT squeak.
    }
    if (site == null) {
      site = selectSite(rc);
      siteRound = now;
      if (site == null) {
        explore(rc);
        return; // Nothing to advertise yet, so stay quiet.
      }
    }
    if (!rc.getLocation().equals(site)) {
      Navigation.stepToward(rc, site, Config.PathingStrategy.DIST_BUG);
    }
    if (tryBecomeKing(rc)) {
      return;
    }
    if (rc.getLocation().equals(site)) {
      sweepCone(rc); // Look around so a newly blocked tile is noticed.
    }
    rc.squeak(RECRUIT | pack(site));
  }

  /** The engine checks everything (rats, cheese, footprint, king limits) for 10 bytecodes. */
  private static boolean tryBecomeKing(RobotController rc) throws GameActionException {
    if (!rc.canBecomeRatKing()) {
      return false;
    }
    rc.becomeRatKing();
    release(); // Next turn this robot runs as a king; drop the baby state now.
    return true;
  }

  /** Give up the current site if it is blocked, unreachable, or has taken too long. */
  private static boolean siteFailed(RobotController rc, int now) throws GameActionException {
    if (site == null) {
      return false;
    }
    int roundsOnSite = now - siteRound;
    boolean cannotReach =
        !rc.getLocation().equals(site) && roundsOnSite > Config.FORMATION_TRAVEL_ROUNDS;
    return blocked(rc, site) || roundsOnSite > Config.FORMATION_SITE_ROUNDS || cannotReach;
  }

  /**
   * No site in view. Step away from the nearest king or cat until far enough, then walk around it
   * while turning to look. Walking around (not away) keeps the chosen near other babies, which are
   * the only recruits it can get.
   */
  private static void explore(RobotController rc) throws GameActionException {
    MapLocation here = rc.getLocation();
    MapLocation nearest = null;
    for (int i = 0; i < hazardCount; i++) {
      if (nearest == null || here.distanceSquaredTo(hazards[i]) < here.distanceSquaredTo(nearest)) {
        nearest = hazards[i];
      }
    }
    Direction heading = nearest == null ? Direction.CENTER : nearest.directionTo(here);
    if (heading == Direction.CENTER) {
      heading = rc.getDirection();
    } else if (steps(here, nearest) > Config.FORMATION_KING_CLEARANCE) {
      heading = heading.rotateLeft().rotateLeft(); // Far enough: go sideways around it.
    }
    Navigation.stepToward(rc, here.add(heading).add(heading), Config.PathingStrategy.GREEDY);
    sweepCone(rc);
  }

  /** Babies only see a 90-degree cone (spec p.10); turn 90 degrees to look somewhere new. */
  private static void sweepCone(RobotController rc) throws GameActionException {
    Direction next = rc.getDirection().rotateRight().rotateRight();
    if (rc.canTurn(next)) {
      rc.turn(next);
    }
  }

  // ============================== Site selection ==============================

  /** The closest usable site the chosen can currently see, or null. */
  private static MapLocation selectSite(RobotController rc) throws GameActionException {
    MapLocation here = rc.getLocation();
    MapLocation best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (MapInfo info : rc.senseNearbyMapInfos()) {
      MapLocation candidate = info.getMapLocation();
      int distance = here.distanceSquaredTo(candidate);
      // Distance check first: it is cheap and skips most tiles.
      if (!isCloser(candidate, distance, best, bestDistance) || !isUsableSite(rc, candidate)) {
        continue;
      }
      best = candidate;
      bestDistance = distance;
    }
    return best;
  }

  /** Closer wins; ties go to the smaller x, then y, so the result never depends on scan order. */
  private static boolean isCloser(
      MapLocation candidate, int distance, MapLocation best, int bestDistance) {
    if (distance != bestDistance) {
      return distance < bestDistance;
    }
    return candidate.x < best.x || (candidate.x == best.x && candidate.y < best.y);
  }

  private static boolean isUsableSite(RobotController rc, MapLocation center)
      throws GameActionException {
    return !center.equals(rejectedSite)
        && !nearHazard(center, Config.FORMATION_KING_CLEARANCE)
        && notAgainstMapEdge(rc, center)
        && footprintClear(rc, center, true);
  }

  /**
   * The tiles two steps north, east, south and west of the middle must be on the map. Each one
   * touches three of the eight waiting tiles, so every waiting tile can be reached from outside.
   * Against the map edge, the first babies to arrive can wall off the rest (seen on evileye).
   */
  private static boolean notAgainstMapEdge(RobotController rc, MapLocation center) {
    for (Direction direction : NORTH_EAST_SOUTH_WEST) {
      if (!rc.onTheMap(center.add(direction).add(direction))) {
        return false;
      }
    }
    return true;
  }

  /**
   * All nine tiles are on the map and have no wall or dirt. With {@code mustSeeAll}, every tile
   * must be visible now; without it, tiles we cannot see are given the benefit of the doubt.
   */
  private static boolean footprintClear(RobotController rc, MapLocation center, boolean mustSeeAll)
      throws GameActionException {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        MapLocation tile = center.translate(dx, dy);
        if (!rc.onTheMap(tile)) {
          return false;
        }
        if (rc.canSenseLocation(tile)) {
          if (!rc.sensePassability(tile)) {
            return false;
          }
        } else if (mustSeeAll) {
          return false;
        }
      }
    }
    return true;
  }

  /** The site now has a wall or dirt we can see, or a king or cat overlaps it. */
  private static boolean blocked(RobotController rc, MapLocation center)
      throws GameActionException {
    return nearHazard(center, OVERLAP) || !footprintClear(rc, center, false);
  }

  // ============================ Kings and cats nearby ============================

  /** Our kings (from KingRegistry) plus any king or cat the chosen can see. */
  private static void collectHazards(RobotController rc) throws GameActionException {
    loadKings(rc);
    for (RobotInfo robot : rc.senseNearbyRobots()) {
      boolean hazard = robot.getType() == UnitType.RAT_KING || robot.getType().isCatType();
      if (hazard && hazardCount < MAX_HAZARDS) {
        hazards[hazardCount++] = robot.getLocation();
      }
    }
  }

  /** Reset the hazard list to our live kings and return how many there are. */
  private static int loadKings(RobotController rc) throws GameActionException {
    hazardCount = 0;
    int now = rc.getRoundNum();
    for (int id = 0; id < KingRegistry.CAPACITY; id++) {
      MapLocation king = KingRegistry.read(rc, id, now);
      if (king != null) {
        hazards[hazardCount++] = king;
      }
    }
    return hazardCount;
  }

  private static boolean nearHazard(MapLocation center, int minSteps) {
    for (int i = 0; i < hazardCount; i++) {
      if (steps(center, hazards[i]) <= minSteps) {
        return true;
      }
    }
    return false;
  }

  /** The engine allows at most 5 kings, or 2 after round 1200 (spec v1.2.6 p.14, p.25). */
  private static boolean kingLimitReached(RobotController rc) throws GameActionException {
    int kings = loadKings(rc);
    return kings >= GameConstants.MAX_NUMBER_OF_RAT_KINGS
        || (rc.getRoundNum() > GameConstants.RAT_KING_CUTOFF_ROUND
            && kings >= GameConstants.MAX_NUMBER_OF_RAT_KINGS_AFTER_CUTOFF);
  }

  // ================================= Helpers =================================

  /** King steps between two tiles (diagonal moves count as one step). */
  private static int steps(MapLocation a, MapLocation b) {
    return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
  }

  /** Sent this round or last round. Robots that act earlier in a round squeak "last round". */
  private static boolean isRecent(int now, Message message) {
    return message.getRound() >= now - 1 && message.getRound() <= now;
  }

  private static int tagOf(int payload) {
    return payload & ~TILE_MASK;
  }

  /** Six bits per coordinate fit the largest 60x60 map (spec p.6). */
  static int pack(MapLocation location) {
    return location.x | location.y << 6;
  }

  static MapLocation unpack(int payload) {
    return new MapLocation(payload & 63, (payload >>> 6) & 63);
  }

  /** Go back to being a normal baby. Raw cheese and the ignore window are kept. */
  static void release() {
    BabyRat.role = null;
    site = null;
    rejectedSite = null;
    leaderId = 0;
    heardRound = 0;
    Navigation.resetFollow();
  }

  /** Clear all state; for tests that reuse the same JVM. */
  static void reset() {
    release();
    requested = false;
    retriesLeft = 0;
    watchedId = 0;
    watchedBirth = 0;
    lastSeen = 0;
    lastSeenAt = null;
    birthChecked = false;
    chosenSince = 0;
    siteRound = 0;
    ignoredId = 0;
    ignoreUntil = 0;
    hazardCount = 0;
  }
}
