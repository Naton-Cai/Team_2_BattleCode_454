package ratplayer;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import ratplayer.navigation.Navigation;

public class RatKing {
  // Single-king shared-array protocol; static fields themselves are robot-local.
  static final int LOCATION_X_SLOT = 0;
  static final int LOCATION_Y_SLOT = 1;
  static final int LOCATION_VALID_SLOT = 2;
  static final int KING_LOCATION_PUBLISHED = 1;
  static boolean locationPublished;
  private static final Direction[] openDirections = new Direction[8];
  private static final int[] openWeights = new int[8];
  private static MapLocation currentLocation;

  // A bit per map tile records mine discoveries, not terrain or routes (spec v1.2.6 pp.6,18).
  private static final long[] seenMines = new long[GameConstants.MAP_MAX_HEIGHT];
  private static final long[] turnMines = new long[GameConstants.MAP_MAX_HEIGHT];
  private static final long[] escapeMines = new long[GameConstants.MAP_MAX_HEIGHT];
  private static final boolean[] openSteps = new boolean[8];
  // Full-turn engine fixtures measure the work remaining after this optional-navigation cutoff.
  private static final int NAVIGATION_BYTECODE_CUTOFF = 10000;
  static MapLocation home;
  static MapLocation deferredHome;
  static int stalledTurns;
  static int retryTurnsLeft;
  static int bestHomeDistance = Integer.MAX_VALUE;
  private static Direction followHeading;
  private static int followHitDistance;
  private static boolean escaping;
  private static boolean handledCatsThisTurn;

  // handles our Rat King turn logic
  static void run(RobotController rc) throws GameActionException {
    // Every 100 turns, print out what type of robot we are.
    if (RobotPlayer.turnCount % 100 == 0) {
      System.out.println("Turn " + RobotPlayer.turnCount + ": I am a " + rc.getType().toString());
    }

    handledCatsThisTurn = false;
    java.util.Arrays.fill(turnMines, 0);
    Trap.writeStartofBackStabbing(rc);
    identifyCheeseMines(rc);
    boolean fled = respondToCats(rc);
    handledCatsThisTurn = fled;
    if (fled) {
      escaping = true;
      for (int y = 0; y < turnMines.length; y++) {
        escapeMines[y] |= turnMines[y];
      }
    } else {
      if (!locationPublished) {
        updateLoc(rc);
      }
      if (Clock.getBytecodeNum() < NAVIGATION_BYTECODE_CUTOFF) {
        if (escaping) {
          finishEscape(rc);
        }
        move(rc);
      }
    }
    pickUpCheese(rc);
    if (!fled && !spawnBabyRat(rc)) {
      Dirt.digAroundKing(rc);
    }
  }

  /**
   * Applies {@link Config#KING_CAT_RESPONSE_STRATEGY}. {@code true} means the policy handled this
   * turn, even if movement was blocked or cooling down; normal movement, spawning and digging skip.
   */
  static boolean respondToCats(RobotController rc) throws GameActionException {
    switch (Config.KING_CAT_RESPONSE_STRATEGY) {
      case BITE_THEN_BLOCK_AND_FLEE:
        return biteThenBlockAndFlee(rc);
      case BLOCK_AND_FLEE_WHEN_LOW_HEALTH:
        return blockAndFleeWhenLowHealth(rc);
      case NONE:
      default:
        return false;
    }
  }

  /**
   * Places dirt toward the nearest visible cat and steps the other way. Kings do not bite, so this
   * policy never waits on {@code attackedThisTurn}.
   *
   * @return true if this policy handled movement
   */
  static boolean biteThenBlockAndFlee(RobotController rc) throws GameActionException {
    RobotInfo cat = Cats.nearestVisibleCat(rc);
    if (cat == null) {
      return false;
    }
    if (!locationPublished) {
      updateLoc(rc);
    }
    Cats.placeDirtTowardCat(rc, cat.getLocation());
    fleeFromCat(rc, cat.getLocation());
    return true;
  }

  /**
   * At or below {@link Config#KING_CAT_FLEE_HEALTH}, places spawn-ring dirt toward the nearest cat
   * and steps the other way.
   *
   * @return true if this policy handled movement
   */
  static boolean blockAndFleeWhenLowHealth(RobotController rc) throws GameActionException {
    if (rc.getHealth() > Config.KING_CAT_FLEE_HEALTH) {
      return false;
    }
    RobotInfo cat = Cats.nearestVisibleCat(rc);
    if (cat == null) {
      return false;
    }
    if (!locationPublished) {
      updateLoc(rc);
    }
    Cats.placeDirtTowardCat(rc, cat.getLocation());
    fleeFromCat(rc, cat.getLocation());
    return true;
  }

  /** One greedy step on the opposite heading from {@code catLocation}, then publish if moved. */
  static void fleeFromCat(RobotController rc, MapLocation catLocation) throws GameActionException {
    Direction away = Cats.awayFrom(rc, catLocation);
    if (away == Direction.CENTER) {
      return;
    }
    MapLocation origin = rc.getLocation();
    Navigation.stepToward(rc, origin.add(away), Config.PathingStrategy.GREEDY);
    if (!rc.getLocation().equals(origin)) {
      updateLoc(rc);
    }
  }

  // Pre-move sensing also runs while movement is cooling down.
  static void identifyCheeseMines(RobotController rc) throws GameActionException {
    // Read earlier committed records before this turn publishes simultaneous local discoveries.
    if (home == null && deferredHome == null) {
      for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
        MapLocation mine = CheeseMines.read(rc, i);
        if (mine != null) {
          adoptHome(rc, mine);
          break;
        }
      }
    }
    observeMines(rc, rc.senseNearbyMapInfos());
  }

  private static void observeMines(RobotController rc, MapInfo[] infos) throws GameActionException {
    MapLocation first = null;
    MapLocation current = rc.getLocation();
    for (MapInfo info : infos) {
      if (!info.hasCheeseMine()) {
        continue;
      }
      MapLocation mine = info.getMapLocation();
      long bit = 1L << mine.x;
      if ((seenMines[mine.y] & bit) == 0) {
        seenMines[mine.y] |= bit;
        turnMines[mine.y] |= bit;
        if (handledCatsThisTurn) {
          escapeMines[mine.y] |= bit;
        }
      }
      CheeseMines.publish(rc, mine);
      if (!mine.equals(deferredHome) && nearer(current, mine, first)) {
        first = mine;
      }
    }
    if (home == null && deferredHome == null && first != null) {
      adoptHome(rc, first);
    }
  }

  private static boolean nearer(MapLocation origin, MapLocation candidate, MapLocation selected) {
    if (selected == null) {
      return true;
    }
    int distance = origin.distanceSquaredTo(candidate);
    int previous = origin.distanceSquaredTo(selected);
    return distance < previous
        || (distance == previous
            && (candidate.x < selected.x
                || (candidate.x == selected.x && candidate.y < selected.y)));
  }

  private static void adoptHome(RobotController rc, MapLocation mine) {
    home = mine;
    deferredHome = null;
    retryTurnsLeft = 0;
    stalledTurns = 0;
    bestHomeDistance = rc.getLocation().distanceSquaredTo(mine);
    followHeading = null;
  }

  private static void finishEscape(RobotController rc) {
    MapLocation current = rc.getLocation();
    MapLocation previous = home != null ? home : deferredHome;
    MapLocation candidate = nearestLocalMine(current, escapeMines, null);
    java.util.Arrays.fill(escapeMines, 0);
    if (previous != null
        && candidate != null
        && current.distanceSquaredTo(candidate) < current.distanceSquaredTo(previous)) {
      adoptHome(rc, candidate);
    }
    // Escape movement can invalidate the old wall-follow heading even when home stays unchanged.
    followHeading = null;
    escaping = false;
  }

  private static MapLocation nearestLocalMine(
      MapLocation current, long[] mines, MapLocation excluded) {
    MapLocation nearest = null;
    for (int y = 0; y < mines.length; y++) {
      long row = mines[y];
      while (row != 0) {
        MapLocation mine = new MapLocation(Long.numberOfTrailingZeros(row), y);
        if (!mine.equals(excluded) && nearer(current, mine, nearest)) {
          nearest = mine;
        }
        row &= row - 1;
      }
    }
    return nearest;
  }

  // Normal movement targets mines only; loose cheese never redirects the king.
  static void move(RobotController rc) throws GameActionException {
    if (Config.KING_HOME_RADIUS < 0
        || Config.KING_STALL_TURNS <= 0
        || Config.KING_RETRY_TURNS <= 0) {
      throw new IllegalArgumentException(
          "King radius must be nonnegative and navigation delays positive");
    }
    if (!locationPublished) {
      updateLoc(rc);
    }
    // Cooldown and low optional-work budget are not evidence of an obstacle.
    if (!rc.isMovementReady() || Clock.getBytecodeNum() >= NAVIGATION_BYTECODE_CUTOFF) {
      return;
    }
    if (home == null && deferredHome != null) {
      MapLocation alternative = alternativeHome(rc, deferredHome);
      if (alternative != null) {
        adoptHome(rc, alternative);
      }
    }
    if (home == null) {
      explore(rc);
      if (deferredHome != null && --retryTurnsLeft == 0) {
        adoptHome(rc, deferredHome);
      }
      return;
    }
    MapLocation current = rc.getLocation();
    int distance = current.distanceSquaredTo(home);
    if (distance <= (long) Config.KING_HOME_RADIUS * Config.KING_HOME_RADIUS) {
      stalledTurns = 0;
      bestHomeDistance = distance;
      followHeading = null;
      return;
    }
    // Account for improved distance from fleeing without counting flee/cooldown turns as stalls.
    if (distance < bestHomeDistance) {
      bestHomeDistance = distance;
      stalledTurns = 0;
    }
    Direction step = homeStep(rc, current, distance);
    if (step != null) {
      if (step != rc.getDirection()) {
        if (rc.canTurn(step)) {
          rc.turn(step);
        }
      } else if (rc.canMoveForward()) {
        rc.moveForward();
        if (followHeading != null) {
          followHeading = step;
        }
        updateLoc(rc);
      }
    }
    int after = rc.getLocation().distanceSquaredTo(home);
    if (after < bestHomeDistance) {
      bestHomeDistance = after;
      stalledTurns = 0;
    } else if (++stalledTurns >= Config.KING_STALL_TURNS) {
      deferHome(rc);
    }
  }

  private static Direction homeStep(RobotController rc, MapLocation current, int distance)
      throws GameActionException {
    Direction desired = current.directionTo(home);
    // Probe each heading once. Only the chosen action gets a final fresh legality check.
    for (int i = 0; i < RobotPlayer.directions.length; i++) {
      openSteps[i] = rc.canMove(RobotPlayer.directions[i]);
    }
    if (followHeading != null && distance < followHitDistance && openSteps[desired.ordinal()]) {
      followHeading = null;
    }
    if (followHeading == null) {
      if (openSteps[desired.ordinal()]) {
        return desired;
      }
      // Occupancy alone causes a bounded wait; do not enter terrain following for another robot.
      MapLocation destination = current.add(desired);
      boolean terrainBlocked = false;
      for (int dx = -1; dx <= 1 && !terrainBlocked; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          MapLocation tile = destination.translate(dx, dy);
          if (tile.x < 0
              || tile.y < 0
              || tile.x >= rc.getMapWidth()
              || tile.y >= rc.getMapHeight()
              || (rc.canSenseLocation(tile) && !rc.sensePassability(tile))) {
            terrainBlocked = true;
            break;
          }
        }
      }
      if (!terrainBlocked) {
        return null;
      }
      followHitDistance = distance;
      followHeading = desired.rotateLeft().rotateLeft();
    }
    // Keep the obstacle on the right: start to the right of the last successful step.
    Direction probe = followHeading.rotateRight().rotateRight();
    for (int i = 0; i < 8; i++) {
      if (openSteps[probe.ordinal()]) {
        return probe;
      }
      probe = probe.rotateLeft();
    }
    return null;
  }

  private static void deferHome(RobotController rc) throws GameActionException {
    MapLocation previous = home;
    MapLocation alternative = alternativeHome(rc, previous);
    if (alternative != null) {
      adoptHome(rc, alternative);
    } else {
      home = null;
      deferredHome = previous;
      retryTurnsLeft = Config.KING_RETRY_TURNS;
      stalledTurns = 0;
      followHeading = null;
    }
  }

  private static MapLocation alternativeHome(RobotController rc, MapLocation previous)
      throws GameActionException {
    MapLocation alternative = nearestLocalMine(rc.getLocation(), seenMines, previous);
    for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
      MapLocation mine = CheeseMines.read(rc, i);
      if (mine != null && !mine.equals(previous) && nearer(rc.getLocation(), mine, alternative)) {
        alternative = mine;
      }
    }
    return alternative;
  }

  private static void explore(RobotController rc) throws GameActionException {
    Direction facing = rc.getDirection();
    MapLocation current = rc.getLocation();
    int width = rc.getMapWidth();
    int height = rc.getMapHeight();
    // Keep the original uniform fallback for an already selected home's retry interval.
    boolean initialSearch = deferredHome == null;
    boolean nearEdge =
        initialSearch
            && (current.x <= Config.KING_SEARCH_EDGE_MARGIN
                || current.y <= Config.KING_SEARCH_EDGE_MARGIN
                || width - 1 - current.x <= Config.KING_SEARCH_EDGE_MARGIN
                || height - 1 - current.y <= Config.KING_SEARCH_EDGE_MARGIN);
    // Doubled coordinates represent the exact center of even-sized maps without floats/objects.
    int centerDx = 2 * current.x - (width - 1);
    int centerDy = 2 * current.y - (height - 1);
    int distance = centerDx * centerDx + centerDy * centerDy;
    int forwardDx = centerDx + 2 * facing.dx;
    int forwardDy = centerDy + 2 * facing.dy;
    boolean forwardInward = forwardDx * forwardDx + forwardDy * forwardDy < distance;
    boolean forwardOpen = rc.canMoveForward();
    if (forwardOpen && (!nearEdge || forwardInward)) {
      rc.moveForward();
      updateLoc(rc);
      return;
    }

    int openCount = 0;
    int totalWeight = 0;
    for (Direction direction : RobotPlayer.directions) {
      if (direction == facing ? forwardOpen : rc.canMove(direction)) {
        int dx = centerDx + 2 * direction.dx;
        int dy = centerDy + 2 * direction.dy;
        int weight =
            initialSearch && dx * dx + dy * dy < distance ? Config.KING_SEARCH_INWARD_WEIGHT : 1;
        openDirections[openCount] = direction;
        openWeights[openCount++] = weight;
        totalWeight += weight;
      }
    }
    if (openCount == 0) {
      return;
    }
    int choice = RobotPlayer.rng.nextInt(totalWeight);
    for (int i = 0; i < openCount; i++) {
      choice -= openWeights[i];
      if (choice < 0) {
        Direction direction = openDirections[i];
        if (direction == facing) {
          if (rc.canMoveForward()) {
            rc.moveForward();
            updateLoc(rc);
          }
        } else if (rc.canTurn(direction)) {
          rc.turn(direction);
        }
        return;
      }
    }
  }

  // handles our cheese picking up logic
  static void pickUpCheese(RobotController rc) throws GameActionException {
    System.out.println("Turn " + RobotPlayer.turnCount + ": Rat King Picking Up Cheese");
    // Re-sense after movement; pickup range includes tiles beyond the center.
    MapInfo[] infos = rc.senseNearbyMapInfos();
    observeMines(rc, infos);
    for (MapInfo info : infos) {
      MapLocation cheese = info.getMapLocation();
      if (info.getCheeseAmount() > 0 && rc.canPickUpCheese(cheese)) {
        rc.pickUpCheese(cheese);
      }
    }
  }

  /** Clears robot-local home state for independent controller scenarios. */
  static void resetHome() {
    home = null;
    deferredHome = null;
    stalledTurns = 0;
    retryTurnsLeft = 0;
    bestHomeDistance = Integer.MAX_VALUE;
    followHeading = null;
    escaping = false;
    handledCatsThisTurn = false;
    java.util.Arrays.fill(seenMines, 0);
    java.util.Arrays.fill(turnMines, 0);
    java.util.Arrays.fill(escapeMines, 0);
  }

  // Spawns a baby rat within the configured population and reserve limits.
  static boolean spawnBabyRat(RobotController rc) throws GameActionException {
    if (Config.MAX_BABY_RATS < 0
        || Config.MAX_BABY_RATS % GameConstants.NUM_ROBOTS_FOR_COST_INCREASE != 0
        || Config.MIN_CHEESE_RESERVE < 0) {
      throw new IllegalArgumentException(
          "Baby cap must be a nonnegative multiple of the cost step; reserve must be nonnegative");
    }
    if (!rc.isActionReady()) {
      return false;
    }
    // v1.2.6, spec p.14: price tracks living allied babies, including unseen losses.
    // shortcut: exact caps require multiples of four; arbitrary caps need population tracking.
    long stopPrice =
        GameConstants.BUILD_ROBOT_BASE_COST
            + (long) GameConstants.BUILD_ROBOT_COST_INCREASE
                * (Config.MAX_BABY_RATS / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    int cost = rc.getCurrentRatCost();
    if (cost >= stopPrice || (long) rc.getGlobalCheese() - cost < Config.MIN_CHEESE_RESERVE) {
      return false;
    }
    // Check all eight directions clockwise from north; the engine validates each candidate.
    MapLocation center = rc.getLocation();
    for (Direction direction : RobotPlayer.directions) {
      MapLocation ratSpawnLocation = center.translate(2 * direction.dx, 2 * direction.dy);
      if (rc.canBuildRat(ratSpawnLocation)) {
        rc.buildRat(ratSpawnLocation);
        return true;
      }
    }
    return false;
  }

  // update our RatKing Location to the global array
  static void updateLoc(RobotController rc) throws GameActionException {
    // Retry from the actual location next turn if any write fails.
    locationPublished = false;
    currentLocation = rc.getLocation();

    // Baby rats should only read from global array if slot 2 equals 1
    rc.writeSharedArray(LOCATION_VALID_SLOT, 0);
    rc.writeSharedArray(LOCATION_X_SLOT, currentLocation.x);
    rc.writeSharedArray(LOCATION_Y_SLOT, currentLocation.y);
    rc.writeSharedArray(LOCATION_VALID_SLOT, KING_LOCATION_PUBLISHED);
    locationPublished = true;
  }
}
