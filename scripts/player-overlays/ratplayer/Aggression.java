package ratplayer;

/**
 * Snapshot-only combat lerp. copy-player installs this into dest bots and hooks Config class
 * init. Negative {@link #AGGRESSION} keeps Config's DEFAULT_* mix.
 */
final class Aggression {
  private Aggression() {}

  /**
   * 0 defensive, 1 offensive. Negative keeps the DEFAULT_* mix ({@link
   * Config#DEFAULT_BACKSTAB_ROUND}, {@link Config#DEFAULT_BABY_CAT_FLEE_HEALTH}, {@link
   * Config#DEFAULT_KING_CAT_FLEE_HEALTH}, {@link Config#DEFAULT_MAX_BABY_RATS}, {@link
   * Config#DEFAULT_BABY_HUNT_IN_BACKSTAB}).
   */
  static final float AGGRESSION = -1f;

  /** Writes combat knobs from {@link #AGGRESSION} when it is in {@code [0, 1]}. */
  static void apply() {
    if (AGGRESSION >= 0f) {
      apply(AGGRESSION);
    }
  }

  /** Writes combat knobs for {@code a} in {@code [0, 1]}. Values outside that range are clamped. */
  static void apply(float a) {
    float clamped = a < 0f ? 0f : (a > 1f ? 1f : a);
    Config.BACKSTAB_ROUND =
        lerpCombat(Config.DEFENSIVE_BACKSTAB_ROUND, Config.OFFENSIVE_BACKSTAB_ROUND, clamped);
    Config.BABY_CAT_FLEE_HEALTH =
        lerpCombat(
            Config.DEFENSIVE_BABY_CAT_FLEE_HEALTH, Config.OFFENSIVE_BABY_CAT_FLEE_HEALTH, clamped);
    Config.KING_CAT_FLEE_HEALTH =
        lerpCombat(
            Config.DEFENSIVE_KING_CAT_FLEE_HEALTH, Config.OFFENSIVE_KING_CAT_FLEE_HEALTH, clamped);
    Config.MAX_BABY_RATS = babyCap(clamped);
    Config.BABY_HUNT_IN_BACKSTAB = clamped >= 0.5f;
  }

  private static int lerpCombat(int defensive, int offensive, float a) {
    return Math.round(defensive + a * (offensive - defensive));
  }

  /** 32 + 32*a below 1, unbounded at 1. Result is a multiple of 4. */
  private static int babyCap(float a) {
    if (a >= 1f) {
      return Config.OFFENSIVE_MAX_BABY_RATS;
    }
    int cap = Math.round(Config.DEFENSIVE_MAX_BABY_RATS + Config.DEFENSIVE_MAX_BABY_RATS * a);
    return Math.round(cap / 4f) * 4;
  }
}
