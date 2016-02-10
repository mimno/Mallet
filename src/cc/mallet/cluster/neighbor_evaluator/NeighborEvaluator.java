package cc.mallet.cluster.neighbor_evaluator;

/**
 * Scores the value of changing the current {@link Clustering} to the
 * modified {@link Clustering} specified in a {@link Neighbor} object.
 *
 * A common implementation of this interface uses a {@link Classifier}
 * to assign a score to a {@link Neighbor}.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 */
public interface NeighborEvaluator {

	/**
	 *
	 * @param neighbor
	 * @return A higher score indicates that the modified Clustering is preferred.
	 */
	public double evaluate (Neighbor neighbor);

	/**
	 *
	 * @param neighbors
	 * @return One score per neighbor. A higher score indicates that the
	 * modified Clustering is preferred.
	 *
	 */
	public double[] evaluate (Neighbor[] neighbors);

	/**
	 * Reset the state of the evaluator.
	 */
	public void reset ();
		
}
