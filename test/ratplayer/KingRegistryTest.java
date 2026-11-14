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

    // An interrupted primary record is also unavailable.
    rc.setSharedArrayValue(2, 769);
    assertNull(KingRegistry.readPrimary(rc));
  }

  // ---------------------------------------------------------------------------------------------
  // Nearest-king delivery (feature-nearest-king-delivery.md). The baby stands at rc's location;
  // each king is published by its own KingRegistry, so IDs follow publication order (0, 1, ...).
  // ---------------------------------------------------------------------------------------------

  /** Publishes one king per center at the current round; centers[i] receives registry ID i. */
  private void publishKings(MapLocation... centers) throws Exception {
    for (MapLocation center : centers) {
      rc.setLocation(center);
      new KingRegistry().publish(rc);
    }
  }

  @Test
  public void nearestPicksSecondaryKingCloserThanPrimary() throws Exception {
    // Arrange: ID 0 is far away, ID 1 is close to the baby.
    rc.setRoundNum(50);
    MapLocation primary = new MapLocation(5, 5);
    MapLocation secondary = new MapLocation(30, 30);
    publishKings(primary, secondary);
    rc.setLocation(new MapLocation(28, 30));

    // Act + Assert: the closer secondary wins even though ID 0 is alive.
    assertEquals(secondary, KingRegistry.readNearest(rc));
  }

  @Test
  public void nearestConsidersEveryEntryIncludingTheLast() throws Exception {
    // Arrange: five live kings along the top edge; the baby sits beside the ID 4 king.
    rc.setRoundNum(50);
    publishKings(
        new MapLocation(5, 50),
        new MapLocation(15, 50),
        new MapLocation(25, 50),
        new MapLocation(35, 50),
        new MapLocation(45, 50));
    rc.setLocation(new MapLocation(46, 48));

    // Assert: the scan reaches the final slot, so ID 4 is chosen.
    assertEquals(new MapLocation(45, 50), KingRegistry.readNearest(rc));
  }

  @Test
  public void nearestTieKeepsLowerRegistryId() throws Exception {
    // Arrange: the baby is exactly halfway between ID 0 and ID 1 (distance² 25 to each).
    rc.setRoundNum(50);
    MapLocation lowId = new MapLocation(10, 20);
    MapLocation highId = new MapLocation(20, 20);
    publishKings(lowId, highId);
    rc.setLocation(new MapLocation(15, 20));

    // Assert: deterministic tie-break toward the lower ID.
    assertEquals(lowId, KingRegistry.readNearest(rc));
  }

  @Test
  public void nearestFollowsKingMovementOnTheNextLookup() throws Exception {
    // Arrange: two kings; ID 0 starts nearer the baby at (20,20).
    rc.setRoundNum(50);
    KingRegistry first = new KingRegistry();
    KingRegistry second = new KingRegistry();
    rc.setLocation(new MapLocation(22, 20));
    first.publish(rc);
    rc.setLocation(new MapLocation(40, 20));
    second.publish(rc);
    MapLocation baby = new MapLocation(20, 20);
    rc.setLocation(baby);
    assertEquals(new MapLocation(22, 20), KingRegistry.readNearest(rc));

    // Act 1: the same king moves a little but stays nearest -> its new center is used.
    rc.setRoundNum(51);
    rc.setLocation(new MapLocation(23, 20));
    first.publish(rc);
    rc.setLocation(new MapLocation(40, 20));
    second.publish(rc);
    rc.setLocation(baby);
    assertEquals(new MapLocation(23, 20), KingRegistry.readNearest(rc));

    // Act 2: ID 0 walks away and ID 1 approaches -> the target switches kings.
    rc.setRoundNum(52);
    rc.setLocation(new MapLocation(50, 20));
    first.publish(rc);
    rc.setLocation(new MapLocation(18, 20));
    second.publish(rc);
    rc.setLocation(baby);
    assertEquals(new MapLocation(18, 20), KingRegistry.readNearest(rc));
  }

  @Test
  public void nearestSkipsExpiredPrimaryAndClearsWhenNoKingIsLeft() throws Exception {
    // Arrange: ID 0 is nearest but only ID 1 keeps publishing.
    rc.setRoundNum(100);
    KingRegistry survivor = new KingRegistry();
    MapLocation primary = new MapLocation(10, 10);
    MapLocation survivorCenter = new MapLocation(40, 40);
    publishKings(primary);
    rc.setLocation(survivorCenter);
    survivor.publish(rc);
    rc.setLocation(new MapLocation(11, 10));

    // Round 101: the previous-round heartbeat grace still keeps ID 0 eligible.
    rc.setRoundNum(101);
    assertEquals(primary, KingRegistry.readNearest(rc));

    // Round 102: ID 0 has expired; the far but live survivor becomes the delivery target.
    rc.setRoundNum(102);
    rc.setLocation(survivorCenter);
    survivor.publish(rc);
    rc.setLocation(new MapLocation(11, 10));
    BabyRat.updateRatKingLoc(rc);
    assertEquals(survivorCenter, BabyRat.kingLoc);

    // Round 104: the survivor has expired too. The baby's cached target must clear, not linger.
    rc.setRoundNum(104);
    BabyRat.updateRatKingLoc(rc);
    assertNull(BabyRat.kingLoc);
  }

  @Test
  public void nearestIgnoresPendingFutureAndOffMapRecords() throws Exception {
    // Arrange: three tempting records right next to the baby, all invalid, plus one valid king far
    // away in ID 3. Records are written directly as (x, packedY, stamp) triples.
    rc.setRoundNum(50);
    rc.setSharedArrayValue(0, 21); // ID 0: pending claim (stamp > 512) -> never readable.
    rc.setSharedArrayValue(1, 20);
    rc.setSharedArrayValue(2, 600);
    rc.setSharedArrayValue(3, 21); // ID 1: committed in round 60 -> future-dated, rejected.
    rc.setSharedArrayValue(4, 20);
    rc.setSharedArrayValue(5, 61);
    rc.setSharedArrayValue(6, 63); // ID 2: x = 63 is off this 60-wide map, rejected.
    rc.setSharedArrayValue(7, 20);
    rc.setSharedArrayValue(8, 51);
    MapLocation valid = new MapLocation(50, 50);
    rc.setSharedArrayValue(9, valid.x); // ID 3: committed in round 50 -> valid.
    rc.setSharedArrayValue(10, valid.y);
    rc.setSharedArrayValue(11, 51);
    rc.setLocation(new MapLocation(20, 20));

    // Assert: only the valid, far record is returned.
    assertEquals(valid, KingRegistry.readNearest(rc));
  }
}
