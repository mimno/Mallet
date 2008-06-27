package cc.mallet.fst;


/**
 * Indicates when the value/gradient during training becomes stale. <p>
 *
 * See <tt>ThreadedOptimizable</tt>.
 *
 * @author Gaurav Chandalia
 */
public interface CacheStaleIndicator {
  public boolean isValueStale();
  public boolean isGradientStale();
}
