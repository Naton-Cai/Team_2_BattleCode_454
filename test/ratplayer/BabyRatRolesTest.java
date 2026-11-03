package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/** Role decisions reuse the existing attack/pickup helpers and never form a king. */
public class BabyRatRolesTest {
  private TestRobotController rc;
  private static final MapLocation ORIGIN = new MapLocation(10, 10);

  @Before
  public void setUp() {
    Config.reset();
    BabyRat.role = null;
    BabyRat.attackedThisTurn = false;
    BabyRat.kingChecked = false;
    BabyRat.kingLoc = null;
    BabyRat.cheeseTile = null;
    BabyRat.pickedUpCheese = false;
    rc = new TestRobotController();
    rc.setLocation(ORIGIN);
    rc.setDirection(Direction.NORTH);
    rc.setRoundNum(12);
    rc.setCooperation(true);
    rc.setActionReady(true);
    rc.setGlobalCheese(1000);
    rc.setCanPlaceCatTrap(true);
    rc.setCanPlaceRatTrap(true);
  }

  @After
  public void tearDown() {
    BabyRat.role = null;
    Config.reset();
  }

  @Test
  public void addressedSqueakAssignsOnceEvenWhenParentIsBehindBaby() throws Exception {
    // Newborn faces away from its parent. Vision must not be required for the handoff.
    rc.setSqueaks(
        new Message[] {
          new Message(rc.getID() | Integer.MIN_VALUE, 12345, 11, new MapLocation(10, 8))
        });
    BabyRat.assignRole(rc);
    assertEquals(Config.BabyRole.TRAPPER, BabyRat.role);

    rc.setBackstabbingTeam(Team.B);
    rc.setSqueaks(new Message[] {new Message(rc.getID(), 12345, 12, new MapLocation(10, 8))});
    BabyRat.assignRole(rc);
    assertEquals(Config.BabyRole.TRAPPER, BabyRat.role);
  }

  @Test
  public void startingBabyIgnoresOtherRecipientsOldMessagesAndDistantSources() throws Exception {
    // Address, birth freshness and spawn range prevent adopting somebody else's old role.
    rc.setSqueaks(
        new Message[] {
          new Message((rc.getID() + 1) | Integer.MIN_VALUE, 3, 12, ORIGIN),
          new Message(rc.getID() | Integer.MIN_VALUE, 3, 10, ORIGIN),
          new Message(rc.getID() | Integer.MIN_VALUE, 3, 12, new MapLocation(1, 1))
        });
    BabyRat.assignRole(rc);
    assertEquals(Config.BabyRole.GATHERER, BabyRat.role);
  }

  @Test
  public void gathererAndSpawnerSkipRoutineTraps() throws Exception {
    // Spawner deliberately shares the gathering fallback; neither role builds a trap.
    Config.BABY_ACTION_ORDER = new Config.BabyAction[] {Config.BabyAction.TRAP};
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.NEUTRAL, UnitType.CAT, 10, 13)});
    for (Config.BabyRole role :
        new Config.BabyRole[] {Config.BabyRole.GATHERER, Config.BabyRole.SPAWNER}) {
      BabyRat.role = role;
      BabyRat.tryConfiguredActions(rc);
    }
    assertTrue(rc.calls().isEmpty());
  }

  @Test
  public void explicitlySelectedSpawnerGathersWithoutFormation() throws Exception {
    Config.BABY_STARTING_ROLE = Config.BabyRole.SPAWNER;
    rc.setCanBecomeRatKing(true);
    rc.setCanMove(true);
    BabyRat.assignRole(rc);
    BabyRat.cheeseTile = new MapLocation(14, 10);
    BabyRat.move(rc);
    assertEquals(Config.BabyRole.SPAWNER, BabyRat.role);
    assertEquals("move:EAST", rc.calls().getLast());
    assertFalse(rc.calls().stream().anyMatch(call -> call.startsWith("becomeRatKing")));
  }

  @Test
  public void cooperationAttackRetainsPriorityOverCatTrap() throws Exception {
    // Existing forward attack wins even when another visible cat offers a legal trap.
    BabyRat.role = Config.BabyRole.TRAPPER;
    rc.setCanSenseRobotAtLocation(true);
    rc.setRobotAtLocation(unit(Team.NEUTRAL, UnitType.CAT, 10, 11));
    rc.setCanAttackWithCheese(true);
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.NEUTRAL, UnitType.CAT, 10, 13)});
    BabyRat.tryConfiguredActions(rc);
    assertTrue(rc.calls().stream().anyMatch(call -> call.startsWith("attack:")));
    assertFalse(rc.calls().stream().anyMatch(call -> call.startsWith("placeCatTrap:")));
  }

  @Test
  public void backstabbingTrapWinsImmediatelyAndAttackRemainsFallback() throws Exception {
    // No shared transition timestamp is needed: the first observed backstab selects rat traps.
    BabyRat.role = Config.BabyRole.TRAPPER;
    rc.setBackstabbingTeam(Team.B);
    rc.setNearbyRobotsOnTeam(new RobotInfo[] {unit(Team.B, UnitType.BABY_RAT, 10, 12)});
    rc.setCanSenseRobotAtLocation(true);
    rc.setRobotAtLocation(unit(Team.NEUTRAL, UnitType.CAT, 10, 11));
    rc.setCanAttackWithCheese(true);
    BabyRat.tryConfiguredActions(rc);
    assertEquals("placeRatTrap:[10, 11]", rc.calls().getFirst());
    assertFalse(BabyRat.attackedThisTurn);

    // An unavailable trap leaves the unchanged cat-first forward attack available.
    rc.setCanPlaceRatTrap(false);
    BabyRat.tryConfiguredActions(rc);
    assertTrue(BabyRat.attackedThisTurn);
  }

  @Test
  public void ratTrapReserveUsesActualTwentyCheeseCost() throws Exception {
    BabyRat.role = Config.BabyRole.TRAPPER;
    rc.setBackstabbingTeam(Team.B);
    rc.setNearbyRobotsOnTeam(new RobotInfo[] {unit(Team.B, UnitType.BABY_RAT, 10, 12)});
    rc.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 19);
    assertFalse(Trap.placeTrap(rc));
    rc.setGlobalCheese(Config.MIN_CHEESE_RESERVE + 20);
    assertTrue(Trap.placeTrap(rc));
  }

  @Test
  public void trapperApproachesVisibleThreatWhileGathererAndSpawnerPursueCheese() throws Exception {
    // Movement decisions use the same navigation helpers; roles change only the objective.
    rc.setCanMove(true);
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.NEUTRAL, UnitType.CAT, 10, 14)});
    BabyRat.cheeseTile = new MapLocation(14, 10);
    BabyRat.role = Config.BabyRole.TRAPPER;
    BabyRat.move(rc);
    assertEquals("move:NORTH", rc.calls().getLast());
    for (Config.BabyRole role :
        new Config.BabyRole[] {Config.BabyRole.GATHERER, Config.BabyRole.SPAWNER}) {
      BabyRat.role = role;
      BabyRat.move(rc);
      assertEquals("move:EAST", rc.calls().getLast());
    }
  }

  @Test
  public void trapperCarryingFortyOneKeepsVisibleThreatAheadOfReturn() throws Exception {
    BabyRat.role = Config.BabyRole.TRAPPER;
    rc.setCanMove(true);
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.B, UnitType.BABY_RAT, 14, 10)});
    rc.setBackstabbingTeam(Team.B);
    BabyRat.kingChecked = true;
    BabyRat.kingLoc = new MapLocation(10, 14);
    rc.setRawCheese(40);
    BabyRat.move(rc);
    assertEquals("move:EAST", rc.calls().getLast());
    rc.setRawCheese(41);
    BabyRat.move(rc);
    assertEquals("move:EAST", rc.calls().getLast());
    // Once the enemy leaves sight, the retained cheese again makes the king the objective.
    rc.setNearbyRobots(new RobotInfo[0]);
    BabyRat.move(rc);
    assertEquals("move:NORTH", rc.calls().getLast());
  }

  @Test
  public void trapperDefersLegalTransferUntilTargetLeavesSightThenSeeksFreshTarget()
      throws Exception {
    prepareCarrier(Config.BabyRole.TRAPPER);
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.B, UnitType.BABY_RAT, 14, 10)});
    BabyRat.run(rc);
    assertEquals(41, rc.getRawCheese());
    assertFalse(rc.calls().stream().anyMatch(call -> call.startsWith("transferCheese:")));
    assertEquals("move:EAST", rc.calls().getLast());

    rc.setNearbyRobots(new RobotInfo[0]);
    BabyRat.run(rc);
    assertEquals(0, rc.getRawCheese());
    assertTrue(rc.calls().contains("transferCheese:[6, 10]:41"));

    // No remembered combat waypoint: a new target becomes the next objective after delivery.
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.B, UnitType.BABY_RAT, 10, 14)});
    BabyRat.run(rc);
    assertEquals("move:NORTH", rc.calls().getLast());
  }

  @Test
  public void gathererStillTransfersWithVisibleEnemyAndCooperatingTrapperIgnoresOpponentRats()
      throws Exception {
    prepareCarrier(Config.BabyRole.GATHERER);
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.B, UnitType.BABY_RAT, 14, 10)});
    BabyRat.run(rc);
    assertEquals(0, rc.getRawCheese());

    // Opposing rats are not combat targets until backstabbing; they must not postpone delivery.
    BabyRat.role = Config.BabyRole.TRAPPER;
    rc.setRawCheese(41);
    rc.setBackstabbingTeam(null);
    rc.setCooperation(true);
    BabyRat.run(rc);
    assertEquals(0, rc.getRawCheese());
    assertEquals(2, rc.calls().stream().filter(call -> call.startsWith("transferCheese:")).count());
  }

  @Test
  public void visibleCooperativeCatDefersTrapperDeliveryButFleeingStillWins() throws Exception {
    prepareCarrier(Config.BabyRole.TRAPPER);
    rc.setBackstabbingTeam(null);
    rc.setCooperation(true);
    rc.setNearbyRobots(new RobotInfo[] {unit(Team.NEUTRAL, UnitType.CAT, 10, 14)});
    BabyRat.run(rc);
    assertEquals(41, rc.getRawCheese());
    assertFalse(rc.calls().stream().anyMatch(call -> call.startsWith("transferCheese:")));
    assertEquals("move:SOUTH", rc.calls().getLast());
  }

  @Test
  public void trapperWithoutTargetKeepsStrictFortyCheeseThreshold() throws Exception {
    prepareCarrier(Config.BabyRole.TRAPPER);
    rc.setRawCheese(40);
    BabyRat.run(rc);
    assertEquals(40, rc.getRawCheese());
    assertFalse(rc.calls().stream().anyMatch(call -> call.startsWith("transferCheese:")));
    rc.setRawCheese(41);
    BabyRat.run(rc);
    assertEquals(0, rc.getRawCheese());
  }

  private void prepareCarrier(Config.BabyRole role) {
    BabyRat.role = role;
    rc.setRawCheese(41);
    rc.setCanMove(true);
    rc.setCanTransferCheese(true);
    rc.setBackstabbingTeam(Team.B);
    rc.setCooperation(false);
    rc.setSharedArrayValue(RatKing.LOCATION_VALID_SLOT, RatKing.KING_LOCATION_PUBLISHED);
    rc.setSharedArrayValue(RatKing.LOCATION_X_SLOT, 6);
    rc.setSharedArrayValue(RatKing.LOCATION_Y_SLOT, 10);
  }

  @Test
  public void kingLookupPrefersNearestSensedAllyWithDeterministicTies() throws Exception {
    // Retain the existing single publication. Local sensing is enough to compare visible kings
    // without adding a registry or future king-formation machinery.
    rc.setSharedArrayValue(RatKing.LOCATION_VALID_SLOT, RatKing.KING_LOCATION_PUBLISHED);
    rc.setSharedArrayValue(RatKing.LOCATION_X_SLOT, 15);
    rc.setSharedArrayValue(RatKing.LOCATION_Y_SLOT, 10);
    rc.setNearbyRobots(
        new RobotInfo[] {
          unit(Team.B, UnitType.RAT_KING, 10, 11),
          unit(Team.A, UnitType.RAT_KING, 12, 10),
          unit(Team.A, UnitType.RAT_KING, 10, 12),
          unit(Team.A, UnitType.RAT_KING, 10, 8),
          unit(Team.A, UnitType.RAT_KING, 8, 10)
        });
    BabyRat.updateRatKingLoc(rc);
    assertEquals(new MapLocation(8, 10), BabyRat.kingLoc);
    rc.setNearbyRobots(
        new RobotInfo[] {
          unit(Team.A, UnitType.RAT_KING, 10, 12), unit(Team.A, UnitType.RAT_KING, 10, 8)
        });
    BabyRat.updateRatKingLoc(rc);
    assertEquals(new MapLocation(10, 8), BabyRat.kingLoc);
  }

  @Test
  public void kingLookupRejectsVisiblyDisprovedPublicationAndEmptyState() throws Exception {
    BabyRat.updateRatKingLoc(rc);
    assertNull(BabyRat.kingLoc);
    rc.setSharedArrayValue(RatKing.LOCATION_VALID_SLOT, RatKing.KING_LOCATION_PUBLISHED);
    rc.setSharedArrayValue(RatKing.LOCATION_X_SLOT, 10);
    rc.setSharedArrayValue(RatKing.LOCATION_Y_SLOT, 13);
    rc.setCanSenseLocation(true);
    BabyRat.updateRatKingLoc(rc);
    assertNull(BabyRat.kingLoc);
    rc.setRobotAtLocation(unit(Team.B, UnitType.RAT_KING, 10, 13));
    BabyRat.updateRatKingLoc(rc);
    assertNull(BabyRat.kingLoc);
  }

  private static RobotInfo unit(Team team, UnitType type, int x, int y) {
    return new RobotInfo(
        300, team, type, type.health, new MapLocation(x, y), Direction.SOUTH, 0, 0, null);
  }
}
