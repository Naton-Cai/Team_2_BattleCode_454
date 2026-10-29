package ratplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class RatKing {
  // Single-king shared-array protocol; static fields themselves are robot-local.
  static final int LOCATION_X_SLOT = 0;
  static final int LOCATION_Y_SLOT = 1;
  static final int LOCATION_VALID_SLOT = 2;
  static final int KING_LOCATION_PUBLISHED = 1;
  static boolean locationPublished;
  private static final Direction[] openDirections = new Direction[7];
  private static MapLocation currentLocation;

  // Home and retreat destinations persist; terrain and routes do not.
  static MapLocation home;
  static MapLocation retreatMine;
  static boolean retreating;
  static int catFreeTurns;

  // handles our Rat King turn logic
  static void run(RobotController rc) throws GameActionException {
    // Every 100 turns, print out what type of robot we are.
    if (RobotPlayer.turnCount % 100 == 0) {
      System.out.println("Turn " + RobotPlayer.turnCount + ": I am a " + rc.getType().toString());
    }
    identifyCheeseMines(rc);
    move(rc);
    pickUpCheese(rc);
    spawnBabyRat(rc);
  }

  // Pre-move sensing also runs while movement is cooling down.
  static void identifyCheeseMines(RobotController rc) throws GameActionException {
    for (MapInfo info : rc.senseNearbyMapInfos()) {
      if (info.hasCheeseMine()) {
        CheeseMines.publish(rc, info.getMapLocation());
      }
    }
  }

  static void move(RobotController rc) throws GameActionException {
    if (Config.KING_HOME_RADIUS_SQUARED < 0 || Config.KING_CAT_FREE_TURNS_BEFORE_RETURN < 0) {
      throw new IllegalArgumentException("King radius and return delay must be nonnegative");
    }
    if (!locationPublished) {
      updateLoc(rc);
    }
    MapLocation current = rc.getLocation();
    RobotInfo[] robots = rc.senseNearbyRobots();
    boolean catVisible = KingNavigation.catDistance(current, robots) != Integer.MAX_VALUE;
    if (catVisible) {
      retreating = true;
      catFreeTurns = 0;
    } else if (retreating) {
      if (catFreeTurns < Config.KING_CAT_FREE_TURNS_BEFORE_RETURN) {
        catFreeTurns++;
      }
      if (catFreeTurns >= Config.KING_CAT_FREE_TURNS_BEFORE_RETURN) {
        retreating = false;
        retreatMine = null;
      }
    }
    if (home == null) {
      home = nearestMine(rc, current);
    }
    // Threat sensing and the return timer run even when movement is cooling down.
    if (!rc.isMovementReady()) {
      return;
    }
    MapInfo[] infos = rc.senseNearbyMapInfos();
    if (retreating) {
      if (catVisible) {
        retreatMine = saferMine(rc, current, robots);
      }
      if (retreatMine != null) {
        Direction step =
            KingNavigation.findRoute(
                rc, infos, robots, retreatMine, home, KingNavigation.Purpose.RETREAT);
        if (step != null) {
          takeStep(rc, step);
          return;
        }
      }
      // No safe route toward a mine: get clear locally, then wait out the return delay.
      if (catVisible) {
        Direction escape = null;
        int bestDistance = KingNavigation.catDistance(current, robots);
        for (Direction direction : RobotPlayer.directions) {
          int distance = KingNavigation.catDistance(current.add(direction), robots);
          if (distance > bestDistance && rc.canMove(direction)) {
            bestDistance = distance;
            escape = direction;
          }
        }
        if (escape != null) {
          takeStep(rc, escape);
        }
      }
      return;
    }
    if (home != null && current.distanceSquaredTo(home) > Config.KING_HOME_RADIUS_SQUARED) {
      takeStep(
          rc, KingNavigation.findRoute(rc, infos, robots, home, home, KingNavigation.Purpose.HOME));
      return;
    }

    MapLocation cheese = nearestCheese(current, infos);
    if (cheese != null) {
      takeStep(
          rc,
          KingNavigation.findRoute(rc, infos, robots, cheese, home, KingNavigation.Purpose.CHEESE));
      // Check one cheese target only; no confirmed route means hold and reassess later.
      return;
    }
    if (home == null) {
      explore(rc);
    }
  }

  private static MapLocation nearestCheese(MapLocation current, MapInfo[] infos) {
    MapLocation nearest = null;
    for (MapInfo info : infos) {
      MapLocation location = info.getMapLocation();
      if (info.getCheeseAmount() > 0
          && (home == null || home.distanceSquaredTo(location) <= Config.KING_HOME_RADIUS_SQUARED)
          && nearer(location, nearest, current)) {
        nearest = location;
      }
    }
    return nearest;
  }

  private static MapLocation nearestMine(RobotController rc, MapLocation current)
      throws GameActionException {
    MapLocation nearest = null;
    for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
      MapLocation mine = CheeseMines.read(rc, i);
      if (mine != null && nearer(mine, nearest, current)) {
        nearest = mine;
      }
    }
    return nearest;
  }

  private static boolean nearer(MapLocation candidate, MapLocation best, MapLocation current) {
    return best == null
        || candidate.distanceSquaredTo(current) < best.distanceSquaredTo(current)
        || (candidate.distanceSquaredTo(current) == best.distanceSquaredTo(current)
            && (candidate.x < best.x || (candidate.x == best.x && candidate.y < best.y)));
  }

  // Safety is relative to visible cat locations, not a guarantee about unseen threats.
  private static MapLocation saferMine(RobotController rc, MapLocation current, RobotInfo[] robots)
      throws GameActionException {
    MapLocation best = null;
    int bestSafety = KingNavigation.catDistance(current, robots);
    for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
      MapLocation mine = CheeseMines.read(rc, i);
      if (mine == null || mine.equals(home)) {
        continue;
      }
      int safety = KingNavigation.catDistance(mine, robots);
      if (safety > bestSafety
          || (safety == bestSafety && best != null && nearer(mine, best, current))) {
        best = mine;
        bestSafety = safety;
      }
    }
    return best;
  }

  private static void takeStep(RobotController rc, Direction step) throws GameActionException {
    if (step == null) {
      return;
    }
    if (step != rc.getDirection()) {
      if (rc.canMove(step) && rc.canTurn(step)) {
        rc.turn(step);
      }
    } else if (rc.canMoveForward()) {
      rc.moveForward();
      updateLoc(rc);
    }
  }

  // Preserve seeded forward/random exploration when no home or loose cheese is known.
  private static void explore(RobotController rc) throws GameActionException {
    if (rc.canMoveForward()) {
      rc.moveForward();
      updateLoc(rc);
      return;
    }
    Direction facing = rc.getDirection();
    int openCount = 0;
    for (Direction direction : RobotPlayer.directions) {
      if (direction != facing && rc.canMove(direction)) {
        openDirections[openCount++] = direction;
      }
    }
    if (openCount > 0) {
      Direction direction = openDirections[RobotPlayer.rng.nextInt(openCount)];
      if (rc.canTurn(direction)) {
        rc.turn(direction);
      }
    }
  }

  // handles our cheese picking up logic
  static void pickUpCheese(RobotController rc) throws GameActionException {
    System.out.println("Turn " + RobotPlayer.turnCount + ": Rat King Picking Up Cheese");
    // Re-sense after movement; pickup range includes tiles beyond the center.
    for (MapInfo info : rc.senseNearbyMapInfos()) {
      if (info.hasCheeseMine()) {
        CheeseMines.publish(rc, info.getMapLocation());
      }
      MapLocation cheese = info.getMapLocation();
      if (info.getCheeseAmount() > 0 && rc.canPickUpCheese(cheese)) {
        rc.pickUpCheese(cheese);
      }
    }
  }

  // Spawns a baby rat within the configured population and reserve limits.
  static void spawnBabyRat(RobotController rc) throws GameActionException {
    if (Config.MAX_BABY_RATS < 0
        || Config.MAX_BABY_RATS % GameConstants.NUM_ROBOTS_FOR_COST_INCREASE != 0
        || Config.MIN_CHEESE_RESERVE < 0) {
      throw new IllegalArgumentException(
          "Baby cap must be a nonnegative multiple of the cost step; reserve must be nonnegative");
    }
    if (!rc.isActionReady()) {
      return;
    }
    // v1.2.6, spec p.14: price tracks living allied babies, including unseen losses.
    // shortcut: exact caps require multiples of four; arbitrary caps need population tracking.
    long stopPrice =
        GameConstants.BUILD_ROBOT_BASE_COST
            + (long) GameConstants.BUILD_ROBOT_COST_INCREASE
                * (Config.MAX_BABY_RATS / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    int cost = rc.getCurrentRatCost();
    if (cost >= stopPrice || (long) rc.getGlobalCheese() - cost < Config.MIN_CHEESE_RESERVE) {
      return;
    }
    // Check all eight directions clockwise from north; the engine validates each candidate.
    MapLocation center = rc.getLocation();
    for (Direction direction : RobotPlayer.directions) {
      MapLocation ratSpawnLocation = center.translate(2 * direction.dx, 2 * direction.dy);
      if (rc.canBuildRat(ratSpawnLocation)) {
        rc.buildRat(ratSpawnLocation);
        return;
      }
    }
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
