package ratplayer;

import static org.junit.Assert.assertEquals;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import org.junit.Test;
import support.TestRobotController;

/** Tests baby rat bite target selection through the controller interaction seam. */
public class BabyRatAttackTest {
  private static final MapLocation ORIGIN = new MapLocation(5, 5);

  /** Arrange an adjacent cat in cooperation mode; the bite can spend up to seven cheese. */
  @Test
  public void attacksAdjacentCatDuringCooperation() throws Exception {
    TestRobotController controller = controllerWithTarget(UnitType.CAT, Team.NEUTRAL);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);
    controller.setRawCheese(10);

    BabyRat.attack(controller);

    assertEquals("attack:" + ORIGIN.add(Direction.NORTH) + ":7", controller.calls().get(0));
  }

  /** With no available cheese, a cooperating rat still issues the baseline ten-damage bite. */
  @Test
  public void attacksAdjacentCatWithoutSpendingCheese() throws Exception {
    TestRobotController controller = controllerWithTarget(UnitType.CAT, Team.NEUTRAL);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals("attack:" + ORIGIN.add(Direction.NORTH) + ":0", controller.calls().get(0));
  }

  /** A cat one tile left of the forward square is sensed and bitten. */
  @Test
  public void attacksCatLeftOfForwardSquare() throws Exception {
    MapLocation targetLocation = ORIGIN.add(Direction.NORTH).add(Direction.WEST);
    TestRobotController controller =
        controllerWithTargetAt(UnitType.CAT, Team.NEUTRAL, targetLocation);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals("attack:" + targetLocation + ":0", controller.calls().get(0));
  }

  /** A cat one tile right of the forward square is sensed and bitten. */
  @Test
  public void attacksCatRightOfForwardSquare() throws Exception {
    MapLocation targetLocation = ORIGIN.add(Direction.NORTH).add(Direction.EAST);
    TestRobotController controller =
        controllerWithTargetAt(UnitType.CAT, Team.NEUTRAL, targetLocation);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals("attack:" + targetLocation + ":0", controller.calls().get(0));
  }

  /** Backstabbing mode caps a cat bite at three cheese even when more is available. */
  @Test
  public void limitsBackstabbingCatBiteToThreeCheese() throws Exception {
    TestRobotController controller = controllerWithTarget(UnitType.CAT, Team.NEUTRAL);
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.A);
    controller.setCanAttackWithCheese(true);
    controller.setGlobalCheese(10);

    BabyRat.attack(controller);

    assertEquals("attack:" + ORIGIN.add(Direction.NORTH) + ":3", controller.calls().get(0));
  }

  /** Arrange an enemy rat while cooperation is active; it must not be bitten. */
  @Test
  public void doesNotAttackEnemyRatDuringCooperation() throws Exception {
    TestRobotController controller = controllerWithTarget(UnitType.BABY_RAT, Team.B);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals(0, controller.calls().size());
  }

  /** Arrange an opposing rat after backstabbing starts; it receives a zero-cheese bite. */
  @Test
  public void attacksEnemyRatDuringBackstabbing() throws Exception {
    TestRobotController controller = controllerWithTarget(UnitType.BABY_RAT, Team.B);
    controller.setCooperation(false);
    controller.setBackstabbingTeam(Team.A);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals("attack:" + ORIGIN.add(Direction.NORTH) + ":0", controller.calls().get(0));
  }

  /** Arrange an empty neighborhood; the routine performs no bite attempt. */
  @Test
  public void doesNotAttackWhenNoAdjacentTargetsExist() throws Exception {
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setCanSenseRobotAtLocation(true);
    controller.setRobotAtLocation(null);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals(0, controller.calls().size());
  }

  /** A robot stored at the forward tile is ignored when that tile cannot be sensed. */
  @Test
  public void doesNotSenseOrAttackForwardRobotOutsideVision() throws Exception {
    TestRobotController controller = controllerWithTarget(UnitType.CAT, Team.NEUTRAL);
    controller.setCanSenseRobotAtLocation(false);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals(0, controller.calls().size());
  }

  private static TestRobotController controllerWithTarget(UnitType type, Team team) {
    return controllerWithTargetAt(type, team, ORIGIN.add(Direction.NORTH));
  }

  private static TestRobotController controllerWithTargetAt(
      UnitType type, Team team, MapLocation targetLocation) {
    TestRobotController controller = new TestRobotController();
    controller.setLocation(ORIGIN);
    controller.setCanSenseRobotAtLocation(true);
    controller.setRobotAtLocation(
        new RobotInfo(2, team, type, type.health, targetLocation, Direction.SOUTH, 0, 0, null));
    return controller;
  }
}
