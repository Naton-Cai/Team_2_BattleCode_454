package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ratplayer.navigation.Navigation;
import support.TestRobotController;

/**
 * Controller checks for backstab enemy-king hunt through {@link RobotPlayer#playTurn}.
 *
 * <p>These tests record movement and attack calls. They do not show what the engine allows. Named
 * cases are in {@code .ai/test-cases.md}.
 */
public class BabyRatHuntTest {
  private static final MapLocation ORIGIN = new MapLocation(5, 5);
  private static final MapLocation KING_NORTH = new MapLocation(5, 9);
  private static final MapLocation KING_IN_RANGE = new MapLocation(5, 7);
  private static final MapLocation KING_NORTHEAST = new MapLocation(7, 9);
  private static final MapLocation ALLIED_KING = new MapLocation(10, 4);
  private static final MapLocation CHEESE_EAST = new MapLocation(9, 5);
  private static final MapLocation CAT_NORTH = new MapLocation(5, 9);

  @Before
  public void setUp() {
    Config.reset();
    RobotPlayer.turnCount = 0;
    RobotPlayer.rng.setSeed(6147);
    Trap.startofBackStabbing = 0;
    BabyRat.attackedThisTurn = false;
    BabyRat.role = null;
    KingFormation.reset();
    Navigation.resetFollow();
  }

  @After
  public void tearDown() {
    Config.reset();
    BabyRat.attackedThisTurn = false;
    BabyRat.role = null;
    KingFormation.reset();
  }

  /**
   * An empty-handed baby in backstab walks toward a visible enemy king instead of wandering.
   *
   * <p>Arrange: north-facing baby at (5,5), Team B king at (5,9) in the cone, greedy steps allowed.
   * Act: one {@code playTurn}. Assert: one north step via {@code move}, not {@code moveForward}.
   */
  @Test
  public void huntsVisibleEnemyKingInsteadOfWandering() {
    TestRobotController controller = huntController();
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_NORTH)});
    controller.setForwardAllowed(true);
    controller.setCanMove(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "move:" + Direction.NORTH));
    assertEquals(0, controller.moveCount());
  }

  /**
   * Enemy babies are biteable in the cone but are never a movement goal.
   *
   * <p>Arrange: backstab, Team B baby at (5,9), forward wander allowed. Act: one {@code playTurn}.
   * Assert: wander {@code moveForward}, no greedy step toward the baby.
   */
  @Test
  public void doesNotHuntVisibleEnemyBaby() {
    TestRobotController controller = huntController();
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyBabyAt(KING_NORTH)});
    controller.setForwardAllowed(true);
    controller.setCanMove(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertEquals(0, countCalls(controller, "move:"));
  }

  /**
   * Hunt is backstab-only. A visible enemy king during cooperation keeps wander.
   *
   * <p>Arrange: cooperation, Team B king at (5,9), forward allowed. Act: one {@code playTurn}.
   * Assert: {@code moveForward}, no north greedy step.
   */
  @Test
  public void doesNotHuntEnemyKingDuringCooperation() {
    TestRobotController controller = huntController();
    controller.setCooperation(true);
    controller.setBackstabbingTeam(null);
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_NORTH)});
    controller.setForwardAllowed(true);
    controller.setCanMove(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertEquals(0, countCalls(controller, "move:"));
  }

  /**
   * A visible pile does not pull an empty-handed hunter off a visible enemy king.
   *
   * <p>Arrange: backstab, king at (5,9), cheese at (9,5) with pickup refused, any step allowed. Act:
   * one {@code playTurn}. Assert: north toward the king, not east toward the pile.
   */
  @Test
  public void huntsEnemyKingInsteadOfChasingVisibleCheese() {
    TestRobotController controller = huntController();
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_NORTH)});
    controller.setNearbyMapInfos(new MapInfo[] {tileWithCheese(CHEESE_EAST, 8)});
    controller.setCanPickUpCheese(false);
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "move:" + Direction.NORTH));
    assertEquals(0, countCalls(controller, "move:" + Direction.EAST));
    assertEquals(0, controller.moveCount());
  }

  /**
   * {@link Config#BABY_HUNT_IN_BACKSTAB} off keeps cheese chase even with a visible enemy king.
   *
   * <p>Arrange: hunt disabled, king at (5,9), cheese at (9,5) with pickup refused. Act: one {@code
   * playTurn}. Assert: east toward the pile, not north toward the king.
   */
  @Test
  public void huntConfigOffChasesCheeseInsteadOfKing() {
    Config.BABY_HUNT_IN_BACKSTAB = false;
    TestRobotController controller = huntController();
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_NORTH)});
    controller.setNearbyMapInfos(new MapInfo[] {tileWithCheese(CHEESE_EAST, 8)});
    controller.setCanPickUpCheese(false);
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "move:" + Direction.EAST));
    assertEquals(0, countCalls(controller, "move:" + Direction.NORTH));
    assertEquals(0, controller.moveCount());
  }

  /**
   * A stash above {@link Config#RAW_CHEESE_MINIMUM} still walks home even with a visible enemy king.
   *
   * <p>Arrange: raw cheese 41, published allied king at (10,4), enemy king at (5,9), DistBug/greedy
   * steps allowed. Act: one {@code playTurn}. Assert: step toward the allied king, not north.
   */
  @Test
  public void deliversCheeseInsteadOfHunting() {
    TestRobotController controller = huntController();
    controller.setRawCheese(Config.RAW_CHEESE_MINIMUM + 1);
    publishKing(controller, ALLIED_KING);
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_NORTH)});
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "move:" + Direction.EAST));
    assertEquals(0, countCalls(controller, "move:" + Direction.NORTH));
    assertEquals(0, controller.moveCount());
  }

  /**
   * Nearest visible enemy king by squared distance; ties keep the earlier sense result.
   *
   * <p>Arrange: farther king listed first at (7,9), closer king second at (5,9). Act: one {@code
   * playTurn}. Assert: north toward the closer king, not northeast.
   */
  @Test
  public void huntsNearestEnemyKingBySquaredDistance() {
    TestRobotController controller = huntController();
    controller.setNearbyRobotsOnTeam(
        new RobotInfo[] {enemyKingAt(2, KING_NORTHEAST), enemyKingAt(3, KING_NORTH)});
    controller.setCanMove(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "move:" + Direction.NORTH));
    assertEquals(0, countCalls(controller, "move:" + Direction.NORTHEAST));
  }

  /**
   * Chebyshev ≤ 2 from a size-3 king center is already bite range, so hunt does not step in.
   *
   * <p>Arrange: enemy king at (5,7), forward wander would otherwise run. Act: one {@code playTurn}.
   * Assert: no movement.
   */
  @Test
  public void staysPutWhenAlreadyInKingBiteRange() {
    TestRobotController controller = huntController();
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_IN_RANGE)});
    controller.setForwardAllowed(true);
    controller.setCanMove(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, countCalls(controller, "move:"));
    assertEquals(0, controller.moveCount());
    assertEquals(0, controller.turnCount());
  }

  /**
   * Cat block-and-flee still owns movement when a cat and an enemy king are both visible.
   *
   * <p>Arrange: cat in {@code senseNearbyRobots} at (5,9), enemy king in the opponent scan, dirt and
   * greedy steps allowed. Act: one {@code playTurn}. Assert: south flee, no north hunt step.
   */
  @Test
  public void visibleCatFleeSkipsHunt() {
    TestRobotController controller = huntController();
    controller.setNearbyRobots(new RobotInfo[] {catAt(CAT_NORTH)});
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyKingAt(KING_NORTH)});
    controller.setCanPlaceDirt(true);
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, countCalls(controller, "move:" + Direction.SOUTH));
    assertEquals(0, countCalls(controller, "move:" + Direction.NORTH));
    assertEquals(0, controller.moveCount());
  }

  /**
   * In-cone enemy-baby bites still fire in backstab; hunt does not replace that action.
   *
   * <p>Arrange: Team B baby on the forward tile, bite legal, no enemy king. Act: one {@code
   * playTurn}. Assert: one zero-cheese bite, then wander forward.
   */
  @Test
  public void stillBitesAdjacentEnemyBabyDuringBackstab() {
    TestRobotController controller = huntController();
    RobotInfo enemyBaby = enemyBabyAt(ORIGIN.add(Direction.NORTH));
    controller.setCanSenseRobotAtLocation(true);
    controller.setRobotAtLocation(enemyBaby);
    controller.setNearbyRobotsOnTeam(new RobotInfo[] {enemyBaby});
    controller.setActionReady(true);
    controller.setCanAttackWithCheese(true);
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertTrue(controller.calls().get(0).startsWith("attack:"));
    assertEquals("attack:" + ORIGIN.add(Direction.NORTH) + ":0", controller.calls().get(0));
    assertEquals(1, controller.moveCount());
  }

  private static TestRobotController huntController() {
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setDirection(Direction.NORTH);
    controller.setRawCheese(0);
    controller.setGlobalCheese(0);
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.A);
    controller.setTeam(Team.A);
    return controller;
  }

  private static void publishKing(TestRobotController controller, MapLocation center) {
    controller.setMapWidth(60);
    controller.setMapHeight(60);
    controller.setSharedArrayValue(0, center.x);
    controller.setSharedArrayValue(1, center.y);
    controller.setSharedArrayValue(2, 1);
  }

  private static RobotInfo enemyKingAt(MapLocation location) {
    return enemyKingAt(2, location);
  }

  private static RobotInfo enemyKingAt(int id, MapLocation location) {
    return new RobotInfo(
        id,
        Team.B,
        UnitType.RAT_KING,
        UnitType.RAT_KING.health,
        location,
        Direction.SOUTH,
        0,
        0,
        null);
  }

  private static RobotInfo enemyBabyAt(MapLocation location) {
    return new RobotInfo(
        4,
        Team.B,
        UnitType.BABY_RAT,
        UnitType.BABY_RAT.health,
        location,
        Direction.SOUTH,
        0,
        0,
        null);
  }

  private static RobotInfo catAt(MapLocation location) {
    return new RobotInfo(
        9, Team.NEUTRAL, UnitType.CAT, UnitType.CAT.health, location, Direction.SOUTH, 0, 0, null);
  }

  private static MapInfo tileWithCheese(MapLocation location, int amount) {
    return new MapInfo(location, true, null, false, false, amount, TrapType.NONE, false);
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
