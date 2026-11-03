package ratplayer;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TrapType;
import battlecode.common.UnitType;

/** Cat detection and placement logic used by baby rats. */
final class Trap {
  static final int BACKSTABSLOT = 45;
  public static int startofBackStabbing = 0;

  /**
   * Returns the cats currently visible to this rat.
   */
  static RobotInfo[] detectCats(RobotController rc) {
    RobotInfo[] visibleRobots = rc.senseNearbyRobots();
    int catCount = 0;
    for (RobotInfo visibleRobot : visibleRobots) {
      if (visibleRobot.getType() == UnitType.CAT) {
        catCount++;
      }
    }
    RobotInfo[] cats = new RobotInfo[catCount];
    int nextCat = 0;
    for (RobotInfo visibleRobot : visibleRobots) {
      if (visibleRobot.getType() == UnitType.CAT) {
        cats[nextCat++] = visibleRobot;
      }
    }
    return cats;
  }

  /**
   * Returns the opponent rats currently visible to this rat.
   */
  static RobotInfo[] detectRats(RobotController rc) throws GameActionException {
    // get the rats of only our opponents
    RobotInfo[] visibleRobots = rc.senseNearbyRobots(4, rc.getTeam().opponent());
    int ratCount = 0;
    for (RobotInfo visibleRobot : visibleRobots) {
      UnitType type = visibleRobot.getType();
      if (type == UnitType.BABY_RAT || type == UnitType.RAT_KING) {
        ratCount++;
      }
    }
    RobotInfo[] rats = new RobotInfo[ratCount];
    int nextRat = 0;
    for (RobotInfo visibleRobot : visibleRobots) {
      UnitType detectedUnit = visibleRobot.getType();
      if (detectedUnit == UnitType.BABY_RAT || detectedUnit == UnitType.RAT_KING) {
        rats[nextRat++] = visibleRobot;
      }
    }
    return rats;
  }

  /**
   * Places at most one trap on the adjacent tile toward either a rat or cat.
   */
  static boolean placeTrap(RobotController rc) throws GameActionException {

    boolean backstabbing = rc.getBackstabbingTeam() != null;
    int cost = backstabbing ? TrapType.RAT_TRAP.buildCost : TrapType.CAT_TRAP.buildCost;
    if (!rc.isActionReady() || (long) rc.getGlobalCheese() - cost < Config.MIN_CHEESE_RESERVE) {
      return false;
    }
    MapLocation ratLocation = rc.getLocation();
    RobotInfo[] units;

    if (backstabbing) {
      units = detectRats(rc);
    } else {
      units = detectCats(rc);
    }

    for (RobotInfo unit : units) {
      MapLocation unitLocation = unit.getLocation();
      int deltaX = unitLocation.x - ratLocation.x;
      int deltaY = unitLocation.y - ratLocation.y;
      int lineLength = Math.max(Math.abs(deltaX), Math.abs(deltaY));
      if (lineLength < 2) {
        continue;
      }

      int stepX = Integer.compare(deltaX, 0);
      int stepY = Integer.compare(deltaY, 0);
      // Only the first tile toward the cat is eligible because traps must be adjacent to the rat.
      MapLocation trapLocation = new MapLocation(ratLocation.x + stepX, ratLocation.y + stepY);

      if (backstabbing) {
        if (rc.canPlaceRatTrap(trapLocation)) {
          rc.placeRatTrap(trapLocation);
          return true;
        }
      } else {
        if (rc.canPlaceCatTrap(trapLocation)) {
          rc.placeCatTrap(trapLocation);
          return true;
        }
      }
    }
    return false;
  }

  // if our internal startofBackStabbing isn't updated and backstabbing is happening
  static void writeStartofBackStabbing(RobotController rc) throws GameActionException {
    if (startofBackStabbing == 0 && !rc.isCooperation()) {
      int readRoundValue = rc.readSharedArray(45);

      // array hasn't been writen to so update it with the new value
      if (readRoundValue == 0) {
        int round = rc.getRoundNum();
        rc.writeSharedArray(BACKSTABSLOT, round);
        startofBackStabbing = round;
      } else {
        startofBackStabbing = readRoundValue;
      }
    }
  }

  // update the internal startofBackStabbing round from the array if it hasn't before
  static void updateStartofBackStabbing(RobotController rc) throws GameActionException {
    if (startofBackStabbing == 0 && !rc.isCooperation()) {
      startofBackStabbing = rc.readSharedArray(BACKSTABSLOT);
    }
  }
}
