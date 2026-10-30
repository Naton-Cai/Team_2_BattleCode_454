package ratplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import ratplayer.navigation.Navigation;

public class BabyRat {
  static MapLocation kingLoc = null;
  static MapLocation cheeseTile = null;
  static boolean pickedUpCheese = false;
  static boolean kingChecked = false;
  static boolean attackedThisTurn = false;

  // handles our BabyRat turn logic
  static void run(RobotController rc) throws GameActionException {
    // Every 100 turns, print out what type of robot we are.
    if (RobotPlayer.turnCount % 100 == 0) {
      System.out.println("Turn " + RobotPlayer.turnCount + ": I am a " + rc.getType().toString());
    }

    kingLoc = null;
    cheeseTile = null;
    pickedUpCheese = false;
    kingChecked = false;
    attackedThisTurn = false;

    int rawCheese = rc.getRawCheese();
    if (rawCheese > Config.RAW_CHEESE_MINIMUM) {
      updateRatKingLoc(rc);
      kingChecked = true;
      if (kingLoc != null && rc.canTransferCheese(kingLoc, rawCheese)) {
        rc.transferCheese(kingLoc, rawCheese);
      }
    }
    Trap.updateStartofBackStabbing(rc);
    pickUpCheese(rc);
    tryConfiguredActions(rc);
    boolean fled = respondToCats(rc);
    if (!fled) {
      move(rc);
    }
  }

  /**
   * Tries each action in {@link Config#BABY_ACTION_ORDER} and stops after the first success. An
   * omitted action does not run.
   */
  static void tryConfiguredActions(RobotController rc) throws GameActionException {
    Config.BabyAction[] actions = Config.BABY_ACTION_ORDER;
    for (int i = 0; i < actions.length; i++) {
      boolean acted = false;
      switch (actions[i]) {
        case ATTACK:
          acted = attack(rc);
          break;
        case TRAP:
          acted = Trap.placeTrap(rc);
          break;
        case DIG_DIRT:
          if (shouldSkipDigForCatResponse(rc)) {
            break;
          }
          acted = digDirt(rc);
          break;
      }
      if (acted) {
        break;
      }
    }
  }

  /** Returns the cats currently visible to this rat. */
  static RobotInfo[] detectCats(RobotController rc) {
    RobotInfo[] visibleRobots = rc.senseNearbyRobots();
    int catCount = 0;
    for (int i = 0; i < visibleRobots.length; i++) {
      if (visibleRobots[i].getType() == UnitType.CAT) {
        catCount++;
      }
    }
    RobotInfo[] cats = new RobotInfo[catCount];
    int nextCat = 0;
    for (int i = 0; i < visibleRobots.length; i++) {
      if (visibleRobots[i].getType() == UnitType.CAT) {
        cats[nextCat++] = visibleRobots[i];
      }
    }
    return cats;
  }

  /**
   * Bites an adjacent cat, or a rat after cooperation has ended.
   *
   * @return true if a bite was issued
   */
  static boolean attack(RobotController rc) throws GameActionException {
    boolean backstabbing = rc.getBackstabbingTeam() != null;
    // a baby rat can attack the 3 tiles in front of it without having to turn so we should check
    // all 3 tiles for valid targets.
    Direction direction = rc.getDirection();
    MapLocation center = rc.getLocation();
    MapLocation forward = center.add(direction);
    MapLocation left = center.add(direction.rotateLeft());
    MapLocation right = center.add(direction.rotateRight());
    // check starting from center, then left ,than right for targets.
    if (tryAttackAt(rc, forward, backstabbing)
        || tryAttackAt(rc, left, backstabbing)
        || tryAttackAt(rc, right, backstabbing)) {
      attackedThisTurn = true;
      return true;
    }
    return false;
  }

  /**
   * Applies {@link Config#BABY_CAT_RESPONSE_STRATEGY}. {@code true} means this method already moved.
   */
  static boolean respondToCats(RobotController rc) throws GameActionException {
    switch (Config.BABY_CAT_RESPONSE_STRATEGY) {
      case BITE_THEN_BLOCK_AND_FLEE:
        return biteThenBlockAndFlee(rc);
      case NONE:
      default:
        return false;
    }
  }

  /**
   * After a miss, places dirt toward the nearest visible cat and steps the other way.
   *
   * @return true if movement was handled by fleeing
   */
  static boolean biteThenBlockAndFlee(RobotController rc) throws GameActionException {
    RobotInfo cat = nearestVisibleCat(rc);
    if (cat == null || attackedThisTurn) {
      return false;
    }
    placeDirtTowardCat(rc, cat.getLocation());
    fleeFromCat(rc, cat.getLocation());
    return true;
  }

  /**
   * Nearest visible cat by squared distance. Equal distances keep the earlier sense result.
   */
  static RobotInfo nearestVisibleCat(RobotController rc) {
    RobotInfo[] cats = detectCats(rc);
    MapLocation origin = rc.getLocation();
    RobotInfo nearest = null;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = 0; i < cats.length; i++) {
      int distance = origin.distanceSquaredTo(cats[i].getLocation());
      if (nearest == null || distance < bestDistance) {
        nearest = cats[i];
        bestDistance = distance;
      }
    }
    return nearest;
  }

  /** Places at most one dirt tile on the adjacent Chebyshev step toward {@code catLocation}. */
  static void placeDirtTowardCat(RobotController rc, MapLocation catLocation)
      throws GameActionException {
    MapLocation ratLocation = rc.getLocation();
    int deltaX = catLocation.x - ratLocation.x;
    int deltaY = catLocation.y - ratLocation.y;
    int stepX = Integer.compare(deltaX, 0);
    int stepY = Integer.compare(deltaY, 0);
    if (stepX == 0 && stepY == 0) {
      return;
    }
    MapLocation dirtLocation = new MapLocation(ratLocation.x + stepX, ratLocation.y + stepY);
    Dirt.tryPlace(rc, dirtLocation);
  }

  /** One greedy step on the opposite heading from {@code catLocation}. */
  static void fleeFromCat(RobotController rc, MapLocation catLocation) throws GameActionException {
    Direction away = rc.getLocation().directionTo(catLocation).opposite();
    if (away == Direction.CENTER) {
      return;
    }
    Navigation.stepToward(rc, rc.getLocation().add(away), Config.PathingStrategy.GREEDY);
  }

  /**
   * Generic digging yields the action when this strategy wants it for a visible-cat dirt block.
   */
  static boolean shouldSkipDigForCatResponse(RobotController rc) {
    return Config.BABY_CAT_RESPONSE_STRATEGY == Config.CatResponseStrategy.BITE_THEN_BLOCK_AND_FLEE
        && detectCats(rc).length > 0;
  }

  /**
   * Digs the first adjacent in-cone dirt tile that is legal and leaves the dig cheese reserve.
   *
   * @return true if a dig was issued
   */
  static boolean digDirt(RobotController rc) throws GameActionException {
    return Dirt.digInVisionCone(rc);
  }

  private static boolean tryAttackAt(RobotController rc, MapLocation location, boolean backstabbing)
      throws GameActionException {
    if (!rc.canSenseRobotAtLocation(location)) {
      return false;
    }
    RobotInfo target = rc.senseRobotAtLocation(location);
    if (target == null) {
      return false;
    }
    boolean isCat = target.getType() == UnitType.CAT;
    boolean isRat = target.getType() == UnitType.BABY_RAT || target.getType() == UnitType.RAT_KING;
    if (!isCat && (!backstabbing || !isRat || target.getTeam() == rc.getTeam())) {
      return false;
    }

    // Determines cheese spend; increasing cat bite strength improves the team's points.
    // spending 3-7 cheese will optimize our points gain when attacking a cat.
    int cheeseToSpend = 0;
    if (isCat) {
      int maxCheese = backstabbing ? 3 : 7;
      cheeseToSpend = Math.min(maxCheese, rc.getRawCheese() + rc.getGlobalCheese());
    }
    if (!rc.canAttack(location, cheeseToSpend)) {
      return false;
    }
    rc.attack(location, cheeseToSpend);
    return true;
  }

  // handles our baby rat movement logic
  static void move(RobotController rc) throws GameActionException {
    int rawCheese = rc.getRawCheese();
    if (rawCheese > Config.RAW_CHEESE_MINIMUM) {
      if (!kingChecked) {
        updateRatKingLoc(rc);
        kingChecked = true;
      }
      if (kingLoc != null) {
        Navigation.stepToward(rc, kingLoc, Config.BABY_PATH_TO_KING_STRATEGY);
        return;
      }
    } else if (cheeseTile != null && !pickedUpCheese) {
      Navigation.stepToward(rc, cheeseTile, Config.BABY_PATH_TO_CHEESE_STRATEGY);
      return;
    }
    wander(rc);
  }

  /**
   * Closest visible tile that holds cheese. Equal distances keep the earlier sense result, which
   * preserves the engine's sense order.
   */
  static MapLocation closestVisibleCheese(RobotController rc) throws GameActionException {
    MapInfo[] infos = rc.senseNearbyMapInfos();
    MapLocation origin = rc.getLocation();
    MapLocation closest = null;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = 0; i < infos.length; i++) {
      MapInfo info = infos[i];
      if (info.getCheeseAmount() <= 0) {
        continue;
      }
      MapLocation loc = info.getMapLocation();
      int distance = origin.distanceSquaredTo(loc);
      if (closest == null || distance < bestDistance) {
        closest = loc;
        bestDistance = distance;
      }
    }
    return closest;
  }

  /**
   * Selects a cheese pile using {@link Config#BABY_CHEESE_TARGET_STRATEGY}.
   */
  static MapLocation selectCheese(RobotController rc) throws GameActionException {
    switch (Config.BABY_CHEESE_TARGET_STRATEGY) {
      case NEAREST_VISIBLE:
      default:
        return closestVisibleCheese(rc);
    }
  }

  // Try to move forward one step.
  static void wander(RobotController rc) throws GameActionException {
    Navigation.resetFollow();
    if (rc.canMoveForward()) {
      System.out.println("Turn " + RobotPlayer.turnCount + ": Trying to move " + rc.getDirection());
      rc.moveForward();
    } else {
      System.out.println(
          "Couldn't move forward on turn "
              + RobotPlayer.turnCount
              + " at location "
              + rc.getLocation()
              + " facing "
              + rc.getDirection());
      // If we can't move forward, try to turn a random direction.
      int randomDirection = RobotPlayer.rng.nextInt(8);
      Direction dir = RobotPlayer.directions[randomDirection];

      if (rc.canTurn(dir)) {
        rc.turn(dir);
      }
    }
  }

  // handles our cheese picking up logic
  static void pickUpCheese(RobotController rc) throws GameActionException {
    if (rc.getRawCheese() > Config.RAW_CHEESE_MINIMUM) {
      return;
    }
    cheeseTile = selectCheese(rc);
    if (cheeseTile != null && rc.canPickUpCheese(cheeseTile)) {
      rc.pickUpCheese(cheeseTile);
      pickedUpCheese = true;
    }
  }

  // update our RatKing Location
  static void updateRatKingLoc(RobotController rc) throws GameActionException {
    int x = rc.readSharedArray(RatKing.LOCATION_X_SLOT);
    int y = rc.readSharedArray(RatKing.LOCATION_Y_SLOT);
    int published = rc.readSharedArray(RatKing.LOCATION_VALID_SLOT);
    if (published != RatKing.KING_LOCATION_PUBLISHED) {
      kingLoc = null;
      return;
    }
    kingLoc = new MapLocation(x, y);
  }
}
