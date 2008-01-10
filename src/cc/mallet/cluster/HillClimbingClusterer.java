package cc.mallet.cluster;

import java.util.LinkedList;

import cc.mallet.cluster.neighbor_evaluator.NeighborEvaluator;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.InstanceList;


/**
 * A Clusterer that iteratively improves a predicted Clustering using
 * a {@link NeighborEvaluator}.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see Clusterer
 */
public abstract class HillClimbingClusterer extends KBestClusterer {

	NeighborEvaluator evaluator;
	
	public HillClimbingClusterer(Pipe instancePipe, NeighborEvaluator evaluator) {
		super(instancePipe);
		this.evaluator = evaluator;
	}

	public NeighborEvaluator getEvaluator () { return evaluator; }
	
	/**
	 * While not converged, calls <code>improveClustering</code> to modify the
	 * current predicted {@link Clustering}.
	 * 
	 * @param instances
	 * @return The predicted {@link Clustering}.
	 */
	public Clustering cluster (InstanceList instances) {
		return clusterKBest(instances, 1)[0];
	}

	/* (non-Javadoc)
	 * @see edu.umass.cs.mallet.base.cluster.KBestClusterer#clusterKBest(edu.umass.cs.mallet.base.types.InstanceList)
	 */
	public Clustering[] clusterKBest (InstanceList instances, int k) {
		reset();
		return clusterKBest(instances, Integer.MAX_VALUE, null, k);
	}

	/**
	 * While not converged, call <code>improveClustering</code> to
	 * modify the current predicted {@link Clustering}.
	 * @param instances Instances to cluster.
	 * @param iterations Maximum number of iterations.
	 * @param initialClustering Initial clustering of the Instances.
	 * @return The predicted {@link Clustering}
	 */
	public Clustering cluster (InstanceList instances, int iterations, Clustering initialClustering) {
		return clusterKBest(instances, iterations, initialClustering, 1)[0];
	}

	
	
	/**
	 * Return the K most recent solutions.
	 * @param instances
	 * @param iterations
	 * @param initialClustering
	 * @return
	 */
	public Clustering[] clusterKBest (InstanceList instances, int iterations, Clustering initialClustering, int k) {
		LinkedList<Clustering> solutions = new LinkedList<Clustering>();
		Clustering bestsofar = (initialClustering == null) ? initializeClustering(instances) : initialClustering;
		solutions.addFirst(bestsofar);
		int iter = 0;		
		do {
			bestsofar = improveClustering(solutions.getFirst().shallowCopy());
			if (!bestsofar.equals(solutions.getFirst())) 
				solutions.addFirst(bestsofar);				
			if (solutions.size() == k + 1)
				solutions.removeLast();			
		} while (!converged(bestsofar) && iter++ < iterations);

		return solutions.toArray(new Clustering[]{});
	}

	/**
	 *
	 * @param clustering
	 * @return True if clustering is complete. 
	 */
	public abstract boolean converged (Clustering clustering);

	/**
	 *
	 * @param clustering
	 * @return A modified Clustering.
	 */
	public abstract Clustering improveClustering (Clustering clustering);
	
	/**
	 *
	 * @param instances
	 * @return An initialized Clustering of these Instances.
	 */
	public abstract Clustering initializeClustering (InstanceList instances);
	
	/**
	 * Perform any cleanup of the clustering algorithm prior to
	 * clustering.
	 */
	public abstract void reset ();
}
