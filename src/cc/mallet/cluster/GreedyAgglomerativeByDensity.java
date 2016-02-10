package cc.mallet.cluster;

import java.util.logging.Logger;

import cc.mallet.cluster.neighbor_evaluator.NeighborEvaluator;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.util.MalletProgressMessageLogger;

import gnu.trove.TIntArrayList;

/**
 * Greedily merges Instances until convergence. New merges are scored
 * using {@link NeighborEvaluator}.
 *
 * Differs from {@link GreedyAgglomerative} in that one cluster is
 * created at a time. That is, nodes are added to a cluster until
 * convergence. Then, a new cluster is created from the remaining
 * nodes. This reduces the number of comparisons from O(n^2) to
 * O(nlg|n|).
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see GreedyAgglomerative
 */
public class GreedyAgglomerativeByDensity extends GreedyAgglomerative {
	
	private static final long serialVersionUID = 1L;

	private static Logger progressLogger =
		MalletProgressMessageLogger.getLogger(GreedyAgglomerativeByDensity.class.getName()+"-pl");

	/**
	 * If true, perform greedy agglomerative clustering on the clusters
	 * at the end of convergence. This may alleviate the greediness of
	 * the byDensity clustering algorithm.
	 */
	boolean doPostConvergenceMerges;

	/**
	 * Integers representing the Instance indices that have not yet been placed in a cluster.
	 */
	TIntArrayList unclusteredInstances;

	/**
	 * Index of an Instance in the cluster currently being created.
	 */
	int instanceBeingClustered;

	/**
	 * Randomness to order instanceBeingClustered.
	 */
	java.util.Random random;
	
	/**
	 *
	 * @param instancePipe Pipe for each underying {@link Instance}.
	 * @param evaluator To score potential merges.
	 * @param stoppingThreshold Clustering converges when the evaluator score is below this value.
	 * @param doPostConvergenceMerges If true, perform greedy
	 * agglomerative clustering on the clusters at the end of
	 * convergence. This may alleviate the greediness of the byDensity
	 * clustering algorithm.
	 * @return
	 */
	public GreedyAgglomerativeByDensity (Pipe instancePipe,
																			 NeighborEvaluator evaluator,
																			 double stoppingThreshold,
																			 boolean doPostConvergenceMerges,
																			 java.util.Random random) {
		super(instancePipe, evaluator, stoppingThreshold);
		this.doPostConvergenceMerges = doPostConvergenceMerges;
		this.random = random;
		this.instanceBeingClustered = -1;
	}


	public boolean converged (Clustering clustering) {
		return converged;
	}

	/**
	 * Reset convergence to false and clear state so a new round of
	 * clustering can begin.
	 */
	public void reset () {
		super.reset();
		this.unclusteredInstances = null;
		this.instanceBeingClustered = -1;
	}
	
	public Clustering improveClustering (Clustering clustering) {
		if (instanceBeingClustered == -1)
			sampleNextInstanceToCluster(clustering);
		int clusterIndex = clustering.getLabel(instanceBeingClustered);
		double bestScore = Double.NEGATIVE_INFINITY;
		int clusterToMerge = -1;
		int instanceToMerge = -1;
		for (int i = 0; i < unclusteredInstances.size(); i++) {
			int neighbor = unclusteredInstances.get(i);
			int neighborCluster = clustering.getLabel(neighbor);
			double score = getScore(clustering, clusterIndex, neighborCluster);
			if (score > bestScore) {
				bestScore = score;
				clusterToMerge = neighborCluster;
				instanceToMerge = neighbor;
			}				
		}

		if (bestScore < stoppingThreshold) { // Move on to next instance to cluster.			
			sampleNextInstanceToCluster(clustering);
			if (instanceBeingClustered != -1 && unclusteredInstances.size() != 0)
				return improveClustering(clustering);
			else { // Converged and no more instances to cluster.
				if (doPostConvergenceMerges) {
					throw new UnsupportedOperationException("PostConvergenceMerges not yet implemented.");
				}
				converged = true;
				progressLogger.info("Converged with score " + bestScore);
			}
		} else { // Merge and continue.
			progressLogger.info("Merging " + clusterIndex + "(" + clustering.size(clusterIndex) +
													" nodes) and " + clusterToMerge + "(" + clustering.size(clusterToMerge) +
													" nodes) [" + bestScore + "] numClusters=" +
													clustering.getNumClusters());
			updateScoreMatrix(clustering, clusterIndex, clusterToMerge);
			unclusteredInstances.remove(unclusteredInstances.indexOf(instanceToMerge));
 			clustering = ClusterUtils.mergeClusters(clustering, clusterIndex, clusterToMerge);
		}
		return clustering;
	}

	private void sampleNextInstanceToCluster (Clustering clustering) {
		if (unclusteredInstances == null)
			fillUnclusteredInstances(clustering.getNumInstances());
		instanceBeingClustered = (unclusteredInstances.size() == 0) ? -1 :
														 unclusteredInstances.remove(0);		
	}

	private void fillUnclusteredInstances (int size) {
		unclusteredInstances = new TIntArrayList(size);
		for (int i = 0; i < size; i++)
			unclusteredInstances.add(i);
		unclusteredInstances.shuffle(random);
	}
	
	public String toString () {
		return "class=" + this.getClass().getName() +
			"\nstoppingThreshold=" + stoppingThreshold +
			"\ndoPostConvergenceMerges=" + doPostConvergenceMerges + 
			"\nneighborhoodEvaluator=[" + evaluator + "]";		
	}
}
