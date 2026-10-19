package ratplayer;

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

  public enum PathingStrategy {
    GREEDY,
    DIST_BUG,
    VISIBLE_BFS
  }

  enum CheeseTargetStrategy {
    NEAREST_VISIBLE
  }

  /** Living allied baby rat cap; must be nonnegative and a multiple of four. Zero stops spawning. */
  static int MAX_BABY_RATS = 16;

  /** Minimum global cheese left after paying for a spawn, before round-end upkeep. */
  static int MIN_CHEESE_RESERVE = 800;

  /** How a carrying rat steps toward the published king. */
  static PathingStrategy BABY_PATH_TO_KING_STRATEGY = PathingStrategy.GREEDY;

  /** How an empty-handed rat (or has cheese below threshold) steps toward the selected pile*/
  static PathingStrategy BABY_PATH_TO_CHEESE_STRATEGY = PathingStrategy.GREEDY;

  /** Which visible pile to chase. */
  static CheeseTargetStrategy BABY_CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;

  /**
   * Return to the king once raw cheese is above this value. 0 means any cheese. Later this can
   * become a function of stash size or distance to the king.
   */
  static final int RAW_CHEESE_MINIMUM = 0;

  /** Restore live configs to their starting values. */
  static void reset() {
    MAX_BABY_RATS = 16;
    MIN_CHEESE_RESERVE = 800;
    BABY_PATH_TO_KING_STRATEGY = PathingStrategy.GREEDY;
    BABY_PATH_TO_CHEESE_STRATEGY = PathingStrategy.GREEDY;
    BABY_CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;
  }
}
