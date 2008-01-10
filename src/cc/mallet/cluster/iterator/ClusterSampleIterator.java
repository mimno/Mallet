package cc.mallet.cluster.iterator;


import java.util.ArrayList;
import java.util.Iterator;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Sample clusters of Instances.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see PairSampleIterator, NeighborIterator
 */
public class ClusterSampleIterator extends PairSampleIterator {
	
	/**
	 *
	 * @param clustering True clustering.
	 * @param random Source of randomness.
	 * @param positiveProportion Proportion of Instances that should be positive examples.
	 * @param numberSamples Total number of samples to generate.
	 * @return
	 */
	public ClusterSampleIterator (Clustering clustering,
																Randoms random,
																double positiveProportion,
																int numberSamples) {
		super(clustering, random, positiveProportion, numberSamples);
	}
	
	public Instance next () {
		AgglomerativeNeighbor neighbor = null;
		
		if ((positiveCount < positiveTarget  || clustering.getNumClusters() == 1) && nonsingletonClusters.length > 0) {
			positiveCount++;
			int label = nonsingletonClusters[random.nextInt(nonsingletonClusters.length)];

			int[] instances = clustering.getIndicesWithLabel(label);
			int[][] clusters = sampleSplitFromArray(instances, random, 2);
			neighbor = new AgglomerativeNeighbor(clustering,
																					 clustering,
																					 clusters);			
		} else {
			int labeli = random.nextInt(clustering.getNumClusters());
			int labelj = random.nextInt(clustering.getNumClusters());
			while (labeli == labelj)
				labelj = random.nextInt(clustering.getNumClusters());
			neighbor =
				new AgglomerativeNeighbor(clustering,
																	ClusterUtils.copyAndMergeClusters(clustering,	labeli, labelj),
																	sampleFromArray(clustering.getIndicesWithLabel(labeli), random, 1),
																	sampleFromArray(clustering.getIndicesWithLabel(labelj), random, 1));						
		}
		totalCount++;
		return new Instance(neighbor, null, null, null);
	}

	/**
	 * Samples a subset of elements from this array.
	 * @param a
	 * @param random
	 * @return
	 */
	protected int[] sampleFromArray (int[] a, Randoms random, int minSize) {
		// Sample size.
		int size = Math.max(random.nextInt(a.length) + 1, minSize);
		ArrayList toInclude = new ArrayList();
		for (int i = 0; i < a.length; i++)
			toInclude.add(new Integer(i));
		while (toInclude.size() > size && (size != a.length))
			toInclude.remove(random.nextInt(toInclude.size()));

		int[] ret = new int[toInclude.size()];
		int i = 0;
		for (Iterator iter = toInclude.iterator(); iter.hasNext(); )
			ret[i++] = a[((Integer)iter.next()).intValue()];
		
		return ret;
	}

	/**
	 * Samples a two disjoint subset of elements from this array.
	 * @param a
	 * @param random
	 * @return
	 */
	protected int[][] sampleSplitFromArray (int[] a, Randoms random, int minSize) {
		// Sample size.
		int size = Math.max(random.nextInt(a.length) + 1, minSize);
		ArrayList toInclude = new ArrayList();
		for (int i = 0; i < a.length; i++)
			toInclude.add(new Integer(i));
		while (toInclude.size() > size && (size != a.length))
			toInclude.remove(random.nextInt(toInclude.size()));

		int[][] ret = new int[2][];
		int size1 = Math.max(random.nextInt(toInclude.size() - 1), 1);
		ret[0] = new int[size1];
		ret[1] = new int[toInclude.size() - size1];
		for (int i = 0; i < size1; i++)
			ret[0][i] = ((Integer)toInclude.get(i)).intValue();
		int nadded = 0;
		for (int i = size1; i < toInclude.size(); i++)
			ret[1][nadded++] = ((Integer)toInclude.get(i)).intValue();
		return ret;
	}
}
