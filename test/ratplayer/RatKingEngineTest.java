package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import battlecode.crossplay.CrossPlayLanguage;
import battlecode.server.GameInfo;
import battlecode.server.GameMaker;
import battlecode.world.GameWorld;
import battlecode.world.InternalRobot;
import battlecode.world.LiveMap;
import battlecode.world.control.NullControlProvider;
import battlecode.world.control.PlayerControlProvider;
import battlecode.world.control.TeamControlProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Test;

/** Runs the real bot loop in the pinned engine sandbox, with observable world state after yield. */
public class RatKingEngineTest {
  private GameWorld world;
  private PlayerControlProvider player;
  private ByteArrayOutputStream output;
  private InternalRobot king;
  private int peakBytecodes;

  @After
  public void stopSandbox() {
    if (player != null) {
      player.matchEnded();
    }
  }

  @Test
  public void emptyMinesBeforeAndAfterMovementPersistForBabyReaders() throws Exception {
    // South is visible only before moving north; north becomes visible only after that move.
    MapLocation south = new MapLocation(8, 3);
    MapLocation north = new MapLocation(8, 14);
    start(
        new MapLocation(8, 8),
        Direction.NORTH,
        null,
        south,
        north,
        new MapLocation(11, 16),
        new MapLocation(11, 5));
    round();
    java.util.List<MapLocation> records = new java.util.ArrayList<>();
    for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
      records.add(CheeseMines.read(king.getController(), i));
    }
    assertTrue(records.contains(south));
    assertTrue(records.contains(north));
    // A later-created baby reads the same engine team memory without sensing either mine.
    world.spawnRobot(200, UnitType.BABY_RAT, new MapLocation(2, 2), Direction.WEST, 0, Team.A);
    InternalRobot baby = world.getObjectInfo().getRobotByID(200);
    for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
      assertEquals(records.get(i), CheeseMines.read(baby.getController(), i));
    }
    round();
    for (int i = 0; i < Config.MAX_CHEESE_MINES; i++) {
      assertEquals(records.get(i), CheeseMines.read(baby.getController(), i));
    }
  }

  @Test
  public void openTravelPublishesActualCentersAndWaitsThroughCooldown() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    round();
    assertEquals(new MapLocation(8, 9), king.getLocation());
    Direction facing = king.getDirection();
    round();
    assertEquals(new MapLocation(8, 9), king.getLocation());
    assertEquals(facing, king.getDirection());
    // Forty movement cooldown decays by ten per round: travel resumes on round five.
    round();
    round();
    round();
    assertEquals(new MapLocation(8, 10), king.getLocation());
  }

  @Test
  public void offCenterFootprintWallBlocksForwardAndOnlyTurns() {
    // Center destination (8,9) is empty; the newly occupied corner (9,10) is a wall.
    start(new MapLocation(8, 8), Direction.NORTH, new MapLocation(9, 10));
    round();
    assertEquals(new MapLocation(8, 8), king.getLocation());
    assertTrue(king.getDirection() != Direction.NORTH);
    assertTrue(king.getController().canMoveForward());
    round();
    assertTrue(!king.getLocation().equals(new MapLocation(8, 8)));
  }

  @Test
  public void diagonalCornerAndMapEdgeUseFullFootprintLegality() {
    start(new MapLocation(1, 1), Direction.SOUTHWEST, null);
    round();
    assertEquals(new MapLocation(1, 1), king.getLocation());
    assertTrue(
        king.getDirection() == Direction.NORTH
            || king.getDirection() == Direction.NORTHEAST
            || king.getDirection() == Direction.EAST);
    round();
    assertTrue(!king.getLocation().equals(new MapLocation(1, 1)));
  }

  @Test
  public void occupiedFootprintTileAndTurningCooldownPreventIllegalActions() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    // Another robot blocks the far corner of the new footprint, not its center.
    world.spawnRobot(200, UnitType.BABY_RAT, new MapLocation(9, 10), Direction.SOUTH, 0, Team.B);
    king.setTurningCooldownTurns(20);
    round();
    assertEquals(new MapLocation(8, 8), king.getLocation());
    assertEquals(Direction.NORTH, king.getDirection());
    round();
    assertEquals(new MapLocation(8, 8), king.getLocation());
    assertTrue(king.getDirection() != Direction.NORTH);
  }

  @Test
  public void trappedKingPublishesAndKeepsFacingAcrossTurns() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    // A wall ring just outside the footprint makes all eight steps illegal.
    boolean[] walls = world.getGameMap().getWallArray();
    for (int x = 6; x <= 10; x++) {
      for (int y = 6; y <= 10; y++) {
        if (x == 6 || x == 10 || y == 6 || y == 10) {
          walls[x + y * 20] = true;
        }
      }
    }
    round();
    round();
    assertEquals(new MapLocation(8, 8), king.getLocation());
    assertEquals(Direction.NORTH, king.getDirection());
  }

  @Test
  public void dirtBlocksCheeseStepAndKingFallsBackToOpenForward() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    world.addCheese(new MapLocation(11, 8), 10);
    world.setDirt(new MapLocation(10, 7), true);
    round();
    assertEquals(Direction.NORTH, king.getDirection());
    assertEquals(new MapLocation(8, 9), king.getLocation());
  }

  @Test
  public void sensedCheeseSteersThenMovesWithImmediatePublication() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    world.addCheese(new MapLocation(11, 8), 10);
    round();
    assertEquals(Direction.EAST, king.getDirection());
    assertEquals(new MapLocation(8, 8), king.getLocation());
    round();
    assertEquals(new MapLocation(9, 8), king.getLocation());
  }

  @Test
  public void denseSensedCheeseScanAndOtherKingWorkLeaveBytecodeHeadroom() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    // Fill every tile with loose cheese to exercise the complete sensing/selection scan.
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {
        world.addCheese(new MapLocation(x, y), 1);
      }
    }
    // Exercise existing spawn work too; the newborn uses its own isolated sandbox state.
    world.getTeamInfo().addCheese(Team.A, 1000);
    for (int turn = 0; turn < 8; turn++) {
      round();
    }
    assertTrue(world.getTeamInfo().getNumBabyRats(Team.A) > 0);
  }

  @Test
  public void picksUpMultipleTilesAroundNewFootprintButLeavesDistantCheese() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    // The closest target is ahead. After moving, these tiles lie around the new footprint.
    MapLocation first = new MapLocation(8, 10);
    MapLocation second = new MapLocation(9, 10);
    MapLocation distant = new MapLocation(8, 14);
    world.addCheese(first, 7);
    world.addCheese(second, 11);
    world.addCheese(distant, 13);
    int before = world.getTeamInfo().getCheese(Team.A);
    round();
    assertEquals(new MapLocation(8, 9), king.getLocation());
    assertEquals(0, world.getCheeseAmount(first));
    assertEquals(0, world.getCheeseAmount(second));
    assertEquals(13, world.getCheeseAmount(distant));
    assertEquals(before + 18 - 2, world.getTeamInfo().getCheese(Team.A));
  }

  @Test
  public void cooldownsStillAllowEngineApprovedPickup() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    MapLocation cheese = new MapLocation(9, 8);
    world.addCheese(cheese, 7);
    king.setMovementCooldownTurns(40);
    king.setActionCooldownTurns(20);
    // Engine 1.2.6 pickup checks range/sensing, not movement or action cooldown.
    round();
    assertEquals(0, world.getCheeseAmount(cheese));
    assertEquals(new MapLocation(8, 8), king.getLocation());
  }

  @Test
  public void reachesConfiguredCapAndReplacesDeathOutsideVision() throws Exception {
    // Arrange one fewer than the configured cap, far from a stationary king, with ample cheese.
    start(new MapLocation(8, 8), Direction.NORTH, null);
    int cap = Config.MAX_BABY_RATS;
    int belowCapCost = spawnCostForPopulation(cap - 1);
    seedBabies(cap - 1);
    setTeamCheese(Config.MIN_CHEESE_RESERVE + 5000);
    king.setMovementCooldownTurns(1000);
    assertEquals(belowCapCost, king.getController().getCurrentRatCost());
    round();
    assertEquals(cap, world.getTeamInfo().getNumBabyRats(Team.A));
    assertEquals(spawnCostForPopulation(cap), king.getController().getCurrentRatCost());
    round();
    assertEquals(cap, world.getTeamInfo().getNumBabyRats(Team.A));

    // Removing a real baby outside vision must reopen recruitment without a heartbeat.
    MapLocation victim = world.getObjectInfo().getRobotByID(300).getLocation();
    assertTrue(king.getLocation().distanceSquaredTo(victim) > 25);
    world.destroyRobot(300);
    assertEquals(belowCapCost, king.getController().getCurrentRatCost());
    round();
    assertEquals(cap, world.getTeamInfo().getNumBabyRats(Team.A));
    round();
    assertEquals(cap, world.getTeamInfo().getNumBabyRats(Team.A));
    // Two more losses require two separate king turns, then recruitment stops again.
    world.destroyRobot(301);
    world.destroyRobot(302);
    round();
    assertEquals(cap - 1, world.getTeamInfo().getNumBabyRats(Team.A));
    // The previous newborn can occupy the fixed spawn tile. Free it with a legal engine move
    // so this assertion tests replenishment, rather than assuming uncongested placement.
    InternalRobot occupant = world.getRobot(new MapLocation(8, 10));
    if (occupant != null) {
      occupant.setMovementCooldownTurns(0);
      assertTrue(occupant.getController().canMove(Direction.EAST));
      occupant.getController().move(Direction.EAST);
    }
    round();
    assertEquals(cap, world.getTeamInfo().getNumBabyRats(Team.A));
    round();
    assertEquals(cap, world.getTeamInfo().getNumBabyRats(Team.A));
  }

  @Test
  public void engineReserveBoundaryAndPickupFundOneReplacement() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    int initialBabyCount = 15;
    int spawnCost = spawnCostForPopulation(initialBabyCount);
    seedBabies(initialBabyCount);
    king.setMovementCooldownTurns(1000);
    setTeamCheese(Config.MIN_CHEESE_RESERVE + spawnCost - 1);
    round();
    assertEquals(initialBabyCount, world.getTeamInfo().getNumBabyRats(Team.A));
    assertEquals(Config.MIN_CHEESE_RESERVE + spawnCost - 3, world.getTeamInfo().getCheese(Team.A));
    // Pickup happens before the guard: cheese reaches reserve + cost, then upkeep spends two.
    world.addCheese(new MapLocation(8, 9), 3);
    round();
    assertEquals(initialBabyCount + 1, world.getTeamInfo().getNumBabyRats(Team.A));
    assertEquals(Config.MIN_CHEESE_RESERVE - 2, world.getTeamInfo().getCheese(Team.A));
    assertNotNull(world.getRobot(new MapLocation(8, 10)));
  }

  @Test
  public void engineActionCooldownThenBlockedNorthFallsBackClockwise() {
    start(new MapLocation(8, 8), Direction.NORTH, null);
    world.getTeamInfo().addCheese(Team.A, 1000);
    king.setMovementCooldownTurns(1000);
    king.setActionCooldownTurns(20);
    round();
    assertEquals(0, world.getTeamInfo().getNumBabyRats(Team.A));
    // North is blocked; northeast is the first clockwise opening in the real engine.
    world.setDirt(new MapLocation(8, 10), true);
    round();
    assertEquals(1, world.getTeamInfo().getNumBabyRats(Team.A));
    assertNotNull(world.getRobot(new MapLocation(10, 10)));
    world.setDirt(new MapLocation(8, 10), false);
    round();
    assertEquals(2, world.getTeamInfo().getNumBabyRats(Team.A));
    assertNotNull(world.getRobot(new MapLocation(8, 10)));
  }

  @Test
  public void movedKingSpawnsNorthOfActualNewCenter() {
    // A real forward move must precede placement; the old north candidate becomes king footprint.
    start(new MapLocation(8, 8), Direction.NORTH, null);
    world.getTeamInfo().addCheese(Team.A, 1000);
    round();
    assertEquals(new MapLocation(8, 9), king.getLocation());
    InternalRobot baby = world.getRobot(new MapLocation(8, 11));
    assertNotNull(baby);
    assertEquals(UnitType.BABY_RAT, baby.getType());
    assertEquals(1, world.getTeamInfo().getNumBabyRats(Team.A));
  }

  private void seedBabies(int count) {
    for (int i = 0; i < count; i++) {
      int x = 1 + i % 18;
      int y = 1 + i / 18;
      world.spawnRobot(
          300 + i, UnitType.BABY_RAT, new MapLocation(x, y), Direction.NORTH, 0, Team.A);
      world.getObjectInfo().getRobotByID(300 + i).setMovementCooldownTurns(1000);
    }
  }

  private int spawnCostForPopulation(int population) {
    return GameConstants.BUILD_ROBOT_BASE_COST
        + GameConstants.BUILD_ROBOT_COST_INCREASE
            * (population / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
  }

  private void setTeamCheese(int amount) {
    int current = world.getTeamInfo().getCheese(Team.A);
    world.getTeamInfo().addCheese(Team.A, amount - current);
  }

  private void start(
      MapLocation location, Direction facing, MapLocation wall, MapLocation... mines) {
    String classes =
        RobotPlayer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    output = new ByteArrayOutputStream();
    player =
        new PlayerControlProvider(
            Team.A, "ratplayer", CrossPlayLanguage.JAVA, null, classes, output, false);
    TeamControlProvider providers = new TeamControlProvider();
    providers.registerControlProvider(Team.A, player);
    providers.registerControlProvider(Team.B, new NullControlProvider());
    providers.registerControlProvider(Team.NEUTRAL, new NullControlProvider());
    RobotInfo[] bodies = {
      new RobotInfo(1, Team.A, UnitType.RAT_KING, 500, location, facing, 0, 0, null),
      new RobotInfo(
          2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17), Direction.SOUTH, 0, 0, null)
    };
    LiveMap map =
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100, "RatKingMovementFixture", bodies);
    for (MapLocation mine : mines) {
      map.getCheeseMineArray()[mine.x + mine.y * 20] = true;
    }
    if (wall != null) {
      map.getWallArray()[wall.x + wall.y * 20] = true;
    }
    GameInfo game =
        new GameInfo(
            "ratplayer",
            CrossPlayLanguage.JAVA,
            "ratplayer",
            classes,
            "stationary",
            CrossPlayLanguage.JAVA,
            "stationary",
            null,
            new String[] {map.getMapName()},
            null,
            false);
    GameMaker maker = new GameMaker(game, null, false);
    maker.makeGameHeader();
    world = new GameWorld(map, providers, maker.getMatchMaker());
    // No cats are needed: backstab mode avoids the no-cats cooperative victory condition.
    world.backstab(Team.A);
    setTeamCheese(Math.max(0, Config.MIN_CHEESE_RESERVE - 1000));
    king = world.getObjectInfo().getRobotByID(1);
  }

  private void round() {
    world.runRound();
    assertNotNull(
        "King must remain alive after the instrumented turn",
        world.getObjectInfo().getRobotByID(1));
    String log = output.toString(StandardCharsets.UTF_8);
    assertFalse(log, log.contains("Exception"));
    assertEquals(king.getLocation().x, world.readSharedArray(0, Team.A));
    assertEquals(king.getLocation().y, world.readSharedArray(1, Team.A));
    assertEquals(1, world.readSharedArray(2, Team.A));
    int used = king.getBytecodesUsed();
    peakBytecodes = Math.max(peakBytecodes, used);
    // Full turns include initialization, sensing, actions, logging, and Clock.yield().
    assertTrue(
        "Expected full-turn bytecodes with at least 1,000 headroom, got " + used,
        used > 0 && used < 19000);
    System.out.println(
        "King fixture round="
            + world.getCurrentRound()
            + " bytecodes="
            + used
            + " peak="
            + peakBytecodes
            + " center="
            + king.getLocation());
  }
}
