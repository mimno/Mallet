package cc.mallet.cluster.iterator;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * Iterate over all pairs of Instances.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see NeighborIterator
 */
public class AllPairsIterator extends NeighborIterator {
	int i;
	int j;
	InstanceList instances;
	
	/**
	 *
	 * @param clustering True Clustering.
	 * @return
	 */
	public AllPairsIterator (Clustering clustering) {
		super(clustering);
		i = 0;
		j = 1;
		this.instances = clustering.getInstances();
	}

	public boolean hasNext () {
		return i < instances.size() - 1;
	}

	public Instance next () {
		AgglomerativeNeighbor neighbor =
			new AgglomerativeNeighbor(clustering,
																ClusterUtils.copyAndMergeInstances(clustering,
																																	 i, j),
																i, j);
		// Increment.
		if (j + 1 == instances.size()) {
			i++;
			j = i + 1;
		} else {
			j++;
		}
		return new Instance(neighbor, null, null, null);
	}
}

