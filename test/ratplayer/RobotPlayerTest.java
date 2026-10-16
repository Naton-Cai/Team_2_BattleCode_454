package ratplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;

/**
 * Checks that the team bot lives in {@code ratplayer} with the engine entry point.
 *
 * <p>The match runner loads {@code <package>.RobotPlayer.run(RobotController)}. Copying the
 * starter without updating the package, or dropping {@code run}, would still compile but would
 * make {@code ./gradlew run} miss the team bot.
 */
public class RobotPlayerTest {

  /**
   * Scenario: {@code gradle.properties} sets {@code teamA}/{@code teamB} to {@code ratplayer}.
   *
   * <p>The engine resolves the player from that package name. A class copied into
   * {@code src/ratplayer} with {@code package examplefuncsplayer;} would not be loaded.
   *
   * <p>Arrange: use the compiled {@code ratplayer.RobotPlayer}. Act: read its package. Assert:
   * the declared package is {@code ratplayer}.
   */
  @Test
  public void robotPlayerLivesInRatplayerPackage() {
    Class<?> player = RobotPlayer.class;

    Package pkg = player.getPackage();

    assertNotNull("RobotPlayer must declare a package", pkg);
    assertEquals("ratplayer", pkg.getName());
  }

  /**
   * Scenario: Battlecode starts each robot by calling {@code public static void
   * run(RobotController)}.
   *
   * <p>Do not invoke {@code run} here: it owns the infinite turn loop and {@code Clock.yield()}.
   * Reflecting the signature is enough to catch a copy that dropped the entry point.
   *
   * <p>Arrange/Act: look up {@code run(RobotController)}. Assert: it is public, static, returns
   * {@code void}, and declares {@code GameActionException}.
   */
  @Test
  public void runEntryPointHasEngineSignature() throws NoSuchMethodException {
    Method run = RobotPlayer.class.getMethod("run", RobotController.class);

    int modifiers = run.getModifiers();
    assertTrue("run must be public", Modifier.isPublic(modifiers));
    assertTrue("run must be static", Modifier.isStatic(modifiers));
    assertEquals(void.class, run.getReturnType());

    boolean declaresGameActionException = false;
    for (Class<?> exceptionType : run.getExceptionTypes()) {
      if (exceptionType == GameActionException.class) {
        declaresGameActionException = true;
        break;
      }
    }
    assertTrue(
        "run must declare GameActionException", declaresGameActionException);
  }
}
