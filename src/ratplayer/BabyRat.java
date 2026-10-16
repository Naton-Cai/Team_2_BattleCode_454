package ratplayer;

import battlecode.common.*;

import java.util.Random;

public class BabyRat {
    static MapLocation kingLoc = null;
    //handles our BabyRat turn logic
    static void run(RobotController rc) throws GameActionException {
        // Every 100 turns, print out what type of robot we are.
        if (RobotPlayer.turnCount % 100 == 0) {
            System.out.println("Turn " + RobotPlayer.turnCount + ": I am a " + rc.getType().toString());
        }
        move(rc);
        pickUpCheese(rc);
    }
    //handles our baby rat movement logic
    static void move(RobotController rc) throws GameActionException {
        //PLACEHOLDER CODE
        // Try to move forward one step.
        if (rc.canMoveForward()) {
            System.out.println("Turn " + RobotPlayer.turnCount + ": Trying to move " + rc.getDirection());
            rc.moveForward();
        } else {
            System.out.println("Couldn't move forward on turn " + RobotPlayer.turnCount + " at location " + rc.getLocation() + " facing " + rc.getDirection());
            // If we can't move forward, try to turn a random direction.
            int randomDirection = RobotPlayer.rng.nextInt(8);
            Direction dir = RobotPlayer.directions[randomDirection];

            if (rc.canTurn(dir)) {
                rc.turn(dir);
            }
        }
        //PLACEHOLDER CODE
    }

    //handles our cheese picking up logic
    static void pickUpCheese(RobotController rc) throws GameActionException {
        System.out.println("Turn " + RobotPlayer.turnCount + ": Baby Rat Picking Up Cheese");
    }

    //update our RatKing Location
    static void updateRatKingLoc(RobotController rc) throws GameActionException {}
}
