package cc.mallet.cluster.util;

import cc.mallet.cluster.Clustering;
import cc.mallet.pipe.Noop;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Utility functions for Clusterings.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see Clustering
 */
public class ClusterUtils {
	
	/**
	 * @param li
	 * @param lj
	 * @return A new {@link InstanceList} where <code>lj</code> is appended to <code>li</code>.
	 */
	public static InstanceList combineLists (InstanceList li,
																					 InstanceList lj) {
		InstanceList newList = new InstanceList(li.getPipe());
		for (int i = 0; i < li.size(); i++) 
			newList.add(li.get(i));
		for (int i = 0; i < lj.size(); i++) 
			newList.add(lj.get(i));
		return newList;
	}

	/**
	 * Relabels the clustering to reflect merging clusters i and
	 * j. Relabels all of Instances with label j to label i.
	 * @param clustering
	 * @param i
	 * @param j
	 * @return Modified Clustering.
	 */
	public static Clustering mergeClusters (Clustering clustering,
																					int labeli, int labelj) {
		if (labeli == labelj)
			return clustering;
		
		// Set all labelj labels to labeli.
		InstanceList instances = clustering.getInstances();		
		for (int i = 0; i < instances.size(); i++) {
			int idx = clustering.getLabel(i);
			if (idx == labelj)
				clustering.setLabel(i, labeli);
		}
		clustering.setNumLabels(clustering.getNumClusters() - 1);

		// Decrement cluster indices that are greater than the number of clusters.
		for (int i = 0; i < instances.size(); i++) {
			int idx = clustering.getLabel(i);
			if (idx > labelj)
				clustering.setLabel(i, idx - 1);
		}
		
		return clustering;
	}
	
	/**
	 * Merge clusters containing the specified instances.
	 * @param clustering
	 * @param instances
	 * @return Modified Clustering.
	 */
	public static Clustering mergeInstances (Clustering clustering,
																					 int[] instances) {
		for (int i = 0; i < instances.length; i++) {
			for (int j = i + 1; j < instances.length; j++) {
				int labeli = clustering.getLabel(instances[i]);
				int labelj = clustering.getLabel(instances[j]);
				clustering = mergeClusters(clustering, labeli, labelj);
			}
		}		
		return clustering;
	}

	public static int[] getCombinedInstances (Clustering clustering, int i, int j) {
		int[] ci = clustering.getIndicesWithLabel(i);
		int[] cj = clustering.getIndicesWithLabel(j);
		int[] merged = new int[ci.length + cj.length];
		System.arraycopy(ci, 0, merged, 0, ci.length);
		System.arraycopy(cj, 0, merged, ci.length, cj.length);
		return merged;
	}
	
	public static Clustering mergeInstances (Clustering clustering,
																					 int i, int j) {
		return mergeInstances(clustering, new int[]{i, j});
	}

	/**
	 * Initializes Clustering to one Instance per cluster.
	 * @param instances
	 * @return Singleton Clustering.
	 */
	public static Clustering createSingletonClustering (InstanceList instances) {
		int[] labels = new int[instances.size()];
		for (int i = 0; i < labels.length; i++)
			labels[i] = i;
 		return new Clustering(instances,
													labels.length,
													labels);
	}

	public static Clustering createRandomClustering (InstanceList instances,
																									 Randoms random) {
		Clustering clustering = createSingletonClustering(instances);
		int numMerges = 2 + random.nextInt(instances.size() - 2);
		for (int i = 0; i < numMerges; i++)
			clustering = mergeInstances(clustering,
																	random.nextInt(instances.size()),
																	random.nextInt(instances.size()));
		return clustering;		
	}

	/**
	 *
	 * @param clustering
	 * @param indices
	 * @return A Clustering where no Instances in <code>indices</code>
	 * are in the same cluster.
	 */
	public static Clustering shatterInstances (Clustering clustering, int[] indices) {
		for (int i = 0; i < indices.length - 1; i++) {
			clustering.setLabel(indices[i], clustering.getNumClusters());
			clustering.setNumLabels(clustering.getNumClusters() + 1);			
		}
		return clustering;
	}
	
	/**
	 *
	 * @param i
	 * @param j
	 * @return A new {@link InstanceList} containing the two argument {@link Instance}s.
	 */
	public static InstanceList makeList (Instance i, Instance j) {
		InstanceList list = new InstanceList(new Noop(i.getDataAlphabet(), i.getTargetAlphabet()));
		list.add(i);
		list.add(j);
		return list;
	}

	/**
	 * @param clustering 
	 * @return A shallow copy of the argument where new objects are only
	 * allocated for the cluster assignment.
	 */
	public static Clustering copyWithNewLabels (Clustering clustering) {
		int[] oldLabels = clustering.getLabels();
		int[] newLabels = new int[oldLabels.length];
		System.arraycopy(oldLabels, 0, newLabels, 0, oldLabels.length);
		return new Clustering(clustering.getInstances(),
													clustering.getNumClusters(),
													newLabels);
	}

	/**
	 *
	 * @param clustering
	 * @param i
	 * @param j
	 * @return A new copy of <code>clustering</code> in which clusters
	 * with labels <code>i</code> and <code>j</code> have been merged.
	 */
	public static Clustering copyAndMergeClusters (Clustering clustering, int i, int j) {
 		return mergeClusters(copyWithNewLabels(clustering), i, j);
	}

	/**
	 *
	 * @param clustering
	 * @param i
	 * @param j
	 * @return A new copy of <code>clustering</code> in which {@link
	 * Instance}s <code>i</code> and <code>j</code> have been put in the
	 * same cluster.
	 */
	public static Clustering copyAndMergeInstances (Clustering clustering, int i, int j) {
 		return copyAndMergeInstances(clustering, new int[]{i, j});
	}

	/**
	 *
	 * @param clustering
	 * @param instances
	 * @return A new copy of <code>clustering</code> in which the
	 * clusters containing the specified {@link Instance}s have been
	 * merged together into one cluster.
	 */
	public static Clustering copyAndMergeInstances (Clustering clustering, int[] instances) {
 		return mergeInstances(copyWithNewLabels(clustering), instances);		
	}

}
