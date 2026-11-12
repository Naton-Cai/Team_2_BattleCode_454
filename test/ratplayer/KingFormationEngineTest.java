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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Test;

/** Real newborn recruitment, assembly, promotion, and next-type dispatch in engine 1.2.6. */
public class KingFormationEngineTest {
  private GameWorld world;
  private PlayerControlProvider player;
  private ByteArrayOutputStream output;
  private int babyPeak;
  private int kingPeak;
  private boolean consumeBirthSqueak;

  @After
  public void stop() {
    if (player != null) player.matchEnded();
  }

  @Test
  public void requestedSevenBirthsAssemblePromoteAndPublishIndependentKing() throws Exception {
    start();
    // Initialize the real sandbox without recruiting, then invoke the explicit API in that copy.
    cheese(100);
    round();
    assertEquals(0, world.getTeamInfo().getNumBabyRats(Team.A));
    Class<?> kingClass = robotClass(1, "ratplayer.RatKing");
    kingClass.getMethod("requestKingFormation").invoke(null);
    cheese(2000);
    Set<Integer> newborns = new HashSet<>();
    int chosen = 0;
    for (int turn = 0; turn < 35 && newborns.size() < 7; turn++) {
      round();
      for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
        if (robot.getID() != 1) newborns.add(robot.getID());
      }
      if (chosen == 0 && !newborns.isEmpty()) chosen = newborns.iterator().next();
    }
    assertEquals("Exactly seven requested births must be observable", 7, newborns.size());
    // Preserve enough for promotion while preventing an unrelated eighth baby from spawning.
    cheese(100);
    InternalRobot leader = world.getObjectInfo().getRobotByID(chosen);
    assertNotNull(leader);
    for (int turn = 0; turn < 60 && leader.getType() != UnitType.RAT_KING; turn++) round();
    assertEquals(debug(), UnitType.RAT_KING, leader.getType());
    assertEquals(2, world.getTeamInfo().getNumRatKings(Team.A));
    // Inspect actual unit types: engine 1.2.6's population counter retains the promoted center.
    for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
      assertEquals(UnitType.RAT_KING, robot.getType());
    }
    for (int id : newborns) {
      if (id != chosen) assertEquals(null, world.getObjectInfo().getRobotByID(id));
    }
    int promotionRound = world.getCurrentRound();
    round(); // Current-type dispatch, group release, and independent king publication.
    assertEquals(0, world.readSharedArray(KingFormation.STATE, Team.A));
    assertNotNull(KingRegistry.read(leader.getController(), 0, world.getCurrentRound()));
    assertEquals(
        leader.getLocation(),
        KingRegistry.read(leader.getController(), 1, world.getCurrentRound()));
    System.out.println(
        "Formation seed=6147 map=SpawnerFormationFixture side=A chosen="
            + chosen
            + " members="
            + newborns
            + " promotionRound="
            + promotionRound
            + " babyPeak="
            + babyPeak
            + " kingPeak="
            + kingPeak);
  }

  @Test
  public void insufficientCheeseKeepsAssembledGroupAliveUntilFundingArrives() throws Exception {
    start();
    cheese(100);
    round();
    robotClass(1, "ratplayer.RatKing").getMethod("requestKingFormation").invoke(null);
    cheese(2000);
    Set<Integer> newborns = new HashSet<>();
    int chosen = 0;
    for (int turn = 0; turn < 35 && newborns.size() < 7; turn++) {
      round();
      for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
        if (robot.getID() != 1) newborns.add(robot.getID());
      }
      if (chosen == 0 && !newborns.isEmpty()) chosen = newborns.iterator().next();
    }
    assertEquals(7, newborns.size());
    InternalRobot leader = world.getObjectInfo().getRobotByID(chosen);
    for (int turn = 0; turn < 15; turn++) {
      cheese(49);
      round();
      assertEquals(UnitType.BABY_RAT, leader.getType());
    }
    assertEquals(7, world.getTeamInfo().getNumBabyRats(Team.A));
    cheese(100);
    for (int turn = 0; turn < 15 && leader.getType() == UnitType.BABY_RAT; turn++) round();
    assertEquals(debug(), UnitType.RAT_KING, leader.getType());
  }

  @Test
  public void failedBirthSqueakRecoversInEngineAndReclaimedSlotsStayUntouched() throws Exception {
    start();
    cheese(100);
    round();
    robotClass(1, "ratplayer.RatKing").getMethod("requestKingFormation").invoke(null);
    consumeBirthSqueak = true; // Real engine one-squeak limit, not a canned false return.
    cheese(2000);
    Set<Integer> newborns = new HashSet<>();
    int chosen = 0;
    for (int turn = 0; turn < 40 && newborns.size() < 7; turn++) {
      round();
      for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
        if (robot.getID() != 1) newborns.add(robot.getID());
      }
      if (chosen == 0 && !newborns.isEmpty()) chosen = newborns.iterator().next();
    }
    assertFalse("The fixture must actually consume the first birth's squeak", consumeBirthSqueak);
    assertEquals(7, newborns.size());
    cheese(100);
    InternalRobot leader = world.getObjectInfo().getRobotByID(chosen);
    for (int turn = 0; turn < 60 && leader.getType() == UnitType.BABY_RAT; turn++) round();
    assertEquals(debug(), UnitType.RAT_KING, leader.getType());
    round();
    assertEquals(0, world.readSharedArray(KingFormation.STATE, Team.A));
    assertEquals(
        leader.getLocation(),
        KingRegistry.read(leader.getController(), 1, world.getCurrentRound()));
    System.out.println(
        "One-slot retry fixture chosen="
            + chosen
            + " babyPeak="
            + babyPeak
            + " kingPeak="
            + kingPeak);
  }

  private void start() {
    String classes =
        RobotPlayer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    output = new ByteArrayOutputStream();
    player =
        new PlayerControlProvider(
            Team.A, "ratplayer", CrossPlayLanguage.JAVA, null, classes, output, false) {
          @Override
          public void runRobot(InternalRobot robot) {
            if (consumeBirthSqueak && robot.getID() == 1 && world.getCurrentRound() == 2) {
              assertTrue(robot.getController().squeak(0));
              consumeBirthSqueak = false;
            }
            super.runRobot(robot);
          }
        };
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
    LiveMap map =
        new LiveMap(20, 20, new MapLocation(0, 0), 6147, 150, "SpawnerFormationFixture", bodies);
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
    for (int slot = 53; slot < 64; slot++) world.writeSharedArray(slot, 777, Team.A);
    world.getObjectInfo().getRobotByID(1).setMovementCooldownTurns(1000);
  }

  /** Host-only reflection selects the actual robot-local copy; the bot uses no reflection. */
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
    for (int slot = 53; slot < 64; slot++) {
      assertEquals("Reclaimed slot " + slot + " changed", 777, world.readSharedArray(slot, Team.A));
    }
    assertFalse(debug(), output.toString(StandardCharsets.UTF_8).contains("Exception"));
    for (InternalRobot robot : world.getAllRobots(Team.A, 0)) {
      int used = robot.getBytecodesUsed();
      if (robot.getType() == UnitType.RAT_KING) {
        kingPeak = Math.max(kingPeak, used);
        assertTrue("King cutoff: " + used, used < 19000);
      } else {
        babyPeak = Math.max(babyPeak, used);
        assertTrue("Baby cutoff: " + used, used < 16500);
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
