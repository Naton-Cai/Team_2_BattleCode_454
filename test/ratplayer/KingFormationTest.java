package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Controller-level checks of KingFormation's message filtering, timeouts and caps.
 *
 * <p>Mocks cannot prove engine geometry or legality; KingFormationEngineTest covers real
 * promotion. These tests pin the boundaries that are slow or awkward to reach in a real match.
 */
public class KingFormationTest {
  private static final MapLocation HERE = new MapLocation(10, 10);
  private static final MapLocation KING = new MapLocation(8, 8);
  private static final int LEADER = 77;
  private static final int CHOSEN_ID = 500;

  private RobotController rc;

  @Before
  public void setUp() throws Exception {
    KingFormation.reset();
    Config.reset();
    rc = mock(RobotController.class);
    when(rc.getLocation()).thenReturn(HERE);
    when(rc.getTeam()).thenReturn(Team.A);
    when(rc.getMapWidth()).thenReturn(30);
    when(rc.getMapHeight()).thenReturn(30);
    when(rc.readSqueaks(-1)).thenReturn(new Message[0]);
    when(rc.getDirection()).thenReturn(Direction.NORTH);
    // King side: the newborn on HERE is the chosen whose ID the king starts watching.
    when(rc.senseRobotAtLocation(HERE)).thenReturn(baby(CHOSEN_ID, UnitType.BABY_RAT));
    round(100);
  }

  @After
  public void tearDown() {
    KingFormation.reset();
    Config.reset();
  }

  /**
   * Scenario: the king's birth squeak fails (one squeak per turn, spec p.18).
   *
   * <p>Expected: the request stays armed for the next birth instead of being lost silently.
   */
  @Test
  public void failedBirthSqueakKeepsRequestArmed() throws Exception {
    // Arrange
    KingFormation.request();
    when(rc.squeak(anyInt())).thenReturn(false);

    // Act
    KingFormation.born(rc, HERE);

    // Assert: still armed; a later successful squeak consumes it.
    assertTrue(KingFormation.requested());
    when(rc.squeak(anyInt())).thenReturn(true);
    KingFormation.born(rc, HERE);
    assertFalse(KingFormation.requested());
  }

  /** Scenario: an unrequested birth. Expected: no squeak, so the newborn stays ordinary. */
  @Test
  public void unrequestedBirthSendsNothing() throws Exception {
    KingFormation.born(rc, HERE);

    verify(rc, never()).squeak(anyInt());
  }

  /**
   * Scenario: newborn's first turn hears a CHOSEN squeak for its own birth tile from the king.
   *
   * <p>Expected: it becomes the chosen.
   */
  @Test
  public void newbornOnAddressedTileBecomesChosen() throws Exception {
    // Arrange: the king squeaked last round (newborns act the round after birth).
    hear(new Message(KingFormation.CHOSEN | KingFormation.pack(HERE), 1, 99, KING));

    // Act
    KingFormation.assign(rc);

    // Assert
    assertEquals(BabyRole.CHOSEN, BabyRat.role);
  }

  /**
   * Scenario: CHOSEN squeaks that must not assign this baby: another tile, a stale round, or a
   * valid message arriving after this baby's first turn.
   *
   * <p>Expected: it stays ordinary in all three cases, so an older baby is never reassigned.
   */
  @Test
  public void chosenSqueakForAnotherTileStaleRoundOrLaterTurnIsIgnored() throws Exception {
    // Arrange/act: wrong tile and stale (two rounds old) message on the first turn.
    MapLocation other = HERE.add(Direction.NORTH);
    hear(
        new Message(KingFormation.CHOSEN | KingFormation.pack(other), 1, 99, KING),
        new Message(KingFormation.CHOSEN | KingFormation.pack(HERE), 1, 98, KING));
    KingFormation.assign(rc);
    assertEquals(null, BabyRat.role);

    // Act: a valid-looking message on a later turn. The birth check already ran.
    round(101);
    hear(new Message(KingFormation.CHOSEN | KingFormation.pack(HERE), 1, 100, KING));
    KingFormation.assign(rc);

    // Assert
    assertEquals(null, BabyRat.role);
  }

  /**
   * Scenario: an ordinary baby hears RECRUIT, then the recruiter goes silent (died or promoted).
   *
   * <p>Expected: it joins as fodder with the advertised site, and resumes ordinary work once the
   * silence exceeds FORMATION_LISTEN_ROUNDS, not before.
   */
  @Test
  public void fodderJoinsThenReleasesAfterSilence() throws Exception {
    // Arrange/act: hear the recruiter.
    MapLocation site = new MapLocation(12, 12);
    hear(recruit(LEADER, 100, site));
    KingFormation.assign(rc);
    assertEquals(BabyRole.FODDER, BabyRat.role);
    assertEquals(site, KingFormation.site);

    // Act: silence exactly at the limit keeps the role.
    hear();
    round(100 + Config.FORMATION_LISTEN_ROUNDS);
    KingFormation.assign(rc);
    assertEquals(BabyRole.FODDER, BabyRat.role);

    // Act: one more silent round releases.
    round(101 + Config.FORMATION_LISTEN_ROUNDS);
    KingFormation.assign(rc);

    // Assert
    assertEquals(null, BabyRat.role);
    assertEquals(null, KingFormation.site);
  }

  /** Scenario: fodder already on a ring tile. Expected: it holds still (no move attempted). */
  @Test
  public void fodderOnRingTileHoldsPosition() throws Exception {
    // Arrange: site one tile east of this baby.
    hear(recruit(LEADER, 100, HERE.add(Direction.EAST)));
    KingFormation.assign(rc);

    // Act
    KingFormation.runFodder(rc);

    // Assert
    verify(rc, never()).move(any());
  }

  /**
   * Scenario: fodder approaching a ring where every visible tile already holds an allied baby.
   *
   * <p>Expected: it leaves (extra babies would only be absorbed) and ignores that recruiter for
   * FORMATION_IGNORE_ROUNDS, while a different recruiter can still enlist it.
   */
  @Test
  public void fullRingReleasesFodderAndIgnoresThatRecruiter() throws Exception {
    // Arrange: site three tiles away; all eight ring tiles are sensed and held by allies.
    MapLocation site = HERE.translate(3, 0);
    when(rc.onTheMap(any())).thenReturn(true);
    when(rc.canSenseLocation(any())).thenReturn(true);
    when(rc.sensePassability(any())).thenReturn(true);
    when(rc.isLocationOccupied(any())).thenReturn(true);
    when(rc.senseRobotAtLocation(any()))
        .thenReturn(
            new RobotInfo(5, Team.A, UnitType.BABY_RAT, 100, site, Direction.NORTH, 0, 0, null));
    hear(recruit(LEADER, 100, site));
    KingFormation.assign(rc);

    // Act
    KingFormation.runFodder(rc);

    // Assert: released, and the same recruiter is ignored next round.
    assertEquals(null, BabyRat.role);
    round(101);
    hear(recruit(LEADER, 101, site));
    KingFormation.assign(rc);
    assertEquals(null, BabyRat.role);

    // Assert: a different recruiter is still heard.
    hear(recruit(LEADER + 1, 101, site));
    KingFormation.assign(rc);
    assertEquals(BabyRole.FODDER, BabyRat.role);
  }

  /**
   * Scenario: a chosen that never assembles a ring.
   *
   * <p>Expected: it keeps its role through FORMATION_TIMEOUT_ROUNDS and gives up one round later.
   */
  @Test
  public void chosenGivesUpAfterTimeout() throws Exception {
    // Arrange
    becomeChosen();

    // Act/assert: at the limit it is still chosen.
    round(100 + Config.FORMATION_TIMEOUT_ROUNDS);
    KingFormation.assign(rc);
    assertEquals(BabyRole.CHOSEN, BabyRat.role);

    // Act/assert: past the limit it is released.
    round(101 + Config.FORMATION_TIMEOUT_ROUNDS);
    KingFormation.assign(rc);
    assertEquals(null, BabyRat.role);
  }

  /**
   * Scenario: two living kings around the round-1200 cutoff (spec v1.2.6 p.14/p.25).
   *
   * <p>Expected: at round 1200 the chosen keeps trying; at 1201 promotion is impossible, so it
   * gives up immediately instead of waiting out the timeout.
   */
  @Test
  public void chosenGivesUpWhenEngineKingCapMakesPromotionImpossible() throws Exception {
    // Arrange: chosen born at round 1100 (within its timeout for the whole test).
    round(1100);
    hear(new Message(KingFormation.CHOSEN | KingFormation.pack(HERE), 1, 1099, KING));
    KingFormation.assign(rc);
    assertEquals(BabyRole.CHOSEN, BabyRat.role);

    // Act/assert: round 1200 is not past the cutoff.
    publishKings(1200, 2);
    KingFormation.assign(rc);
    assertEquals(BabyRole.CHOSEN, BabyRat.role);

    // Act/assert: round 1201 with two kings.
    publishKings(1201, 2);
    KingFormation.assign(rc);
    assertEquals(null, BabyRat.role);
  }

  /** Scenario: five living kings at any round. Expected: the chosen gives up (hard cap). */
  @Test
  public void chosenGivesUpAtFiveKings() throws Exception {
    becomeChosen();

    publishKings(150, GameConstants.MAX_NUMBER_OF_RAT_KINGS);
    KingFormation.assign(rc);

    assertEquals(null, BabyRat.role);
  }

  /**
   * Scenario: the population cap is reached (cost >= stop price) on the recruiting king.
   *
   * <p>Expected: without a request nothing is built; with a request exactly one baby is built
   * above the cap and told it is chosen; the next attempt is capped again.
   */
  @Test
  public void requestExemptsExactlyOneBirthFromTheCap() throws Exception {
    // Arrange: cap zero means the base price already meets the stop price.
    Config.MAX_BABY_RATS = 0;
    MapLocation spawn = new MapLocation(12, 12); // Two tiles toward map center from (10,10).
    when(rc.isActionReady()).thenReturn(true);
    when(rc.getCurrentRatCost()).thenReturn(GameConstants.BUILD_ROBOT_BASE_COST);
    when(rc.getGlobalCheese()).thenReturn(Config.MIN_CHEESE_RESERVE + 100);
    when(rc.canBuildRat(spawn)).thenReturn(true);
    when(rc.squeak(anyInt())).thenReturn(true);
    when(rc.senseRobotAtLocation(spawn)).thenReturn(baby(CHOSEN_ID, UnitType.BABY_RAT));

    // Act/assert: capped without a request.
    assertFalse(RatKing.spawnBabyRat(rc));

    // Act/assert: the request allows one birth and its CHOSEN squeak.
    KingFormation.request();
    assertTrue(RatKing.spawnBabyRat(rc));
    verify(rc).buildRat(spawn);
    verify(rc).squeak(KingFormation.CHOSEN | KingFormation.pack(spawn));

    // Act/assert: consumed; capped again.
    assertFalse(RatKing.spawnBabyRat(rc));
  }

  /**
   * Scenario: the engine says the chosen can become a king (enough rats, cheese, clear 3x3).
   *
   * <p>Expected: the chosen promotes immediately, wherever it stands, and clears its baby role so
   * next turn's type dispatch runs it as a king.
   */
  @Test
  public void chosenPromotesWheneverTheEngineAllows() throws Exception {
    // Arrange
    becomeChosen();
    when(rc.canBecomeRatKing()).thenReturn(true);

    // Act
    KingFormation.runChosen(rc);

    // Assert
    verify(rc).becomeRatKing();
    assertEquals(null, BabyRat.role);
    verify(rc, never()).squeak(anyInt());
  }

  /**
   * Scenario: two open candidate sites in view, no kings or cats.
   *
   * <p>Expected: the nearer one is chosen and advertised in the RECRUIT squeak.
   */
  @Test
  public void chosenSelectsNearestOpenSiteAndRecruits() throws Exception {
    // Arrange
    becomeChosen();
    openTerrain(new MapLocation(10, 13), new MapLocation(10, 14));

    // Act
    KingFormation.runChosen(rc);

    // Assert
    assertEquals(new MapLocation(10, 13), KingFormation.site);
    verify(rc).squeak(KingFormation.RECRUIT | KingFormation.pack(new MapLocation(10, 13)));
  }

  /**
   * Scenario: the nearer candidate is within FORMATION_KING_CLEARANCE of a published king.
   *
   * <p>Expected: it is skipped for the farther candidate, so a king stepping toward the site does
   * not immediately make the site illegal (a new king may not overlap a king, spec p.14).
   */
  @Test
  public void chosenSkipsSitesTooCloseToAKing() throws Exception {
    // Arrange: king 0 at (10,9) is 4 steps from (10,13) (too close) and 5 from (10,14).
    becomeChosen();
    openTerrain(new MapLocation(10, 13), new MapLocation(10, 14));
    publishKingAt(100, new MapLocation(10, 9));

    // Act
    KingFormation.runChosen(rc);

    // Assert
    assertEquals(new MapLocation(10, 14), KingFormation.site);
  }

  /**
   * Scenario: the only candidate's ring touches the map edge (y = 1 puts y - 2 off the map).
   *
   * <p>Expected: rejected; edge rings let early fodder seal off the remaining ring tiles. With no
   * site, the chosen explores and stays quiet rather than advertising nothing.
   */
  @Test
  public void chosenRejectsEdgeSitesAndExploresQuietly() throws Exception {
    // Arrange
    becomeChosen();
    openTerrain(new MapLocation(10, 1));
    when(rc.canTurn(any())).thenReturn(true);

    // Act
    KingFormation.runChosen(rc);

    // Assert
    assertEquals(null, KingFormation.site);
    verify(rc, never()).squeak(anyInt());
    verify(rc).turn(Direction.EAST); // Cone sweep: two 45-degree steps from NORTH.
  }

  /**
   * Scenario: one of the site's nine tiles gets blocked after the chosen picked it (e.g. dirt).
   *
   * <p>Expected: the chosen re-selects a different open site the same turn.
   */
  @Test
  public void chosenReplacesASiteThatBecomesBlocked() throws Exception {
    // Arrange: commit to (10,13).
    becomeChosen();
    openTerrain(new MapLocation(10, 13), new MapLocation(14, 13));
    KingFormation.runChosen(rc);
    assertEquals(new MapLocation(10, 13), KingFormation.site);

    // Act: a ring tile of (10,13) turns impassable.
    when(rc.sensePassability(new MapLocation(9, 13))).thenReturn(false);
    round(101);
    KingFormation.runChosen(rc);

    // Assert
    assertEquals(new MapLocation(14, 13), KingFormation.site);
  }

  /**
   * Scenario: the chosen cannot reach its site (center occupied, path blocked).
   *
   * <p>Expected: it keeps the site through FORMATION_TRAVEL_ROUNDS, then tries a different one.
   */
  @Test
  public void chosenReplacesAnUnreachableSiteAfterTravelBudget() throws Exception {
    // Arrange: commit at round 100; mocks never move the chosen.
    becomeChosen();
    openTerrain(new MapLocation(10, 13), new MapLocation(14, 13));
    KingFormation.runChosen(rc);

    // Act/assert: at the limit it keeps the site.
    round(100 + Config.FORMATION_TRAVEL_ROUNDS);
    KingFormation.runChosen(rc);
    assertEquals(new MapLocation(10, 13), KingFormation.site);

    // Act/assert: one round later it switches.
    round(101 + Config.FORMATION_TRAVEL_ROUNDS);
    KingFormation.runChosen(rc);
    assertEquals(new MapLocation(14, 13), KingFormation.site);
  }

  /**
   * Scenario: the chosen stands on its site but the ring never completes (sealed tiles, traffic).
   *
   * <p>Expected: after FORMATION_SITE_ROUNDS it tries another site; fodder follow the new RECRUIT.
   */
  @Test
  public void chosenOnSiteMovesOnAfterSiteBudget() throws Exception {
    // Arrange: the chosen already stands on the nearest site, its own tile.
    becomeChosen();
    openTerrain(HERE, new MapLocation(10, 13));
    KingFormation.runChosen(rc);
    assertEquals(HERE, KingFormation.site);

    // Act
    round(101 + Config.FORMATION_SITE_ROUNDS);
    KingFormation.runChosen(rc);

    // Assert
    assertEquals(new MapLocation(10, 13), KingFormation.site);
  }

  /** Visible open terrain around the given candidates; every in-bounds tile is sensed and open. */
  private void openTerrain(MapLocation... candidates) throws Exception {
    MapInfo[] infos = new MapInfo[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      infos[i] = new MapInfo(candidates[i], true, null, false, false, 0, TrapType.NONE, false);
    }
    when(rc.senseNearbyMapInfos()).thenReturn(infos);
    when(rc.senseNearbyRobots()).thenReturn(new RobotInfo[0]);
    when(rc.onTheMap(any()))
        .thenAnswer(
            invocation -> {
              MapLocation tile = invocation.getArgument(0);
              return tile.x >= 0 && tile.y >= 0 && tile.x < 30 && tile.y < 30;
            });
    when(rc.canSenseLocation(any())).thenReturn(true);
    when(rc.sensePassability(any())).thenReturn(true);
  }

  /** Publish king 0 at {@code center} with a fresh heartbeat for {@code now}. */
  private void publishKingAt(int now, MapLocation center) throws Exception {
    when(rc.readSharedArray(0)).thenReturn(center.x);
    when(rc.readSharedArray(1)).thenReturn(center.y + 64 * (now / 512));
    when(rc.readSharedArray(2)).thenReturn(now % 512 + 1);
  }

  /**
   * Scenario: the king saw its chosen last round one diagonal step from its center and now cannot.
   *
   * <p>Expected: the request is re-armed. A live baby moves at most one tile per round, so it
   * could not have walked out of the king's view in one round: it died, typically to a cat.
   */
  @Test
  public void kingRearmsWhenANearbyChosenVanishes() throws Exception {
    // Arrange: seen at round 101 one diagonal step from the king's center.
    bornChosen();
    seeChosenAt(101, HERE.translate(1, 1));

    // Act: gone at round 102.
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(false);
    round(102);
    KingFormation.watch(rc);

    // Assert
    assertTrue(KingFormation.requested());
  }

  /**
   * Scenario: the chosen was last seen too far away to be sure (distance squared 13, limit 12), or the
   * king missed a round of observation, and then it is gone.
   *
   * <p>Expected: no re-arm. It may simply have walked out of view; sites sit near the vision edge,
   * so treating absence as death caused false retries in the engine fixture.
   */
  @Test
  public void farOrStaleSightingIsNotTreatedAsDeath() throws Exception {
    // Arrange/act: seen 3 east and 2 north of the king (distance squared 13), gone next round.
    bornChosen();
    seeChosenAt(101, HERE.translate(3, 2));
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(false);
    round(102);
    KingFormation.watch(rc);
    assertFalse(KingFormation.requested());

    // Arrange/act: seen close at 110, but the next observation is at 112 (a round skipped).
    seeChosenAt(110, HERE.translate(1, 0));
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(false);
    round(112);
    KingFormation.watch(rc);

    // Assert
    assertFalse(KingFormation.requested());
  }

  /**
   * Scenario: every replacement chosen also dies next to the king (a cat-heavy map).
   *
   * <p>Expected: at most FORMATION_RETRIES automatic re-requests, so the king does not keep
   * feeding cap-exempt babies to cats.
   */
  @Test
  public void automaticRetriesAreCappedPerRequest() throws Exception {
    // Arrange: each chosen is born right next to the king and is gone the next round.
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(false);
    bornChosen();
    int now = 100;

    // Act: lose the original and each replacement.
    for (int retry = 0; retry < Config.FORMATION_RETRIES; retry++) {
      round(++now);
      KingFormation.watch(rc);
      assertTrue("Retry " + retry + " re-arms", KingFormation.requested());
      KingFormation.born(rc, HERE); // The replacement birth consumes the request.
    }
    round(++now);
    KingFormation.watch(rc);

    // Assert
    assertFalse(KingFormation.requested());
  }

  /** Scenario: the chosen is seen as a rat king. Expected: watching ends; no later re-arm. */
  @Test
  public void promotedChosenEndsTheWatch() throws Exception {
    // Arrange
    bornChosen();
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(true);
    when(rc.senseRobot(CHOSEN_ID)).thenReturn(baby(CHOSEN_ID, UnitType.RAT_KING));
    round(101);
    KingFormation.watch(rc);

    // Act: it then leaves view for a long time.
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(false);
    round(200);
    KingFormation.watch(rc);

    // Assert
    assertFalse(KingFormation.requested());
  }

  /**
   * Scenario: the chosen stays alive and visible past its own give-up deadline (no fodder).
   *
   * <p>Expected: the king stops watching instead of retrying; a slow area is the strategy's call.
   */
  @Test
  public void liveChosenPastItsTimeoutIsNotRetried() throws Exception {
    // Arrange
    bornChosen();
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(true);
    when(rc.senseRobot(CHOSEN_ID)).thenReturn(baby(CHOSEN_ID, UnitType.BABY_RAT));
    round(101 + Config.FORMATION_TIMEOUT_ROUNDS);
    KingFormation.watch(rc);

    // Act: the ordinary baby later wanders off.
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(false);
    round(200 + Config.FORMATION_TIMEOUT_ROUNDS);
    KingFormation.watch(rc);

    // Assert
    assertFalse(KingFormation.requested());
  }

  /** King-side: request and a successful chosen birth at HERE on round 100. */
  private void bornChosen() throws Exception {
    when(rc.squeak(anyInt())).thenReturn(true);
    when(rc.senseRobotAtLocation(HERE)).thenReturn(baby(CHOSEN_ID, UnitType.BABY_RAT));
    KingFormation.request();
    KingFormation.born(rc, HERE);
    assertFalse(KingFormation.requested());
  }

  /** The king senses its chosen (still a baby) at {@code location} on {@code now}. */
  private void seeChosenAt(int now, MapLocation location) throws Exception {
    round(now);
    when(rc.canSenseRobot(CHOSEN_ID)).thenReturn(true);
    when(rc.senseRobot(CHOSEN_ID))
        .thenReturn(
            new RobotInfo(
                CHOSEN_ID, Team.A, UnitType.BABY_RAT, 100, location, Direction.NORTH, 0, 0, null));
    KingFormation.watch(rc);
  }

  private static RobotInfo baby(int id, UnitType type) {
    return new RobotInfo(id, Team.A, type, 100, HERE, Direction.NORTH, 0, 0, null);
  }

  private void becomeChosen() throws Exception {
    hear(new Message(KingFormation.CHOSEN | KingFormation.pack(HERE), 1, 99, KING));
    KingFormation.assign(rc);
    assertEquals(BabyRole.CHOSEN, BabyRat.role);
    hear();
  }

  /** Write fresh KingRegistry heartbeats for kings 0..count-1 (layout in KingRegistry.publish). */
  private void publishKings(int now, int count) throws Exception {
    round(now);
    for (int id = 0; id < KingRegistry.CAPACITY; id++) {
      int slot = KingRegistry.slot(id);
      boolean alive = id < count;
      when(rc.readSharedArray(slot)).thenReturn(alive ? 3 : 0);
      when(rc.readSharedArray(slot + 1)).thenReturn(alive ? 3 + 64 * (now / 512) : 0);
      when(rc.readSharedArray(slot + 2)).thenReturn(alive ? now % 512 + 1 : 0);
    }
  }

  private static Message recruit(int sender, int round, MapLocation site) {
    return new Message(KingFormation.RECRUIT | KingFormation.pack(site), sender, round, site);
  }

  private void hear(Message... messages) {
    when(rc.readSqueaks(-1)).thenReturn(messages);
  }

  private void round(int round) {
    when(rc.getRoundNum()).thenReturn(round);
  }
}
