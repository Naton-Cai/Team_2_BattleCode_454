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
 * Instrumented engine check for baby-rat enemy-king hunt.
 *
 * <p>{@link support.TestRobotController} cannot count bytecodes or prove king-footprint bites. This
 * fixture loads {@code ratplayer} through {@link PlayerControlProvider} so {@link
 * InternalRobot#getBytecodesUsed()} is the instrumented per-turn count after {@code Clock.yield()}.
 * Named cases are in {@code .ai/test-cases.md}.
 */
public class BabyRatHuntEngineTest {
  private static final int BABY_BYTECODE_LIMIT = 17500;
  private static final int BYTECODE_HEADROOM = 1000;
  private static final MapLocation BABY_START = new MapLocation(8, 8);
  private static final MapLocation ENEMY_KING = new MapLocation(8, 12);

  private GameWorld world;
  private PlayerControlProvider player;
  private ByteArrayOutputStream output;
  private InternalRobot baby;
  private InternalRobot enemyKing;
  private int peakBytecodes;

  @After
  public void stopSandbox() {
    if (player != null) {
      player.matchEnded();
    }
  }

  /**
   * An empty-handed baby in backstab should walk a visible enemy king down to bite range and then
   * bite, leaving bytecode headroom on every observed turn.
   *
   * <p>Arrange: north-facing baby at (8,8), Team B king at (8,12) in the 90-degree cone, no cheese,
   * backstab on, enemy king movement held. Act: three instrumented turns. Assert: steps (8,9) then
   * (8,10), the third turn bites for 10 damage without stepping into the footprint, and each turn
   * stays under 16,500.
   */
  @Test
  public void engineHuntsVisibleEnemyKingThenBitesWithBytecodeHeadroom() {
    start();
    freezeEnemyKing();
    int healthBefore = enemyKing.getHealth();

    observeRound();
    assertEquals(new MapLocation(8, 9), baby.getLocation());

    observeRound();
    assertEquals(new MapLocation(8, 10), baby.getLocation());

    observeRound();
    assertEquals(new MapLocation(8, 10), baby.getLocation());
    assertEquals(healthBefore - 10, enemyKing.getHealth());

    System.out.println(
        "engineHuntsVisibleEnemyKing peak=" + peakBytecodes + " health=" + enemyKing.getHealth());
    System.out.flush();
    assertTrue(
        "Expected a hunt peak with at least "
            + BYTECODE_HEADROOM
            + " headroom, got "
            + peakBytecodes,
        peakBytecodes > 0 && peakBytecodes < BABY_BYTECODE_LIMIT - BYTECODE_HEADROOM);
  }

  private void freezeEnemyKing() {
    assertNotNull("Fixture must spawn a living enemy king", enemyKing);
    enemyKing.setMovementCooldownTurns(1000);
    enemyKing.setActionCooldownTurns(1000);
    enemyKing.setTurningCooldownTurns(1000);
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
          2,
          Team.B,
          UnitType.RAT_KING,
          UnitType.RAT_KING.health,
          ENEMY_KING,
          Direction.SOUTH,
          0,
          0,
          null)
    };
    LiveMap map =
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 100, "BabyRatHuntFixture", bodies);
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
    enemyKing = world.getObjectInfo().getRobotByID(2);
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
        "hunt engine bytecodes used="
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
