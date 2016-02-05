package cc.mallet.util.search;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 20, 2005
 * Time: 5:16:05 PM
 * Search state with heuristic cost-to-completion.
 */
public interface AStarState extends SearchState {
  /**
   * Get the cost to completion.
   * @return the cost
   */
  public abstract double completionCost();
}
