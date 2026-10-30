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
import battlecode.world.MapSymmetry;
import battlecode.world.control.NullControlProvider;
import battlecode.world.control.PlayerControlProvider;
import battlecode.world.control.TeamControlProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Test;

/**
 * Instrumented engine check for the king low-health cat block-and-flee turn.
 *
 * <p>{@link support.TestRobotController} cannot count bytecodes. This fixture loads {@code
 * ratplayer} through {@link PlayerControlProvider} so {@link InternalRobot#getBytecodesUsed()} is
 * the instrumented per-turn count after {@code Clock.yield()}. The sandbox uses compiled {@link
 * Config} values. Named cases are in {@code .ai/test-cases.md}.
 */
public class RatKingCatAvoidanceEngineTest {
  private static final int KING_BYTECODE_LIMIT = 20000;
  private static final int BYTECODE_HEADROOM = 1000;
  private static final MapLocation KING_START = new MapLocation(8, 8);
  private static final MapLocation NORTH_RING = new MapLocation(8, 10);
  private static final MapLocation FLEE_SOUTH = new MapLocation(8, 7);
  private static final MapLocation CAT_START = new MapLocation(8, 12);

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
   * A low-health king with a visible cat should take the dirt-and-flee path, which is the extra
   * sensing and {@code placeDirt} work this feature adds on top of a normal king turn.
   *
   * <p>Arrange: compiled strategy {@code BLOCK_AND_FLEE_WHEN_LOW_HEALTH}, north-facing king at
   * (8,8) with 20 HP, one dirt in the team stash, a frozen cat at (8,12). Act: one instrumented
   * turn. Assert: dirt is on the north spawn-ring tile, the king stepped south, and the full turn
   * stays under 19,000.
   */
  @Test
  public void engineLowHealthCatBlockBytecodeHeadroom() {
    assertEquals(
        "This test measures the compiled default king cat policy",
        Config.KingCatResponseStrategy.BLOCK_AND_FLEE_WHEN_LOW_HEALTH,
        Config.KING_CAT_RESPONSE_STRATEGY);
    assertEquals(20, Config.KING_CAT_FLEE_HEALTH);
    start();
    freezeCat();
    world.getTeamInfo().updateDirt(Team.A, false);
    int dirtBefore = world.getTeamInfo().getDirt(Team.A);
    assertEquals(1, dirtBefore);

    int used = observeRound();

    System.out.println(
        "engineLowHealthCatBlockBytecodeHeadroom peak=" + peakBytecodes + " used=" + used);
    System.out.flush();
    assertTrue("Expected dirt on the spawn-ring tile toward the cat", world.getDirt(NORTH_RING));
    assertEquals(FLEE_SOUTH, king.getLocation());
    assertEquals(dirtBefore - 1, world.getTeamInfo().getDirt(Team.A));
    assertTrue(
        "Expected a king cat-block peak with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + peakBytecodes,
        peakBytecodes > 0 && peakBytecodes < KING_BYTECODE_LIMIT - BYTECODE_HEADROOM);
  }

  private void freezeCat() {
    InternalRobot cat = world.getObjectInfo().getRobotByID(3);
    assertNotNull("Fixture must spawn a living cat", cat);
    cat.setMovementCooldownTurns(1000);
    cat.setActionCooldownTurns(1000);
    cat.setTurningCooldownTurns(1000);
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
      new RobotInfo(
          1,
          Team.A,
          UnitType.RAT_KING,
          Config.KING_CAT_FLEE_HEALTH,
          KING_START,
          Direction.NORTH,
          0,
          0,
          null),
      new RobotInfo(
          2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17), Direction.SOUTH, 0, 0, null),
      new RobotInfo(
          3,
          Team.NEUTRAL,
          UnitType.CAT,
          UnitType.CAT.health,
          CAT_START,
          Direction.SOUTH,
          0,
          0,
          null)
    };
    int tiles = 20 * 20;
    ArrayList<Integer> catIds = new ArrayList<>();
    catIds.add(3);
    ArrayList<int[]> catWaypoints = new ArrayList<>();
    catWaypoints.add(new int[] {8 + 18 * 20});
    LiveMap map =
        new LiveMap(
            20,
            20,
            new MapLocation(0, 0),
            6147,
            100,
            "RatKingCatAvoidanceFixture",
            MapSymmetry.ROTATIONAL,
            new boolean[tiles],
            new boolean[tiles],
            new boolean[tiles],
            new int[tiles],
            catIds,
            catWaypoints,
            bodies);
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
    king = world.getObjectInfo().getRobotByID(1);
    // LiveMap bodies do not set InternalRobot health; the type default is 600.
    king.addHealth(Config.KING_CAT_FLEE_HEALTH - king.getHealth());
    RatKing.locationPublished = false;
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
        "king cat avoidance engine bytecodes used="
            + used
            + " peak="
            + peakBytecodes
            + " limit="
            + KING_BYTECODE_LIMIT
            + " round="
            + world.getCurrentRound()
            + " loc="
            + king.getLocation()
            + " health="
            + king.getHealth());
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
