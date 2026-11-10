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

/**
 * Instrumented engine checks for king dirt collection.
 *
 * <p>Spawn uses the action first, so these fixtures wall the eight spawn-ring tiles. Dirt is placed
 * on in-range tiles that are not spawn candidates. Named cases are in {@code .ai/test-cases.md}.
 */
public class RatKingDirtEngineTest {
  private static final int KING_BYTECODE_LIMIT = 20000;
  private static final int BYTECODE_HEADROOM = 1000;
  private static final MapLocation KING_START = new MapLocation(8, 8);
  private static final MapLocation FIRST_DIG = new MapLocation(9, 10);
  private static final MapLocation SECOND_DIG = new MapLocation(10, 9);
  private static final MapLocation[] SPAWN_RING = {
    new MapLocation(8, 10),
    new MapLocation(10, 10),
    new MapLocation(10, 8),
    new MapLocation(10, 6),
    new MapLocation(8, 6),
    new MapLocation(6, 6),
    new MapLocation(6, 8),
    new MapLocation(6, 10)
  };

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

  /**
   * Arrange: spawn ring walled, dirt at (9,10), cheese above the dig reserve. Act: one instrumented
   * turn with movement held. Assert: the tile is passable, team dirt is up by 1, cheese is down by
   * 5 plus king upkeep.
   */
  @Test
  public void engineDigIncreasesTeamDirtAndCharges5Cheese() {
    start();
    world.setDirt(FIRST_DIG, true);
    int cheeseBefore = world.getTeamInfo().getCheese(Team.A);
    int dirtBefore = world.getTeamInfo().getDirt(Team.A);

    observeRound();

    assertFalse(world.getDirt(FIRST_DIG));
    assertTrue(world.isPassable(FIRST_DIG));
    assertEquals(dirtBefore + 1, world.getTeamInfo().getDirt(Team.A));
    assertEquals(0, world.getTeamInfo().getNumBabyRats(Team.A));
    assertEquals(
        cheeseBefore
            - GameConstants.DIG_DIRT_CHEESE_COST
            - GameConstants.RAT_KING_CHEESE_CONSUMPTION,
        world.getTeamInfo().getCheese(Team.A));
  }

  /**
   * Arrange: dirt at (9,10) then (10,9), spawn ring walled, movement held. Assert: the second dig
   * waits until action cooldown is below 10.
   */
  @Test
  public void engineRespectsCooldownBetweenDigs() {
    start();
    world.setDirt(FIRST_DIG, true);
    world.setDirt(SECOND_DIG, true);

    observeRound();
    assertFalse(world.getDirt(FIRST_DIG));
    assertTrue(world.getDirt(SECOND_DIG));
    assertTrue(
        "First dig should leave action cooldown at or above the act limit",
        king.getActionCooldownTurns() >= GameConstants.COOLDOWN_LIMIT);

    observeRound();
    assertTrue(
        "Second dirt must wait until cooldown is below " + GameConstants.COOLDOWN_LIMIT,
        world.getDirt(SECOND_DIG));

    observeRound();
    assertFalse(world.getDirt(SECOND_DIG));
    assertEquals(2, world.getTeamInfo().getDirt(Team.A));
    assertEquals(0, world.getTeamInfo().getNumBabyRats(Team.A));
  }

  /**
   * Same fixture as {@link #engineDigIncreasesTeamDirtAndCharges5Cheese}. Assert: the dig turn
   * leaves at least 1,000 bytecodes under the 20,000 king limit.
   */
  @Test
  public void engineDigTurnBytecodeHeadroom() {
    start();
    world.setDirt(FIRST_DIG, true);

    int used = observeRound();

    System.out.println("engineKingDigTurnBytecodeHeadroom peak=" + peakBytecodes + " used=" + used);
    System.out.flush();
    assertTrue(
        "Expected a dig-turn peak with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + peakBytecodes,
        peakBytecodes > 0 && peakBytecodes < KING_BYTECODE_LIMIT - BYTECODE_HEADROOM);
  }

  private void start() {
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
      new RobotInfo(1, Team.A, UnitType.RAT_KING, 500, KING_START, Direction.NORTH, 0, 0, null),
      new RobotInfo(
          2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17), Direction.SOUTH, 0, 0, null)
    };
    LiveMap map =
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100, "RatKingDirtFixture", bodies);
    for (int i = 0; i < SPAWN_RING.length; i++) {
      MapLocation wall = SPAWN_RING[i];
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
    world.backstab(Team.A);
    int current = world.getTeamInfo().getCheese(Team.A);
    world.getTeamInfo().addCheese(Team.A, 1000 - current);
    king = world.getObjectInfo().getRobotByID(1);
    king.setMovementCooldownTurns(1000);
    king.setTurningCooldownTurns(1000);
  }

  private int observeRound() {
    world.runRound();
    assertNotNull(
        "King must remain alive after the instrumented turn",
        world.getObjectInfo().getRobotByID(1));
    String log = output.toString(StandardCharsets.UTF_8);
    assertFalse(log, log.contains("Exception"));
    int used = king.getBytecodesUsed();
    peakBytecodes = Math.max(peakBytecodes, used);
    System.out.println(
        "king dirt engine bytecodes used="
            + used
            + " peak="
            + peakBytecodes
            + " limit="
            + KING_BYTECODE_LIMIT
            + " round="
            + world.getCurrentRound()
            + " loc="
            + king.getLocation());
    System.out.flush();
    assertTrue(
        "Expected full-turn bytecodes with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + used,
        used > 0 && used < KING_BYTECODE_LIMIT - BYTECODE_HEADROOM);
    return used;
  }
}
