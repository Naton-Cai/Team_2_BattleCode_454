package ratplayer;

import battlecode.common.*;

public class RatKing {
    //handles our Rat King turn logic
    static void run(RobotController rc) throws GameActionException {
        // Every 100 turns, print out what type of robot we are.
        if (RobotPlayer.turnCount % 100 == 0) {
            System.out.println("Turn " + RobotPlayer.turnCount + ": I am a " + rc.getType().toString());
        }
        move(rc);
        pickUpCheese(rc);
        spawnBabyRat(rc);
    }

    //handles our King rat movement logic
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
        System.out.println("Turn " + RobotPlayer.turnCount + ": Rat King Picking Up Cheese");
    }

    //spawns our baby rats
    static void spawnBabyRat(RobotController rc) throws GameActionException {
        System.out.println("Turn " + RobotPlayer.turnCount + ": Rat King Spawning Baby Rat");
        //PLACEHOLDER CODE
        //spawns a baby rat north of the rat king if cheese is >= 800
        if (rc.getAllCheese() >= 800) {
            System.out.println("Turn " + RobotPlayer.turnCount + ": I am trying to spawn a baby");
            rc.buildRat(rc.adjacentLocation(RobotPlayer.directions[0]).translate(0, 1));
        }
        //PLACEHOLDER CODE
    }

    //update our RatKing Location to the global array
    static void updateLoc(RobotController rc) throws GameActionException {
    }
}
