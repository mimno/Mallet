package cc.mallet.util.search;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 18, 2005
 * Time: 7:46:46 PM
 *
 * Interface representing the basic methods for a priority queue.
 */
public interface PriorityQueue {
  /**
   * Insert element <code>e</code> into the queue.
   * @param e the element to insert
   */
  public void insert(QueueElement e);

  /**
   * The current size of the queue.
   * @return current size
   */
  public int size();

  /**
   * Return the top element of the queue.
   * @return top element of the queue
   */
  public QueueElement min();

  /**
   * Remove the top element of the queue.
   * @return the element removed
   */
  public QueueElement extractMin();

  /**
   * Change the priority of queue element <code>e</code> to <code>priority</code>.
   * The element's position in the queue is adjusted as needed.
   * @param e the element that has been changed
   * @param priority the new priority
   */
  public void changePriority (QueueElement e, double priority);

  /**
   * Does the queue contain an element?
   * @param e the element
   * @return whether the queue contains the element
   */
  public boolean contains(QueueElement e);

  /** Returns any array containing all of the elements in the queue.
   *   They are not guaranteed to be in any particular order.
   */
  public QueueElement[] toArray ();

}
