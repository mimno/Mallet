package cc.mallet.util.search;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 18, 2005
 * Time: 9:11:24 PM
 * <p/>
 * Binary heap implementation of <code>PriorityQueue</code>.
 * Based on algorithm in Corman, Leiserson, Rivest, and Stein (Section 6.5).
 */
public class MinHeap implements PriorityQueue {

  private QueueElement[] elts;
  private int size = 0;
  private static final int MIN_CAPACITY = 16;

  /**
   * Create a binary heap with initial capacity <code>capacity</code>.
   * The heap's capacity grows as needed to accomodate insertions.
   *
   * @param capacity initial capacity
   */
  public MinHeap (int capacity)
  {
    if (capacity < MIN_CAPACITY)
      capacity = MIN_CAPACITY;
    elts = new QueueElement[capacity];
    size = 0;
  }

  /**
   * Create a binary heap with minimum initial capacity.
   */
  public MinHeap ()
  {
    this (MIN_CAPACITY);
  }

  private void heapify (int i)
  {
    int l = 2 * i + 1;
    int r = 2 * i + 2;
    int first;
    if (l < size && elts[l].getPriority () < elts[i].getPriority ())
      first = l;
    else
      first = i;
    if (r < size && elts[r].getPriority () < elts[first].getPriority ())
      first = r;
    if (first != i) {
      QueueElement e = elts[i];
      elts[i] = elts[first];
      elts[i].setPosition (i);
      elts[first] = e;
      e.setPosition (first);
      heapify (first);
    }
  }

  public int size ()
  {
    return size;
  }

  public QueueElement min ()
  {
    if (size == 0)
      throw new IndexOutOfBoundsException ("queue empty");
    return elts[0];
  }

  public QueueElement extractMin ()
  {
    if (size == 0)
      throw new IndexOutOfBoundsException ("queue empty");
    QueueElement min = elts[0];
    elts[0] = elts[--size];
    elts[0].setPosition (0);  // this is necessary in case elts[size-1] happens to be the best --cas
    heapify (0);
    min.setPosition (-1);
    return min;
  }

  public void changePriority (QueueElement e, double priority)
  {
    if (!contains (e))
      throw new IllegalArgumentException ("Element not in queue");
    if (priority <= e.getPriority ()) {
      decreaseKey (e, priority);
    } else {
      increaseKey (e, priority);
    }
  }

  private void increaseKey (QueueElement e, double priority)
  {
    e.setPriority (priority);
    heapify (e.getPosition ());
  }

  private void decreaseKey (QueueElement e, double priority)
  {
    e.setPriority (priority);
    int i = e.getPosition ();
    int j;
    while (i > 0 && elts[j = (i - 1) / 2].getPriority () > elts[i].getPriority ()) {
      QueueElement p = elts[j];
      elts[j] = elts[i];
      elts[j].setPosition (j);
      elts[i] = p;
      p.setPosition (i);
      i = j;
    }
  }

  public void insert (QueueElement e)
  {
    if (size == elts.length) {
      QueueElement[] newElts = new QueueElement[size + size / 2];
      for (int i = 0; i < size; i++)
        newElts[i] = elts[i];
      elts = newElts;
    }
    e.setPosition (size);
    elts[size++] = e;
    changePriority (e, e.getPriority ());
  }

  public boolean contains (QueueElement e)
  {
    int pos = e.getPosition ();
    return pos >= 0 && pos < size && e == elts[pos];
  }

  public QueueElement[] toArray ()
  {
    QueueElement[] arr = new QueueElement[size ()];
    System.arraycopy (elts, 0, arr, 0, size ());
    return arr;
  }

  // for debugging --cas
  private void checkHeap (int i)
  {
    int child = 2*i + 1;
    if (child < size) {
      assert elts[i].getPriority () <= elts [child].getPriority ();
      checkHeap (child);
    }

    child = 2*i + 2;
    if (child < size) {
      assert elts[i].getPriority () <= elts [child].getPriority ();
      checkHeap (child);
    }
  }

}
