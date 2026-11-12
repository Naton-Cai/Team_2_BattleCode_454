package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Controller scenarios exercise the protocol; the engine fixture checks real geometry and yields. */
public class KingFormationTest {
  private RobotController rc;
  private int[] shared;
  private MapLocation location;

  @Before
  public void setUp() throws Exception {
    KingFormation.reset();
    Config.reset();
    shared = new int[64];
    rc = mock(RobotController.class);
    location = new MapLocation(10, 10);
    when(rc.getID()).thenReturn(100);
    when(rc.getTeam()).thenReturn(Team.A);
    when(rc.getRoundNum()).thenReturn(10);
    when(rc.getLocation()).thenAnswer(call -> location);
    when(rc.getDirection()).thenReturn(Direction.NORTH);
    when(rc.getMapWidth()).thenReturn(30);
    when(rc.getMapHeight()).thenReturn(30);
    when(rc.isActionReady()).thenReturn(true);
    when(rc.getGlobalCheese()).thenReturn(1000);
    when(rc.getCurrentRatCost()).thenReturn(10);
    when(rc.readSharedArray(anyInt())).thenAnswer(call -> shared[call.getArgument(0)]);
    doAnswer(
            call -> {
              int value = call.getArgument(1);
              assertTrue(
                  "Shared values must fit the engine's ten bits", value >= 0 && value <= 1023);
              shared[call.getArgument(0)] = value;
              return null;
            })
        .when(rc)
        .writeSharedArray(anyInt(), anyInt());
    when(rc.squeak(anyInt())).thenReturn(true);
    when(rc.readSqueaks(-1)).thenReturn(new Message[0]);
    when(rc.senseNearbyRobots()).thenReturn(new RobotInfo[0]);
    when(rc.senseNearbyMapInfos()).thenReturn(new MapInfo[0]);
  }

  @After
  public void tearDown() {
    KingFormation.reset();
    Config.reset();
  }

  @Test
  public void explicitRequestAssignsSevenBirthsAndTouchesOnlyTheLock() throws Exception {
    // Canary every other slot, including all ten reclaimed entries and the previously free slot.
    Arrays.fill(shared, 777);
    shared[KingFormation.STATE] = 0;
    MapLocation birth = new MapLocation(10, 12);
    when(rc.canBuildRat(birth)).thenReturn(true);
    when(rc.senseRobotAtLocation(birth)).thenReturn(robot(200, birth));
    RatKing.spawnBabyRat(rc);
    verify(rc, never()).squeak(anyInt());
    verify(rc, never()).writeSharedArray(anyInt(), anyInt());
    clearInvocations(rc);

    RatKing.requestKingFormation();
    for (int slot = 0; slot < 7; slot++) {
      assertTrue(RatKing.spawnBabyRat(rc));
      verify(rc).squeak(birthPacket(slot, birth, birth));
      assertEquals(
          11, shared[KingFormation.STATE]); // Round 10 selects token 11, not a birth count.
    }
    RatKing.spawnBabyRat(rc); // The eighth birth is ordinary.
    verify(rc, times(7)).squeak(anyInt());
    verify(rc, times(1)).writeSharedArray(KingFormation.STATE, 11);
    for (int slot = 0; slot < 64; slot++) {
      if (slot != KingFormation.STATE) assertEquals(777, shared[slot]);
    }
  }

  @Test
  public void blockedAndThrowingBirthDoNotConsumeChosenAssignment() throws Exception {
    RatKing.requestKingFormation();
    assertFalse(RatKing.spawnBabyRat(rc));
    MapLocation birth = new MapLocation(10, 12);
    when(rc.canBuildRat(birth)).thenReturn(true);
    doThrow(new GameActionException(GameActionExceptionType.CANT_DO_THAT, "fixture"))
        .doNothing()
        .when(rc)
        .buildRat(birth);
    assertThrows(GameActionException.class, () -> RatKing.spawnBabyRat(rc));
    when(rc.senseRobotAtLocation(birth)).thenReturn(robot(Integer.MAX_VALUE, birth));
    assertTrue(RatKing.spawnBabyRat(rc));
    verify(rc).squeak(birthPacket(0, birth, birth));
    assertEquals(11, shared[KingFormation.STATE]);
  }

  @Test
  public void secondKingCannotRecruitWhileFirstGroupIsReserved() throws Exception {
    RatKing.requestKingFormation();
    RatKing.spawnBabyRat(rc);
    KingFormation.reset(); // Independent robot-local state, same shared team lock.
    when(rc.getID()).thenReturn(101);
    RatKing.requestKingFormation();
    when(rc.canBuildRat(new MapLocation(10, 12))).thenReturn(true);
    assertTrue(RatKing.spawnBabyRat(rc));
    verify(rc, never()).squeak(anyInt());
    assertEquals(11, shared[KingFormation.STATE]);
  }

  @Test
  public void failedBirthSqueakRetriesByFullIdWithoutAnotherBirth() throws Exception {
    MapLocation birth = new MapLocation(10, 12);
    when(rc.canBuildRat(birth)).thenReturn(true);
    when(rc.senseRobotAtLocation(birth)).thenReturn(robot(Integer.MAX_VALUE, birth));
    when(rc.squeak(anyInt())).thenReturn(false, true);
    RatKing.requestKingFormation();
    assertTrue(RatKing.spawnBabyRat(rc));
    when(rc.isActionReady()).thenReturn(false);
    when(rc.getGlobalCheese()).thenReturn(0);
    assertFalse(RatKing.spawnBabyRat(rc));
    verify(rc).squeak(-1); // Sign bit plus all 31 ID bits; no truncation or shared ID words.
    assertTrue(KingFormation.hasPendingBirth());
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(
                  KingFormation.packet(KingFormation.HELLO, 11, 0, birth),
                  Integer.MAX_VALUE,
                  10,
                  birth)
            });
    assertFalse(RatKing.spawnBabyRat(rc));
    assertFalse(KingFormation.hasPendingBirth());
    verify(rc).squeak(KingFormation.packet(KingFormation.ASSIGN, 11, 0, birth));
    verify(rc, times(1)).buildRat(birth);
  }

  @Test
  public void birthMessageCarriesRoleAndRendezvousAndRoleStaysStable() throws Exception {
    shared[KingFormation.STATE] = 42;
    MapLocation rendezvous = new MapLocation(13, 12);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(birthPacket(2, location, rendezvous), 1, 9, new MapLocation(10, 8))
            });
    KingFormation.assign(rc);
    assertEquals(BabyRole.SPAWNER, BabyRat.role);
    assertEquals(2, KingFormation.index);
    assertEquals(rendezvous, KingFormation.anchor);
    assertEquals(42, KingFormation.group);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(birthPacket(6, location, location), 1, 9, new MapLocation(10, 8))
            });
    KingFormation.assign(rc);
    assertEquals(2, KingFormation.index);
  }

  @Test
  public void addressedRetryWaitsForMatchingParentMetadataWithoutActing() throws Exception {
    shared[KingFormation.STATE] = 42;
    when(rc.getID()).thenReturn(Integer.MAX_VALUE);
    when(rc.readSqueaks(-1)).thenReturn(new Message[] {new Message(-1, 1, 9, location)});
    BabyRat.run(rc);
    assertEquals(BabyRole.SPAWNER, BabyRat.role);
    assertEquals(-1, KingFormation.index);
    verify(rc).squeak(packet(KingFormation.HELLO, 0, location));
    verify(rc, never()).getRawCheese(); // Invited retries wait rather than collecting or fighting.
    int metadata = packet(KingFormation.ASSIGN, 3, new MapLocation(59, 59));
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(
                  metadata, 2, 9, location), // Different sender cannot finish the handshake.
              new Message(
                  KingFormation.packet(KingFormation.ASSIGN, 41, 3, location), 1, 9, location)
            });
    KingFormation.assign(rc);
    assertEquals(-1, KingFormation.index);
    when(rc.readSqueaks(-1)).thenReturn(new Message[] {new Message(metadata, 1, 9, location)});
    KingFormation.assign(rc);
    assertEquals(3, KingFormation.index);
    assertEquals(new MapLocation(59, 59), KingFormation.anchor);
  }

  @Test
  public void staleWrongTileAndOtherRecipientPacketsDoNotAssignOrdinaryBabies() throws Exception {
    shared[KingFormation.STATE] = 42;
    int birth = birthPacket(1, location, location);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(birth, 1, 8, new MapLocation(10, 8)),
              new Message(birth + 1, 1, 9, new MapLocation(10, 8)),
              new Message(Integer.MIN_VALUE | 101, 1, 9, location)
            });
    KingFormation.assign(rc);
    assertNull(BabyRat.role);
  }

  @Test
  public void oldBabyCannotAcceptFutureBirthAndTokensNeverUseZero() throws Exception {
    shared[KingFormation.STATE] = 42;
    KingFormation.assign(rc); // This baby was born on round 9.
    when(rc.getRoundNum()).thenReturn(12);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(birthPacket(1, location, location), 1, 11, new MapLocation(10, 8))
            });
    KingFormation.assign(rc);
    assertNull(BabyRat.role);
    for (int round : new int[] {1022, 1023, 1024}) {
      KingFormation.reset();
      shared[KingFormation.STATE] = 0;
      when(rc.getRoundNum()).thenReturn(round);
      RatKing.requestKingFormation();
      KingFormation.prepare(rc);
      assertEquals(round % 1023 + 1, shared[KingFormation.STATE]);
    }
  }

  @Test
  public void failedLockWriteCanRetryWithoutConsumingRequestOrBirth() throws Exception {
    doThrow(new GameActionException(GameActionExceptionType.CANT_DO_THAT, "interrupted"))
        .doAnswer(
            call -> {
              shared[call.getArgument(0)] = call.getArgument(1);
              return null;
            })
        .when(rc)
        .writeSharedArray(KingFormation.STATE, 11);
    RatKing.requestKingFormation();
    assertThrows(GameActionException.class, () -> RatKing.spawnBabyRat(rc));
    assertEquals(0, shared[KingFormation.STATE]);
    assertFalse(RatKing.spawnBabyRat(rc)); // Still blocked, but the original request reserves now.
    assertEquals(11, shared[KingFormation.STATE]);
    verify(rc, never()).buildRat(any());
  }

  @Test
  public void lastFodderSlotAndMaximumCoordinatesDoNotOverlapMessageFields() throws Exception {
    shared[KingFormation.STATE] = 1023;
    location = new MapLocation(59, 59);
    MapLocation rendezvous = new MapLocation(0, 0);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(birthPacket(6, location, rendezvous), 1, 9, new MapLocation(57, 59))
            });
    KingFormation.assign(rc);
    assertEquals(6, KingFormation.index);
    assertEquals(rendezvous, KingFormation.anchor);
    assertEquals(1023, KingFormation.group);
    MapLocation selected = new MapLocation(58, 58);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(
                  KingFormation.packet(KingFormation.READY, 1023, 0, selected), 200, 9, selected)
            });
    KingFormation.run(rc);
    assertEquals(selected, KingFormation.center);
    verify(rc).squeak(KingFormation.packet(KingFormation.READY, 1023, 6, selected));
  }

  @Test
  public void requestedFormationBypassesCapForOnlyItsSevenBirths() throws Exception {
    MapLocation birth = new MapLocation(10, 12);
    when(rc.canBuildRat(birth)).thenReturn(true);
    when(rc.senseRobotAtLocation(birth)).thenReturn(robot(200, birth));
    when(rc.getGlobalCheese()).thenReturn(10000);
    int stopPrice =
        GameConstants.BUILD_ROBOT_BASE_COST
            + GameConstants.BUILD_ROBOT_COST_INCREASE
                * (Config.MAX_BABY_RATS / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    when(rc.getCurrentRatCost()).thenReturn(stopPrice);
    assertFalse(RatKing.spawnBabyRat(rc)); // Ordinary births remain capped.

    RatKing.requestKingFormation();
    for (int slot = 0; slot < 7; slot++) {
      // Formation births themselves raise population and price above the normal cap.
      when(rc.getCurrentRatCost())
          .thenReturn(
              stopPrice
                  + (slot / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE)
                      * GameConstants.BUILD_ROBOT_COST_INCREASE);
      assertTrue(
          "Formation birth " + slot + " must bypass the strategy cap", RatKing.spawnBabyRat(rc));
      verify(rc).squeak(birthPacket(slot, birth, birth));
    }
    assertFalse(
        RatKing.spawnBabyRat(rc)); // Seven assigned: an eighth cannot inherit the exemption.
    verify(rc, times(7)).buildRat(birth);
  }

  @Test
  public void capExemptionPreservesReserveAtEqualityAndRechecksRisingCosts() throws Exception {
    MapLocation birth = new MapLocation(10, 12);
    when(rc.canBuildRat(birth)).thenReturn(true);
    when(rc.senseRobotAtLocation(birth)).thenReturn(robot(200, birth));
    int cost =
        GameConstants.BUILD_ROBOT_BASE_COST
            + GameConstants.BUILD_ROBOT_COST_INCREASE
                * (Config.MAX_BABY_RATS / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    when(rc.getCurrentRatCost()).thenReturn(cost);
    RatKing.requestKingFormation();
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + cost - 1);
    assertFalse(RatKing.spawnBabyRat(rc));
    verify(rc, never()).buildRat(any());
    assertEquals(0, shared[KingFormation.STATE]);
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + cost);
    assertTrue(RatKing.spawnBabyRat(rc));

    // Being partway through a requested group must not bypass the next birth's higher price.
    int higherCost = cost + GameConstants.BUILD_ROBOT_COST_INCREASE;
    when(rc.getCurrentRatCost()).thenReturn(higherCost);
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + higherCost - 1);
    assertFalse(RatKing.spawnBabyRat(rc));
    verify(rc, times(1)).buildRat(birth);
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + higherCost);
    assertTrue(RatKing.spawnBabyRat(rc));
    verify(rc).squeak(birthPacket(1, birth, birth));
  }

  @Test
  public void waitingRequestCannotBypassCapWhileAnotherKingOwnsTheGroup() throws Exception {
    RatKing.requestKingFormation();
    RatKing.spawnBabyRat(rc); // First king reserves, but all placement checks are blocked.
    int token = shared[KingFormation.STATE];
    KingFormation.reset();
    clearInvocations(rc);
    when(rc.getID()).thenReturn(101);
    when(rc.canBuildRat(new MapLocation(10, 12))).thenReturn(true);
    when(rc.getGlobalCheese()).thenReturn(10000);
    when(rc.getCurrentRatCost())
        .thenReturn(
            GameConstants.BUILD_ROBOT_BASE_COST
                + GameConstants.BUILD_ROBOT_COST_INCREASE
                    * (Config.MAX_BABY_RATS / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE));
    RatKing.requestKingFormation();
    assertFalse(RatKing.spawnBabyRat(rc));
    assertEquals(token, shared[KingFormation.STATE]);
    verify(rc, never()).buildRat(any());
    verify(rc, never()).writeSharedArray(anyInt(), anyInt());
  }

  @Test
  public void chosenSelectsNearestFullySensedEmptyFootprintAndWaitsWhenBlocked() throws Exception {
    chosen();
    MapLocation near = new MapLocation(10, 12);
    MapLocation far = new MapLocation(10, 13);
    when(rc.senseNearbyMapInfos()).thenReturn(new MapInfo[] {tile(far), tile(near)});
    when(rc.onTheMap(any())).thenReturn(true);
    when(rc.canSenseLocation(any())).thenReturn(true);
    when(rc.senseMapInfo(any())).thenAnswer(call -> tile(call.getArgument(0)));
    KingFormation.run(rc);
    assertEquals(near, KingFormation.center);
    verify(rc, never()).becomeRatKing();
    // No passable candidates means bounded turning, not an invented or off-map destination.
    KingFormation.center = null;
    when(rc.canSenseLocation(any())).thenReturn(false);
    when(rc.canTurn(any())).thenReturn(true);
    KingFormation.run(rc);
    assertNull(KingFormation.center);
    verify(rc).turn(Direction.NORTHEAST);
  }

  @Test
  public void followerWaitsForLeaderThenUsesUniqueTileWithoutGatheringOrCombat() throws Exception {
    BabyRat.role = BabyRole.SPAWNER;
    KingFormation.index = 1;
    KingFormation.group = 42;
    KingFormation.anchor = location;
    shared[KingFormation.STATE] = 42;
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(birthPacket(1, location, location), 1, 9, new MapLocation(10, 8))
            });
    KingFormation.assign(rc);
    when(rc.readSqueaks(-1)).thenReturn(new Message[0]);
    KingFormation.run(rc);
    assertNull(KingFormation.center);
    MapLocation selected = new MapLocation(10, 9);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {new Message(packet(KingFormation.READY, 0, selected), 99, 9, selected)});
    when(rc.getRawCheese()).thenReturn(41);
    BabyRat.run(rc);
    assertEquals(selected, KingFormation.center);
    verify(rc).squeak(packet(KingFormation.ARRIVED, 1, selected));
    verify(rc, never()).canTransferCheese(any(), anyInt());
    verify(rc, never()).canAttack(any(), anyInt());
    verify(rc, never()).canPlaceDirt(any());
    verify(rc, never()).canPickUpCheese(any());
    for (int i = 1; i <= 6; i++) {
      assertNotEquals(selected, KingFormation.target(selected, i));
      for (int j = 1; j < i; j++)
        assertNotEquals(KingFormation.target(selected, i), KingFormation.target(selected, j));
    }
  }

  @Test
  public void leaderRequiresSixFreshArrivalsAndChecksPromotionLegalityImmediately()
      throws Exception {
    chosen();
    KingFormation.center = location;
    Message[] arrivals = new Message[6];
    for (int slot = 1; slot <= 6; slot++) {
      arrivals[slot - 1] =
          new Message(
              packet(KingFormation.ARRIVED, slot, location),
              200 + slot,
              9,
              KingFormation.target(location, slot));
    }
    when(rc.readSqueaks(-1)).thenReturn(Arrays.copyOf(arrivals, 5));
    when(rc.canBecomeRatKing()).thenReturn(true);
    KingFormation.run(rc);
    verify(rc, never()).canBecomeRatKing();
    when(rc.readSqueaks(-1)).thenReturn(arrivals);
    when(rc.canBecomeRatKing()).thenReturn(false);
    KingFormation.run(rc);
    verify(rc, never()).becomeRatKing();
    when(rc.canBecomeRatKing()).thenReturn(true);
    KingFormation.run(rc);
    var order = inOrder(rc);
    order.verify(rc, times(2)).canBecomeRatKing();
    order.verify(rc).becomeRatKing();
    when(rc.getRoundNum()).thenReturn(12);
    when(rc.readSqueaks(-1)).thenReturn(new Message[0]);
    KingFormation.run(rc);
    verify(rc, times(2)).canBecomeRatKing(); // Dead/stale members cannot trigger a later attempt.
  }

  @Test
  public void promotedChosenReleasesOnlyItsOwnToken() throws Exception {
    chosen();
    shared[KingFormation.STATE] = 41;
    KingFormation.promoted(rc);
    assertEquals(41, shared[KingFormation.STATE]);
    chosen();
    shared[KingFormation.STATE] = 42;
    KingFormation.promoted(rc);
    assertEquals(0, shared[KingFormation.STATE]);
    assertNull(BabyRat.role);
  }

  @Test
  public void occupiedFootprintRejectsSiteAndNonmemberPreventsPromotion() throws Exception {
    chosen();
    MapLocation site = new MapLocation(10, 12);
    when(rc.senseNearbyMapInfos()).thenReturn(new MapInfo[] {tile(site)});
    when(rc.onTheMap(any())).thenReturn(true);
    when(rc.canSenseLocation(any())).thenReturn(true);
    when(rc.senseMapInfo(any())).thenAnswer(call -> tile(call.getArgument(0)));
    when(rc.senseRobotAtLocation(site)).thenReturn(robot(300, site));
    KingFormation.run(rc);
    assertNull(KingFormation.center);

    // Even with six arrivals, a visible unrelated rat on an unused tile must not be absorbed.
    KingFormation.center = location;
    Message[] arrivals = new Message[6];
    for (int slot = 1; slot <= 6; slot++) {
      arrivals[slot - 1] =
          new Message(
              packet(KingFormation.ARRIVED, slot, location),
              200 + slot,
              9,
              KingFormation.target(location, slot));
    }
    when(rc.readSqueaks(-1)).thenReturn(arrivals);
    when(rc.senseNearbyRobots())
        .thenReturn(new RobotInfo[] {robot(300, location.translate(-1, 0))});
    when(rc.canBecomeRatKing()).thenReturn(true);
    KingFormation.run(rc);
    verify(rc, never()).canBecomeRatKing();
  }

  @Test
  public void siteWithKingClearanceWinsEvenWhenNearerSiteHasMoreOpenTerrain() throws Exception {
    chosen();
    MapLocation near = new MapLocation(10, 12);
    MapLocation clear = new MapLocation(10, 13);
    arrangeSites(near, clear, new MapLocation(10, 10));
    // Registry data covers a stationary allied king even when it is behind the baby's vision.
    when(rc.getLocation()).thenReturn(new MapLocation(10, 9));
    new KingRegistry().publish(rc);
    when(rc.getLocation()).thenAnswer(call -> location);
    KingFormation.run(rc);
    assertEquals(clear, KingFormation.center);
  }

  @Test
  public void openPerimeterWinsOverNearestLegalFootprint() throws Exception {
    chosen();
    MapLocation near = new MapLocation(10, 12);
    MapLocation open = new MapLocation(10, 13);
    // Only the farther center has this known passable approach tile on its outer ring.
    arrangeSites(near, open, new MapLocation(10, 15));
    KingFormation.run(rc);
    assertEquals(open, KingFormation.center);
  }

  @Test
  public void wallsAndUnknownApproachesDoNotCountAndTightSitesRemainFallbacks() throws Exception {
    chosen();
    MapLocation near = new MapLocation(10, 12);
    MapLocation far = new MapLocation(10, 13);
    MapLocation wall = new MapLocation(10, 15);
    arrangeSites(near, far, wall);
    when(rc.senseNearbyMapInfos())
        .thenReturn(
            new MapInfo[] {
              tile(far),
              new MapInfo(wall, false, null, true, false, 0, TrapType.NONE, false),
              tile(near)
            });
    when(rc.senseNearbyRobots())
        .thenReturn(
            new RobotInfo[] {
              new RobotInfo(
                  300,
                  Team.A,
                  UnitType.RAT_KING,
                  500,
                  new MapLocation(13, 11),
                  Direction.NORTH,
                  0,
                  0,
                  null)
            });
    // Both sites are tight. Equal known approach counts preserve distance as the fallback.
    KingFormation.run(rc);
    assertEquals(near, KingFormation.center);
  }

  @Test
  public void followerLearnsCenterFromMemberAndForwardsItWhileTraveling() throws Exception {
    BabyRat.role = BabyRole.SPAWNER;
    KingFormation.index = 1;
    KingFormation.group = 42;
    KingFormation.anchor = location;
    MapLocation site = new MapLocation(12, 12);
    // The leader is out of range; a nearer traveling member forwards its READY announcement.
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {new Message(packet(KingFormation.READY, 2, site), 202, 9, location)});
    KingFormation.run(rc);
    assertEquals(site, KingFormation.center);
    verify(rc).squeak(packet(KingFormation.READY, 1, site));
  }

  @Test
  public void settledArrivalAdvertisesCenterButStaleOrMisplacedArrivalsDoNot() throws Exception {
    BabyRat.role = BabyRole.SPAWNER;
    KingFormation.index = 1;
    KingFormation.group = 42;
    KingFormation.anchor = location;
    MapLocation site = new MapLocation(12, 12);
    int payload = packet(KingFormation.ARRIVED, 2, site);
    when(rc.readSqueaks(-1))
        .thenReturn(
            new Message[] {
              new Message(payload, 202, 8, KingFormation.target(site, 2)),
              new Message(payload, 202, 9, location),
              new Message(KingFormation.packet(KingFormation.READY, 41, 0, site), 202, 9, location)
            });
    KingFormation.run(rc);
    assertNull(KingFormation.center);
    when(rc.readSqueaks(-1))
        .thenReturn(new Message[] {new Message(payload, 202, 9, KingFormation.target(site, 2))});
    KingFormation.run(rc);
    assertEquals(site, KingFormation.center);
  }

  private void arrangeSites(MapLocation near, MapLocation far, MapLocation approach)
      throws Exception {
    when(rc.senseNearbyMapInfos())
        .thenReturn(new MapInfo[] {tile(far), tile(approach), tile(near)});
    when(rc.onTheMap(any())).thenReturn(true);
    when(rc.canSenseLocation(any())).thenReturn(true);
    // The two footprints and one approach are passable; other tiles reject spurious centers.
    when(rc.senseMapInfo(any()))
        .thenAnswer(
            call -> {
              MapLocation tile = call.getArgument(0);
              boolean passable =
                  (Math.abs(tile.x - near.x) <= 1 && Math.abs(tile.y - near.y) <= 1)
                      || (Math.abs(tile.x - far.x) <= 1 && Math.abs(tile.y - far.y) <= 1)
                      || tile.equals(approach);
              return new MapInfo(tile, passable, null, !passable, false, 0, TrapType.NONE, false);
            });
  }

  private int birthPacket(int slot, MapLocation recipient, MapLocation rendezvous) {
    return KingFormation.BIRTH
        | slot << 24
        | KingFormation.pack(rendezvous) << 12
        | KingFormation.pack(recipient);
  }

  private int packet(int kind, int slot, MapLocation target) {
    return KingFormation.packet(kind, 42, slot, target);
  }

  private void chosen() {
    BabyRat.role = BabyRole.SPAWNER;
    KingFormation.index = 0;
    KingFormation.group = 42;
    KingFormation.anchor = location;
  }

  private RobotInfo robot(int id, MapLocation tile) {
    return new RobotInfo(id, Team.A, UnitType.BABY_RAT, 100, tile, Direction.NORTH, 0, 0, null);
  }

  private MapInfo tile(MapLocation tile) {
    return new MapInfo(tile, true, null, false, false, 0, TrapType.NONE, false);
  }
}
