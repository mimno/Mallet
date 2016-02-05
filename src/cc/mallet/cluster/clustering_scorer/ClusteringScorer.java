package cc.mallet.cluster.clustering_scorer;

import cc.mallet.cluster.Clustering;

/**
 * Assign a score to a Clustering. Higher is better.
 * @author culotta
 *
 */
public interface ClusteringScorer {

	public double score (Clustering clustering);
}
