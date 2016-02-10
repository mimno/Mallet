package cc.mallet.cluster.clustering_scorer;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.iterator.AllPairsIterator;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.neighbor_evaluator.NeighborEvaluator;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.types.Instance;

/**
 * For each pair of Instances, if the pair is predicted to be in the same
 * cluster, increment the total by the evaluator's score for merging the two.
 * Else, increment by 1 - evaluator score. Divide by number of pairs.
 * 
 * @author culotta
 * 
 */
public class PairwiseScorer implements ClusteringScorer {

	NeighborEvaluator evaluator;

	public PairwiseScorer(NeighborEvaluator evaluator) {
		super();
		this.evaluator = evaluator;
	}

	public double score(Clustering clustering) {
		Clustering singletons = ClusterUtils
				.createSingletonClustering(clustering.getInstances());
		double total = 0;
		int count = 0;
		for (AllPairsIterator iter = new AllPairsIterator(singletons); iter
				.hasNext(); count++) {
			Instance instance = (Instance) iter.next();
			AgglomerativeNeighbor neighbor = (AgglomerativeNeighbor) instance
					.getData();
			double score = evaluator.evaluate(neighbor);
			int[][] clusters = neighbor.getOldClusters();
			if (clustering.getLabel(clusters[0][0]) == clustering
					.getLabel(clusters[1][0]))
				total += score;
			else
				total += 1.0 - score;
		}
		return (double) total / count;
	}

}
