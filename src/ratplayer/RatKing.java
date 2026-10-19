package ratplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class RatKing {
  // Change after testing
  static final int MAX_BABY_RATS = 16;
  static final int MIN_CHEESE_RESERVE = 800;
  // Single-king shared-array protocol; static fields themselves are robot-local.
  static final int LOCATION_X_SLOT = 0;
  static final int LOCATION_Y_SLOT = 1;
  static final int LOCATION_VALID_SLOT = 2;
  static boolean locationPublished;
  private static final Direction[] openDirections = new Direction[7];
  private static MapLocation currentLocation;

  // handles our Rat King turn logic
  static void run(RobotController rc) throws GameActionException {
    // Every 100 turns, print out what type of robot we are.
    if (RobotPlayer.turnCount % 100 == 0) {
      System.out.println("Turn " + RobotPlayer.turnCount + ": I am a " + rc.getType().toString());
    }
    move(rc);
    pickUpCheese(rc);
    spawnBabyRat(rc);
  }

  // handles our King rat movement logic
  static void move(RobotController rc) throws GameActionException {
    if (!locationPublished) {
      updateLoc(rc);
    }
    // Cooldown is not an obstacle and must not cause random redirection.
    if (!rc.isMovementReady()) {
      return;
    }

    MapLocation current = rc.getLocation();
    MapLocation nearest = null;
    int nearestDistance = Integer.MAX_VALUE;
    for (MapInfo info : rc.senseNearbyMapInfos()) {
      if (info.getCheeseAmount() <= 0) {
        continue;
      }
      MapLocation cheese = info.getMapLocation();
      int distance = current.distanceSquaredTo(cheese);
      if (nearest == null
          || distance < nearestDistance
          || (distance == nearestDistance
              && (cheese.x < nearest.x || (cheese.x == nearest.x && cheese.y < nearest.y)))) {
        nearest = cheese;
        nearestDistance = distance;
      }
    }

    Direction facing = rc.getDirection();
    if (nearest != null) {
      Direction towardCheese = current.directionTo(nearest);
      if (towardCheese != Direction.CENTER && rc.canMove(towardCheese) && towardCheese != facing) {
        if (rc.canTurn(towardCheese)) {
          rc.turn(towardCheese);
        }
        return;
      }
    }

    if (rc.canMoveForward()) {
      rc.moveForward();
      updateLoc(rc);
      return;
    }

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
      MapLocation cheese = info.getMapLocation();
      if (info.getCheeseAmount() > 0 && rc.canPickUpCheese(cheese)) {
        rc.pickUpCheese(cheese);
      }
    }
  }

  // spawns our baby rats
  static void spawnBabyRat(RobotController rc) throws GameActionException {
    spawnBabyRat(rc, MAX_BABY_RATS, MIN_CHEESE_RESERVE);
  }

  // Parameter seam keeps experimental limits testable without changing decision logic.
  static void spawnBabyRat(RobotController rc, int maxBabyRats, int minCheeseReserve)
      throws GameActionException {
    if (maxBabyRats < 0
        || maxBabyRats % GameConstants.NUM_ROBOTS_FOR_COST_INCREASE != 0
        || minCheeseReserve < 0) {
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
                * (maxBabyRats / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    int cost = rc.getCurrentRatCost();
    if (cost >= stopPrice || (long) rc.getGlobalCheese() - cost < minCheeseReserve) {
      return;
    }
    MapLocation ratSpawnLocation = rc.adjacentLocation(RobotPlayer.directions[0]).translate(0, 1);
    if (rc.canBuildRat(ratSpawnLocation)) {
      rc.buildRat(ratSpawnLocation);
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
    rc.writeSharedArray(LOCATION_VALID_SLOT, 1);
    locationPublished = true;
  }
}
