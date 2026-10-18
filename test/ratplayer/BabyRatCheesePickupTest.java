package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.TrapType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Tests that a baby rat picks the closest visible cheese pile from sensed map info. These mocks
 * share cheese state; they do not validate engine pickup range.
 */
public class BabyRatCheesePickupTest {

  /**
   * Arrange one visible pile, act through the pickup routine, and assert cheese transfers once.
   * The mock removes cheese when claimed, matching the engine's shared map state.
   */
  @Test
  public void picksUpOneAdjacentPile() throws Exception {
    PickupScenario scenario = new PickupScenario(new MapLocation(5, 5), false);
    scenario.addCheese(Direction.NORTH, 12);

    BabyRat.pickUpCheese(scenario.controller);

    assertEquals(12, scenario.rawCheese[0]);
    assertEquals(0, scenario.cheeseAt(Direction.NORTH));
  }

  /** Arrange no visible piles, act, and assert there is no pickup attempt or penalty. */
  @Test
  public void doesNothingWhenNoAdjacentCheeseExists() throws Exception {
    PickupScenario scenario = new PickupScenario(new MapLocation(5, 5), false);

    BabyRat.pickUpCheese(scenario.controller);

    assertEquals(0, scenario.rawCheese[0]);
    assertEquals(0, scenario.pickupCount[0]);
  }

  /**
   * Arrange three visible piles, act, and assert only the closest pile is taken. Equal distances
   * would keep the earlier sense result; here NORTH is strictly closer than SOUTHWEST, and EAST
   * is the same distance as NORTH but later in sense order, so NORTH wins on first scan then the
   * threshold blocks a second pile.
   */
  @Test
  public void collectsPilesFromAllAdjacentTiles() throws Exception {
    PickupScenario scenario = new PickupScenario(new MapLocation(5, 5), false);
    scenario.addCheese(Direction.NORTH, 3);
    scenario.addCheese(Direction.EAST, 7);
    scenario.addCheese(Direction.SOUTHWEST, 11);

    BabyRat.pickUpCheese(scenario.controller);

    assertEquals(3, scenario.rawCheese[0]);
    assertEquals(0, scenario.cheeseAt(Direction.NORTH));
    assertEquals(7, scenario.cheeseAt(Direction.EAST));
    assertEquals(11, scenario.cheeseAt(Direction.SOUTHWEST));
    assertEquals(1, scenario.pickupCount[0]);
  }

  /** Arrange a corner location, act, and assert only the sensed on-map pile is collected. */
  @Test
  public void skipsOffMapNeighborsAtCorner() throws Exception {
    PickupScenario scenario = new PickupScenario(new MapLocation(0, 0), true);
    scenario.addCheese(Direction.EAST, 9);

    BabyRat.pickUpCheese(scenario.controller);

    assertEquals(9, scenario.rawCheese[0]);
    assertEquals(0, scenario.cheeseAt(Direction.EAST));
    assertEquals(1, scenario.pickupCount[0]);
  }

  /** Arrange one contested pile and run two rats sequentially against shared map state. */
  @Test
  public void secondRatDoesNotAttemptToClaimAlreadyCollectedPile() throws Exception {
    PickupScenario scenario = new PickupScenario(new MapLocation(5, 5), false);
    scenario.addCheese(Direction.NORTH, 14);

    BabyRat.pickUpCheese(scenario.controller);
    int firstRatCheese = scenario.rawCheese[0];
    scenario.rawCheese[0] = 0;
    BabyRat.pickUpCheese(scenario.controller);

    assertEquals(14, firstRatCheese);
    assertEquals(0, scenario.rawCheese[0]);
    assertEquals(1, scenario.pickupCount[0]);
  }

  private static final class PickupScenario {
    private final RobotController controller = mock(RobotController.class);
    private final Map<MapLocation, Integer> cheese = new LinkedHashMap<>();
    private final int[] rawCheese = {0};
    private final int[] pickupCount = {0};
    private final MapLocation center;

    private PickupScenario(MapLocation center, boolean corner) throws Exception {
      this.center = center;
      when(controller.getLocation()).thenReturn(center);
      when(controller.onTheMap(any(MapLocation.class)))
          .thenAnswer(
              invocation -> {
                MapLocation location = invocation.getArgument(0);
                return !corner || (location.x >= 0 && location.y >= 0);
              });
      when(controller.canPickUpCheese(any(MapLocation.class)))
          .thenAnswer(invocation -> cheese.getOrDefault(invocation.getArgument(0), 0) > 0);
      when(controller.getRawCheese()).thenAnswer(invocation -> rawCheese[0]);
      when(controller.senseNearbyMapInfos()).thenAnswer(invocation -> sensedTiles());
      doAnswer(
              invocation -> {
                MapLocation location = invocation.getArgument(0);
                int amount = cheese.getOrDefault(location, 0);
                cheese.put(location, 0);
                rawCheese[0] += amount;
                pickupCount[0]++;
                return null;
              })
          .when(controller)
          .pickUpCheese(any(MapLocation.class));
    }

    private MapInfo[] sensedTiles() {
      List<MapInfo> infos = new ArrayList<>();
      for (Map.Entry<MapLocation, Integer> entry : cheese.entrySet()) {
        infos.add(
            new MapInfo(
                entry.getKey(), true, null, false, false, entry.getValue(), TrapType.NONE, false));
      }
      return infos.toArray(new MapInfo[0]);
    }

    private void addCheese(Direction direction, int amount) {
      cheese.put(center.add(direction), amount);
    }

    private int cheeseAt(Direction direction) {
      return cheese.getOrDefault(center.add(direction), 0);
    }
  }
}
