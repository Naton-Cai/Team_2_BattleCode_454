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
import org.junit.Ignore;
import org.junit.Test;

/**
 * Measures full baby-rat turns in the pinned engine sandbox.
 *
 * <p>{@link support.TestRobotController} cannot count bytecodes. These fixtures load {@code
 * ratplayer} through {@link PlayerControlProvider} so {@link InternalRobot#getBytecodesUsed()} is
 * the instrumented per-turn count after {@code Clock.yield()}. That number is the whole turn, not
 * one pathing helper alone.
 *
 * <p>The sandbox loads its own copy of {@link Config} from the compiled classes, so assigning
 * {@link Config#BABY_PATH_TO_CHEESE_STRATEGY} from a test never reaches the robot. The robot runs
 * whatever value that field is compiled with. The default is greedy, so the visible BFS tests are
 * ignored; see {@link #BFS_MANUAL_CONFIG} for how to run them.
 *
 * <p>The baby limit is 17,500; each observed turn must keep 1,000 headroom, matching {@link
 * RatKingEngineTest}.
 */
public class BabyRatBytecodeEngineTest {
  private static final int BABY_BYTECODE_LIMIT = 17500;
  private static final int BYTECODE_HEADROOM = 1000;
  private static final int MAX_TURNS_TO_CHEESE = 20;

  /**
   * Ignore reason for the visible BFS tests. Cheese chase is not using visible BFS, and the robot
   * only runs the compiled {@code Config} value.
   */
  static final String BFS_MANUAL_CONFIG =
      "Visible BFS is not the cheese default. To run: set Config.BABY_PATH_TO_CHEESE_STRATEGY (and"
          + " its reset() value) to VISIBLE_BFS, remove this @Ignore, run, then revert both.";

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
   * Cheese 4 tiles north is in the 90-degree cone (r²=16 ≤ 20) and not adjacent, so pickup does not
   * fire and movement runs visible BFS over the open cone. That is the expensive cheese-chase case.
   *
   * <p>Arrange: compiled config set to visible BFS by hand, north-facing baby at (8,8), one pile at
   * (8,12), open map. Act: three instrumented turns. Assert: each step is north toward the pile, and
   * each full turn stays under 16,500.
   */
  @Ignore(BFS_MANUAL_CONFIG)
  @Test
  public void openFieldCheeseChaseLeavesBytecodeHeadroom() {
    requireCompiledCheesePath(Config.PathingStrategy.VISIBLE_BFS);
    start(new MapLocation(8, 8), Direction.NORTH);
    MapLocation cheese = new MapLocation(8, 12);
    world.addCheese(cheese, 5);

    observeRound();
    assertEquals(new MapLocation(8, 9), baby.getLocation());
    assertEquals(5, world.getCheeseAmount(cheese));

    observeRound();
    assertEquals(new MapLocation(8, 10), baby.getLocation());

    observeRound();
    assertEquals(new MapLocation(8, 11), baby.getLocation());
    assertEquals(5, world.getCheeseAmount(cheese));
  }

  /**
   * Adjacent cheese is picked up this turn, so the cheese step is skipped and the rat wanders. That
   * is the cheap control: the harness must still report a positive instrumented count. It does not
   * depend on the cheese strategy.
   *
   * <p>Arrange: north-facing baby at (8,8), pile on the forward tile (8,9). Act: one instrumented
   * turn. Assert: the pile is gone and the turn stays under 16,500.
   */
  @Test
  public void adjacentCheesePickupLeavesBytecodeHeadroom() {
    start(new MapLocation(8, 8), Direction.NORTH);
    MapLocation cheese = new MapLocation(8, 9);
    world.addCheese(cheese, 5);

    observeRound();

    assertEquals(0, world.getCheeseAmount(cheese));
    assertTrue(baby.getController().getRawCheese() >= 5);
  }

  /**
   * Worst-case visible BFS: cheese on the far edge of the 90-degree cone (r²=16) and a wall the rat
   * can also see between them. BFS must search around the wall on the visible graph.
   *
   * <p>Arrange: compiled config set to visible BFS by hand, north-facing baby at (8,8), wall at
   * (8,10), pile at (8,12). Act: instrumented turns until pickup, or {@link #MAX_TURNS_TO_CHEESE}.
   * Assert: the pile is collected, the second step goes east to (9,10), and every turn stays under
   * 16,500. Prints per-turn bytecodes and a turns/peak/sum summary.
   */
  @Ignore(BFS_MANUAL_CONFIG)
  @Test
  public void coneEdgeCheeseWithVisibleWallUsesVisibleBfs() {
    requireCompiledCheesePath(Config.PathingStrategy.VISIBLE_BFS);
    runConeEdgeCheeseWithVisibleWall(new MapLocation(9, 10));
  }

  /**
   * Same map as {@link #coneEdgeCheeseWithVisibleWallUsesVisibleBfs}, with the compiled greedy
   * default. Greedy does not search the visible graph; it only probes {@code canMove}.
   *
   * <p>Arrange: compiled config as committed (greedy). Act/assert match the BFS twin, except the
   * second step goes west to (7,10).
   */
  @Test
  public void coneEdgeCheeseWithVisibleWallUsesGreedy() {
    requireCompiledCheesePath(Config.PathingStrategy.GREEDY);
    runConeEdgeCheeseWithVisibleWall(new MapLocation(7, 10));
  }

  /**
   * Fails fast with setup instructions when the compiled config does not match the strategy a test
   * measures. JUnit's {@code Config} is compiled from the same source as the sandbox copy, and no
   * test assigns this field, so its value is the one the robot will run.
   */
  private static void requireCompiledCheesePath(Config.PathingStrategy expected) {
    assertEquals(
        "This test measures "
            + expected
            + "; set Config.BABY_PATH_TO_CHEESE_STRATEGY to it before running",
        expected,
        Config.BABY_PATH_TO_CHEESE_STRATEGY);
  }

  private void runConeEdgeCheeseWithVisibleWall(MapLocation expectedSecondStep) {
    start(new MapLocation(8, 8), Direction.NORTH);
    // Wall two north sits in the cone between the rat and the far pile.
    world.getGameMap().getWallArray()[8 + 10 * 20] = true;
    MapLocation cheese = new MapLocation(8, 12);
    world.addCheese(cheese, 5);

    peakBytecodes = 0;
    int turns = 0;
    int sum = 0;
    MapLocation afterSecondStep = null;
    while (world.getCheeseAmount(cheese) > 0 && turns < MAX_TURNS_TO_CHEESE) {
      sum += observeRound();
      turns += 1;
      if (turns == 2) {
        afterSecondStep = baby.getLocation();
      }
    }

    System.out.println(
        "strategy="
            + Config.BABY_PATH_TO_CHEESE_STRATEGY
            + " turns="
            + turns
            + " peak="
            + peakBytecodes
            + " sum="
            + sum
            + " loc="
            + baby.getLocation()
            + " collected="
            + (world.getCheeseAmount(cheese) == 0));
    System.out.flush();

    assertEquals(0, world.getCheeseAmount(cheese));
    assertTrue(baby.getController().getRawCheese() >= 5);
    assertTrue(
        "Expected pickup within " + MAX_TURNS_TO_CHEESE + " turns, used " + turns, turns > 0);
    // Second step is the first place the strategies split around the wall at (8,10).
    assertEquals(expectedSecondStep, afterSecondStep);
  }

  private void start(MapLocation location, Direction facing) {
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
      new RobotInfo(1, Team.A, UnitType.BABY_RAT, 100, location, facing, 0, 0, null),
      new RobotInfo(
          2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17), Direction.SOUTH, 0, 0, null)
    };
    LiveMap map =
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100, "BabyRatBytecodeFixture", bodies);
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
    // Print before the headroom check so an over-budget turn still shows its count.
    System.out.println(
        "strategy="
            + Config.BABY_PATH_TO_CHEESE_STRATEGY
            + " bytecodes used="
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
    // Full turns include initialization, sensing, actions, logging, and Clock.yield().
    assertTrue(
        "Expected full-turn bytecodes with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + used,
        used > 0 && used < BABY_BYTECODE_LIMIT - BYTECODE_HEADROOM);
    return used;
  }
}
