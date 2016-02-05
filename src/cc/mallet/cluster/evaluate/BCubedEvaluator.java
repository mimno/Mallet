package cc.mallet.cluster.evaluate;

import cc.mallet.cluster.Clustering;
import cc.mallet.types.InstanceList;

/**
 * Evaluate a Clustering using the B-Cubed evaluation metric. See
 * Bagga & Baldwin, "Algorithms for scoring coreference chains."
 *
 * Unlike other metrics, this evaluation awards points to correct
 * singleton clusters.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see ClusteringEvaluator
 */
public class BCubedEvaluator extends ClusteringEvaluator {

	double macroPrecision;
	double macroRecall;
	int macroNumInstances;

	public BCubedEvaluator () {
		macroPrecision = macroRecall = 0.0;
		macroNumInstances = 0;
	}
	
	public String evaluate (Clustering truth, Clustering predicted) {
		double[] vals = getEvaluationScores(truth, predicted);
		return "pr=" + vals[0] + " re=" + vals[1] + " f1=" + vals[2];
	}

	public String evaluateTotals () {
		double pr = macroPrecision / macroNumInstances;
		double re = macroRecall / macroNumInstances;
		double f1 = (2 * pr * re) / (pr + re);
		return "pr=" + pr + " re=" + re + " f1=" + f1;
	}

	@Override
	public double[] getEvaluationScores(Clustering truth, Clustering predicted) {
		double precision = 0.0;
		double recall = 0.0;

		InstanceList instances = truth.getInstances();

		for (int i = 0; i < instances.size(); i++) {
			int trueLabel = truth.getLabel(i);
			int predLabel = predicted.getLabel(i);
			int[] trueIndices = truth.getIndicesWithLabel(trueLabel);
			int[] predIndices = predicted.getIndicesWithLabel(predLabel);

			int correct = 0;
			for (int j = 0; j < predIndices.length; j++) {
				for (int k = 0; k < trueIndices.length; k++)
					if (trueIndices[k] == predIndices[j])
						correct++;
			}			
			precision += (double)correct / predIndices.length;
			recall += (double)correct / trueIndices.length;		
		}

		macroPrecision += precision;
		macroRecall += recall;
		macroNumInstances += instances.size();

		precision /= instances.size();
		recall /= instances.size();
		return new double[]{precision, recall, (2 * precision * recall / (precision + recall))};
	}	
}
