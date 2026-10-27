package ratplayer_sprint1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.TrapType;
import battlecode.common.UnitType;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import ratplayer_sprint1.navigation.Navigation;
import support.TestRobotController;

/**
 * One turn of {@code ratplayer_sprint1} behavior. Tests call {@code playTurn} and return. {@code run}
 * loops on {@code Clock.yield()}, so calling it here would not finish.
 */
public class RobotPlayerTest {

  /** Static robot state is shared by every test in this JVM. Each robot gets its own copy in a match. */
  @Before
  public void resetRobotLocalState() {
    RobotPlayer.turnCount = 0;
    RatKing.locationPublished = false;
    RobotPlayer.rng.setSeed(6147);
    BabyRat.kingLoc = null;
    BabyRat.cheeseTile = null;
    BabyRat.pickedUpCheese = false;
    BabyRat.kingChecked = false;
    Config.reset();
    Navigation.resetFollow();
  }

  @Test
  public void movesForwardWhenAllowed() {
    TestRobotController controller = new TestRobotController();
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertEquals(0, controller.turnCount());
    assertEquals(1, RobotPlayer.turnCount);
  }

  @Test
  public void dispatchesBabyRatWithoutRunningRatKingSpawnBehavior() {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.BABY_RAT);
    controller.setGlobalCheese(1000);
    controller.setActionReady(true);
    controller.setCurrentRatCost(10);
    controller.setAdjacentLocation(new MapLocation(1, 0));
    controller.setForwardAllowed(true);

    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertTrue(controller.calls().stream().noneMatch(call -> call.startsWith("buildRat:")));
    assertEquals(1, RobotPlayer.turnCount);
  }

  @Test
  public void ratKingRunsSpawnBehaviorWhenCheeseIsAvailable() {
    TestRobotController controller = ratKingController();

    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertTrue(controller.calls().stream().anyMatch(call -> call.startsWith("buildRat:")));
    assertEquals(1, RobotPlayer.turnCount);
  }

  /** {@code CENTER} is not a facing. Each other direction is chosen by this test, not by the bot. */
  @Test
  public void movesForwardInEachFacing() {
    for (Direction facing : Direction.values()) {
      if (facing == Direction.CENTER) {
        continue;
      }
      RobotPlayer.turnCount = 0;

      TestRobotController controller = new TestRobotController();
      controller.setDirection(facing);
      controller.setForwardAllowed(true);

      RobotPlayer.playTurn(controller);

      assertEquals(facing, controller.getDirection());
      assertEquals(1, controller.moveCount());
      assertEquals(0, controller.turnCount());
    }
  }

  @Test
  public void turnsOnceWhenForwardIsBlocked() {
    TestRobotController controller = blockedController(true);

    RobotPlayer.playTurn(controller);

    assertEquals(0, controller.moveCount());
    assertOneFacingTurn(controller);
    assertEquals(1, RobotPlayer.turnCount);
  }

  @Test
  public void skipsTurnWhenForwardIsBlockedAndTurnIsRefused() {
    TestRobotController controller = blockedController(false);

    RobotPlayer.playTurn(controller);

    assertEquals(0, controller.moveCount());
    assertEquals(0, controller.turnCount());

    controller.setTurnAllowed(true);
    RobotPlayer.playTurn(controller);

    assertEquals(0, controller.moveCount());
    assertOneFacingTurn(controller);
  }

  @Test
  public void turnsTwiceWhenForwardStaysBlocked() {
    TestRobotController controller = blockedController(true);

    RobotPlayer.playTurn(controller);
    RobotPlayer.playTurn(controller);

    assertEquals(0, controller.moveCount());
    assertEquals(2, controller.turnCount());
    assertFacing(controller.turns().get(0));
    assertFacing(controller.turns().get(1));
    assertEquals(2, RobotPlayer.turnCount);
  }

  @Test
  public void movesOnceBetweenBlockedTurns() {
    TestRobotController controller = blockedController(true);

    RobotPlayer.playTurn(controller);
    controller.setForwardAllowed(true);
    RobotPlayer.playTurn(controller);
    controller.setForwardAllowed(false);
    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertEquals(2, controller.turns().size());
    assertFacing(controller.turns().get(0));
    assertFacing(controller.turns().get(1));
  }

  @Test
  public void containsGameActionExceptionFromMove() {
    TestRobotController controller = new TestRobotController();
    controller.setForwardAllowed(true);
    controller.setMoveFailure(
        new GameActionException(GameActionExceptionType.CANT_MOVE_THERE, "test move failure"));

    RobotPlayer.playTurn(controller);

    assertEquals(1, controller.moveCount());
    assertEquals(0, controller.turnCount());
    assertEquals(1, RobotPlayer.turnCount);
  }

  @Test
  public void containsGameActionExceptionFromTurn() {
    TestRobotController controller = blockedController(true);
    controller.setTurnFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test turn failure"));

    RobotPlayer.playTurn(controller);

    assertEquals(0, controller.moveCount());
    assertOneFacingTurn(controller);
    assertEquals(1, RobotPlayer.turnCount);
  }

  @Test
  public void containsUnexpectedExceptionFromForwardCheck() {
    TestRobotController controller = new TestRobotController();
    controller.setForwardFailure(new RuntimeException("test forward check failure"));

    RobotPlayer.playTurn(controller);

    assertEquals(0, controller.moveCount());
    assertEquals(0, controller.turnCount());
    assertEquals(1, RobotPlayer.turnCount);
  }

  /**
   * King publication happens before wander.
   *
   * <p>Slots 0 and 1 store the center, and slot 2 becomes 1. Forward movement is still the wander
   * step that follows the writes. Spawn stays off because cheese is below the placeholder threshold.
   */
  @Test
  public void publishesCenterThenMovesForward() throws GameActionException {
    // Arrange: a king at (4, 7) that is allowed to move forward and cannot spawn.
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.RAT_KING);
    controller.setLocation(new MapLocation(4, 7));
    controller.setMovementReady(true);
    controller.setForwardAllowed(true);
    controller.setAllCheese(0);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: invalidate, write, validate, move, then republish the same center.
    assertEquals(
        Arrays.asList(
            "writeSharedArray:2:0",
            "writeSharedArray:0:4",
            "writeSharedArray:1:7",
            "writeSharedArray:2:1",
            "moveForward",
            "writeSharedArray:2:0",
            "writeSharedArray:0:4",
            "writeSharedArray:1:7",
            "writeSharedArray:2:1"),
        controller.calls());
    assertEquals(4, controller.readSharedArray(RatKing.LOCATION_X_SLOT));
    assertEquals(7, controller.readSharedArray(RatKing.LOCATION_Y_SLOT));
    assertEquals(
        RatKing.KING_LOCATION_PUBLISHED, controller.readSharedArray(RatKing.LOCATION_VALID_SLOT));
    assertEquals(1, controller.moveCount());
    assertEquals(0, controller.turnCount());
  }

  /**
   * An empty-handed baby rat with nothing in vision wanders forward.
   *
   * <p>A stash at or below {@code RAW_CHEESE_MINIMUM} collects instead of returning.
   * It does not read the shared array. Existing wander tests cover the blocked-forward cases
   * on this same empty-handed path.
   */
  @Test
  public void movesForwardWhenNoCheeseIsVisible() {
    // Arrange: baby rat, no raw cheese, default empty vision, forward allowed.
    TestRobotController controller = babyRat();
    controller.setRawCheese(0);
    controller.setForwardAllowed(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert.
    assertEquals(Arrays.asList("moveForward"), controller.calls());
    assertEquals(0, controller.sharedArrayReadCount());
    assertEquals(0, controller.turnCount());
  }

  /**
   * Pickup chooses the closest visible pile, not the first sense result and not a nearer empty
   * tile.
   *
   * <p>The rat starts empty-handed, so the king-return threshold is not crossed and the shared
   * array is not read. Wander is refused so the recorded action is only the pickup.
   */
  @Test
  public void picksUpCloserVisibleCheese() {
    // Arrange: empty tile at the rat, then a far pile, then a closer pile.
    TestRobotController controller = babyRat();
    controller.setLocation(new MapLocation(0, 0));
    controller.setNearbyMapInfos(
        new MapInfo[] {
          tileWithCheese(0, 0, 0), tileWithCheese(5, 0, 20), tileWithCheese(1, 0, 3),
        });
    controller.setCanPickUpCheese(true);
    controller.setForwardAllowed(false);
    controller.setTurnAllowed(false);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: the closer pile is the only action, and the array was not read.
    assertEquals(Arrays.asList("pickUpCheese:[1, 0]"), controller.calls());
    assertEquals(0, controller.sharedArrayReadCount());
  }

  /**
   * Visible cheese that cannot be picked up is a movement goal. King lookup stays off because the
   * rat is still empty-handed.
   *
   * <p>{@code canMove} true accepts the first probe, which is {@code directionTo} the pile. The
   * direct step uses {@code move}, so {@code moveCount} stays 0; that counter tracks
   * {@code moveForward} only.
   */
  @Test
  public void stepsTowardCheeseWhenPickupIsRefused() {
    // Arrange: one pile to the east, pickup refused, any step allowed.
    TestRobotController controller = babyRat();
    controller.setLocation(new MapLocation(0, 0));
    controller.setNearbyMapInfos(new MapInfo[] {tileWithCheese(4, 0, 8)});
    controller.setCanPickUpCheese(false);
    controller.setCanMove(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: one east step and no turn.
    assertEquals(Arrays.asList("move:EAST"), controller.calls());
    assertEquals(0, controller.sharedArrayReadCount());
    assertEquals(0, controller.turnCount());
    assertEquals(0, controller.moveCount());
  }

  /**
   * Carrying raw cheese transfers the whole stash when the published king accepts it.
   *
   * <p>After the stash hits 0 and no cheese is visible, the rat wanders. It does not pick up
   * cheese on a turn that started while carrying unless the handoff emptied it.
   */
  @Test
  public void transfersAllRawCheeseToPublishedKing() {
    int carryingCheese = Config.RAW_CHEESE_MINIMUM + 1;
    // Arrange: cheese above the return threshold and a published king at (10, 4).
    // Transfer and forward move are legal.
    TestRobotController controller = babyRat();
    controller.setRawCheese(carryingCheese);
    publishKing(controller, 10, 4);
    controller.setCanTransferCheese(true);
    controller.setForwardAllowed(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: the full stash transfers, then the rat wanders forward without picking up cheese.
    assertEquals(
        Arrays.asList("transferCheese:[10, 4]:" + carryingCheese, "moveForward"),
        controller.calls());
    assertEquals(0, controller.getRawCheese());
    assertEquals(3, controller.sharedArrayReadCount());
    assertFalse(controller.calls().toString().contains("pickUpCheese"));
  }

  /**
   * A refused transfer still steps toward the published king.
   *
   * <p>The king is north of the rat, so the direct step is north. No cheese is sensed while the
   * rat is carrying.
   */
  @Test
  public void stepsTowardPublishedKingWhenTransferIsRefused() {
    int carryingCheese = Config.RAW_CHEESE_MINIMUM + 1;
    // Arrange: cheese above the return threshold, king published at (0, 5), and transfer refused.
    TestRobotController controller = babyRat();
    controller.setLocation(new MapLocation(0, 0));
    controller.setRawCheese(carryingCheese);
    publishKing(controller, 0, 5);
    controller.setCanTransferCheese(false);
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: one north step, no forward wander, no transfer, and no pickup.
    assertEquals(Arrays.asList("move:NORTH"), controller.calls());
    assertEquals(0, controller.turnCount());
    assertEquals(carryingCheese, controller.getRawCheese());
  }

  /**
   * After a visible pickup fills the stash above {@code RAW_CHEESE_MINIMUM}, the same turn looks
   * up the published king and steps toward it.
   *
   * <p>The pile is east and the king is north. North shows the goal changed from the pile to the
   * king once {@code getRawCheese} became positive. Transfer waits for a later turn.
   */
  @Test
  public void stepsTowardKingAfterPickingUpCheese() {
    int carryingCheese = Config.RAW_CHEESE_MINIMUM + 1;
    // Arrange: empty-handed, one eastern pile, pickup above threshold, king published north.
    TestRobotController controller = babyRat();
    controller.setLocation(new MapLocation(0, 0));
    controller.setNearbyMapInfos(new MapInfo[] {tileWithCheese(1, 0, carryingCheese)});
    controller.setCanPickUpCheese(true);
    controller.setCheesePickupYield(carryingCheese);
    publishKing(controller, 0, 6);
    controller.setCanMove(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: pickup, then a north step. The array is read only after the stash is filled.
    assertEquals(Arrays.asList("pickUpCheese:[1, 0]", "move:NORTH"), controller.calls());
    assertEquals(3, controller.sharedArrayReadCount());
    assertEquals(carryingCheese, controller.getRawCheese());
    assertEquals(0, controller.turnCount());
  }

  /**
   * Carrying cheese with slot 2 still 0 does not invent a king location.
   *
   * <p>Slots 0 and 1 hold coordinates, but the published flag is still 0, so the rat wanders
   * forward instead of stepping toward (8, 8).
   */
  @Test
  public void wandersWhenCarryingAndKingIsUnpublished() {
    int carryingCheese = Config.RAW_CHEESE_MINIMUM + 1;
    // Arrange: cheese above the return threshold, unpublished center, forward allowed.
    TestRobotController controller = babyRat();
    controller.setRawCheese(carryingCheese);
    controller.setSharedArrayValue(RatKing.LOCATION_X_SLOT, 8);
    controller.setSharedArrayValue(RatKing.LOCATION_Y_SLOT, 8);
    controller.setForwardAllowed(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: the three reads happened, then wander, with the stash unchanged.
    assertEquals(Arrays.asList("moveForward"), controller.calls());
    assertEquals(3, controller.sharedArrayReadCount());
    assertEquals(carryingCheese, controller.getRawCheese());
    assertEquals(0, controller.turnCount());
  }

  /**
   * A {@code GameActionException} from pickup stays inside {@code playTurn}.
   *
   * <p>The pickup was attempted. The exception aborts the rest of the turn, so king lookup and
   * wander do not run.
   */
  @Test
  public void containsGameActionExceptionFromPickUp() {
    // Arrange: a visible pile that can be picked up, and a pickup that throws.
    TestRobotController controller = babyRat();
    controller.setLocation(new MapLocation(0, 0));
    controller.setNearbyMapInfos(new MapInfo[] {tileWithCheese(1, 0, 2)});
    controller.setCanPickUpCheese(true);
    controller.setPickUpCheeseFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test pickup failure"));
    controller.setForwardAllowed(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: the pickup was recorded and neither move ran.
    assertEquals(Arrays.asList("pickUpCheese:[1, 0]"), controller.calls());
    assertEquals(0, controller.moveCount());
    assertEquals(0, controller.turnCount());
    assertEquals(1, RobotPlayer.turnCount);
  }

  /**
   * A {@code GameActionException} from transfer stays inside {@code playTurn}.
   *
   * <p>The transfer was attempted. The stash is unchanged because the fake applies the subtraction
   * only after a successful call, and no king step runs after the exception.
   */
  @Test
  public void containsGameActionExceptionFromTransfer() {
    int carryingCheese = Config.RAW_CHEESE_MINIMUM + 1;
    // Arrange: cheese above threshold, published king, throwing transfer, and legal movement.
    TestRobotController controller = babyRat();
    controller.setLocation(new MapLocation(0, 0));
    controller.setRawCheese(carryingCheese);
    publishKing(controller, 2, 2);
    controller.setCanTransferCheese(true);
    controller.setTransferCheeseFailure(
        new GameActionException(GameActionExceptionType.CANT_DO_THAT, "test transfer failure"));
    controller.setCanMove(true);
    controller.setForwardAllowed(true);

    // Act.
    RobotPlayer.playTurn(controller);

    // Assert: only the transfer was attempted, and the stash remains unchanged.
    assertEquals(Arrays.asList("transferCheese:[2, 2]:" + carryingCheese), controller.calls());
    assertEquals(carryingCheese, controller.getRawCheese());
    assertEquals(0, controller.moveCount());
    assertEquals(0, controller.turnCount());
    assertEquals(1, RobotPlayer.turnCount);
  }

  private static TestRobotController babyRat() {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.BABY_RAT);
    return controller;
  }

  /** Slot 2 set to the published flag makes (x, y) the king center a baby rat will trust. */
  private static void publishKing(TestRobotController controller, int x, int y) {
    controller.setSharedArrayValue(RatKing.LOCATION_X_SLOT, x);
    controller.setSharedArrayValue(RatKing.LOCATION_Y_SLOT, y);
    controller.setSharedArrayValue(RatKing.LOCATION_VALID_SLOT, RatKing.KING_LOCATION_PUBLISHED);
  }

  /** A passable tile. {@code amount} 0 is visible ground that is not a cheese pile. */
  private static MapInfo tileWithCheese(int x, int y, int amount) {
    return new MapInfo(
        new MapLocation(x, y), true, null, false, false, amount, TrapType.NONE, false);
  }

  private static TestRobotController blockedController(boolean turnAllowed) {
    TestRobotController controller = new TestRobotController();
    controller.setForwardAllowed(false);
    controller.setTurnAllowed(turnAllowed);
    return controller;
  }

  private static TestRobotController ratKingController() {
    TestRobotController controller = new TestRobotController();
    controller.setUnitType(UnitType.RAT_KING);
    controller.setMovementReady(true);
    controller.setGlobalCheese(1000);
    controller.setActionReady(true);
    controller.setCurrentRatCost(10);
    controller.setAdjacentLocation(new MapLocation(1, 0));
    controller.setCanBuildRat(true);
    controller.setForwardAllowed(true);
    return controller;
  }

  private static void assertOneFacingTurn(TestRobotController controller) {
    assertEquals(1, controller.turns().size());
    assertFacing(controller.turns().get(0));
  }

  private static void assertFacing(Direction direction) {
    assertTrue(direction != null && direction != Direction.CENTER);
  }
}
