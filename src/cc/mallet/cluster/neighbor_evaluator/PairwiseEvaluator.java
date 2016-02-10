package cc.mallet.cluster.neighbor_evaluator;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

import cc.mallet.classify.Classifier;
import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.util.PairwiseMatrix;
import cc.mallet.types.MatrixOps;

/**
 * Uses a {@link Classifier} over pairs of {@link Instances} to score
 * {@link Neighbor}. Currently only supports {@link
 * AgglomerativeNeighbor}s.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see ClassifyingNeighborEvaluator
 */
public class PairwiseEvaluator extends ClassifyingNeighborEvaluator {

	private static final long serialVersionUID = 1L;

	/**
	 * How to combine a set of pairwise scores (e.g. mean, max, ...).
	 */
	CombiningStrategy combiningStrategy;

	/**
	 * If true, score all edges involved in a merge. If false, only
	 * score the edges that croess the boundaries of the clusters being
	 * merged.
	 */
	boolean mergeFirst;

	/**
	 * Cache for calls to getScore. In some experiments, reduced running
	 * time by nearly half.
	 */
	PairwiseMatrix scoreCache;
	
	/**
	 *
	 * @param classifier Classifier to assign scores to {@link
	 * Neighbor}s for which a pair of Instances has been merged.
	 * @param scoringLabel The predicted label that corresponds to a
	 * positive example (e.g. "YES").
	 * @param combiningStrategy How to combine the pairwise scores
	 * (e.g. max, mean, ...).
	 * @param mergeFirst If true, score all edges involved in a
	 * merge. If false, only score the edges that cross the boundaries
	 * of the clusters being merged.
	 * @return
	 */
	public PairwiseEvaluator (Classifier classifier,
														String scoringLabel,
														CombiningStrategy combiningStrategy,
														boolean mergeFirst) {
		super(classifier, scoringLabel);
		this.combiningStrategy = combiningStrategy;
		this.mergeFirst = mergeFirst;
	}

	public double[] evaluate (Neighbor[] neighbors) {
		double[] scores = new double[neighbors.length];
		for (int i = 0; i < neighbors.length; i++)
			scores[i] = evaluate(neighbors[i]);
		return scores;
	}
	
	public double evaluate (Neighbor neighbor) {
 		if (!(neighbor instanceof AgglomerativeNeighbor))
 			throw new IllegalArgumentException("Expect AgglomerativeNeighbor not " + neighbor.getClass().getName());
 		AgglomerativeNeighbor aneighbor = (AgglomerativeNeighbor) neighbor;

		Clustering original = neighbor.getOriginal();
//		int[] mergedIndices = ((AgglomerativeNeighbor)neighbor).getNewCluster();
		int[] cluster1 = aneighbor.getOldClusters()[0];
		int[] cluster2 = aneighbor.getOldClusters()[1];
		ArrayList<Double> scores = new ArrayList<Double>();

		for (int i = 0; i < cluster1.length; i++) // Between cluster scores.
			for (int j = 0; j < cluster2.length; j++) {
				AgglomerativeNeighbor pwneighbor =
					new AgglomerativeNeighbor(original,	original, cluster1[i], cluster2[j]);
				scores.add(new Double(getScore(pwneighbor)));
			}
		if (mergeFirst) { // Also add w/in cluster scores.
			for (int i = 0; i < cluster1.length; i++)
				for (int j = i + 1; j < cluster1.length; j++) {
					AgglomerativeNeighbor pwneighbor =
						new AgglomerativeNeighbor(original,	original, cluster1[i], cluster1[j]);
				scores.add(new Double(getScore(pwneighbor)));				
			}
			for (int i = 0; i < cluster2.length; i++)
				for (int j = i + 1; j < cluster2.length; j++) {
					AgglomerativeNeighbor pwneighbor =
						new AgglomerativeNeighbor(original,	original, cluster2[i], cluster2[j]);
				scores.add(new Double(getScore(pwneighbor)));				
			}				
		}
				
// XXX This breaks during training if original cluster does not agree with mergedIndices.		
// 		for (int i = 0; i < mergedIndices.length; i++) {
//			for (int j = i + 1; j < mergedIndices.length; j++) {
//				if ((original.getLabel(mergedIndices[i]) != original.getLabel(mergedIndices[j])) || mergeFirst) {
//					AgglomerativeNeighbor pwneighbor =
//						new AgglomerativeNeighbor(original,	original,
//																			mergedIndices[i], mergedIndices[j]);
//					scores.add(new Double(getScore(pwneighbor)));
//				}
//			}
//		}

		if (scores.size() < 1)
			throw new IllegalStateException("No pairs of Instances were scored.");
		
 		double[] vals = new double[scores.size()];
		for (int i = 0; i < vals.length; i++)
			vals[i] = ((Double)scores.get(i)).doubleValue();
 		return combiningStrategy.combine(vals);
	}

	public void reset () {
		scoreCache = null;
	}
	
	public String toString () {
		return "class=" + this.getClass().getName() +
			" classifier=" + classifier.getClass().getName();
	}

	private double getScore (AgglomerativeNeighbor pwneighbor) {
		if (scoreCache == null)
			scoreCache = new PairwiseMatrix(pwneighbor.getOriginal().getNumInstances());
		int[] indices = pwneighbor.getNewCluster();
		if (scoreCache.get(indices[0], indices[1]) == 0.0) {
			scoreCache.set(indices[0], indices[1],
								 classifier.classify(pwneighbor).getLabelVector().value(scoringLabel));
		}
		return scoreCache.get(indices[0], indices[1]);
	}

	/**
	 * Specifies how to combine a set of pairwise scores into a
	 * cluster-wise score.
	 *
	 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
	 * @version 1.0
	 * @since 1.0
	 */
	public static interface CombiningStrategy {
		public double combine (double[] scores);
	}

	public static class Average implements CombiningStrategy, Serializable {
		public double combine (double[] scores) {
			return MatrixOps.mean(scores);
		}		
		// SERIALIZATION

		private static final long serialVersionUID = 1;

		private static final int CURRENT_SERIAL_VERSION = 1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.defaultWriteObject();
			out.writeInt(CURRENT_SERIAL_VERSION);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			in.defaultReadObject();
			int version = in.readInt();
		}	
	}

	public static class Minimum implements CombiningStrategy, Serializable {
		public double combine (double[] scores) {
			return MatrixOps.min(scores);
		}		
		// SERIALIZATION

		private static final long serialVersionUID = 1;

		private static final int CURRENT_SERIAL_VERSION = 1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.defaultWriteObject();
			out.writeInt(CURRENT_SERIAL_VERSION);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			in.defaultReadObject();
			int version = in.readInt();
		}	
	}

	public static class Maximum implements CombiningStrategy, Serializable {
		public double combine (double[] scores) {
			return MatrixOps.max(scores);
		}		
		// SERIALIZATION

		private static final long serialVersionUID = 1;

		private static final int CURRENT_SERIAL_VERSION = 1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.defaultWriteObject();
			out.writeInt(CURRENT_SERIAL_VERSION);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			in.defaultReadObject();
			int version = in.readInt();
		}			
	}
}
