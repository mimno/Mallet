package cc.mallet.cluster.evaluate;

import cc.mallet.cluster.Clusterer;
import cc.mallet.cluster.Clustering;

/**
 * Evaluates a predicted Clustering against a true Clustering.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 */
public abstract class ClusteringEvaluator {

	/**
	 *
	 * @param truth
	 * @param predicted
	 * @return A String summarizing the evaluation metric.
	 */
	public abstract String evaluate (Clustering truth, Clustering predicted);

	public String evaluate (Clustering[] truth, Clustering[] predicted) {
		for (int i = 0; i < truth.length; i++)
			evaluate(truth[i], predicted[i]);
		return evaluateTotals();
	}

	public String evaluate (Clustering[] truth, Clusterer clusterer) {
		for (int i = 0; i < truth.length; i++)
			evaluate(truth[i], clusterer.cluster(truth[i].getInstances()));
		return evaluateTotals();
	}
	
	public abstract double[] getEvaluationScores (Clustering truth, Clustering predicted);
	
	/**
	 *
	 * @return If the ClusteringEvaluator maintains state between calls
	 * to evaluate, this method will return the total evaluation metric
	 * since the first evaluation.
	 */
	public abstract String evaluateTotals ();
}
