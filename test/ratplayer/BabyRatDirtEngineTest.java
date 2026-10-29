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
 * Instrumented engine checks for baby-rat dirt collection.
 *
 * <p>The sandbox loads compiled {@link Config} values, so these tests use the starting dig reserve
 * and action order. Dirt is placed with {@link GameWorld#setDirt}. Named cases are in {@code
 * .ai/test-cases.md}.
 */
public class BabyRatDirtEngineTest {
  private static final int BABY_BYTECODE_LIMIT = 17500;
  private static final int BYTECODE_HEADROOM = 1000;
  private static final MapLocation BABY_START = new MapLocation(8, 8);
  private static final MapLocation FORWARD = new MapLocation(8, 9);
  private static final MapLocation LEFT = new MapLocation(7, 9);

  private GameWorld world;
  private PlayerControlProvider player;
  private ByteArrayOutputStream output;
  private InternalRobot baby;
  private int peakBytecodes;

  @After
  public void stopSandbox() {
    if (player != null) {
      player.matchEnded();
    }
  }

  /**
   * Arrange: dirt on the forward tile, global cheese above the reserve plus 5. Act: one instrumented
   * turn. Assert: the tile is passable, team dirt is up by 1, and team cheese is down by 5 plus any
   * king upkeep in this fixture.
   */
  @Test
  public void engineDigIncreasesTeamDirtAndCharges5Cheese() {
    start();
    world.setDirt(FORWARD, true);
    int cheeseBefore = world.getTeamInfo().getCheese(Team.A);
    int dirtBefore = world.getTeamInfo().getDirt(Team.A);

    observeRound();

    assertFalse(world.getDirt(FORWARD));
    assertTrue(world.isPassable(FORWARD));
    assertEquals(dirtBefore + 1, world.getTeamInfo().getDirt(Team.A));
    assertEquals(
        cheeseBefore - GameConstants.DIG_DIRT_CHEESE_COST, world.getTeamInfo().getCheese(Team.A));
  }

  /**
   * Arrange: dirt on the forward tile and the left tile. Movement and turning are held so the rat
   * stays on the starting square facing north. Assert: the second dig waits until action cooldown
   * is below 10, and neither turn logs an exception.
   */
  @Test
  public void engineRespectsCooldownBetweenDigs() {
    start();
    world.setDirt(FORWARD, true);
    world.setDirt(LEFT, true);
    holdPosition();

    observeRound();
    assertFalse(world.getDirt(FORWARD));
    assertTrue(world.getDirt(LEFT));
    assertTrue(
        "First dig should leave action cooldown at or above the act limit",
        baby.getActionCooldownTurns() >= GameConstants.COOLDOWN_LIMIT);

    observeRound();
    assertTrue(
        "Second dirt must wait until cooldown is below " + GameConstants.COOLDOWN_LIMIT,
        world.getDirt(LEFT));

    observeRound();
    assertFalse(world.getDirt(LEFT));
    assertEquals(2, world.getTeamInfo().getDirt(Team.A));
  }

  /**
   * Same fixture as {@link #engineDigIncreasesTeamDirtAndCharges5Cheese}. Assert: the dig turn
   * leaves at least 1,000 bytecodes under the 17,500 baby-rat limit.
   */
  @Test
  public void engineDigTurnBytecodeHeadroom() {
    start();
    world.setDirt(FORWARD, true);

    int used = observeRound();

    System.out.println("engineDigTurnBytecodeHeadroom peak=" + peakBytecodes + " used=" + used);
    System.out.flush();
    assertTrue(
        "Expected a dig-turn peak with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + peakBytecodes,
        peakBytecodes > 0 && peakBytecodes < BABY_BYTECODE_LIMIT - BYTECODE_HEADROOM);
  }

  private void holdPosition() {
    baby.setMovementCooldownTurns(100);
    baby.setTurningCooldownTurns(100);
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
      new RobotInfo(1, Team.A, UnitType.BABY_RAT, 100, BABY_START, Direction.NORTH, 0, 0, null),
      new RobotInfo(
          2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17), Direction.SOUTH, 0, 0, null)
    };
    LiveMap map =
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100, "BabyRatDirtFixture", bodies);
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
    baby = world.getObjectInfo().getRobotByID(1);
  }

  private int observeRound() {
    world.runRound();
    assertNotNull(
        "Baby rat must remain alive after the instrumented turn",
        world.getObjectInfo().getRobotByID(1));
    String log = output.toString(StandardCharsets.UTF_8);
    assertFalse(log, log.contains("Exception"));
    int used = baby.getBytecodesUsed();
    peakBytecodes = Math.max(peakBytecodes, used);
    System.out.println(
        "dirt engine bytecodes used="
            + used
            + " peak="
            + peakBytecodes
            + " limit="
            + BABY_BYTECODE_LIMIT
            + " round="
            + world.getCurrentRound()
            + " loc="
            + baby.getLocation());
    System.out.flush();
    assertTrue(
        "Expected full-turn bytecodes with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + used,
        used > 0 && used < BABY_BYTECODE_LIMIT - BYTECODE_HEADROOM);
    return used;
  }
}
