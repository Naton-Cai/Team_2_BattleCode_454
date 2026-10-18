package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import battlecode.common.Direction;
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
    assertTrue(king.getDirection() == Direction.NORTH
        || king.getDirection() == Direction.NORTHEAST || king.getDirection() == Direction.EAST);
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

  private void start(MapLocation location, Direction facing, MapLocation wall) {
    String classes = RobotPlayer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    output = new ByteArrayOutputStream();
    player = new PlayerControlProvider(Team.A, "ratplayer", CrossPlayLanguage.JAVA, null,
        classes, output, false);
    TeamControlProvider providers = new TeamControlProvider();
    providers.registerControlProvider(Team.A, player);
    providers.registerControlProvider(Team.B, new NullControlProvider());
    providers.registerControlProvider(Team.NEUTRAL, new NullControlProvider());
    RobotInfo[] bodies = {
        new RobotInfo(1, Team.A, UnitType.RAT_KING, 500, location, facing, 0, 0, null),
        new RobotInfo(2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17),
            Direction.SOUTH, 0, 0, null)
    };
    LiveMap map = new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100,
        "RatKingMovementFixture", bodies);
    if (wall != null) {
      map.getWallArray()[wall.x + wall.y * 20] = true;
    }
    GameInfo game = new GameInfo("ratplayer", CrossPlayLanguage.JAVA, "ratplayer", classes,
        "stationary", CrossPlayLanguage.JAVA, "stationary", null,
        new String[] {map.getMapName()}, null, false);
    GameMaker maker = new GameMaker(game, null, false);
    maker.makeGameHeader();
    world = new GameWorld(map, providers, maker.getMatchMaker());
    // No cats are needed: backstab mode avoids the no-cats cooperative victory condition.
    world.backstab(Team.A);
    world.getTeamInfo().addCheese(Team.A, -world.getTeamInfo().getCheese(Team.A) + 400);
    king = world.getObjectInfo().getRobotByID(1);
  }

  private void round() {
    world.runRound();
    assertNotNull("King must remain alive after the instrumented turn",
        world.getObjectInfo().getRobotByID(1));
    String log = output.toString(StandardCharsets.UTF_8);
    assertFalse(log, log.contains("Exception"));
    assertEquals(king.getLocation().x, world.readSharedArray(0, Team.A));
    assertEquals(king.getLocation().y, world.readSharedArray(1, Team.A));
    assertEquals(1, world.readSharedArray(2, Team.A));
    int used = king.getBytecodesUsed();
    peakBytecodes = Math.max(peakBytecodes, used);
    // Full turns include initialization, sensing, actions, logging, and Clock.yield().
    assertTrue("Expected full-turn bytecodes with at least 1,000 headroom, got " + used,
        used > 0 && used < 19000);
    System.out.println("King fixture round=" + world.getCurrentRound() + " bytecodes=" + used
        + " peak=" + peakBytecodes + " center=" + king.getLocation());
  }
}
