package cc.mallet.cluster.neighbor_evaluator;

import cc.mallet.cluster.Clustering;
import cc.mallet.util.ArrayUtils;

/**
 * A {@link Neighbor} created by merging two clusters of the original
 * Clustering.
 * 
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 */
public class AgglomerativeNeighbor extends Neighbor {

	/**
	 * Instance indices in the new, merged cluster.
	 */
	int[] newCluster;

	/**
	 * Instance indices in the old, pre-merged clusters.
	 */
	int[][] oldClusters;
	
	/**
	 *
	 * @param original
	 * @param modified
	 * @param cluster1 Instance indices for one cluster that was merged.
	 * @param cluster2 Instance indices for other cluster that was merged.
	 * @return
	 */
	public AgglomerativeNeighbor (Clustering original,
																Clustering modified,
																int[][] oldClusters) {
		super(original, modified);
		if (oldClusters.length != 2)
			throw new IllegalArgumentException("Agglomerations of more than 2 clusters not yet implemented.");
		this.oldClusters = oldClusters;
		this.newCluster = ArrayUtils.append(oldClusters[0], oldClusters[1]);	
	}

	public AgglomerativeNeighbor (Clustering original,
																Clustering modified,
																int[] oldCluster1, int[] oldCluster2) {
		this(original, modified, new int[][]{oldCluster1, oldCluster2});
	}

	public AgglomerativeNeighbor (Clustering original,
																Clustering modified,
																int oldCluster1, int oldCluster2) {
		this(original, modified, new int[][]{{oldCluster1}, {oldCluster2}});
	}

	public int[] getNewCluster () { return newCluster; }

	public int[][] getOldClusters () { return oldClusters; }
	
	public String toString () {
		String ret = super.toString() + "\nnewcluster=";
		for (int i = 0; i < newCluster.length; i++) 
			ret += newCluster[i] + " ";
		return ret;
	}
}
