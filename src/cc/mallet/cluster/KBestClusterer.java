package cc.mallet.cluster;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.InstanceList;

/**
 * Return the K best predicted Clusterings
 * @author culotta
 *
 */
public abstract class KBestClusterer extends Clusterer {
	
	public KBestClusterer(Pipe instancePipe) {
		super(instancePipe);
	}

	public abstract Clustering[] clusterKBest(InstanceList trainingSet, int k);
	
}
