package cc.mallet.cluster.iterator;

import java.util.Iterator;

import cc.mallet.cluster.Clustering;
import cc.mallet.types.Instance;

/**
 * Sample Instances with data objects equal to {@link Neighbor}s. This
 * class is mainly used to generate training Instances from a true
 * {@link Clustering}.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see InstanceIterator
 */
public abstract class NeighborIterator implements Iterator<Instance> {
	protected Clustering clustering;

	/**
	 *
	 * @param clustering A true Clustering.
	 * @return
	 */
	public NeighborIterator (Clustering clustering) {
		this.clustering = clustering;
	}

	protected Clustering getClustering () { return clustering; }
	
	public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not support remove()."); }
}
