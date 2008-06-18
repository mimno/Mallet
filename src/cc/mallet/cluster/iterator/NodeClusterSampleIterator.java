package cc.mallet.cluster.iterator;


import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.types.Instance;
import cc.mallet.util.Randoms;

/**
 * Samples merges of a singleton cluster with another (possibly
 * non-singleton) cluster.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see PairSampleIterator, NeighborIterator
 */
public class NodeClusterSampleIterator extends ClusterSampleIterator {
	
	/**
	 *
	 * @param clustering True clustering.
	 * @param random Source of randomness.
	 * @param positiveProportion Proportion of Instances that should be positive examples.
	 * @param numberSamples Total number of samples to generate.
	 * @return
	 */
	public NodeClusterSampleIterator (Clustering clustering,
																		Randoms random,
																		double positiveProportion,
																		int numberSamples) {
		super(clustering, random, positiveProportion, numberSamples);
	}
	
	public Instance next () {
		AgglomerativeNeighbor neighbor = null;
		
		if (positiveCount < positiveTarget) { // Sample positive.
			positiveCount++;
			int label = nonsingletonClusters[random.nextInt(nonsingletonClusters.length)];

			int[] instances = clustering.getIndicesWithLabel(label);
			int[] subcluster = sampleFromArray(instances, random, 2);
			int[] cluster1 = new int[]{subcluster[random.nextInt(subcluster.length)]}; // Singleton.
			int[] cluster2 = new int[subcluster.length - 1]; 
			int nadded = 0;
			for (int i = 0; i < subcluster.length; i++)
				if (subcluster[i] != cluster1[0])
					cluster2[nadded++] = subcluster[i];
			
			neighbor = new AgglomerativeNeighbor(clustering,
																					 clustering,
																					 cluster1,
																					 cluster2);			
		} else { // Sample negative.
			int labeli = random.nextInt(clustering.getNumClusters());
			int labelj = random.nextInt(clustering.getNumClusters());
			while (labeli == labelj)
				labelj = random.nextInt(clustering.getNumClusters());

			int[] ii = sampleFromArray(clustering.getIndicesWithLabel(labeli), random, 1);
			int[] ij = sampleFromArray(clustering.getIndicesWithLabel(labelj), random, 1);

			neighbor =
				new AgglomerativeNeighbor(clustering,
																	ClusterUtils.copyAndMergeClusters(clustering,
																																		labeli,
																																		labelj),
																	ii,
																	new int[]{ij[random.nextInt(ij.length)]});						
		}
		totalCount++;
		return new Instance(neighbor, null, null, null);
	}
}
