package cc.mallet.types;

import java.util.Iterator;

/** Under development, and not sure we actually want to have a class list this.  
 * It seems quite dangerous, and error-prone. 
 */
@Deprecated 
public abstract class ChainedInstanceIterator implements Iterator<Instance> {

	Iterator<Instance> source;
	ChainedInstanceIterator target;

	/** Both source and target may be null. */
	public ChainedInstanceIterator (Iterator<Instance> source, ChainedInstanceIterator target) {
		this.source = source;
	}

	public abstract Instance next ();
	public abstract boolean hasNext ();
	public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not support remove()."); }


	/** The "source" of this iterator sends this message to tell this iterator
	 * that, even though source.hasNext() may have returned false before, it 
	 * would now return true.  
	 * Note that not all iterators handle this strange
	 * situation in which an iterator indicates that hasNext is false, but then
	 * later hasNext becomes true.  In particular, if this iterator has also
	 * returned false for hasNext() to its consumer, but is now ready to provide
	 * more since its source now hasNext(), the consumer may not properly handle
	 * this situation.  (One possible solution: create a ChainedIterator interface,
	 * and be more strict about type-checking all sources and targets of these
	 * kinds of iterators. -akm)  (Also consider passing the source as an argument here.) */ 
	public boolean sourceNowHasNext (Iterator<Instance> source) {
		if (target != null) target.sourceNowHasNext(this);
		return false; 
	}

}

