package ratplayer_sprint1;

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

  /**
   * Forward-then-left/right of the forward tile is adjacent when facing a cardinal, but not when
   * facing a diagonal. Facing NE, adjacent north is still in vision and biteable (spec p.10/155).
   *
   * <pre>
   *       | 4 | 5 | 6 | 7 |
   *     6 |   | o | F |   |     o = cat on adjacent north (5,6)
   *     5 |   | R |   |   |     R facing NE; F = forward (6,6)
   * </pre>
   *
   * Arrange: cat on north, facing NE, bite legal. Assert: one bite at {@code o}. The scan must step
   * 45° from the rat, not add left to {@code F} (that tile is two north).
   */
  @Test
  public void attacksAdjacentNorthCatWhenFacingNortheast() throws Exception {
    MapLocation targetLocation = ORIGIN.add(Direction.NORTH);
    TestRobotController controller =
        controllerWithTargetAt(UnitType.CAT, Team.NEUTRAL, targetLocation);
    controller.setDirection(Direction.NORTHEAST);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals(1, controller.calls().size());
    assertEquals("attack:" + targetLocation + ":0", controller.calls().get(0));
  }

  /**
   * Same diagonal miss on the other side: adjacent east while facing NE.
   *
   * <pre>
   *       | 4 | 5 | 6 | 7 |
   *     6 |   |   | F |   |
   *     5 |   | R | o |   |     o = cat on adjacent east (6,5)
   * </pre>
   *
   * Arrange: cat on east, facing NE, bite legal. Assert: one bite at {@code o}. The scan must step
   * 45° from the rat, not add right to {@code F} (that tile is two east).
   */
  @Test
  public void attacksAdjacentEastCatWhenFacingNortheast() throws Exception {
    MapLocation targetLocation = ORIGIN.add(Direction.EAST);
    TestRobotController controller =
        controllerWithTargetAt(UnitType.CAT, Team.NEUTRAL, targetLocation);
    controller.setDirection(Direction.NORTHEAST);
    controller.setCooperation(true);
    controller.setCanAttackWithCheese(true);

    BabyRat.attack(controller);

    assertEquals(1, controller.calls().size());
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
