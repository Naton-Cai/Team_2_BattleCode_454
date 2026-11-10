package examplefuncsplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;
import org.junit.Before;
import org.junit.Test;
import support.TestRobotController;

/**
 * One turn of {@code examplefuncsplayer} behavior. {@code ./gradlew test} must keep discovering
 * this class. Tests call {@code playTurn} and return. {@code run} loops on {@code Clock.yield()},
 * so calling it here would not finish.
 */
public class RobotPlayerTest {

  /** Static robot state is shared by every test in this JVM. Each robot gets its own copy in a match. */
  @Before
  public void resetRobotLocalState() {
    RobotPlayer.turnCount = 0;
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

  private static TestRobotController blockedController(boolean turnAllowed) {
    TestRobotController controller = new TestRobotController();
    controller.setForwardAllowed(false);
    controller.setTurnAllowed(turnAllowed);
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
