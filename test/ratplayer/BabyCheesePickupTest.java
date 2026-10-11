package ratplayer;

import battlecode.common.*;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

public class BabyCheesePickupTest {

    private static final int MAP_WIDTH = 10;
    private static final int MAP_HEIGHT = 10;

    private RobotController rc;
    private Map<MapLocation, Integer> cheesePiles;  // location -> pile amount
    private int rawCheese;                          // the rat's raw cheese total

    @Before
    public void setUp() throws GameActionException {
        cheesePiles = new HashMap<>();
        rawCheese = 0;
        rc = mock(RobotController.class);

        when(rc.onTheMap(any())).thenAnswer(inv -> onMap(inv.getArgument(0)));
        when(rc.canPickUpCheese(any())).thenAnswer(inv -> {
            MapLocation loc = inv.getArgument(0);
            return onMap(loc) && cheesePiles.containsKey(loc);
        });
        when(rc.getRawCheese()).thenAnswer(inv -> rawCheese);

        // Simulates the real pickup. An illegal call simulates the error penalty.
        doAnswer(inv -> {
            MapLocation loc = inv.getArgument(0);
            if (!onMap(loc) || !cheesePiles.containsKey(loc)) {
                throw new AssertionError("Error penalty: illegal pickUpCheese at " + loc);
            }
            rawCheese += cheesePiles.remove(loc);
            return null;
        }).when(rc).pickUpCheese(any());
    }

    private boolean onMap(MapLocation loc) {
        return loc.x >= 0 && loc.y >= 0 && loc.x < MAP_WIDTH && loc.y < MAP_HEIGHT;
    }

    // Main case: one adjacent pile is collected
    @Test
    public void picksUpSingleAdjacentPile() throws GameActionException {
        MapLocation rat = new MapLocation(5, 5);
        MapLocation pile = rat.add(Direction.NORTH);
        cheesePiles.put(pile, 7);

        RobotPlayer.babyCheesePickup(rc, rat);

        assertEquals("raw cheese should increase by the pile amount", 7, rc.getRawCheese());
        assertFalse("the tile should no longer have cheese", cheesePiles.containsKey(pile));
    }

    // Test 1: no adjacent cheese -> no pickup attempt, no penalty
    @Test
    public void doesNotAttemptPickupWhenNoAdjacentCheese() throws GameActionException {
        MapLocation rat = new MapLocation(5, 5);

        RobotPlayer.babyCheesePickup(rc, rat);   // would throw AssertionError on a bad attempt

        verify(rc, never()).pickUpCheese(any());
        assertEquals(0, rc.getRawCheese());
    }

    // Test 2: three adjacent piles -> sum collected, all tiles cleared
    @Test
    public void picksUpAllThreeAdjacentPiles() throws GameActionException {
        MapLocation rat = new MapLocation(5, 5);
        cheesePiles.put(rat.add(Direction.NORTH), 3);
        cheesePiles.put(rat.add(Direction.EAST), 5);
        cheesePiles.put(rat.add(Direction.SOUTHWEST), 9);

        RobotPlayer.babyCheesePickup(rc, rat);

        assertEquals("raw cheese should be the sum of all piles", 3 + 5 + 9, rc.getRawCheese());
        assertTrue("all three tiles should be cleared", cheesePiles.isEmpty());
        verify(rc, times(3)).pickUpCheese(any());
    }

    // Test 3: map corner -> off-map neighbors skipped, valid neighbor collected
    @Test
    public void skipsOffMapNeighborsInCornerAndCollectsValidOne() throws GameActionException {
        MapLocation rat = new MapLocation(0, 0);
        MapLocation pile = rat.add(Direction.EAST);
        cheesePiles.put(pile, 4);

        RobotPlayer.babyCheesePickup(rc, rat);

        assertEquals(4, rc.getRawCheese());
        assertFalse(cheesePiles.containsKey(pile));
        verify(rc, never()).pickUpCheese(argThat(loc -> !onMap(loc)));
    }

    // Test 4: Two baby rats adjacent to the same cheese pile -> first rat claims it, second gets nothing and makes no illegal attempt
    @Test
    public void secondRatCannotClaimPileAlreadyTakenByFirstRat() throws GameActionException {
        MapLocation ratALoc = new MapLocation(4, 5);
        MapLocation ratBLoc = new MapLocation(6, 5);
        MapLocation pile = new MapLocation(5, 5);   // EAST of rat A, WEST of rat B
        cheesePiles.put(pile, 6);

        // Rat B: its own controller and cheese total, sharing the same cheesePiles map
        RobotController rcB = mock(RobotController.class);
        int[] rawCheeseB = {0};
        when(rcB.onTheMap(any())).thenAnswer(inv -> onMap(inv.getArgument(0)));
        when(rcB.canPickUpCheese(any())).thenAnswer(inv -> {
            MapLocation loc = inv.getArgument(0);
            return onMap(loc) && cheesePiles.containsKey(loc);
        });
        when(rcB.getRawCheese()).thenAnswer(inv -> rawCheeseB[0]);
        doAnswer(inv -> {
            MapLocation loc = inv.getArgument(0);
            if (!onMap(loc) || !cheesePiles.containsKey(loc)) {
                throw new AssertionError("Error penalty: illegal pickUpCheese at " + loc);
            }
            rawCheeseB[0] += cheesePiles.remove(loc);
            return null;
        }).when(rcB).pickUpCheese(any());

        // Rat A (the existing rc) acts first, then rat B
        RobotPlayer.babyCheesePickup(rc, ratALoc);
        RobotPlayer.babyCheesePickup(rcB, ratBLoc);   // would throw if it attempted an illegal pickup

        assertEquals("rat A should receive the pile", 6, rc.getRawCheese());
        assertEquals("rat B should receive nothing", 0, rcB.getRawCheese());
        assertFalse("the pile should be gone", cheesePiles.containsKey(pile));
        verify(rc, times(1)).pickUpCheese(pile);
        verify(rcB, never()).pickUpCheese(any());
    }
}