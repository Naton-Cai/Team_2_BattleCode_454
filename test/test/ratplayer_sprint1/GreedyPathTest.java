package ratplayer_sprint1;

/**
 * Runs the shared {@link CommonPathingTest} cases using greedy pathing.
 */
public class GreedyPathTest extends CommonPathingTest {

  @Override
  Config.PathingStrategy strategy() {
    return Config.PathingStrategy.GREEDY;
  }
}
