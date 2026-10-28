package ratplayer_sprint1;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/** Guard decisions use engine-consistent prices; engine tests establish actual population changes. */
public class RatKingSpawningTest {
  private RobotController rc;
  private final MapLocation candidate = new MapLocation(10, 12);

  @Before
  public void setUp() {
    Config.reset();
    rc = mock(RobotController.class);
    when(rc.isActionReady()).thenReturn(true);
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + 40);
    when(rc.getCurrentRatCost()).thenReturn(40);
    when(rc.getLocation()).thenReturn(new MapLocation(10, 10));
    when(rc.canBuildRat(candidate)).thenReturn(true);
    RatKing.locationPublished = false;
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
  }

  @After
  public void tearDown() {
    Config.reset();
  }

  @Test
  public void allowsEachPriceBelowCapAndChecksLegalityImmediately() throws Exception {
    // Prices for populations 0, 4, 8, and 12; each call attempts exactly one baby.
    for (int cost : new int[] {10, 20, 30, 40}) {
      setUp();
      when(rc.getCurrentRatCost()).thenReturn(cost);
      RatKing.spawnBabyRat(rc);
      InOrder order = inOrder(rc);
      order.verify(rc).canBuildRat(candidate);
      order.verify(rc).buildRat(candidate);
      order.verifyNoMoreInteractions();
      verify(rc, times(1)).buildRat(candidate);
    }
  }

  @Test
  public void preservesReserveAtEqualityAndRefusesOneBelow() throws Exception {
    // At the configured reserve, one less than reserve plus cost blocks; equality allows it.
    when(rc.getGlobalCheese())
        .thenReturn(Config.MIN_CHEESE_RESERVE + 39, Config.MIN_CHEESE_RESERVE + 40);
    RatKing.spawnBabyRat(rc);
    verify(rc, never()).buildRat(any());
    RatKing.spawnBabyRat(rc);
    verify(rc, times(1)).buildRat(candidate);
  }

  @Test
  public void stopsAtCapAndReplacesSeveralUnseenLossesOverLaterTurns() throws Exception {
    // Reaching the configured cap blocks recruitment; a later price drop permits replacements.
    when(rc.getGlobalCheese()).thenReturn(10000);
    int capCost = spawnCostForPopulation(Config.MAX_BABY_RATS);
    int belowCapCost = spawnCostForPopulation(Config.MAX_BABY_RATS - 1);
    when(rc.getCurrentRatCost())
        .thenReturn(
            capCost,
            capCost + GameConstants.BUILD_ROBOT_COST_INCREASE,
            belowCapCost,
            belowCapCost,
            capCost);
    RatKing.spawnBabyRat(rc);
    RatKing.spawnBabyRat(rc);
    verify(rc, never()).canBuildRat(any());
    RatKing.spawnBabyRat(rc);
    RatKing.spawnBabyRat(rc);
    RatKing.spawnBabyRat(rc);
    verify(rc, times(2)).buildRat(candidate);
  }

  @Test
  public void readinessAndBlockedCandidateReturnWithoutBuilding() throws Exception {
    // Cooldown skips even the price query. Blocked/off-map candidates use the same legality guard.
    when(rc.isActionReady()).thenReturn(false);
    RatKing.spawnBabyRat(rc);
    verify(rc, never()).getCurrentRatCost();
    when(rc.isActionReady()).thenReturn(true);
    when(rc.canBuildRat(candidate)).thenReturn(false);
    RatKing.spawnBabyRat(rc);
    verify(rc, never()).buildRat(any());
    when(rc.canBuildRat(candidate)).thenReturn(true);
    RatKing.spawnBabyRat(rc);
    verify(rc, times(1)).buildRat(candidate);
  }

  @Test
  public void triesEightDirectionsClockwiseAndStopsAtFirstLegalSpawn() throws Exception {
    // Verify N, NE, E, SE, S, SW, W, NW ordering and stop at the first engine-approved tile.
    MapLocation[] clockwise = {
      candidate,
      new MapLocation(12, 12),
      new MapLocation(12, 10),
      new MapLocation(12, 8),
      new MapLocation(10, 8),
      new MapLocation(8, 8),
      new MapLocation(8, 10),
      new MapLocation(8, 12)
    };
    for (int opening = 0; opening < clockwise.length; opening++) {
      setUp();
      when(rc.canBuildRat(candidate)).thenReturn(false);
      when(rc.canBuildRat(clockwise[opening])).thenReturn(true);
      RatKing.spawnBabyRat(rc);
      InOrder order = inOrder(rc);
      for (int i = 0; i <= opening; i++) {
        order.verify(rc).canBuildRat(clockwise[i]);
      }
      order.verify(rc).buildRat(clockwise[opening]);
      order.verifyNoMoreInteractions();
      verify(rc, times(1)).buildRat(any());
    }
  }

  @Test
  public void allBlockedChecksEightTilesWithoutBuilding() throws Exception {
    // A trapped king finishes its bounded scan without spending cheese or attempting a build.
    when(rc.canBuildRat(candidate)).thenReturn(false);
    RatKing.spawnBabyRat(rc);
    InOrder order = inOrder(rc);
    order.verify(rc).canBuildRat(candidate);
    order.verify(rc).canBuildRat(new MapLocation(12, 12));
    order.verify(rc).canBuildRat(new MapLocation(12, 10));
    order.verify(rc).canBuildRat(new MapLocation(12, 8));
    order.verify(rc).canBuildRat(new MapLocation(10, 8));
    order.verify(rc).canBuildRat(new MapLocation(8, 8));
    order.verify(rc).canBuildRat(new MapLocation(8, 10));
    order.verify(rc).canBuildRat(new MapLocation(8, 12));
    order.verifyNoMoreInteractions();
    verify(rc, never()).buildRat(any());
  }

  @Test
  public void supportsEightZeroAndIndependentReserveLimits() throws Exception {
    // Seven babies have price 20; eight have price 30. Reserve changes require no logic edit.
    when(rc.getCurrentRatCost()).thenReturn(30, 20, 10, 20, 20);
    Config.MAX_BABY_RATS = 8;
    RatKing.spawnBabyRat(rc);
    verify(rc, never()).buildRat(any());
    RatKing.spawnBabyRat(rc);
    Config.MAX_BABY_RATS = 0;
    RatKing.spawnBabyRat(rc);
    Config.MAX_BABY_RATS = 8;
    Config.MIN_CHEESE_RESERVE = 900;
    when(rc.getGlobalCheese())
        .thenReturn(Config.MIN_CHEESE_RESERVE + 19, Config.MIN_CHEESE_RESERVE + 20);
    RatKing.spawnBabyRat(rc);
    RatKing.spawnBabyRat(rc);
    verify(rc, times(2)).buildRat(candidate);
  }

  @Test
  public void rejectsUnsupportedConfigWithoutActions() throws Exception {
    // Reject invalid experiments explicitly, rather than silently rounding the cap.
    for (int cap : new int[] {-4, 15, 17}) {
      Config.MAX_BABY_RATS = cap;
      assertThrows(IllegalArgumentException.class, () -> RatKing.spawnBabyRat(rc));
    }
    Config.MAX_BABY_RATS = Config.DEFAULT_MAX_BABY_RATS;
    Config.MIN_CHEESE_RESERVE = -1;
    assertThrows(IllegalArgumentException.class, () -> RatKing.spawnBabyRat(rc));
    verify(rc, never()).buildRat(any());
  }

  @Test
  public void pickupFundsSpawnDespiteMovementCooldownThroughCurrentTypeDispatch() throws Exception {
    // The complete king decision picks up first; its new global balance funds the spawn.
    when(rc.getType()).thenReturn(UnitType.RAT_KING);
    when(rc.getLocation()).thenReturn(new MapLocation(10, 10));
    when(rc.isMovementReady()).thenReturn(false);
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + 39);
    MapLocation pile = new MapLocation(10, 11);
    when(rc.senseNearbyMapInfos())
        .thenReturn(
            new MapInfo[] {new MapInfo(pile, true, null, false, false, 1, TrapType.NONE, false)});
    when(rc.canPickUpCheese(pile)).thenReturn(true);
    doAnswer(
            invocation -> {
              when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + 40);
              return null;
            })
        .when(rc)
        .pickUpCheese(pile);
    RobotPlayer.playTurn(rc);
    InOrder order = inOrder(rc);
    order.verify(rc).pickUpCheese(pile);
    order.verify(rc).getGlobalCheese();
    order.verify(rc).buildRat(candidate);
    verify(rc, never()).moveForward();
  }

  @Test
  public void buildFailureRecoversOnNextTurnWithoutDisablingSpawns() throws Exception {
    // A failed engine action reaches the existing turn boundary; a later turn can retry.
    when(rc.getType()).thenReturn(UnitType.RAT_KING);
    when(rc.getLocation()).thenReturn(new MapLocation(10, 10));
    when(rc.senseNearbyMapInfos()).thenReturn(new MapInfo[0]);
    org.mockito.Mockito.doThrow(
            new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test spawn failure"))
        .doNothing()
        .when(rc)
        .buildRat(candidate);
    RobotPlayer.playTurn(rc);
    RobotPlayer.playTurn(rc);
    verify(rc, times(2)).buildRat(candidate);
  }

  private static int spawnCostForPopulation(int population) {
    return GameConstants.BUILD_ROBOT_BASE_COST
        + GameConstants.BUILD_ROBOT_COST_INCREASE
            * (population / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
  }
}
