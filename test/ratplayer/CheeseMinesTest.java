package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/** Shared protocol tests; changing controller type models readers without robot-local caches. */
public class CheeseMinesTest {
  private TestRobotController rc;

  @Before
  public void setup() {
    Config.reset();
    rc = new TestRobotController();
    rc.setMapWidth(60);
    rc.setMapHeight(60);
    rc.setUnitType(UnitType.RAT_KING);
  }

  @After
  public void reset() {
    Config.reset();
  }

  @Test
  public void resetRestoresMineCapacityAfterOverride() {
    // A test or match override must not leak into the next default configuration.
    Config.MAX_CHEESE_MINES = 5;
    Config.reset();
    assertEquals(18, Config.MAX_CHEESE_MINES);
  }

  @Test
  public void defaultCapacityRetainsLargestBundledMapPopulation() throws Exception {
    // TheHeist and cheeseguardians each contain 18 mines in engine 1.2.6.
    assertEquals(18, Config.MAX_CHEESE_MINES);
    for (int i = 0; i < 18; i++) {
      CheeseMines.publish(rc, new MapLocation(i, 59));
    }
    for (int i = 0; i < 18; i++) {
      assertEquals(new MapLocation(i, 59), CheeseMines.read(rc, i));
    }
    // The 19th discovery cannot consume the first entry reserved for future uses.
    CheeseMines.publish(rc, new MapLocation(59, 59));
    assertEquals(0, rc.readSharedArray(45));
  }

  @Test
  public void capDeduplicationAndReadersPreserveOtherAllocations() throws Exception {
    // Exercise a deliberately smaller configured cap, independent of the team default.
    Config.MAX_CHEESE_MINES = 5;
    // Arrange sentinels for every slot outside this five-record allocation.
    for (int slot = 0; slot < 64; slot++) {
      if (slot < 9 || slot > 18) {
        rc.writeSharedArray(slot, 777);
      }
    }
    for (int i = 0; i < 6; i++) {
      CheeseMines.publish(rc, new MapLocation(i, i));
      CheeseMines.publish(rc, new MapLocation(i, i));
    }
    // A baby reads the same committed records but cannot publish.
    rc.setUnitType(UnitType.BABY_RAT);
    CheeseMines.publish(rc, new MapLocation(59, 59));
    for (int i = 0; i < 5; i++) {
      assertEquals(new MapLocation(i, i), CheeseMines.read(rc, i));
    }
    for (int slot = 0; slot < 64; slot++) {
      if (slot < 9 || slot > 18) {
        assertEquals(777, rc.readSharedArray(slot));
      }
    }
  }

  @Test
  public void interruptedAppendCanBeReclaimedWithoutLosingCommittedRecords() throws Exception {
    // Fail each possible append write. Prior records survive and incomplete data stays invisible.
    for (int write = 1; write <= 2; write++) {
      setup();
      CheeseMines.publish(rc, new MapLocation(0, 0));
      rc.failSharedWrite(
          write, new GameActionException(GameActionExceptionType.CANT_DO_THAT, "interrupted"));
      try {
        CheeseMines.publish(rc, new MapLocation(59, 59));
        fail("Expected append interruption");
      } catch (GameActionException expected) {
        assertNull(CheeseMines.read(rc, 1));
      }
      assertEquals(new MapLocation(0, 0), CheeseMines.read(rc, 0));
      // No robot-local recovery state: a later king can retry and repeated sightings deduplicate.
      CheeseMines.publish(rc, new MapLocation(59, 59));
      CheeseMines.publish(rc, new MapLocation(59, 59));
      assertEquals(new MapLocation(59, 59), CheeseMines.read(rc, 1));
      assertNull(CheeseMines.read(rc, 2));
    }
  }

  @Test
  public void configurableCapacityUsesLastLegalPairAndZeroDisablesPublication() throws Exception {
    Config.MAX_CHEESE_MINES = 0;
    CheeseMines.publish(rc, new MapLocation(0, 0));
    assertEquals(0, rc.readSharedArray(10));
    Config.MAX_CHEESE_MINES = 27;
    for (int i = 0; i < 27; i++) {
      CheeseMines.publish(rc, new MapLocation(i, 59));
    }
    assertEquals(new MapLocation(26, 59), CheeseMines.read(rc, 26));
    assertEquals(60, rc.readSharedArray(62));
    assertEquals(0, rc.readSharedArray(63));
    Config.MAX_CHEESE_MINES = 28;
    try {
      CheeseMines.publish(rc, new MapLocation(59, 59));
      fail("Oversized allocation must fail explicitly");
    } catch (IllegalArgumentException expected) {
      assertEquals(0, rc.readSharedArray(63));
    }
  }
}
