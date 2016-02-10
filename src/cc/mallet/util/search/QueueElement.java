package cc.mallet.util.search;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 18, 2005
 * Time: 7:31:08 PM
 *
 * Queue elements have a priority, and a queue position.
 * Lower-priority elements are closer to the front of the queue.
 * The queue position is set by the queue implementation,
 * and should not be changed outside the queue implementation.
 */
public interface QueueElement {
  /**
   * Get this element's priority.
   * @return the priority
   */
  public double getPriority();
  /**
   * Set the priority of this element.
   * @param priority the element's new priority
   */
  public void setPriority(double priority);
  /**
   * Get the queue position of this element. If the element is not in a queue,
   * the returned value is meaningless.
   * @return the current position
   */
  public int getPosition();
  /**
   * Set the current queue position for this element. This should only
   * be called by a queue implementation.
   * @param pos the new position for the element
   */
  public void setPosition(int pos);
}
