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

  static final int DEFAULT_MAX_BABY_RATS = 32;
  static final int DEFAULT_MIN_CHEESE_RESERVE = 200;
  static final int DEFAULT_MIN_CHEESE_RESERVE_FOR_DIG = 800;
  static final int DEFAULT_KING_CAT_FLEE_HEALTH = 20;

  public enum PathingStrategy {
    GREEDY,
    DIST_BUG,
    VISIBLE_BFS
  }

  enum CheeseTargetStrategy {
    NEAREST_VISIBLE
  }

  enum BabyAction {
    ATTACK,
    DIG_DIRT
  }

  enum CatResponseStrategy {
    NONE,
    BITE_THEN_BLOCK_AND_FLEE
  }

  enum KingCatResponseStrategy {
    NONE,
    BLOCK_AND_FLEE_WHEN_LOW_HEALTH
  }

  /** Living allied baby rat cap; must be nonnegative and a multiple of four. Zero stops spawning. */
  static int MAX_BABY_RATS = DEFAULT_MAX_BABY_RATS;

  /** Retained mine cap, 0–27. Change for the whole team between matches, not during a game. */
  static int MAX_CHEESE_MINES = 18;

  /** Minimum global cheese left after paying for a spawn, before round-end upkeep. */
  static int MIN_CHEESE_RESERVE = DEFAULT_MIN_CHEESE_RESERVE;

  /**
   * Lowest global cheese allowed after paying for a dig. Independent of the spawn reserve.
   */
  static int MIN_CHEESE_RESERVE_FOR_DIG = DEFAULT_MIN_CHEESE_RESERVE_FOR_DIG;

  /** Default baby-rat action order: attack, then dig. */
  static final BabyAction[] DEFAULT_BABY_ACTION_ORDER = {BabyAction.ATTACK, BabyAction.DIG_DIRT};

  /**
   * Baby-rat actions tried in list order; the first success ends the action phase. An omitted
   * action does not run.
   */
  static BabyAction[] BABY_ACTION_ORDER = DEFAULT_BABY_ACTION_ORDER;

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
   * Default cat policy: bite an adjacent cat, otherwise block with dirt and walk away.
   */
  static final CatResponseStrategy DEFAULT_BABY_CAT_RESPONSE_STRATEGY =
      CatResponseStrategy.BITE_THEN_BLOCK_AND_FLEE;

  /**
   * How a baby rat reacts to a visible cat. {@link CatResponseStrategy#NONE} keeps king, cheese, or
   * wander movement and does not place blocking dirt.
   */
  static CatResponseStrategy BABY_CAT_RESPONSE_STRATEGY = DEFAULT_BABY_CAT_RESPONSE_STRATEGY;

  /**
   * Default king cat policy: place blocking dirt and walk away only at low health.
   */
  static final KingCatResponseStrategy DEFAULT_KING_CAT_RESPONSE_STRATEGY =
      KingCatResponseStrategy.BLOCK_AND_FLEE_WHEN_LOW_HEALTH;

  /**
   * How a king reacts to a visible cat. {@link KingCatResponseStrategy#NONE} keeps spawn, dig, and
   * normal movement.
   */
  static KingCatResponseStrategy KING_CAT_RESPONSE_STRATEGY = DEFAULT_KING_CAT_RESPONSE_STRATEGY;

  /**
   * A king with health at or below this value, and a visible cat, blocks and flees. Starting value
   * is one cat scratch.
   */
  static int KING_CAT_FLEE_HEALTH = DEFAULT_KING_CAT_FLEE_HEALTH;

  /**
   * Return to the king once raw cheese is above this value. 0 means any cheese. Later this can
   * become a function of stash size or distance to the king.
   */
  static final int RAW_CHEESE_MINIMUM = 40;

  /**
   * Restore live configs to their starting values.
   */
  static void reset() {
    MAX_BABY_RATS = DEFAULT_MAX_BABY_RATS;
    MAX_CHEESE_MINES = 18;
    MIN_CHEESE_RESERVE = DEFAULT_MIN_CHEESE_RESERVE;
    MIN_CHEESE_RESERVE_FOR_DIG = DEFAULT_MIN_CHEESE_RESERVE_FOR_DIG;
    BABY_ACTION_ORDER = DEFAULT_BABY_ACTION_ORDER;
    BABY_PATH_TO_KING_STRATEGY = PathingStrategy.DIST_BUG;
    BABY_PATH_TO_CHEESE_STRATEGY = PathingStrategy.GREEDY;
    BABY_CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;
    BABY_CAT_RESPONSE_STRATEGY = DEFAULT_BABY_CAT_RESPONSE_STRATEGY;
    KING_CAT_RESPONSE_STRATEGY = DEFAULT_KING_CAT_RESPONSE_STRATEGY;
    KING_CAT_FLEE_HEALTH = DEFAULT_KING_CAT_FLEE_HEALTH;
  }
}
