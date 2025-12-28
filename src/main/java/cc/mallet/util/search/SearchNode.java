package cc.mallet.util.search;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 20, 2005
 * Time: 4:46:56 PM
 *
 * Search tree node. A search tree node pertains to some search graph state.
 * Multiple nodes may refer to the same state, representing different ways
 * of reaching the state. Search nodes have a priority, which determines when
 * they will be expanded, and cost of reaching the node from the start of the
 * search.
 */
public class SearchNode implements QueueElement {
  private int position = -1;
  private double priority = Double.POSITIVE_INFINITY;
  private double cost;
  private SearchNode parent;
  private SearchState state;
  /**
   * This iterator generates search nodes that refer to the
   * states reachable from the state pertaining to a this search node.
   */
  public class NextNodeIterator implements Iterator {
    private SearchState.NextStateIterator stateIter;
    protected NextNodeIterator() {
      stateIter = state.getNextStates();
    }
    public boolean hasNext() { return stateIter.hasNext(); }
    public Object next() { return nextNode(); };
    /**
     * The search tree node for the next state reached from
     * the current state.
     * @return a new search tree node
     */
    public SearchNode nextNode() {
      SearchNode p = SearchNode.this;
      SearchState s = stateIter.nextState();
      return new SearchNode(s, p, p.getCost() + cost());
    }
    /**
     * The cost associated to the transition from the previous
     * state to this state.
     * @return the cost
     */
    public double cost() { return stateIter.cost(); }
    public void remove() {
      throw new UnsupportedOperationException();
    }
    protected SearchState.NextStateIterator getStateIter() {
      return stateIter;
    }
  }
  /**
   * Create a search node with given state, parent, and cost.
   * @param state the state
   * @param parent the parent
   * @param cost the cost
   */
  public SearchNode(SearchState state, SearchNode parent, double cost) {
    this.state = state;
    this.parent = parent;
    this.cost = cost;
  }

  public double getPriority() { return priority; }
  public void setPriority(double priority) { this.priority = priority; }
  public int getPosition() { return position; }
  public void setPosition(int position) { this.position = position; }
  /**
   * The node that generated this node.
   * @return the parent
   */
  public SearchNode getParent() { return parent; }
  /**
   * Get the cost for this node.
   * @return the cost
   */
  public double getCost() { return cost; }
  /**
   * The state for this search node.
   * @return the state
   */
  public SearchState getState() { return state; }
  /**
   * Is the node's state final?
   * @return whether this state's node is final
   */
  public boolean isFinal() { return state.isFinal(); }
  /**
   * Get an iterator over the new search nodes reachable
   * from this node by state transitions.
   * @return the iterator
   */
  public NextNodeIterator getNextNodes() {
    return new NextNodeIterator();
  }
  public String toString() {
    return state.toString() + "/" + priority;
  }
}
