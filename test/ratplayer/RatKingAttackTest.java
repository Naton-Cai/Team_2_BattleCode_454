package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/**
 * Controller checks for king bites when the king cannot flee: enemy rats after a backstab, and an
 * adjacent cat when dirt and a cat trap are also unavailable.
 *
 * <p>These tests record {@code attack}, {@code buildRat}, {@code placeDirt}, and movement calls.
 * They do not show what the engine allows. Named cases are in {@code .ai/test-cases.md}.
 */
public class RatKingAttackTest {
  private static final MapLocation ORIGIN = new MapLocation(10, 10);
  private static final MapLocation NORTH_RING = ORIGIN.translate(0, 2);
  private static final MapLocation KNIGHT_RING = ORIGIN.translate(1, 2);
  private static final MapLocation OUTSIDE_RING = ORIGIN.translate(0, 3);
  private static final MapLocation DISTANT_NORTH = new MapLocation(10, 16);

  @Before
  public void setUp() {
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    Trap.startofBackStabbing = 0;
    RatKing.kingRegistry = new KingRegistry();
    RatKing.locationPublished = false;
    RatKing.resetHome();
  }

  @After
  public void tearDown() {
    Config.reset();
    Trap.startofBackStabbing = 0;
    RatKing.kingRegistry = new KingRegistry();
    RatKing.locationPublished = false;
    RatKing.resetHome();
  }

  /**
   * A king that cannot step bites an opposing baby on the north attack-ring tile after cooperation
   * has ended. Spawn is skipped because the bite spends the action.
   *
   * <p>Arrange: movement not ready, backstabbing, enemy baby at (10,12), bite legal, spawn would
   * otherwise be legal. Act: one {@code playTurn}. Assert: one zero-cheese bite at (10,12) and no
   * spawn.
   */
  @Test
  public void attacksEnemyBabyWhenUnableToFleeDuringBackstabbing() {
    TestRobotController controller = attackController();
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));
    controller.setCanBuildRat(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "attack:"));
    assertEquals("attack:" + NORTH_RING + ":0", firstCall(controller, "attack:"));
    assertEquals(0, countCalls(controller, "buildRat:"));
  }

  /**
   * The extra eight attack-ring tiles (Chebyshev 2, not the spawn ring) are still biteable.
   *
   * <p>Arrange: enemy baby at (11,12). Act: one {@code playTurn}. Assert: one bite there.
   */
  @Test
  public void attacksEnemyBabyOnKnightRingTile() {
    TestRobotController controller = attackController();
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, KNIGHT_RING));

    RobotPlayer.playTurn(controller);

    assertEquals("attack:" + KNIGHT_RING + ":0", firstCall(controller, "attack:"));
  }

  /**
   * An opposing king on the attack ring is a legal backstab target.
   *
   * <p>Arrange: enemy king at (10,12). Act: one {@code playTurn}. Assert: one zero-cheese bite.
   */
  @Test
  public void attacksEnemyKingWhenUnableToFlee() {
    TestRobotController controller = attackController();
    controller.setRobotAtLocation(enemy(UnitType.RAT_KING, NORTH_RING));

    RobotPlayer.playTurn(controller);

    assertEquals("attack:" + NORTH_RING + ":0", firstCall(controller, "attack:"));
  }

  /**
   * Biting an enemy rat during cooperation would itself start a backstab, so the king does not.
   *
   * <p>Arrange: cooperation, enemy baby on the ring, movement not ready, bite otherwise legal. Act:
   * one {@code playTurn}. Assert: no attack.
   */
  @Test
  public void doesNotAttackEnemyRatDuringCooperation() {
    TestRobotController controller = attackController();
    controller.setCooperation(true);
    controller.setBackstabbingTeam(null);
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * If the king can still step, it keeps traveling instead of biting a nearby enemy rat.
   *
   * <p>Arrange: movement ready, every heading legal, backstabbing, enemy baby on the ring. Act: one
   * {@code playTurn}. Assert: no attack, and the usual forward step still runs.
   */
  @Test
  public void doesNotAttackWhenKingCanFlee() {
    TestRobotController controller = attackController();
    controller.setMovementReady(true);
    controller.setCanMove(true);
    controller.setForwardAllowed(true);
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
    assertEquals(1, controller.moveCount());
  }

  /**
   * A successful cat flee is the preferred step; an in-range enemy rat is not bitten that turn.
   *
   * <p>Arrange: health at the flee threshold, cat north, movement legal, enemy baby on the ring,
   * bite otherwise legal. Act: one {@code playTurn}. Assert: south flee, dirt block, no attack.
   */
  @Test
  public void successfulCatFleeSkipsAttack() {
    TestRobotController controller = attackController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setMovementReady(true);
    controller.setCanMove(true);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * When cat policy wants to flee but the king cannot step, an in-range enemy rat is bitten.
   *
   * <p>Arrange: health at the flee threshold, visible cat, movement not ready, dirt refused so the
   * action stays free, enemy baby on the ring. Act: one {@code playTurn}. Assert: one bite and no
   * flee step.
   */
  @Test
  public void blockedCatFleeAttacksEnemyRat() {
    TestRobotController controller = attackController();
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setNearbyRobots(new RobotInfo[] {catAt(DISTANT_NORTH)});
    controller.setCanPlaceDirt(false);
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "move:" + Direction.SOUTH));
    assertEquals("attack:" + NORTH_RING + ":0", firstCall(controller, "attack:"));
  }

  /**
   * Allied rats are never bitten, even after a backstab when the king cannot flee.
   *
   * <p>Arrange: allied baby on the ring. Act: one {@code playTurn}. Assert: no attack.
   */
  @Test
  public void doesNotAttackAlliedRat() {
    TestRobotController controller = attackController();
    controller.setRobotAtLocation(
        new RobotInfo(
            2,
            Team.A,
            UnitType.BABY_RAT,
            UnitType.BABY_RAT.health,
            NORTH_RING,
            Direction.SOUTH,
            0,
            0,
            null));

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * Tiles beyond Chebyshev 2 are outside {@code RAT_KING_ATTACK_DISTANCE_SQUARED}.
   *
   * <p>Arrange: enemy baby at (10,13). Act: one {@code playTurn}. Assert: no attack.
   */
  @Test
  public void doesNotAttackEnemyOutsideAttackRing() {
    TestRobotController controller = attackController();
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, OUTSIDE_RING));

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * Without an enemy in range, a stuck king still spends the action on spawn.
   *
   * <p>Arrange: movement not ready, no nearby robot, spawn legal. Act: one {@code playTurn}.
   * Assert: one {@code buildRat} and no attack.
   */
  @Test
  public void spawnStillRunsWhenUnableToFleeWithoutEnemy() {
    TestRobotController controller = attackController();
    controller.setCanBuildRat(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "buildRat:"));
    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * A {@code GameActionException} from {@code attack} reaches {@link RobotPlayer#playTurn} and is
   * not retried that turn. Clearing the failure lets the next turn bite once.
   *
   * <p>Arrange: first turn throws. Act: two {@code playTurn}s, with the failure cleared between
   * them. Assert: one attack attempt per turn and a normal return.
   */
  @Test
  public void attackFailureContainedAtTurnBoundary() {
    TestRobotController controller = attackController();
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));
    controller.setAttackWithCheeseFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test king attack"));

    RobotPlayer.playTurn(controller);

    assertEquals(1, RobotPlayer.turnCount);
    assertEquals(1, countCalls(controller, "attack:"));

    controller.setAttackWithCheeseFailure(null);
    RobotPlayer.playTurn(controller);

    assertEquals(2, RobotPlayer.turnCount);
    assertEquals(2, countCalls(controller, "attack:"));
  }

  /**
   * {@link RatKing#cannotFlee} is true when movement is ready but every heading is illegal.
   *
   * <p>Arrange: movement ready, {@code canMove} false, enemy on the ring. Act: one {@code
   * playTurn}. Assert: one bite.
   */
  @Test
  public void attacksWhenEveryHeadingIsBlocked() {
    TestRobotController controller = attackController();
    controller.setMovementReady(true);
    controller.setCanMove(false);
    controller.setForwardAllowed(false);
    controller.setRobotAtLocation(enemy(UnitType.BABY_RAT, NORTH_RING));

    RobotPlayer.playTurn(controller);

    assertEquals("attack:" + NORTH_RING + ":0", firstCall(controller, "attack:"));
    assertNull(firstCall(controller, "move:"));
  }

  /**
   * A stuck king bites an adjacent cat during cooperation when the blocking tile cannot take dirt
   * or a cat trap. Cat bites do not require a backstab.
   *
   * <p>Arrange: cooperation, movement not ready, cat at (10,12), dirt and cat trap refused, spawn
   * otherwise legal. Act: one {@code playTurn}. Assert: one zero-cheese bite and no spawn.
   */
  @Test
  public void attacksAdjacentCatWhenStuckAndCannotBlock() {
    TestRobotController controller = stuckCatController(NORTH_RING);
    controller.setCanBuildRat(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "attack:"));
    assertEquals("attack:" + NORTH_RING + ":0", firstCall(controller, "attack:"));
    assertEquals(0, countCalls(controller, "buildRat:"));
    assertEquals(0, countCalls(controller, "placeCatTrap:"));
  }

  /**
   * The knight-offset attack-ring tiles are adjacent for a 3x3 king ({@code r² <= 8}).
   *
   * <p>Arrange: cat at (11,12), movement not ready, dirt and cat trap refused. Act: one {@code
   * playTurn}. Assert: one bite at (11,12).
   */
  @Test
  public void attacksAdjacentCatOnKnightRingTile() {
    TestRobotController controller = stuckCatController(KNIGHT_RING);

    RobotPlayer.playTurn(controller);

    assertEquals("attack:" + KNIGHT_RING + ":0", firstCall(controller, "attack:"));
  }

  /**
   * A visible cat beyond the attack ring is not adjacent, so a stuck king does not bite it.
   *
   * <p>Arrange: cat at (10,16), movement not ready, dirt and cat trap refused. Act: one {@code
   * playTurn}. Assert: no attack.
   */
  @Test
  public void doesNotAttackDistantCatWhenStuck() {
    TestRobotController controller = stuckCatController(DISTANT_NORTH);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * A legal step is the preferred escape. An adjacent cat is not bitten while the king can move.
   *
   * <p>Arrange: movement ready, headings legal, cat at (10,12), dirt and cat trap refused. Act: one
   * {@code playTurn}. Assert: one south step and no attack.
   */
  @Test
  public void doesNotAttackAdjacentCatWhenKingCanStep() {
    TestRobotController controller = stuckCatController(NORTH_RING);
    controller.setMovementReady(true);
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
  }

  /**
   * Blocking dirt is preferred to a bite. The king does not bite while the spawn-ring tile can take
   * dirt.
   *
   * <p>Arrange: stuck king, cat at (10,12), {@code canPlaceDirt} true, cat trap refused. Act: one
   * {@code playTurn}. Assert: one {@code placeDirt} and no attack.
   */
  @Test
  public void doesNotAttackAdjacentCatWhenDirtCanBePlaced() {
    TestRobotController controller = stuckCatController(NORTH_RING);
    controller.setCanPlaceDirt(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "placeDirt:"));
    assertEquals(0, countCalls(controller, "attack:"));
  }

  /**
   * A legal cat trap on the blocking tile also suppresses the bite. This change does not place the
   * trap; it only refuses the attack.
   *
   * <p>Arrange: stuck king, cat at (10,12), dirt refused, {@code canPlaceCatTrap} true. Act: one
   * {@code playTurn}. Assert: no attack and no {@code placeCatTrap}.
   */
  @Test
  public void doesNotAttackAdjacentCatWhenCatTrapCanBePlaced() {
    TestRobotController controller = stuckCatController(NORTH_RING);
    controller.setCanPlaceCatTrap(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "attack:"));
    assertEquals(0, countCalls(controller, "placeCatTrap:"));
  }

  /**
   * A {@code GameActionException} from the cat bite reaches {@link RobotPlayer#playTurn} and is not
   * retried that turn. Clearing the failure lets the next turn bite once.
   *
   * <p>Arrange: stuck king, adjacent cat, first {@code attack} throws. Act: two {@code playTurn}s,
   * with the failure cleared between them. Assert: one attack attempt per turn.
   */
  @Test
  public void adjacentCatAttackFailureContainedAtTurnBoundary() {
    TestRobotController controller = stuckCatController(NORTH_RING);
    controller.setAttackWithCheeseFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test king cat attack"));

    RobotPlayer.playTurn(controller);

    assertEquals(1, RobotPlayer.turnCount);
    assertEquals(1, countCalls(controller, "attack:"));

    controller.setAttackWithCheeseFailure(null);
    RobotPlayer.playTurn(controller);

    assertEquals(2, RobotPlayer.turnCount);
    assertEquals(2, countCalls(controller, "attack:"));
  }

  /**
   * Cooperation, movement locked, and an adjacent cat whose blocking tile accepts neither dirt nor
   * a cat trap.
   */
  private static TestRobotController stuckCatController(MapLocation catLocation) {
    TestRobotController controller = attackController();
    controller.setCooperation(true);
    controller.setBackstabbingTeam(null);
    controller.setHealth(Config.KING_CAT_FLEE_HEALTH);
    controller.setCanPlaceDirt(false);
    controller.setCanPlaceCatTrap(false);
    RobotInfo cat = catAt(catLocation);
    controller.setNearbyRobots(new RobotInfo[] {cat});
    controller.setRobotAtLocation(cat);
    return controller;
  }

  private static TestRobotController attackController() {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.RAT_KING);
    controller.setLocation(ORIGIN);
    controller.setMapWidth(60);
    controller.setMapHeight(60);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(1000);
    controller.setActionReady(true);
    controller.setCurrentRatCost(10);
    controller.setCanBuildRat(false);
    controller.setMovementReady(false);
    controller.setCanMove(false);
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.A);
    controller.setCanSenseRobotAtLocation(true);
    controller.setCanAttackWithCheese(true);
    return controller;
  }

  private static RobotInfo enemy(UnitType type, MapLocation location) {
    return new RobotInfo(2, Team.B, type, type.health, location, Direction.SOUTH, 0, 0, null);
  }

  private static RobotInfo catAt(MapLocation location) {
    return new RobotInfo(
        9, Team.NEUTRAL, UnitType.CAT, UnitType.CAT.health, location, Direction.SOUTH, 0, 0, null);
  }

  private static String firstCall(TestRobotController controller, String prefix) {
    for (String call : controller.calls()) {
      if (call.startsWith(prefix)) {
        return call;
      }
    }
    return null;
  }

  private static int countCalls(TestRobotController controller, String prefix) {
    int count = 0;
    for (String call : controller.calls()) {
      if (call.startsWith(prefix)) {
        count++;
      }
    }
    return count;
  }
}
