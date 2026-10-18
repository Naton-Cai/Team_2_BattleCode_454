package ratplayer;

/**
 * Team-chosen configs, not engine rules and not turn logic.
 *
 * <p>Handlers read these values. Static fields are still robot-local copies; changing a config on
 * one rat does not publish it to teammates. Tests may overwrite non-final configs and must restore
 * them with {@link #reset()}.
 *
 * <p>The current cheese policy is nearest-visible targeting plus greedy walking. Those are two
 * configs ({@link #CHEESE_TARGET_STRATEGY} and {@link #CHEESE_PATHING_STRATEGY}), not one combined
 * enum value.
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

  /** How a carrying rat steps toward the published king. Starting value is greedy. */
  static PathingStrategy KING_PATHING_STRATEGY = PathingStrategy.GREEDY;

  /** How an empty-handed rat steps toward the selected pile. Starting value is greedy. */
  static PathingStrategy CHEESE_PATHING_STRATEGY = PathingStrategy.GREEDY;

  /** Which visible pile to chase. Starting value is nearest by squared distance. */
  static CheeseTargetStrategy CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;

  /**
   * Return to the king once raw cheese is above this value. 0 means any cheese. Later this can
   * become a function of stash size or distance to the king.
   */
  static final int RAW_CHEESE_MINIMUM = 0;

  /** Restore live configs to their starting values. */
  static void reset() {
    KING_PATHING_STRATEGY = PathingStrategy.GREEDY;
    CHEESE_PATHING_STRATEGY = PathingStrategy.GREEDY;
    CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;
  }
}
