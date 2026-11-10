package ratplayer_sprint1_always_attacks.navigation;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * One greedy step toward a goal: desired heading, then left/right pairs, then opposite.
 *
 * <p>Already standing on the goal ({@code CENTER}) does not move or turn. Uses {@link
 * Navigation#stepCandidates}.
 */
final class GreedyPath {
  private GreedyPath() {}

  static void stepToward(RobotController rc, MapLocation goal) throws GameActionException {
    Direction desired = rc.getLocation().directionTo(goal);
    if (desired == Direction.CENTER) {
      return;
    }
    Direction left = desired;
    Direction right = desired;
    Navigation.stepCandidates[0] = desired;
    int index = 1;
    for (int offset = 1; offset <= 3; offset++) {
      left = left.rotateLeft();
      right = right.rotateRight();
      Navigation.stepCandidates[index] = left;
      index += 1;
      Navigation.stepCandidates[index] = right;
      index += 1;
    }
    Navigation.stepCandidates[7] = desired.opposite();
    for (int i = 0; i < Navigation.stepCandidates.length; i++) {
      Direction dir = Navigation.stepCandidates[i];
      if (rc.canMove(dir)) {
        rc.move(dir);
        return;
      }
    }
    if (rc.canTurn(desired)) {
      rc.turn(desired);
    }
  }
}
