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
 * Explicitly requested king formation: one chosen birth recruits nearby babies by squeak.
 *
 * <p>Flow: {@link #request()} arms one king -> its next birth is told it is chosen -> the chosen
 * picks a 3x3 site, stands on its center and squeaks RECRUIT every turn -> ordinary babies that
 * hear it fill any free ring tile -> the chosen promotes once the engine allows it. The engine's
 * {@code canBecomeRatKing} is the only completion check (spec v1.2.6 p.14), so no IDs, slot grants
 * or acknowledgments are needed. Uses no shared-array slots; every static is robot-local.
 */
final class KingFormation {
  // Payload: kind[31:12], location x[5:0] y[11:6]. Squeaks reach allies and cats only (p.18).
  static final int CHOSEN = 0x43000000;
  static final int RECRUIT = 0x52000000;
  private static final int KIND_MASK = 0xfffff000;

  // A 3x3 overlaps a king or cat footprint within this Chebyshev distance of its center.
  private static final int OVERLAP = 2;
  private static final int MAX_HAZARDS = 16;
  private static final MapLocation[] hazards = new MapLocation[MAX_HAZARDS];
  private static int hazardCount;

  // King: the next successful birth becomes the chosen; then watch it and re-arm if it vanishes.
  private static boolean requested;
  private static int retriesLeft;
  private static int watchedId;
  private static int watchedBirth;
  private static int lastSeen;
  private static MapLocation lastSeenAt;

  // Baby: birth check runs once; site is the center the chosen occupies or fodder surrounds.
  private static boolean birthChecked;
  static MapLocation site;
  private static int startRound;
  private static int siteRound;
  private static MapLocation rejectedSite;
  private static int leaderId;
  private static int heardRound;
  private static int ignoredId;
  private static int ignoreUntil;

  private KingFormation() {}

  /** Arms this king only. No gameplay policy calls this yet; the team decides when to attempt. */
  static void request() {
    requested = true;
    retriesLeft = Config.FORMATION_RETRIES;
  }

  /** The armed king's next birth may exceed the ordinary baby cap (reserve still applies). */
  static boolean requested() {
    return requested;
  }

  /** After a successful build: tell the newborn it is chosen; keep the request if squeak fails. */
  static void born(RobotController rc, MapLocation location) throws GameActionException {
    if (requested && rc.squeak(CHOSEN | pack(location))) {
      requested = false;
      watchedId = rc.senseRobotAtLocation(location).getID();
      watchedBirth = rc.getRoundNum();
      lastSeen = watchedBirth;
      lastSeenAt = location;
    }
  }

  /**
   * King turn: re-arm the request when the chosen provably died, at most {@link
   * Config#FORMATION_RETRIES} times per request. A baby moves at most one tile per round, so if the
   * king saw it last round within r^2 = 12 of its center (sqrt 12 + sqrt 2 < 5), a live chosen is
   * still inside king vision (r^2 = 25, spec v1.2.6 p.14). Farther out absence is ambiguous: sites
   * sit at the vision edge, so a healthy chosen is often unseen; the chosen's own timeout covers it.
   */
  static void watch(RobotController rc) throws GameActionException {
    if (watchedId == 0) {
      return;
    }
    int now = rc.getRoundNum();
    if (rc.canSenseRobot(watchedId)) {
      RobotInfo chosen = rc.senseRobot(watchedId);
      // Promoted (same ID, now a king): done. Alive past its own give-up deadline: stop watching.
      if (chosen.getType() == UnitType.RAT_KING
          || now - watchedBirth > Config.FORMATION_TIMEOUT_ROUNDS) {
        watchedId = 0;
      } else {
        lastSeen = now;
        lastSeenAt = chosen.getLocation();
      }
      return;
    }
    if (lastSeen != now - 1
        || rc.getLocation().distanceSquaredTo(lastSeenAt)
            > Config.FORMATION_LOST_DISTANCE_SQUARED) {
      return; // Out of view or never close: unknown, not dead.
    }
    watchedId = 0;
    if (retriesLeft > 0) {
      retriesLeft--;
      requested = true;
    }
  }

  /** Before role dispatch: accept a chosen birth, join a recruiter, or release on timeouts. */
  static void assign(RobotController rc) throws GameActionException {
    int now = rc.getRoundNum();
    if (!birthChecked) {
      birthChecked = true;
      MapLocation here = rc.getLocation();
      for (Message message : rc.readSqueaks(-1)) {
        int payload = message.getBytes();
        // Newborns have not moved, so the packed birth tile identifies exactly one robot.
        if ((payload & KIND_MASK) == CHOSEN
            && fresh(now, message)
            && unpack(payload).equals(here)
            && message.getSource().distanceSquaredTo(here)
                <= GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED) {
          BabyRat.role = BabyRole.CHOSEN;
          startRound = now;
          return;
        }
      }
    }
    if (BabyRat.role == BabyRole.CHOSEN) {
      if (now - startRound > Config.FORMATION_TIMEOUT_ROUNDS || kingLimitReached(rc)) {
        release();
      }
      return;
    }
    listen(rc, now);
  }

  /** Ordinary babies join the first recruiter heard; fodder tracks its leader's site. */
  private static void listen(RobotController rc, int now) {
    for (Message message : rc.readSqueaks(-1)) {
      int payload = message.getBytes();
      if ((payload & KIND_MASK) != RECRUIT || !fresh(now, message)) {
        continue;
      }
      int sender = message.getSenderID();
      if (BabyRat.role == null && (sender != ignoredId || now >= ignoreUntil)) {
        BabyRat.role = BabyRole.FODDER;
        leaderId = sender;
      }
      if (sender == leaderId && message.getRound() >= heardRound) {
        site = unpack(payload);
        heardRound = message.getRound();
      }
    }
    // Silence means the chosen promoted, died or gave up; resume ordinary work this turn.
    if (BabyRat.role == BabyRole.FODDER && now - heardRound > Config.FORMATION_LISTEN_ROUNDS) {
      release();
    }
  }

  /** Fodder turn: hold a ring tile, or walk to the nearest free one; leave if the ring is full. */
  static void runFodder(RobotController rc) throws GameActionException {
    MapLocation here = rc.getLocation();
    if (chebyshev(here, site) == 1) {
      return; // Holding position; the chosen's engine check decides when the ring suffices.
    }
    MapLocation best = null;
    int bestDistance = Integer.MAX_VALUE;
    int allies = 0;
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
            allies++;
          }
          continue;
        }
      }
      // Unsensed tiles stay candidates; the next turn's sensing corrects the choice.
      int distance = here.distanceSquaredTo(tile);
      if (distance < bestDistance) {
        best = tile;
        bestDistance = distance;
      }
    }
    // Six ring allies plus the chosen meet the engine's seven-rat rule; extras would be absorbed.
    if (best == null || allies >= Config.FORMATION_FODDER_NEEDED) {
      ignoredId = leaderId;
      ignoreUntil = rc.getRoundNum() + Config.FORMATION_IGNORE_ROUNDS;
      release();
      return;
    }
    Navigation.stepToward(rc, best, Config.PathingStrategy.DIST_BUG);
  }

  /** Chosen turn: keep a valid site, occupy it, promote when legal, and keep recruiting. */
  static void runChosen(RobotController rc) throws GameActionException {
    // Promote wherever the engine already allows it, e.g. volunteers crowding the chosen en route.
    if (promote(rc)) {
      return;
    }
    int now = rc.getRoundNum();
    collectHazards(rc);
    if (site != null
        && (blocked(rc, site)
            || now - siteRound > Config.FORMATION_SITE_ROUNDS
            || (!rc.getLocation().equals(site)
                && now - siteRound > Config.FORMATION_TRAVEL_ROUNDS))) {
      // The site cannot or did not finish: re-select, skipping it once; fodder follow RECRUIT.
      rejectedSite = site;
      site = null;
    }
    if (site == null) {
      site = selectSite(rc);
      siteRound = now;
      if (site == null) {
        explore(rc);
        return;
      }
    }
    if (!rc.getLocation().equals(site)) {
      Navigation.stepToward(rc, site, Config.PathingStrategy.DIST_BUG);
    }
    if (promote(rc)) {
      return;
    }
    if (rc.getLocation().equals(site)) {
      // A baby cone sees 90 degrees (p.10); sweep it so revalidation covers the whole footprint.
      Direction sweep = rc.getDirection().rotateRight().rotateRight();
      if (rc.canTurn(sweep)) {
        rc.turn(sweep);
      }
    }
    rc.squeak(RECRUIT | pack(site));
  }

  /** The engine checks cheese, cooldown, footprint and king caps; 10 bytecodes per check. */
  private static boolean promote(RobotController rc) throws GameActionException {
    if (!rc.canBecomeRatKing()) {
      return false;
    }
    rc.becomeRatKing();
    // Next turn dispatches by the new type; clear baby-only state before then.
    release();
    return true;
  }

  /** Nearest fully sensed, passable, on-map 3x3 clear of kings and cats by the configured gap. */
  private static MapLocation selectSite(RobotController rc) throws GameActionException {
    MapLocation here = rc.getLocation();
    MapLocation best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (MapInfo info : rc.senseNearbyMapInfos()) {
      MapLocation candidate = info.getMapLocation();
      int distance = here.distanceSquaredTo(candidate);
      // Cheap ordering first; x/y ties keep the choice independent of sense order (chirality).
      if (distance > bestDistance
          || (distance == bestDistance
              && (candidate.x > best.x || (candidate.x == best.x && candidate.y > best.y)))
          || candidate.equals(rejectedSite)
          || nearHazard(candidate, Config.FORMATION_KING_CLEARANCE)
          || !approachable(rc, candidate)
          || !open(rc, candidate)) {
        continue;
      }
      best = candidate;
      bestDistance = distance;
    }
    return best;
  }

  /**
   * Keep the ring off the map edge: each tile two steps N/E/S/W of the center borders three ring
   * tiles, so all four on the map gives every ring tile an outside approach. Edge sites let early
   * fodder seal off the rest of the ring (seen on evileye). Walls are left to the site budget;
   * rejecting walled approaches too pushed the chosen away from traffic in matches.
   */
  private static boolean approachable(RobotController rc, MapLocation center) {
    for (int i = 0; i < 8; i += 2) {
      Direction direction = RobotPlayer.directions[i];
      if (!rc.onTheMap(center.add(direction).add(direction))) {
        return false;
      }
    }
    return true;
  }

  /** All nine tiles must be on the map, sensed now and passable (no wall or dirt). */
  private static boolean open(RobotController rc, MapLocation center) throws GameActionException {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        MapLocation tile = center.translate(dx, dy);
        if (!rc.onTheMap(tile) || !rc.canSenseLocation(tile) || !rc.sensePassability(tile)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Visible evidence the committed site is now illegal: new wall/dirt, or a king or cat on it. */
  private static boolean blocked(RobotController rc, MapLocation center)
      throws GameActionException {
    if (nearHazard(center, OVERLAP)) {
      return true;
    }
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        MapLocation tile = center.translate(dx, dy);
        if (rc.canSenseLocation(tile) && !rc.sensePassability(tile)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * No site in view: step clear of the nearest king or cat, then circle it, sweeping the cone.
   * Circling instead of fleeing keeps the chosen inside baby traffic, which is its only recruits.
   */
  private static void explore(RobotController rc) throws GameActionException {
    MapLocation here = rc.getLocation();
    MapLocation nearest = null;
    for (int i = 0; i < hazardCount; i++) {
      if (nearest == null || here.distanceSquaredTo(hazards[i]) < here.distanceSquaredTo(nearest)) {
        nearest = hazards[i];
      }
    }
    Direction away = nearest == null ? Direction.CENTER : nearest.directionTo(here);
    if (away == Direction.CENTER) {
      away = rc.getDirection();
    } else if (chebyshev(here, nearest) > Config.FORMATION_KING_CLEARANCE) {
      away = away.rotateLeft().rotateLeft();
    }
    Navigation.stepToward(rc, here.add(away).add(away), Config.PathingStrategy.GREEDY);
    Direction sweep = rc.getDirection().rotateRight().rotateRight();
    if (rc.canTurn(sweep)) {
      rc.turn(sweep);
    }
  }

  /** Allied kings from the registry plus sensed kings and cats, gathered once per chosen turn. */
  private static void collectHazards(RobotController rc) throws GameActionException {
    hazardCount = 0;
    int now = rc.getRoundNum();
    for (int id = 0; id < KingRegistry.CAPACITY; id++) {
      MapLocation king = KingRegistry.read(rc, id, now);
      if (king != null) {
        hazards[hazardCount++] = king;
      }
    }
    for (RobotInfo robot : rc.senseNearbyRobots()) {
      if (hazardCount < MAX_HAZARDS
          && (robot.getType() == UnitType.RAT_KING || robot.getType().isCatType())) {
        hazards[hazardCount++] = robot.getLocation();
      }
    }
  }

  private static boolean nearHazard(MapLocation center, int gap) {
    for (int i = 0; i < hazardCount; i++) {
      if (chebyshev(center, hazards[i]) <= gap) {
        return true;
      }
    }
    return false;
  }

  /** Engine caps: five kings, or two after round 1200 (spec v1.2.6 p.14, p.25). */
  private static boolean kingLimitReached(RobotController rc) throws GameActionException {
    int now = rc.getRoundNum();
    int kings = 0;
    for (int id = 0; id < KingRegistry.CAPACITY; id++) {
      if (KingRegistry.read(rc, id, now) != null) {
        kings++;
      }
    }
    return kings >= GameConstants.MAX_NUMBER_OF_RAT_KINGS
        || (now > GameConstants.RAT_KING_CUTOFF_ROUND
            && kings >= GameConstants.MAX_NUMBER_OF_RAT_KINGS_AFTER_CUTOFF);
  }

  private static int chebyshev(MapLocation a, MapLocation b) {
    return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
  }

  // Earlier-acting robots squeak in the previous round relative to later ones.
  private static boolean fresh(int now, Message message) {
    return message.getRound() >= now - 1 && message.getRound() <= now;
  }

  // Six bits per axis cover the 60x60 maximum map (spec p.6).
  static int pack(MapLocation location) {
    return location.x | location.y << 6;
  }

  static MapLocation unpack(int payload) {
    return new MapLocation(payload & 63, (payload >>> 6) & 63);
  }

  /** Return to ordinary baby behavior, keeping raw cheese and the ignore window. */
  static void release() {
    BabyRat.role = null;
    site = null;
    rejectedSite = null;
    leaderId = 0;
    heardRound = 0;
    Navigation.resetFollow();
  }

  /** Reset robot-local state between host-side test scenarios. */
  static void reset() {
    release();
    requested = false;
    retriesLeft = 0;
    watchedId = 0;
    watchedBirth = 0;
    lastSeen = 0;
    lastSeenAt = null;
    birthChecked = false;
    startRound = 0;
    siteRound = 0;
    ignoredId = 0;
    ignoreUntil = 0;
    hazardCount = 0;
  }
}
