package ratplayer_sprint1;

/**
 * Team-chosen configs, not engine rules and not turn logic.
 *
 * <p>Handlers read these values. Static fields are still robot-local copies; changing a config on
 * one rat does not publish it to teammates. Tests may overwrite non-final configs and must restore
 * them with {@link #reset()}.
 *
 */
public final class Config {
  private Config() {}

  static final int DEFAULT_MAX_BABY_RATS = 16;
  static final int DEFAULT_MIN_CHEESE_RESERVE = 200;

  public enum PathingStrategy {
    GREEDY,
    DIST_BUG,
    VISIBLE_BFS
  }

  enum CheeseTargetStrategy {
    NEAREST_VISIBLE
  }

  /** Living allied baby rat cap; must be nonnegative and a multiple of four. Zero stops spawning. */
  static int MAX_BABY_RATS = DEFAULT_MAX_BABY_RATS;

  /** Minimum global cheese left after paying for a spawn, before round-end upkeep. */
  static int MIN_CHEESE_RESERVE = DEFAULT_MIN_CHEESE_RESERVE;

  /**
   * How a carrying rat steps toward the published king.
   */
  static PathingStrategy BABY_PATH_TO_KING_STRATEGY = PathingStrategy.DIST_BUG;

  /**
   * How an empty-handed rat (or has cheese below threshold) steps toward the selected pile
   */
  static PathingStrategy BABY_PATH_TO_CHEESE_STRATEGY = PathingStrategy.GREEDY;

  /**
   * Which visible pile to chase.
   */
  static CheeseTargetStrategy BABY_CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;

  /**
   * Return to the king once raw cheese is above this value. 0 means any cheese. Later this can
   * become a function of stash size or distance to the king.
   */
  static final int RAW_CHEESE_MINIMUM = 20;

  /**
   * Restore live configs to their starting values.
   */
  static void reset() {
    MAX_BABY_RATS = DEFAULT_MAX_BABY_RATS;
    MIN_CHEESE_RESERVE = DEFAULT_MIN_CHEESE_RESERVE;
    BABY_PATH_TO_KING_STRATEGY = PathingStrategy.DIST_BUG;
    BABY_PATH_TO_CHEESE_STRATEGY = PathingStrategy.GREEDY;
    BABY_CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;
  }
}
