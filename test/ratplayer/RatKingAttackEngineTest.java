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

/**
 * Instrumented engine check for a king bite of an in-range enemy rat when the king cannot flee.
 *
 * <p>{@link support.TestRobotController} cannot count bytecodes or apply bite damage. This fixture
 * loads {@code ratplayer} through {@link PlayerControlProvider}. Named cases are in {@code
 * .ai/test-cases.md}.
 */
public class RatKingAttackEngineTest {
  private static final int KING_BYTECODE_LIMIT = 20000;
  private static final int BYTECODE_HEADROOM = 1000;
  private static final MapLocation KING_START = new MapLocation(8, 8);
  private static final MapLocation ENEMY_START = new MapLocation(9, 10);

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
   * A movement-locked king in backstabbing mode should bite an opposing baby on an attack-ring
   * tile and leave bytecode headroom.
   *
   * <p>Arrange: north-facing king at (8,8) with movement and turning held, Team B baby at (9,10),
   * spawn ring walled, backstab already started. Act: one instrumented turn. Assert: the baby lost
   * 10 HP, the king stayed put, no allied baby was spawned, and the turn stays under 19,000.
   */
  @Test
  public void engineBlockedKingBitesEnemyBabyAndLeavesBytecodeHeadroom() {
    start();
    InternalRobot enemy = world.getObjectInfo().getRobotByID(200);
    assertNotNull(enemy);
    int healthBefore = enemy.getHealth();

    int used = observeRound();

    System.out.println("engineBlockedKingBitesEnemyBaby peak=" + peakBytecodes + " used=" + used);
    System.out.flush();
    assertEquals(KING_START, king.getLocation());
    assertEquals(healthBefore - 10, enemy.getHealth());
    assertEquals(0, world.getTeamInfo().getNumBabyRats(Team.A));
    assertTrue(
        "Expected a king attack peak with at least "
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
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100, "RatKingAttackFixture", bodies);
    MapLocation[] spawnRing = {
      new MapLocation(8, 10),
      new MapLocation(10, 10),
      new MapLocation(10, 8),
      new MapLocation(10, 6),
      new MapLocation(8, 6),
      new MapLocation(6, 6),
      new MapLocation(6, 8),
      new MapLocation(6, 10)
    };
    for (int i = 0; i < spawnRing.length; i++) {
      MapLocation wall = spawnRing[i];
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
    world.spawnRobot(200, UnitType.BABY_RAT, ENEMY_START, Direction.SOUTH, 0, Team.B);
    InternalRobot enemy = world.getObjectInfo().getRobotByID(200);
    enemy.setMovementCooldownTurns(1000);
    enemy.setActionCooldownTurns(1000);
    enemy.setTurningCooldownTurns(1000);
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
        "king attack engine bytecodes used="
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
