package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapLocation;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

public class KingRegistryTest {
  private final TestRobotController rc = new TestRobotController();

  @Before
  public void setupMap() {
    rc.setMapWidth(60);
    rc.setMapHeight(60);
  }

  @Test
  public void fivePublishersKeepSeparateRecordsAcrossRoundEncodingBoundary() throws Exception {
    // Each publisher has its own local state. All publishers use the same team array.
    rc.setSharedArrayValue(15, 17);
    rc.setSharedArrayValue(50, 19);
    rc.setSharedArrayValue(51, 23);
    KingRegistry[] kings = new KingRegistry[5];
    rc.setRoundNum(1022);
    for (int i = 0; i < kings.length; i++) {
      kings[i] = new KingRegistry();
      rc.setLocation(new MapLocation(i + 1, 58));
      kings[i].publish(rc);
    }
    rc.setRoundNum(1023);
    for (int i = 0; i < kings.length; i++) {
      // Before each king acts, the previous-round heartbeat is still valid.
      assertEquals(new MapLocation(i + 1, 58), KingRegistry.read(rc, i, 1023));
      rc.setLocation(new MapLocation(i + 1, 57));
      kings[i].publish(rc);
      assertEquals(new MapLocation(i + 1, 57), KingRegistry.read(rc, i, 1023));
    }
    assertEquals(17, rc.readSharedArray(15));
    assertEquals(19, rc.readSharedArray(50));
    assertEquals(23, rc.readSharedArray(51));
    assertEquals(4, rc.readSharedArray(9));
    assertEquals(5, rc.readSharedArray(12));
    assertNull(KingRegistry.read(rc, 0, 1025));
    assertNull(KingRegistry.read(rc, 0, 2000));
  }

  @Test
  public void incompleteLocationIsInvisibleAndResumingPublisherDoesNotOverwriteReplacement()
      throws Exception {
    KingRegistry first = new KingRegistry();
    KingRegistry second = new KingRegistry();
    MapLocation firstCenter = new MapLocation(5, 5);
    MapLocation replacementCenter = new MapLocation(30, 30);
    rc.setRoundNum(20);
    rc.setLocation(firstCenter);
    first.publish(rc);
    // Fail the commit after moving. A partial record cannot become a delivery destination.
    rc.setLocation(new MapLocation(5, 6));
    rc.failSharedWrite(4, new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test"));
    assertThrows(GameActionException.class, () -> first.publish(rc));
    assertNull(KingRegistry.read(rc, 0, 20));
    // A new king cannot take the pending slot while its owner can still retry.
    rc.setLocation(replacementCenter);
    second.publish(rc);
    rc.setRoundNum(21);
    rc.setLocation(new MapLocation(5, 6));
    first.publish(rc);
    assertEquals(new MapLocation(5, 6), KingRegistry.read(rc, 0, 21));
    assertEquals(replacementCenter, KingRegistry.read(rc, 1, 21));
    // If the owner misses its heartbeat, a replacement can reclaim the expired ID.
    rc.setRoundNum(23);
    rc.setLocation(new MapLocation(40, 40));
    new KingRegistry().publish(rc);
    rc.setLocation(firstCenter);
    first.publish(rc);
    assertEquals(new MapLocation(40, 40), KingRegistry.read(rc, 0, 23));
    assertEquals(firstCenter, KingRegistry.read(rc, 1, 23));
  }

  @Test
  public void abandonedPartialClaimExpiresWithoutRevivingAtTheEndOfTheGame() throws Exception {
    rc.setRoundNum(1995);
    rc.setLocation(new MapLocation(5, 5));
    rc.failSharedWrite(4, new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test"));
    assertThrows(GameActionException.class, () -> new KingRegistry().publish(rc));
    assertNull(KingRegistry.read(rc, 0, 1995));

    // At the end of the game the old pending bucket has elapsed and can be replaced.
    rc.setRoundNum(2000);
    MapLocation replacement = new MapLocation(50, 50);
    rc.setLocation(replacement);
    new KingRegistry().publish(rc);
    assertEquals(replacement, KingRegistry.read(rc, 0, 2000));
    assertNull(KingRegistry.read(rc, 1, 2000));
  }

  @Test
  public void resumingKingLeavesAnotherKingsPendingClaimAlone() throws Exception {
    KingRegistry first = new KingRegistry();
    rc.setRoundNum(10);
    rc.setLocation(new MapLocation(5, 5));
    first.publish(rc);

    // A replacement claims the expired slot but fails to commit its location.
    KingRegistry replacement = new KingRegistry();
    MapLocation replacementCenter = new MapLocation(20, 20);
    rc.setRoundNum(12);
    rc.setLocation(replacementCenter);
    rc.failSharedWrite(4, new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test"));
    assertThrows(GameActionException.class, () -> replacement.publish(rc));

    rc.setLocation(new MapLocation(5, 5));
    first.publish(rc);
    assertNull(KingRegistry.read(rc, 0, 12));
    assertEquals(new MapLocation(5, 5), KingRegistry.read(rc, 1, 12));
    rc.setLocation(replacementCenter);
    replacement.publish(rc);
    assertEquals(replacementCenter, KingRegistry.read(rc, 0, 12));
  }

  @Test
  public void primaryLookupAcceptsPreviousRoundButExpiresWithoutFollowingOtherIds()
      throws Exception {
    // Accept a heartbeat before the king's next turn, including across the encoding boundary.
    rc.setRoundNum(1023);
    MapLocation primary = new MapLocation(10, 9);
    rc.setLocation(primary);
    new KingRegistry().publish(rc);
    assertEquals(primary, KingRegistry.readPrimary(rc));
    KingRegistry second = new KingRegistry();
    MapLocation survivor = new MapLocation(12, 10);
    rc.setLocation(survivor);
    second.publish(rc);
    rc.setRoundNum(1024);
    assertEquals(primary, KingRegistry.readPrimary(rc));

    // A live ID 1 does not make the expired primary a valid delivery destination.
    rc.setRoundNum(1025);
    second.publish(rc);
    assertNull(KingRegistry.readPrimary(rc));
    assertEquals(survivor, KingRegistry.read(rc, 1, 1025));
    // Refreshing the baby's cached destination must clear the old location too.
    BabyRat.kingLoc = primary;
    BabyRat.updateRatKingLoc(rc);
    assertNull(BabyRat.kingLoc);

    // An interrupted primary record is also unavailable.
    rc.setSharedArrayValue(2, 769);
    assertNull(KingRegistry.readPrimary(rc));
  }
}
