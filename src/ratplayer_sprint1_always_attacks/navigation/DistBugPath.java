package ratplayer_sprint1_always_attacks.navigation;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Seek the goal until a known wall, dirt, or map edge blocks the heading. Follow with a fixed
 * hand until the heading is open and the unit is closer than at the hit.
 *
 * <p>Follow state lives on {@link Navigation}. Falls back to {@link GreedyPath} while seeking.
 */
final class DistBugPath {
  private DistBugPath() {}

  static void stepToward(RobotController rc, MapLocation goal) throws GameActionException {
    MapLocation current = rc.getLocation();
    Direction desired = current.directionTo(goal);
    if (desired == Direction.CENTER) {
      Navigation.resetFollow();
      return;
    }
    if (Navigation.followGoal == null || !Navigation.followGoal.equals(goal)) {
      Navigation.resetFollow();
      Navigation.followGoal = goal;
    }

    MapInfo[] infos = rc.senseNearbyMapInfos();
    int distanceSq = current.distanceSquaredTo(goal);
    if (Navigation.following) {
      if (rc.canMove(desired) && distanceSq < Navigation.hitDistanceSq) {
        Navigation.following = false;
        rc.move(desired);
        return;
      }
      followWall(rc, desired);
      return;
    }

    MapLocation ahead = current.add(desired);
    if (!rc.canMove(desired) && Navigation.isKnownObstacle(rc, infos, ahead)) {
      enterFollow(rc, current, goal, desired);
      followWall(rc, desired);
      return;
    }
    GreedyPath.stepToward(rc, goal);
  }

  static void enterFollow(
      RobotController rc, MapLocation current, MapLocation goal, Direction blocked) {
    Navigation.following = true;
    Navigation.followGoal = goal;
    Navigation.hitDistanceSq = current.distanceSquaredTo(goal);
    Direction left = blocked.rotateLeft();
    Direction right = blocked.rotateRight();
    boolean leftOpen = rc.canMove(left);
    boolean rightOpen = rc.canMove(right);
    Navigation.followLeft = leftOpen || !rightOpen;
  }

  static void followWall(RobotController rc, Direction blocked) throws GameActionException {
    Direction facing = rc.getDirection();
    Direction probe = Navigation.followLeft ? blocked.rotateLeft() : blocked.rotateRight();
    Direction firstOpen = null;
    Direction facingOpen = null;
    for (int i = 0; i < 7; i++) {
      if (rc.canMove(probe)) {
        if (firstOpen == null) {
          firstOpen = probe;
        }
        if (probe == facing) {
          facingOpen = probe;
          break;
        }
      }
      probe = Navigation.followLeft ? probe.rotateLeft() : probe.rotateRight();
    }
    Direction chosen = facingOpen != null ? facingOpen : firstOpen;
    if (chosen != null) {
      rc.move(chosen);
      return;
    }
    Direction turnTo = Navigation.followLeft ? blocked.rotateLeft() : blocked.rotateRight();
    if (rc.canTurn(turnTo)) {
      rc.turn(turnTo);
    }
  }
}
