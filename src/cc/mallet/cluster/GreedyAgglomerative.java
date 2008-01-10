package cc.mallet.cluster;

import java.util.logging.Logger;

import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.neighbor_evaluator.Neighbor;
import cc.mallet.cluster.neighbor_evaluator.NeighborEvaluator;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.cluster.util.PairwiseMatrix;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletProgressMessageLogger;


/**
 * Greedily merges Instances until convergence. New merges are scored
 * using {@link NeighborEvaluator}.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see HillClimbingClusterer
 */
public class GreedyAgglomerative extends HillClimbingClusterer {

	
	private static final long serialVersionUID = 1L;

	private static Logger progressLogger =
		MalletProgressMessageLogger.getLogger(GreedyAgglomerative.class.getName()+"-pl");

	/**
	 * Converged when merge score is below this value.
	 */
	protected double stoppingThreshold;

	/**
	 * True if should stop clustering.
	 */
   protected boolean converged;

	/**
	 * Cache for calls to {@link NeighborhoodEvaluator}. In some
	 * experiments, reduced running time by nearly half.
	 */
   protected PairwiseMatrix scoreCache;
	
	/**
	 *
	 * @param instancePipe Pipe for each underying {@link Instance}.
	 * @param evaluator To score potential merges.
	 * @param stoppingThreshold Clustering converges when the evaluator score is below this value.
	 * @return
	 */
	public GreedyAgglomerative (Pipe instancePipe,
															NeighborEvaluator evaluator,
															double stoppingThreshold) {
		super(instancePipe, evaluator);		
		this.stoppingThreshold = stoppingThreshold;
		this.converged = false;
	}

	/**
	 *
	 * @param instances
	 * @return A singleton clustering (each Instance in its own cluster).
	 */
	public Clustering initializeClustering (InstanceList instances) {
		reset();
		return ClusterUtils.createSingletonClustering(instances);
	}

	public boolean converged (Clustering clustering) {
		return converged;
	}

	/**
	 * Reset convergence to false so a new round of clustering can begin.
	 */
	public void reset () {
		converged = false;
		scoreCache = null;
		evaluator.reset();
	}
	
	/**
	 * For each pair of clusters, calculate the score of the {@link Neighbor}
	 * that would result from merging the two clusters. Choose the merge that
	 * obtains the highest score. If no merge improves score, return original
	 * Clustering
	 * 
	 * @param clustering
	 * @return
	 */
	public Clustering improveClustering (Clustering clustering) {
		double bestScore = Double.NEGATIVE_INFINITY;
		int[] toMerge = new int[]{-1,-1};
		for (int i = 0; i < clustering.getNumClusters(); i++) {
			for (int j = i + 1; j < clustering.getNumClusters(); j++) {
				double score = getScore(clustering, i, j);
				if (score > bestScore) {
					bestScore = score;
					toMerge[0] = i;
					toMerge[1] = j;
				}				
			}
		}
		
		converged = (bestScore < stoppingThreshold);

		if (!(converged)) {
			progressLogger.info("Merging " + toMerge[0] + "(" + clustering.size(toMerge[0]) +
													" nodes) and " + toMerge[1] + "(" + clustering.size(toMerge[1]) +
													" nodes) [" + bestScore + "] numClusters=" +
													clustering.getNumClusters());
			updateScoreMatrix(clustering, toMerge[0], toMerge[1]);
			clustering = ClusterUtils.mergeClusters(clustering, toMerge[0], toMerge[1]);
		} else {
			progressLogger.info("Converged with score " + bestScore);
		}
		return clustering;
	}
	
	/**
	 *
	 * @param clustering
	 * @param i
	 * @param j
	 * @return The score for merging these two clusters.
	 */
	protected double getScore (Clustering clustering, int i, int j) {
		if (scoreCache == null)
			scoreCache = new PairwiseMatrix(clustering.getNumInstances());

		int[] ci = clustering.getIndicesWithLabel(i);
		int[] cj = clustering.getIndicesWithLabel(j);
		if (scoreCache.get(ci[0], cj[0]) == 0.0) {
			double val = evaluator.evaluate(
				new AgglomerativeNeighbor(clustering,
																	ClusterUtils.copyAndMergeClusters(clustering, i, j),
																	ci, cj));
			for (int ni = 0; ni < ci.length; ni++) 
				for (int nj = 0; nj < cj.length; nj++)
					scoreCache.set(ci[ni], cj[nj], val);
		}

		return scoreCache.get(ci[0], cj[0]);														
	}

	/**
	 * Resets the values of clusters that have been merged.
	 * @param clustering
	 * @param i
	 * @param j
	 */
	protected void updateScoreMatrix (Clustering clustering, int i, int j) {
		int size = clustering.getNumInstances();
		int[] ci = clustering.getIndicesWithLabel(i);
		for (int ni = 0; ni < ci.length; ni++) {
			for (int nj = 0; nj < size; nj++)
				if (ci[ni] != nj)
					scoreCache.set(ci[ni], nj, 0.0);
		}
		int[] cj = clustering.getIndicesWithLabel(j);
		for (int ni = 0; ni < cj.length; ni++) {
			for (int nj = 0; nj < size; nj++)
				if (cj[ni] != nj)
					scoreCache.set(cj[ni], nj, 0.0);
		}
	}
		
	public String toString () {
		return "class=" + this.getClass().getName() +
			"\nstoppingThreshold=" + stoppingThreshold + 
			"\nneighborhoodEvaluator=[" + evaluator + "]";		
	}
}
