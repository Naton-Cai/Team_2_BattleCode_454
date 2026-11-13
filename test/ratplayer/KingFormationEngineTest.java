package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

/**
 * Real engine 1.2.6 runs of the squeak-recruited king formation.
 *
 * <p>Fixture: 20x20 open map, our king fixed at (4,4), enemy king at (17,17), no cats. Babies are
 * placed with ids 300+ next to where the chosen spawns (toward the map center, around (6,6)). The
 * recruiting king's baby cap is set to zero, so the only birth the request may cause is the chosen
 * itself. Every round asserts no logged exception and bytecode use below the per-unit limits with
 * headroom (baby 17,500 / king 20,000, spec v1.2.6 p.18).
 */
public class KingFormationEngineTest {
  private static final int FIRST_VOLUNTEER = 300;

  private GameWorld world;
  private PlayerControlProvider player;
  private ByteArrayOutputStream output;
  private int babyPeak;
  private int kingPeak;

  @After
  public void stop() {
    if (player != null) {
      player.matchEnded();
    }
  }

  /**
   * Scenario: the normal path. Six existing babies stand near the chosen's birth tile.
   *
   * <p>Expected: exactly one baby is born above the cap, it promotes, all six volunteers are
   * consumed, and the next round runs the new robot as a king that publishes itself.
   */
  @Test
  public void chosenRecruitsSixExistingBabiesAndPromotes() throws Exception {
    // Arrange: six volunteers in a block east of the expected site.
    int chosen = requestWith(volunteers(6));
    InternalRobot leader = world.getObjectInfo().getRobotByID(chosen);

    // Act: run until promotion or a generous bound.
    int rounds = runUntilKing(leader, 80);

    // Assert: promoted, two kings, every volunteer absorbed, one extra baby only.
    assertEquals(debug(), UnitType.RAT_KING, leader.getType());
    assertEquals(2, world.getTeamInfo().getNumRatKings(Team.A));
    for (int id = FIRST_VOLUNTEER; id < FIRST_VOLUNTEER + 6; id++) {
      assertEquals(null, world.getObjectInfo().getRobotByID(id));
    }
    round(); // Next-type dispatch: the new king runs RatKing and claims a registry ID.
    assertNotNull(KingRegistry.read(leader.getController(), 1, world.getCurrentRound()));
    System.out.println(
        "Formation promoted after "
            + rounds
            + " rounds babyPeak="
            + babyPeak
            + " kingPeak="
            + kingPeak);
  }

  /**
   * Scenario: more volunteers than the ring needs.
   *
   * <p>Expected: promotion still happens; babies seeing six ring allies leave, so at least four of
   * twelve survive (a 3x3 holds at most eight absorbed babies) and survivors return to ordinary
   * roles after the recruiter falls silent.
   */
  @Test
  public void excessVolunteersReturnToOrdinaryWork() throws Exception {
    // Arrange
    int chosen = requestWith(volunteers(12));
    InternalRobot leader = world.getObjectInfo().getRobotByID(chosen);

    // Act: promote, then let the silence window pass.
    runUntilKing(leader, 80);
    for (int i = 0; i <= Config.FORMATION_LISTEN_ROUNDS + 1; i++) {
      round();
    }

    // Assert
    assertEquals(debug(), UnitType.RAT_KING, leader.getType());
    int survivors = 0;
    for (int id = FIRST_VOLUNTEER; id < FIRST_VOLUNTEER + 12; id++) {
      if (world.getObjectInfo().getRobotByID(id) != null) {
        survivors++;
        assertEquals("Survivor " + id + " must be ordinary", null, role(id));
      }
    }
    assertTrue("At most eight babies fit in a 3x3: " + survivors, survivors >= 4);
    System.out.println("Excess volunteers survived=" + survivors + " of 12");
  }

  /**
   * Scenario: a footprint tile becomes impassable after the chosen commits to its site.
   *
   * <p>Expected: the chosen notices the visible dirt, selects a different site, and still
   * promotes; without relocation the engine would refuse promotion forever.
   */
  @Test
  public void blockedSiteIsReplacedAndFormationStillPromotes() throws Exception {
    // Arrange: wait until the chosen has committed to a site, then dirt an empty ring tile.
    int chosen = requestWith(volunteers(6));
    InternalRobot leader = world.getObjectInfo().getRobotByID(chosen);
    MapLocation first = null;
    for (int i = 0; i < 10 && first == null; i++) {
      round();
      first = site(chosen);
    }
    assertNotNull("Chosen must commit to a site", first);
    MapLocation dirt = null;
    for (Direction direction : RobotPlayer.directions) {
      MapLocation tile = first.add(direction);
      if (dirt == null && world.getRobot(tile) == null) {
        dirt = tile;
      }
    }
    world.setDirt(dirt, true);

    // Act
    runUntilKing(leader, 120);

    // Assert: promoted somewhere whose footprint excludes the dirt tile.
    assertEquals(debug(), UnitType.RAT_KING, leader.getType());
    assertNotEquals(first, leader.getLocation());
    assertTrue(
        Math.max(
                Math.abs(leader.getLocation().x - dirt.x),
                Math.abs(leader.getLocation().y - dirt.y))
            > 1);
  }

  /**
   * Scenario: nobody answers the recruiter.
   *
   * <p>Expected: no further births (the request covered one chosen only), and after the timeout the
   * chosen gives up and is an ordinary baby again instead of waiting forever.
   */
  @Test
  public void chosenWithoutVolunteersGivesUpAfterTimeout() throws Exception {
    // Arrange
    int chosen = requestWith(new MapLocation[0]);

    // Act: run past the give-up deadline.
    for (int i = 0; i < Config.FORMATION_TIMEOUT_ROUNDS + 2; i++) {
      round();
    }

    // Assert
    assertEquals(1, world.getTeamInfo().getNumBabyRats(Team.A));
    assertEquals(UnitType.BABY_RAT, world.getObjectInfo().getRobotByID(chosen).getType());
    assertEquals(null, role(chosen));
  }

  /** Block of volunteers just east of the chosen's expected birth tile (6,6). */
  private static MapLocation[] volunteers(int count) {
    MapLocation[] result = new MapLocation[count];
    for (int i = 0; i < count; i++) {
      result[i] = new MapLocation(8 + i % 3, 6 + i / 3);
    }
    return result;
  }

  /** Spawn volunteers, request a formation with cap zero, and return the chosen's ID. */
  private int requestWith(MapLocation[] babies) throws Exception {
    start();
    for (int i = 0; i < babies.length; i++) {
      world.spawnRobot(
          FIRST_VOLUNTEER + i, UnitType.BABY_RAT, babies[i], Direction.NORTH, 0, Team.A);
    }
    cheese(100);
    round(); // Volunteers take an ordinary turn before any request exists.
    Field cap = robotClass(1, "ratplayer.Config").getDeclaredField("MAX_BABY_RATS");
    cap.setAccessible(true);
    cap.setInt(null, 0);
    robotClass(1, "ratplayer.RatKing").getMethod("requestKingFormation").invoke(null);
    cheese(2000);
    round();
    int chosen = 0;
    for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
      if (robot.getID() != 1
          && (robot.getID() < FIRST_VOLUNTEER || robot.getID() >= FIRST_VOLUNTEER + 100)) {
        chosen = robot.getID();
      }
    }
    assertTrue("Exactly one chosen must be born", chosen != 0);
    assertEquals(babies.length + 1, world.getTeamInfo().getNumBabyRats(Team.A));
    return chosen;
  }

  private int runUntilKing(InternalRobot leader, int limit) {
    int rounds = 0;
    while (rounds < limit && leader.getType() == UnitType.BABY_RAT) {
      round();
      rounds++;
    }
    return rounds;
  }

  private Object role(int id) throws Exception {
    Field role = robotClass(id, "ratplayer.BabyRat").getDeclaredField("role");
    role.setAccessible(true);
    return role.get(null);
  }

  private MapLocation site(int id) throws Exception {
    Field site = robotClass(id, "ratplayer.KingFormation").getDeclaredField("site");
    site.setAccessible(true);
    return (MapLocation) site.get(null);
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
          1, Team.A, UnitType.RAT_KING, 500, new MapLocation(4, 4), Direction.NORTH, 0, 0, null),
      new RobotInfo(
          2, Team.B, UnitType.RAT_KING, 500, new MapLocation(17, 17), Direction.SOUTH, 0, 0, null)
    };
    LiveMap map = new LiveMap(20, 20, new MapLocation(0, 0), 6147, 700, "FormationFixture", bodies);
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
    // No cats on this map: backstab keeps cooperation's all-cats-defeated end from firing (p.4).
    world.backstab(Team.A);
    // Keep the parent stationary so the fixture geometry is reproducible.
    world.getObjectInfo().getRobotByID(1).setMovementCooldownTurns(10000);
  }

  /** Host-only reflection selects the robot-local class copy; the bot itself uses no reflection. */
  private Class<?> robotClass(int id, String name) throws Exception {
    Field sandboxes = PlayerControlProvider.class.getDeclaredField("sandboxes");
    sandboxes.setAccessible(true);
    Object sandbox = ((Map<?, ?>) sandboxes.get(player)).get(id);
    Field loader = sandbox.getClass().getDeclaredField("individualLoader");
    loader.setAccessible(true);
    return ((ClassLoader) loader.get(sandbox)).loadClass(name);
  }

  private void cheese(int amount) {
    world.getTeamInfo().addCheese(Team.A, amount - world.getTeamInfo().getCheese(Team.A));
  }

  private void round() {
    world.runRound();
    assertFalse(debug(), output.toString(StandardCharsets.UTF_8).contains("Exception"));
    for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
      int used = robot.getBytecodesUsed();
      if (robot.getType() == UnitType.RAT_KING) {
        kingPeak = Math.max(kingPeak, used);
        assertTrue("King cutoff: " + used, used < 19000);
      } else {
        babyPeak = Math.max(babyPeak, used);
        assertTrue("Baby cutoff id=" + robot.getID() + ": " + used, used < 16500);
      }
    }
  }

  private String debug() {
    StringBuilder result = new StringBuilder(output.toString(StandardCharsets.UTF_8));
    result.append(" round=").append(world.getCurrentRound());
    for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
      result
          .append(" id=")
          .append(robot.getID())
          .append(" type=")
          .append(robot.getType())
          .append(" loc=")
          .append(robot.getLocation());
    }
    return result.toString();
  }
}
