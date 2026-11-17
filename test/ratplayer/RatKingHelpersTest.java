package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/**
 * Covers the helpers extracted from RatKing.move/homeStep/explore/run during the complexity
 * refactor, plus the king-spawn assessment that previously had no test coverage at all.
 *
 * <p>All scenarios use the canned TestRobotController, so they check decisions, not engine
 * geometry. Engine tests (RatKingEngineTest) cover real collisions.
 */
public class RatKingHelpersTest {
  // A spawn-rate round after the spawn round: 1110 % KING_SPAWN_RATE (15) == 0.
  private static final int RATE_ROUND = 1110;

  private TestRobotController rc;

  @Before
  public void setUp() {
    // Fresh king state for every scenario; RatKing keeps its memory in static fields.
    Config.reset();
    KingFormation.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    RatKing.kingRegistry = new KingRegistry();
    RatKing.locationPublished = false;
    RatKing.resetHome();
    Trap.startofBackStabbing = 0;
    rc = new TestRobotController();
    rc.setUnitType(UnitType.RAT_KING);
    rc.setLocation(new MapLocation(10, 10));
    rc.setMapWidth(60);
    rc.setMapHeight(60);
    rc.setMovementReady(true);
    rc.setCooperation(true);
    rc.setSimulateMovement(true);
  }

  @After
  public void tearDown() {
    KingFormation.reset();
    Config.reset();
  }

  // ---------------------------------------------------------------------------------------------
  // terrainBlockedAround: decides between "wait for a robot to move" and "follow the wall".
  // ---------------------------------------------------------------------------------------------

  @Test
  public void interiorFootprintWithNothingSensedIsNotTerrainBlocked() throws Exception {
    // Arrange: every tile around (20,20) is on the map and nothing can be sensed.
    rc.setCanSenseLocation(false);

    // Act + Assert: unknown tiles are not treated as walls, so the king will wait, not detour.
    assertFalse(RatKing.terrainBlockedAround(rc, new MapLocation(20, 20)));
  }

  @Test
  public void footprintTouchingAnyMapEdgeIsTerrainBlocked() throws Exception {
    // Arrange: a 3x3 footprint centered on an edge tile pokes one row/column off the 60x60 map.
    rc.setCanSenseLocation(false);
    MapLocation[] edgeCenters = {
      new MapLocation(0, 20), // west
      new MapLocation(20, 0), // south
      new MapLocation(59, 20), // east
      new MapLocation(20, 59) // north
    };

    // Act + Assert: each of the four bounds checks fires independently.
    for (MapLocation center : edgeCenters) {
      assertTrue("edge at " + center, RatKing.terrainBlockedAround(rc, center));
    }
  }

  @Test
  public void sensedWallBlocksButSensedOpenGroundDoesNot() throws Exception {
    MapLocation interior = new MapLocation(20, 20);
    rc.setCanSenseLocation(true);

    // A sensed impassable tile inside the footprint is a real obstacle.
    rc.setPassable(false);
    assertTrue(RatKing.terrainBlockedAround(rc, interior));

    // The same sensed tiles, but passable: nothing blocks, so only robots are in the way.
    rc.setPassable(true);
    assertFalse(RatKing.terrainBlockedAround(rc, interior));
  }

  // ---------------------------------------------------------------------------------------------
  // nearSearchEdge: explore() only insists on inward steps inside this margin.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void nearSearchEdgeIsInclusiveAtTheMarginOnEverySide() {
    int m = Config.KING_SEARCH_EDGE_MARGIN;
    int size = 60;
    int mid = size / 2;
    // Exactly at the margin counts as near the edge (<=), on each of the four sides.
    assertTrue(RatKing.nearSearchEdge(new MapLocation(m, mid), size, size));
    assertTrue(RatKing.nearSearchEdge(new MapLocation(mid, m), size, size));
    assertTrue(RatKing.nearSearchEdge(new MapLocation(size - 1 - m, mid), size, size));
    assertTrue(RatKing.nearSearchEdge(new MapLocation(mid, size - 1 - m), size, size));
    // One tile further inward on every side is clear of the edge.
    assertFalse(RatKing.nearSearchEdge(new MapLocation(m + 1, m + 1), size, size));
    assertFalse(RatKing.nearSearchEdge(new MapLocation(size - 2 - m, size - 2 - m), size, size));
  }

  // ---------------------------------------------------------------------------------------------
  // turnOrStep: shared "turn toward, else step forward" used by move() and explore().
  // ---------------------------------------------------------------------------------------------

  @Test
  public void turnOrStepTurnsWhenNotFacingAndReportsNoMove() throws Exception {
    // Arrange: facing NORTH (default), asked to go EAST, turning is legal.
    rc.setTurnAllowed(true);

    // Act
    boolean moved = RatKing.turnOrStep(rc, Direction.EAST);

    // Assert: one turn, no step; a turn alone is not a move.
    assertFalse(moved);
    assertEquals(List.of(Direction.EAST), rc.turns());
    assertEquals(0, rc.moveCount());
  }

  @Test
  public void turnOrStepDoesNothingWhenTurnIsIllegal() throws Exception {
    rc.setTurnAllowed(false);

    assertFalse(RatKing.turnOrStep(rc, Direction.EAST));

    // Illegal turn is never attempted (no exception path, no wasted action).
    assertEquals(0, rc.turnCount());
    assertEquals(0, rc.moveCount());
  }

  @Test
  public void turnOrStepWaitsWhenFacingButForwardIsBlocked() throws Exception {
    rc.setForwardAllowed(false);

    assertFalse(RatKing.turnOrStep(rc, Direction.NORTH));

    assertEquals(0, rc.turnCount());
    assertEquals(0, rc.moveCount());
    assertEquals(new MapLocation(10, 10), rc.getLocation());
  }

  @Test
  public void turnOrStepMovesForwardAndPublishesNewCenter() throws Exception {
    // Arrange: already facing NORTH with an open tile ahead.
    rc.setForwardAllowed(true);

    // Act
    boolean moved = RatKing.turnOrStep(rc, Direction.NORTH);

    // Assert: moved one tile, and babies can read the new center from the registry immediately.
    assertTrue(moved);
    assertEquals(new MapLocation(10, 11), rc.getLocation());
    assertEquals(new MapLocation(10, 11), KingRegistry.readPrimary(rc));
  }

  // ---------------------------------------------------------------------------------------------
  // homeStep (through move): occupancy wait vs. wall following.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void robotBlockingHomeDirectionCausesWaitNotDetour() throws Exception {
    // Arrange: home is due north, every step is blocked, but no wall is sensed and we are
    // far from the map edge, so the blocker must be a robot.
    RatKing.home = new MapLocation(10, 30);
    rc.setCanMove(false);
    rc.setCanSenseLocation(false);

    // Act
    RatKing.move(rc);

    // Assert: no turn and no step; the turn counts toward the stall limit instead.
    assertEquals(0, rc.turnCount());
    assertEquals(0, rc.moveCount());
    assertEquals(1, RatKing.stalledTurns);
  }

  @Test
  public void wallBlockingHomeDirectionStartsRightHandWallFollow() throws Exception {
    // Arrange: home is due north behind a sensed wall. Only EAST is open.
    // Wall following starts heading WEST (desired rotated left twice) and probes
    // counterclockwise from NORTH (its right), so the only open probe it finds is EAST.
    RatKing.home = new MapLocation(10, 30);
    rc.setCanSenseLocation(true);
    rc.setPassable(false);
    rc.setCanMove(false);
    rc.setDirectionAllowed(Direction.EAST, true);

    // Act 1: facing NORTH, so the king first turns to the chosen follow heading.
    RatKing.move(rc);
    assertEquals(List.of(Direction.EAST), rc.turns());
    assertEquals(0, rc.moveCount());

    // Act 2: now facing EAST with EAST open, the king steps along the wall.
    RatKing.move(rc);
    assertEquals(new MapLocation(11, 10), rc.getLocation());
    assertEquals(1, rc.moveCount());
  }

  @Test
  public void fullyEnclosedByWallsReturnsNoStep() throws Exception {
    // Arrange: wall ahead and no open direction at all; the 8-way probe must give up cleanly.
    RatKing.home = new MapLocation(10, 30);
    rc.setCanSenseLocation(true);
    rc.setPassable(false);
    rc.setCanMove(false);

    RatKing.move(rc);

    assertEquals(0, rc.turnCount());
    assertEquals(0, rc.moveCount());
    assertEquals(1, RatKing.stalledTurns);
  }

  // ---------------------------------------------------------------------------------------------
  // assessForKingSpawn: previously 0% covered.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void requestsFormationWhenBelowTargetOnRateRound() throws Exception {
    // Arrange: no king records at all, target of one.
    rc.setRoundNum(RATE_ROUND);

    RatKing.assessForKingSpawn(rc, 1);

    assertTrue(KingFormation.requested());
  }

  @Test
  public void doesNotRequestOffTheRateRound() throws Exception {
    // One round past the rate boundary: still below target, but requests are rate-limited.
    rc.setRoundNum(RATE_ROUND + 1);

    RatKing.assessForKingSpawn(rc, 1);

    assertFalse(KingFormation.requested());
  }

  @Test
  public void doesNotRequestWhenFreshKingsMeetTarget() throws Exception {
    // Arrange: one king publishes a fresh record this round, and the target is one.
    rc.setRoundNum(RATE_ROUND);
    publishKingAt(new MapLocation(10, 10));

    RatKing.assessForKingSpawn(rc, 1);

    assertFalse(KingFormation.requested());
  }

  @Test
  public void staleKingRecordDoesNotCountTowardTarget() throws Exception {
    // Arrange: a king published several rounds ago and has since gone quiet (likely dead).
    rc.setRoundNum(RATE_ROUND - 5);
    publishKingAt(new MapLocation(10, 10));
    rc.setRoundNum(RATE_ROUND);

    // Act: its expired slot must not block a replacement.
    RatKing.assessForKingSpawn(rc, 1);

    assertTrue(KingFormation.requested());
  }

  @Test
  public void zeroTargetNeverRequests() throws Exception {
    // Not enough cheese at the spawn round gives a target of zero: never form a king.
    rc.setRoundNum(RATE_ROUND);

    RatKing.assessForKingSpawn(rc, 0);

    assertFalse(KingFormation.requested());
  }

  // ---------------------------------------------------------------------------------------------
  // updateKingSpawn (through RatKing.run): target fixed at the spawn round from global cheese.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void spawnRoundCheeseSetsTargetThatLaterTriggersRequest() throws Exception {
    // Arrange: at the spawn round the team holds cheese for two kings; this king is the only one.
    rc.setMovementReady(false);
    rc.setGlobalCheese(2 * Config.ALLOCATED_CHEESE_PER_KING);
    rc.setRoundNum(Config.KING_SPAWN_ROUND);
    RatKing.run(rc);
    // 1100 is not a rate round (1100 % 15 != 0), so nothing is requested yet.
    assertFalse(KingFormation.requested());

    // Act: at the next rate round the king (still publishing a fresh record) sees 1 of 2 kings.
    rc.setRoundNum(RATE_ROUND);
    RatKing.run(rc);

    assertTrue(KingFormation.requested());
  }

  @Test
  public void spawnRoundCheeseForOneKingMeansNoRequest() throws Exception {
    // Arrange: cheese for exactly one king, and this king already exists.
    rc.setMovementReady(false);
    rc.setGlobalCheese(Config.ALLOCATED_CHEESE_PER_KING);
    rc.setRoundNum(Config.KING_SPAWN_ROUND);
    RatKing.run(rc);

    rc.setRoundNum(RATE_ROUND);
    RatKing.run(rc);

    assertFalse(KingFormation.requested());
  }

  /** Publish a fresh king record at {@code at} using an independent registry (a second king). */
  private void publishKingAt(MapLocation at) throws Exception {
    MapLocation saved = rc.getLocation();
    rc.setLocation(at);
    assertTrue(new KingRegistry().publish(rc));
    rc.setLocation(saved);
  }
}
