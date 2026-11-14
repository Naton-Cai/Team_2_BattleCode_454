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
  static final int DEFAULT_BABY_CAT_FLEE_HEALTH = 20;
  static final int DEFAULT_KING_CAT_FLEE_HEALTH = 600;

  // Initial-search choices, not engine rules. Home travel and fleeing use their own policies.
  static final int KING_SEARCH_EDGE_MARGIN = 3;
  static final int KING_SEARCH_INWARD_WEIGHT = 3;

  // ---- King formation (see KingFormation) ----
  // Strategy values are first guesses, not tuned. Tests may override them; reset() restores them.
  static final int DEFAULT_FORMATION_TIMEOUT_ROUNDS = 300;
  static final int DEFAULT_FORMATION_TRAVEL_ROUNDS = 20;
  static final int DEFAULT_FORMATION_SITE_ROUNDS = 60;
  static final int DEFAULT_FORMATION_LISTEN_ROUNDS = 3;
  static final int DEFAULT_FORMATION_IGNORE_ROUNDS = 30;
  static final int DEFAULT_FORMATION_KING_CLEARANCE = 4;
  static final int DEFAULT_FORMATION_RETRIES = 2;

  /** How many rounds the chosen keeps trying before it gives up and becomes a normal baby. */
  static int FORMATION_TIMEOUT_ROUNDS = DEFAULT_FORMATION_TIMEOUT_ROUNDS;

  /** How many rounds the chosen may try to walk onto its site before picking a different one. */
  static int FORMATION_TRAVEL_ROUNDS = DEFAULT_FORMATION_TRAVEL_ROUNDS;

  /** How many rounds the chosen keeps one site in total before trying a different one. */
  static int FORMATION_SITE_ROUNDS = DEFAULT_FORMATION_SITE_ROUNDS;

  /** How many rounds a fodder baby waits without hearing the chosen before going back to work. */
  static int FORMATION_LISTEN_ROUNDS = DEFAULT_FORMATION_LISTEN_ROUNDS;

  /** How many rounds a baby ignores a chosen after finding that chosen's ring already full. */
  static int FORMATION_IGNORE_ROUNDS = DEFAULT_FORMATION_IGNORE_ROUNDS;

  /**
   * How far (in king steps, diagonals count as one) a new site must be from any king or cat.
   * Bigger keeps the site safe if a king moves; smaller keeps it closer to baby traffic.
   */
  static int FORMATION_KING_CLEARANCE = DEFAULT_FORMATION_KING_CLEARANCE;

  /** How many times a king automatically re-requests after seeing its chosen die. */
  static int FORMATION_RETRIES = DEFAULT_FORMATION_RETRIES;

  // Fixed by engine rules; do not tune.
  /** Babies needed around the chosen: the engine wants 7 rats in the 3x3 (spec v1.2.6 p.14). */
  static final int FORMATION_FODDER_NEEDED = 6;

  /**
   * Distance squared from its king where a vanished chosen must be dead. A baby moves one tile per
   * round, so from this close it cannot walk out of the king's view (distance squared 25) in one
   * round. Must stay at 12 or less.
   */
  static final int FORMATION_LOST_DISTANCE_SQUARED = 12;

  static final int DEFAULT_KING_HOME_RADIUS = 4;
  static final int DEFAULT_KING_STALL_TURNS = 10;
  static final int DEFAULT_KING_RETRY_TURNS = 10;

  /** Center-to-mine radius at which normal king movement holds position. */
  static int KING_HOME_RADIUS = DEFAULT_KING_HOME_RADIUS;

  /** Movement-ready navigation turns without progress before deferring a home. */
  static int KING_STALL_TURNS = DEFAULT_KING_STALL_TURNS;

  /** Movement-ready exploration turns before retrying a deferred home. */
  static int KING_RETRY_TURNS = DEFAULT_KING_RETRY_TURNS;

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
    DIG_DIRT,
    TRAP
  }

  enum CatResponseStrategy {
    NONE,
    BITE_THEN_BLOCK_AND_FLEE,
    BLOCK_AND_FLEE_WHEN_LOW_HEALTH
  }

  /**
   * Ordinary baby cap; nonnegative and a multiple of four. Zero stops ordinary births. Explicit
   * formation may exceed this cap only for its one chosen birth, respecting the cheese reserve.
   */
  static int MAX_BABY_RATS = DEFAULT_MAX_BABY_RATS;

  /** Retained mine cap, 0–18. Change for the whole team between matches, not during a game. */
  static int MAX_CHEESE_MINES = CheeseMines.MAX_CAPACITY;

  /** Minimum global cheese left after paying for a spawn, before round-end upkeep. */
  static int MIN_CHEESE_RESERVE = DEFAULT_MIN_CHEESE_RESERVE;

  /**
   * Lowest global cheese allowed after paying for a dig. Independent of the spawn reserve.
   */
  static int MIN_CHEESE_RESERVE_FOR_DIG = DEFAULT_MIN_CHEESE_RESERVE_FOR_DIG;

  /** Default baby-rat action order: attack, then dig. */
  static final BabyAction[] DEFAULT_BABY_ACTION_ORDER = {
    BabyAction.ATTACK, BabyAction.TRAP, BabyAction.DIG_DIRT
  };

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
   * wander movement and does not place blocking dirt. The king field uses the same enum so either
   * unit can select any policy.
   */
  static CatResponseStrategy BABY_CAT_RESPONSE_STRATEGY = DEFAULT_BABY_CAT_RESPONSE_STRATEGY;

  /**
   * Default king cat policy: place blocking dirt and walk away only at low health.
   */
  static final CatResponseStrategy DEFAULT_KING_CAT_RESPONSE_STRATEGY =
      CatResponseStrategy.BLOCK_AND_FLEE_WHEN_LOW_HEALTH;

  /**
   * How a king reacts to a visible cat. {@link CatResponseStrategy#NONE} keeps spawn, dig, and
   * normal movement.
   */
  static CatResponseStrategy KING_CAT_RESPONSE_STRATEGY = DEFAULT_KING_CAT_RESPONSE_STRATEGY;

  /**
   * A baby using {@link CatResponseStrategy#BLOCK_AND_FLEE_WHEN_LOW_HEALTH} with health at or below
   * this value, and a visible cat, blocks and flees. Starting value is one cat scratch.
   */
  static int BABY_CAT_FLEE_HEALTH = DEFAULT_BABY_CAT_FLEE_HEALTH;

  /**
   * A king using {@link CatResponseStrategy#BLOCK_AND_FLEE_WHEN_LOW_HEALTH} with health at or below
   * this value, and a visible cat, blocks and flees. Starting value is full king HP so a visible
   * cat always tries dirt and flee before a last-resort bite.
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
    KING_HOME_RADIUS = DEFAULT_KING_HOME_RADIUS;
    KING_STALL_TURNS = DEFAULT_KING_STALL_TURNS;
    KING_RETRY_TURNS = DEFAULT_KING_RETRY_TURNS;
    MAX_BABY_RATS = DEFAULT_MAX_BABY_RATS;
    MAX_CHEESE_MINES = CheeseMines.MAX_CAPACITY;
    MIN_CHEESE_RESERVE = DEFAULT_MIN_CHEESE_RESERVE;
    MIN_CHEESE_RESERVE_FOR_DIG = DEFAULT_MIN_CHEESE_RESERVE_FOR_DIG;
    BABY_ACTION_ORDER = DEFAULT_BABY_ACTION_ORDER;
    BABY_PATH_TO_KING_STRATEGY = PathingStrategy.DIST_BUG;
    BABY_PATH_TO_CHEESE_STRATEGY = PathingStrategy.GREEDY;
    BABY_CHEESE_TARGET_STRATEGY = CheeseTargetStrategy.NEAREST_VISIBLE;
    BABY_CAT_RESPONSE_STRATEGY = DEFAULT_BABY_CAT_RESPONSE_STRATEGY;
    KING_CAT_RESPONSE_STRATEGY = DEFAULT_KING_CAT_RESPONSE_STRATEGY;
    BABY_CAT_FLEE_HEALTH = DEFAULT_BABY_CAT_FLEE_HEALTH;
    KING_CAT_FLEE_HEALTH = DEFAULT_KING_CAT_FLEE_HEALTH;
    FORMATION_TIMEOUT_ROUNDS = DEFAULT_FORMATION_TIMEOUT_ROUNDS;
    FORMATION_TRAVEL_ROUNDS = DEFAULT_FORMATION_TRAVEL_ROUNDS;
    FORMATION_SITE_ROUNDS = DEFAULT_FORMATION_SITE_ROUNDS;
    FORMATION_LISTEN_ROUNDS = DEFAULT_FORMATION_LISTEN_ROUNDS;
    FORMATION_IGNORE_ROUNDS = DEFAULT_FORMATION_IGNORE_ROUNDS;
    FORMATION_KING_CLEARANCE = DEFAULT_FORMATION_KING_CLEARANCE;
    FORMATION_RETRIES = DEFAULT_FORMATION_RETRIES;
  }
}
