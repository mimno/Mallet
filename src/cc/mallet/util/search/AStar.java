package cc.mallet.util.search;


import java.util.Iterator;
import java.util.logging.Logger;

import cc.mallet.util.MalletLogger;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 19, 2005
 * Time: 1:38:28 PM
 * A* search iterator over an underlying graph. The iterator returns
 * search nodes for final states in order of increasing cost to reach them
 * from the initial states, assuming that the heuristic cost-to-completion
 * function is admissible. This very simple version
 * assumes that we may revisit already visited states, because
 * we want to generate all paths to final states in order of
 * increasing cost.
 */
public class AStar implements Iterator<AStarNode> {
  private static Logger logger = MalletLogger.getLogger(AStar.class.getName());
  private PriorityQueue q;
  private AStarNode answer;
  private boolean needNext;
  /**
   * Create an A* search iterator starting from the given initial states.
   * The expected size parameter gives the size of the search queue. If this
   * is too small, growing the queue costs more time. If this is too big,
   * space is wasted.
   *
   * @param initial the set of initial states
   * @param expectedSize the expected size of the search queue
   */
  public AStar(AStarState[] initial, int expectedSize) {
    q = new MinHeap(expectedSize);
    for (int i = 0; i < initial.length; i++) {
      AStarState s = initial[i];
      AStarNode n = new AStarNode(s, null, 0);
      n.setPriority(s.completionCost());
      q.insert(n);
    }
    needNext = true;
  }
  private void lookAhead() {
    if (needNext) {
      answer = search();
      needNext = false;
    }
  }
  public boolean hasNext() {
    lookAhead();
    return answer != null;
  }
  public AStarNode next() { return nextAnswer(); }
  /**
   * Get the next search node for a final state.
   * @return a final search node
   */
  public AStarNode nextAnswer() {
    lookAhead();
    needNext = true;
    return answer;
  }
  public void remove() {
    throw new UnsupportedOperationException();
  }
  private AStarNode search() {
    while (q.size() > 0) {
      AStarNode u = (AStarNode)q.extractMin();
      //logger.info(u + ": " + u.getPriority());
      if (u.isFinal()) {
        //logger.info("Final " + u);
        return u;
      }
      SearchNode.NextNodeIterator i = u.getNextNodes();
      while (i.hasNext()) {
        AStarNode v = (AStarNode)i.nextNode();
        double priority = v.getCost() + v.completionCost();
        //logger.info("insert " + v + " at " + priority);
        v.setPriority(priority);
        q.insert(v);
      }
    }
    return null;
  }
}
