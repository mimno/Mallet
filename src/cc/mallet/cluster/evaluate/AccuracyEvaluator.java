package cc.mallet.cluster.evaluate;

import cc.mallet.cluster.Clustering;

/**
 * Accuracy of a clustering is (truePositive + trueNegative) / (numberPairwiseComparisons)
 * @author culotta
 *
 */
public class AccuracyEvaluator extends ClusteringEvaluator {

	int correctTotal;
	int comparisonsTotal;
	
	public AccuracyEvaluator () {
		correctTotal = comparisonsTotal = 0;
	}

	public String evaluate (Clustering truth, Clustering predicted) {
		return "accuracy=" + String.valueOf(getEvaluationScores(truth, predicted)[0]);
	}

	public String evaluateTotals () {
		return ("accuracy=" + ((double)correctTotal / comparisonsTotal));
	}

	@Override
	public double[] getEvaluationScores(Clustering truth, Clustering predicted) {
		int correct = 0;
		int comparisons = 0;
		
		for (int i = 0; i < truth.getNumInstances(); i++)
			for (int j = i + 1; j < truth.getNumInstances(); j++) {
				if ((truth.getLabel(i) == truth.getLabel(j)) == 
					(predicted.getLabel(i) == predicted.getLabel(j)))
					correct++;
				comparisons++;
			}

		this.correctTotal += correct;
		this.comparisonsTotal += comparisons;

		return new double[]{(double)correct / comparisons};
	}

}
