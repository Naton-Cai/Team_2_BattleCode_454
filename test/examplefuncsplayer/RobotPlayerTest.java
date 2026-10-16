package examplefuncsplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;

/**
 * Sanity checks for the untouched starter player.
 *
 * <p>{@code examplefuncsplayer} stays as a baseline even though {@code ./gradlew run} uses
 * {@code ratplayer}. {@code ./gradlew test} must keep discovering this class.
 */
public class RobotPlayerTest {

	/**
	 * Scenario: the starter copy still lives in {@code examplefuncsplayer}.
	 *
	 * <p>A later edit that changes this package without updating this test would silently stop
	 * treating the scaffold as a separate baseline.
	 *
	 * <p>Arrange: use compiled {@code examplefuncsplayer.RobotPlayer}. Act: read its package.
	 * Assert: the declared package is {@code examplefuncsplayer}.
	 */
	@Test
	public void robotPlayerLivesInExamplefuncsplayerPackage() {
		Class<?> player = RobotPlayer.class;

		Package pkg = player.getPackage();

		assertNotNull("RobotPlayer must declare a package", pkg);
		assertEquals("examplefuncsplayer", pkg.getName());
	}

	/**
	 * Scenario: Battlecode starts each starter robot with {@code public static void
	 * run(RobotController)}.
	 *
	 * <p>Do not invoke {@code run}: it owns the infinite turn loop. Reflecting the signature
	 * catches a baseline that no longer loads.
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
