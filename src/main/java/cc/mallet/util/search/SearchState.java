package cc.mallet.util.search;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 20, 2005
 * Time: 4:54:46 PM
 * A state (vertex) in a graph being searched.
 */
public interface SearchState {
  /**
   * Iterator over the states with transitions from a given state.
   */
  public static abstract class NextStateIterator implements Iterator<SearchState> {
    public abstract boolean hasNext();
    public SearchState next() {return nextState(); };
    /**
     * Get the next reachable state.
     * @return the state
     */
    public abstract SearchState nextState();
    /**
     * The cost of the transition to the current state.
     * @return transition cost
     */
    public abstract double cost();
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
  /**
   * Get an iterator over the states with transitions from
   * this state.
   * @return the iterator
   */
  public abstract NextStateIterator getNextStates();
  /**
   * Is this state final?
   * @return whether this state is final
   */
  public abstract boolean isFinal();
}
