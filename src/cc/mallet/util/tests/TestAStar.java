package cc.mallet.util.tests;

import cc.mallet.util.search.AStar;
import cc.mallet.util.search.AStarState;
import cc.mallet.util.search.SearchNode;
import cc.mallet.util.search.SearchState;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 19, 2005
 * Time: 2:36:10 PM
 * Test A* search.
 */
public class TestAStar extends TestCase {
  private class State implements AStarState {
    private double to;
    private State next[];
    private double cost[];
    private int id;
    private boolean sink;
    private State(int id, int numEdges, double to, boolean sink) {
      this.id = id;
      next = new State[numEdges];
      cost = new double[numEdges];
      this.to = to;
      this.sink = sink;
    }
    public boolean isFinal() { return sink; }
    public double completionCost() { return to; }
    private class NextStates extends SearchState.NextStateIterator {
      private int i;
      private NextStates() {
        i = 0;
      }
      public boolean hasNext() { return i < next.length; }
      public SearchState nextState() { return next[i++]; }
      public double cost() { return cost[i-1]; }
    }
    public SearchState.NextStateIterator getNextStates() {
      return new NextStates();
    }
    public String toString() { return "node " + id; }
  }
  public TestAStar(String name) {
    super(name);
  }
  public void testSmall() {
    State node5 = new State(5, 0, 0, true);
    State node6 = new State(6, 0, 0, true);
    State node2 = new State(2, 1, 6, false);
    node2.next[0] = node5; node2.cost[0] = 6;
    State node3 = new State(3, 2, 2, false);
    node3.next[0] = node5; node3.cost[0] = 4;
    node3.next[1] = node6; node3.cost[1] = 2;
    State node4 = new State(4, 1, 6, false);
    node4.next[0] = node6; node4.cost[0] = 6;
    State node0 = new State(0, 2, 4, false);
    node0.next[0] = node2; node0.cost[0] = 2;
    node0.next[1] = node3; node0.cost[1] = 2;
    State node1 = new State(1, 2, 3, false);
    node1.next[0] = node3; node1.cost[0] = 1;
    node1.next[1] = node4; node1.cost[1] = 1;
    State[][] paths = new State[6][];
    double[] costs = new double[6];
    paths[0] = new State[] { node6, node3, node1 };
    costs[0] = 3;
    paths[1] = new State[] { node6, node3, node0 };
    costs[1] = 4;
    paths[2] = new State[] { node5, node3, node1 };
    costs[2] = 5;
    paths[3] = new State[] { node5, node3, node0 };
    costs[3] = 6;
    paths[4] = new State[] { node6, node4, node1 };
    costs[4] = 7;
    paths[5] = new State[] { node5, node2, node0 };
    costs[5] = 8;
    AStar s = new AStar(new State[] {node0, node1}, 7);
    int i = 0;
    while (s.hasNext()) {
      assertTrue("number of answers > " + i, i < 6);
      SearchNode n = s.nextAnswer();
      assertEquals("costs[" + i + "] != " + n.getPriority(), costs[i],
              n.getPriority(), 1e-5);
      int j = 0;
      while (n != null) {
        assertTrue("path length > " + j, j < 3);
        assertTrue("path[" + i + "][" + j + "] != " + n,
                paths[i][j] == n.getState());
        j++;
        n = n.getParent();
      }
      assertTrue("path length != " + j, j == 3);
      i++;
    }
    assertTrue("number of answers != " + i, i == 6);
  }
  public static Test suite() {
    return new TestSuite(TestAStar.class);
  }
  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}
