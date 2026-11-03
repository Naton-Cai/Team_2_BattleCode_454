package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/** Successful allied births, not robot IDs or movement randomness, determine the role mix. */
public class BabyRolesTest {
  private final List<Integer> payloads = new ArrayList<>();
  private int nextId = 10000;

  @Before
  public void setUp() {
    Config.reset();
    RatKing.birthSequence = 0;
  }

  @After
  public void resetState() {
    BabyRat.role = null;
    RatKing.birthSequence = 0;
    Config.reset();
  }

  @Test
  public void oneKingAssignsEightBirthsWithoutOverwritingNewbornSqueaks() throws Exception {
    // Pin the original 3:1 example so changing the compiled experiment defaults is safe.
    Config.BABY_GATHERERS_PER_CYCLE = 3;
    Config.BABY_TRAPPERS_PER_CYCLE = 1;
    RobotController parent = king(new MapLocation(10, 10));
    for (int birth = 0; birth < 8; birth++) {
      assertEquals(true, RatKing.spawnBabyRat(parent));
      int payload = payloads.get(birth);
      assertEquals(birth % 4 == 3, payload < 0);
      // All assignments remain independently readable when later babies are spawned.
      TestRobotController baby = new TestRobotController();
      baby.setId(payload & Integer.MAX_VALUE);
      baby.setLocation(parent.getLocation().translate(0, 2));
      baby.setRoundNum(2);
      baby.setSqueaks(new Message[] {new Message(payload, 123, 1, parent.getLocation())});
      BabyRat.role = null;
      BabyRat.assignRole(baby);
      assertEquals(
          birth % 4 == 3 ? Config.BabyRole.TRAPPER : Config.BabyRole.GATHERER, BabyRat.role);
    }
    assertEquals(8, payloads.size());
    assertEquals(0, RatKing.birthSequence);
    verify(parent, never()).writeSharedArray(anyInt(), anyInt());
  }

  @Test
  public void blockedAndThrowingBuildsConsumeNeitherSequenceNorSqueak() throws Exception {
    Config.reset();
    RobotController parent = king(new MapLocation(10, 10));
    when(parent.canBuildRat(any())).thenReturn(false);
    assertEquals(false, RatKing.spawnBabyRat(parent));
    assertEquals(0, RatKing.birthSequence);
    when(parent.canBuildRat(any())).thenReturn(true);
    doThrow(new GameActionException(GameActionExceptionType.CANT_DO_THAT, "failed birth"))
        .when(parent)
        .buildRat(any());
    assertThrows(GameActionException.class, () -> RatKing.spawnBabyRat(parent));
    assertEquals(0, RatKing.birthSequence);
    assertEquals(0, payloads.size());
  }

  @Test
  public void configurableCountsRepeatTwoToOneAndOneToOneMixes() throws Exception {
    // Expected strings state the intended order independently of the production calculation.
    Config.BABY_GATHERERS_PER_CYCLE = 2;
    Config.BABY_TRAPPERS_PER_CYCLE = 1;
    assertAssignments("GGTGGT");
    Config.BABY_GATHERERS_PER_CYCLE = 1;
    Config.BABY_TRAPPERS_PER_CYCLE = 1;
    assertAssignments("GTGTGT");
  }

  @Test
  public void zeroCountDisablesEitherRoleAndLargerCountsKeepTheirGrouping() throws Exception {
    Config.BABY_GATHERERS_PER_CYCLE = 1;
    Config.BABY_TRAPPERS_PER_CYCLE = 0;
    assertAssignments("GGGG");
    Config.BABY_GATHERERS_PER_CYCLE = 0;
    Config.BABY_TRAPPERS_PER_CYCLE = 2;
    assertAssignments("TTTT");
    Config.BABY_GATHERERS_PER_CYCLE = 2;
    Config.BABY_TRAPPERS_PER_CYCLE = 3;
    assertAssignments("GGTTTGGTTT");
  }

  @Test
  public void blockedBirthAtRoleBoundaryDoesNotConsumeNextTrapper() throws Exception {
    Config.BABY_GATHERERS_PER_CYCLE = 2;
    Config.BABY_TRAPPERS_PER_CYCLE = 1;
    RobotController parent = king(new MapLocation(10, 10));
    RatKing.spawnBabyRat(parent);
    RatKing.spawnBabyRat(parent);
    when(parent.canBuildRat(any())).thenReturn(false);
    assertEquals(false, RatKing.spawnBabyRat(parent));
    assertEquals(2, RatKing.birthSequence);
    assertEquals(2, payloads.size());
    when(parent.canBuildRat(any())).thenReturn(true);
    RatKing.spawnBabyRat(parent);
    assertEquals(true, payloads.getLast() < 0);
    assertEquals(0, RatKing.birthSequence);
  }

  @Test
  public void invalidMixesAreRejectedBeforeBuildingOrSqueaking() throws Exception {
    RobotController parent = king(new MapLocation(10, 10));
    // Negative counts, no enabled roles, and integer overflow are configuration errors.
    for (int[] counts : new int[][] {{-1, 1}, {1, -1}, {0, 0}, {Integer.MAX_VALUE, 1}}) {
      Config.BABY_GATHERERS_PER_CYCLE = counts[0];
      Config.BABY_TRAPPERS_PER_CYCLE = counts[1];
      assertThrows(IllegalArgumentException.class, () -> RatKing.spawnBabyRat(parent));
    }
    verify(parent, never()).buildRat(any());
    verify(parent, never()).squeak(anyInt());
    assertEquals(0, RatKing.birthSequence);
  }

  @Test
  public void largestValidCycleWrapsWithoutOverflow() throws Exception {
    Config.BABY_GATHERERS_PER_CYCLE = Integer.MAX_VALUE - 1;
    Config.BABY_TRAPPERS_PER_CYCLE = 1;
    RatKing.birthSequence = Integer.MAX_VALUE - 1;
    RobotController parent = king(new MapLocation(10, 10));
    RatKing.spawnBabyRat(parent);
    assertEquals(true, payloads.getLast() < 0);
    assertEquals(0, RatKing.birthSequence);
    RatKing.spawnBabyRat(parent);
    assertEquals(false, payloads.getLast() < 0);
  }

  @Test
  public void resetRestoresConfiguredExperimentDefaults() {
    Config.BABY_GATHERERS_PER_CYCLE = 2;
    Config.BABY_TRAPPERS_PER_CYCLE = 5;
    Config.reset();
    assertEquals(Config.DEFAULT_BABY_GATHERERS_PER_CYCLE, Config.BABY_GATHERERS_PER_CYCLE);
    assertEquals(Config.DEFAULT_BABY_TRAPPERS_PER_CYCLE, Config.BABY_TRAPPERS_PER_CYCLE);
  }

  private void assertAssignments(String expected) throws Exception {
    RatKing.birthSequence = 0;
    payloads.clear();
    RobotController parent = king(new MapLocation(10, 10));
    for (int i = 0; i < expected.length(); i++) {
      assertEquals(true, RatKing.spawnBabyRat(parent));
      assertEquals("Birth " + (i + 1), expected.charAt(i) == 'T', payloads.getLast() < 0);
    }
  }

  private RobotController king(MapLocation center) throws Exception {
    RobotController rc = mock(RobotController.class);
    when(rc.getLocation()).thenReturn(center);
    when(rc.isActionReady()).thenReturn(true);
    when(rc.getGlobalCheese()).thenReturn(10000);
    when(rc.getCurrentRatCost()).thenReturn(10);
    when(rc.getRoundNum()).thenReturn(1);
    when(rc.canBuildRat(any())).thenReturn(true);
    doAnswer(
            invocation -> {
              // Widely spaced IDs make a consecutive-ID handoff fail this test.
              nextId += 173;
              return null;
            })
        .when(rc)
        .buildRat(any());
    when(rc.senseRobotAtLocation(any()))
        .thenAnswer(
            invocation ->
                new RobotInfo(
                    nextId,
                    Team.A,
                    UnitType.BABY_RAT,
                    100,
                    invocation.getArgument(0),
                    Direction.NORTH,
                    0,
                    0,
                    null));
    when(rc.squeak(anyInt()))
        .thenAnswer(
            invocation -> {
              payloads.add(invocation.getArgument(0));
              return true;
            });
    return rc;
  }
}
