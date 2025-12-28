package cc.mallet.cluster.iterator;

import com.google.errorprone.annotations.Var;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Sample pairs of Instances.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see NeighborIterator
 */
public class PairSampleIterator extends NeighborIterator {

	protected InstanceList instances;
	protected Randoms random;
	protected double positiveProportion;
	protected int numberSamples;
	protected int positiveTarget;
	protected int positiveCount;
	protected int totalCount;
	protected int[] nonsingletonClusters;
	
	/**
	 *
	 * @param clustering True clustering.
	 * @param random Source of randomness.
	 * @param positiveProportion Proportion of Instances that should be positive examples.
	 * @param numberSamples Total number of samples to generate.
	 * @return
	 */
	public PairSampleIterator (Clustering clustering,
														 Randoms random,
														 double positiveProportion,
														 int numberSamples) {
		super(clustering);
		this.random = random;
		this.positiveProportion = positiveProportion;
		this.numberSamples = numberSamples;
		this.positiveTarget = (int)(numberSamples * positiveProportion);
		this.totalCount = this.positiveCount = 0;
		this.instances = clustering.getInstances();
		setNonSingletons();
	}

	private void setNonSingletons () {
		@Var
		int c = 0;
		for (int i = 0; i < clustering.getNumClusters(); i++)
			if (clustering.size(i) > 1)
				c++;
		nonsingletonClusters = new int[c];
		c = 0;
		for (int i = 0; i < clustering.getNumClusters(); i++)
			if (clustering.size(i) > 1)
				nonsingletonClusters[c++] = i;				
	}
	
	public boolean hasNext () {
	    return totalCount < numberSamples;
	}

	public Instance next () {
		@Var
		AgglomerativeNeighbor neighbor = null;
		
		if (nonsingletonClusters.length>0 && (  positiveCount < positiveTarget || clustering.getNumClusters() == 1)) { //mmwick modified
			positiveCount++;
			int label = nonsingletonClusters[random.nextInt(nonsingletonClusters.length)];
			int[] instances = clustering.getIndicesWithLabel(label);
			int ii = instances[random.nextInt(instances.length)];
			@Var
			int ij = instances[random.nextInt(instances.length)];
			while (ii == ij)
				ij = instances[random.nextInt(instances.length)];
			neighbor = new AgglomerativeNeighbor(clustering,
																					 clustering,
																					 ii, ij);			
		} else {
			int ii = random.nextInt(instances.size());
			@Var
			int ij = random.nextInt(instances.size());
			while (clustering.getLabel(ii) == clustering.getLabel(ij))
				ij = random.nextInt(instances.size());
			neighbor =
				new AgglomerativeNeighbor(clustering,
																	ClusterUtils.copyAndMergeInstances(clustering,
																																		 ii, ij),
																	ii, ij);				
		}
		totalCount++;
		return new Instance(neighbor, null, null, null);
	}
}
