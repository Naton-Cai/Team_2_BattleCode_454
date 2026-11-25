package ratplayer;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Combat knobs lerp from defensive (0) to offensive (1). Overlay {@code AGGRESSION} is compiled
 * at {@code -1}, so {@link Config#reset()} keeps the current mix unless a test calls {@link
 * Aggression#apply(float)}.
 */
public class ConfigAggressionTest {

  @Before
  public void setUp() {
    Config.reset();
  }

  @After
  public void tearDown() {
    Config.reset();
  }

  /**
   * Aggression 0 is fully defensive: latest backstab, flee at max HP.
   *
   * <p>Arrange: defaults after reset. Act: apply 0. Assert: defensive endpoints.
   */
  @Test
  public void aggressionZeroUsesDefensiveEndpoints() {
    Aggression.apply(0f);

    assertEquals(Config.DEFENSIVE_BACKSTAB_ROUND, Config.BACKSTAB_ROUND);
    assertEquals(Config.DEFENSIVE_BABY_CAT_FLEE_HEALTH, Config.BABY_CAT_FLEE_HEALTH);
    assertEquals(Config.DEFENSIVE_KING_CAT_FLEE_HEALTH, Config.KING_CAT_FLEE_HEALTH);
    assertEquals(Config.DEFENSIVE_MAX_BABY_RATS, Config.MAX_BABY_RATS);
    assertEquals(false, Config.BABY_HUNT_IN_BACKSTAB);
  }

  /**
   * Aggression 1 is fully offensive: backstab from round 1, never flee on HP.
   *
   * <p>Arrange: defaults after reset. Act: apply 1. Assert: offensive endpoints.
   */
  @Test
  public void aggressionOneUsesOffensiveEndpoints() {
    Aggression.apply(1f);

    assertEquals(Config.OFFENSIVE_BACKSTAB_ROUND, Config.BACKSTAB_ROUND);
    assertEquals(Config.OFFENSIVE_BABY_CAT_FLEE_HEALTH, Config.BABY_CAT_FLEE_HEALTH);
    assertEquals(Config.OFFENSIVE_KING_CAT_FLEE_HEALTH, Config.KING_CAT_FLEE_HEALTH);
    assertEquals(Config.OFFENSIVE_MAX_BABY_RATS, Config.MAX_BABY_RATS);
    assertEquals(true, Config.BABY_HUNT_IN_BACKSTAB);
  }

  /**
   * Aggression 0.5 is the midpoint of the combat endpoints, not today's mixed DEFAULT_* values.
   *
   * <p>Arrange: defaults after reset. Act: apply 0.5. Assert: backstab 1001, baby 50, king 300,
   * max babies 48, hunt on.
   */
  @Test
  public void aggressionHalfUsesMidpoints() {
    Aggression.apply(0.5f);

    assertEquals(1001, Config.BACKSTAB_ROUND);
    assertEquals(50, Config.BABY_CAT_FLEE_HEALTH);
    assertEquals(300, Config.KING_CAT_FLEE_HEALTH);
    assertEquals(48, Config.MAX_BABY_RATS);
    assertEquals(true, Config.BABY_HUNT_IN_BACKSTAB);
  }

  /**
   * Hunt is off below 0.5 on the combat lerp, including the 0.25 grid point.
   *
   * <p>Arrange: defaults after reset. Act: apply 0.25. Assert: hunt false.
   */
  @Test
  public void aggressionBelowHalfTurnsHuntOff() {
    Aggression.apply(0.25f);

    assertEquals(false, Config.BABY_HUNT_IN_BACKSTAB);
  }

  /**
   * Production {@link Config#reset()} does not apply overlay aggression, so it restores the mixed
   * DEFAULT_* literals.
   *
   * <p>Arrange: apply 1. Act: reset. Assert: DEFAULT_* mix, not offensive endpoints.
   */
  @Test
  public void resetKeepsDefaultMixWhenAggressionIsOptOut() {
    Aggression.apply(1f);

    Config.reset();

    assertEquals(Config.DEFAULT_BACKSTAB_ROUND, Config.BACKSTAB_ROUND);
    assertEquals(Config.DEFAULT_BABY_CAT_FLEE_HEALTH, Config.BABY_CAT_FLEE_HEALTH);
    assertEquals(Config.DEFAULT_KING_CAT_FLEE_HEALTH, Config.KING_CAT_FLEE_HEALTH);
    assertEquals(Config.DEFAULT_MAX_BABY_RATS, Config.MAX_BABY_RATS);
    assertEquals(Config.DEFAULT_BABY_HUNT_IN_BACKSTAB, Config.BABY_HUNT_IN_BACKSTAB);
  }
}
