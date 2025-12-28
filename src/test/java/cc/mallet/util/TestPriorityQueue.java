package cc.mallet.util;

import cc.mallet.util.search.MinHeap;
import cc.mallet.util.search.PriorityQueue;
import cc.mallet.util.search.QueueElement;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 18, 2005
 * Time: 11:19:36 PM
 * Test priority queues and their implementation.
 */
public class TestPriorityQueue extends TestCase {
  private static final int N = 100;
  private static class Item implements QueueElement {
    private int position;
    private double priority;
  private Item(double p) {
    priority = p;
  }
  public double getPriority() { return priority; };
  public void setPriority(double p) { priority = p; }
  public int getPosition() { return position; }
  public void setPosition(int p) { position = p; }
  }
  public TestPriorityQueue(String name) {
    super(name);
  }
  public void testAscending() {
    PriorityQueue q = new MinHeap(N);
    double p[] = new double[N];
    for (int i = 0; i < N; i++) {
      p[i] = i;
      Item e = new Item(i);
      q.insert(e);
    }
    int j = 0;
    double pr = Double.NEGATIVE_INFINITY;
    assertTrue("ascending size", q.size() == N);
    while (q.size() > 0) {
      assertTrue("ascending extract", j < N);
      QueueElement e = q.extractMin();
      assertTrue("ascending order", e.getPriority() > pr);
      assertEquals("ascending priority", e.getPriority(), p[j++], 1e-5);
      pr = e.getPriority();
    }
  }
  public void testDescending() {
    PriorityQueue q = new MinHeap(N);
    double p[] = new double[N];
    for (int i = 0; i < N; i++) {
      p[i] = i;
      Item e = new Item(N-i-1);
      q.insert(e);
    }
    int j = 0;
    double pr = Double.NEGATIVE_INFINITY;
    assertTrue("descending size", q.size() == N);
    while (q.size() > 0) {
      assertTrue("descending extract", j < N);
      QueueElement e = q.extractMin();
      assertTrue("descending order", e.getPriority() > pr);
      assertEquals("descending priority", e.getPriority(), p[j++], 1e-5);
      pr = e.getPriority();
    }
  }

  public void testChangePriority ()
  {
    PriorityQueue q = new MinHeap(N);
    Item items[] = new Item[N];
    for (int i = 0; i < N; i++) {
      Item e = new Item(N-i-1);
      q.insert(e);
      items[i] = e;
    }

    q.changePriority (items[N-1], -2);
    q.changePriority (items[N/2], -1);
    q.changePriority (items[N/2 + 1], N*2);

    int j = 0;
    double pr_last = Double.NEGATIVE_INFINITY;
    assertTrue("descending size", q.size() == N);
    
    while (q.size() > 0) {
      assertTrue("descending extract", j < N);
      QueueElement e = q.extractMin();
      assertTrue("descending order", e.getPriority() > pr_last);
      pr_last = e.getPriority();
      if (j == 0) assertTrue ("lowest elt", e.getPriority () == -2);
      if (j == 1) assertTrue ("second-lowest elt", e.getPriority () == -1);
      if (q.size() == 1) assertTrue ("penultimate elt", e.getPriority () == N-1);
      if (q.size() == 0) assertTrue ("final elt", e.getPriority () == N*2);
      j++;
    }
  }

  public void testReverse ()
  {
    PriorityQueue q = new MinHeap(N);
    Item items[] = new Item[N];
    for (int i = 0; i < N; i++) {
      Item e = new Item(N-i-1);
      q.insert(e);
      items[i] = e;
    }

    for (int i = 0; i < N; i++) {
      q.changePriority (items[i], i);
    }

    int j = 0;
    double pr_last = Double.NEGATIVE_INFINITY;
    assertTrue("ascending size", q.size() == N);

    while (q.size() > 0) {
      assertTrue("ascending extract", j < N);
      QueueElement e = q.extractMin();
      assertTrue("ascending order", e.getPriority() > pr_last);
      pr_last = e.getPriority();
      assertEquals ("ascending priority", items[j].getPriority (), e.getPriority ());
      assertEquals ("ascending identity", items[j], e);
      j++;
    }
  }

  public void testEqualKeys ()
  {
    PriorityQueue q = new MinHeap (N);
    Item[] items = new Item[20];
    int j = 0;

    for (int i = 0; i < 5; i++) {
      items[j] = new Item (5);
      q.insert (items[j]);
      j++;
    }
    for (int i = 0; i < 5; i++) {
      items[j] = new Item (3);
      q.insert (items[j]);
      j++;
    }
    for (int i = 0; i < 5; i++) {
      items[j] = new Item (4);
      q.insert (items[j]);
      j++;
    }
    for (int i = 0; i < 5; i++) {
      items[j] = new Item (7);
      q.insert (items[j]);
      j++;
    }

    assertEquals (20, q.size ());
    for (int i = 0; i < items.length; i++) {
      assertTrue (q.contains (items[i]));
    }
    
    for (int i = 0; i < 5; i++) {
      QueueElement e = q.extractMin ();
      assertTrue (q.contains (q.min ()));
      assertEquals (3.0, e.getPriority ());
    }
    for (int i = 0; i < 5; i++) {
      QueueElement e = q.extractMin ();
      assertTrue (q.contains (q.min ()));
      assertEquals (4.0, e.getPriority ());
    }
    for (int i = 0; i < 5; i++) {
      QueueElement e = q.extractMin ();
      assertTrue (q.contains (q.min ()));
      assertEquals (5.0, e.getPriority ());
    }
    for (int i = 0; i < 5; i++) {
      QueueElement e = q.extractMin ();
      if (q.size() > 0)
        assertTrue (q.contains (q.min ()));
      assertEquals (7.0, e.getPriority ());
    }
  }

  public static Test suite() {
    return new TestSuite(TestPriorityQueue.class);
  }
  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}
