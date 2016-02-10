package cc.mallet.cluster.evaluate;


import java.util.HashSet;

import cc.mallet.cluster.Clustering;

/**
 * Evaluate a Clustering using the MUC evaluation metric. See Marc
 * Vilain, John Burger, John Aberdeen, Dennis Connolly, and Lynette
 * Hirschman. 1995. A model-theoretic coreference scoring scheme. In
 * Proceedings fo the 6th Message Understanding Conference
 * (MUC6). 45--52. Morgan Kaufmann.
 *
 * Note that MUC more or less ignores singleton clusters.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see ClusteringEvaluator
 */
public class MUCEvaluator extends ClusteringEvaluator {

	int precisionNumerator;
	int precisionDenominator;
	int recallNumerator;
	int recallDenominator;
	
	public MUCEvaluator () {
		precisionNumerator = precisionDenominator = recallNumerator = recallDenominator = 0;
	}
	
	public String evaluate (Clustering truth, Clustering predicted) {
		double[] vals = getEvaluationScores(truth, predicted);
		return "pr=" + vals[0] + " re=" + vals[1] + " f1=" + vals[2];
	}

	public String evaluateTotals () {
		double precision = (double)precisionNumerator / precisionDenominator;
		double recall = (double)recallNumerator / recallDenominator;
		return "pr=" + precision + " re=" + recall + " f1=" + (2 * precision * recall / (precision + recall));		
	}

	@Override
	public double[] getEvaluationScores(Clustering truth, Clustering predicted) {
		// Precision = \sum_i [ |siprime| - |pOfsiprime| ] / \sum_i [ |siprime| - 1 ]		
		// where siprime is a predicted cluster, pOfsiprime is the set of
		// true clusters that contain elements of siprime.
		int numerator = 0;
		int denominator = 0;
		for (int i = 0; i < predicted.getNumClusters(); i++) {
			int[] siprime = predicted.getIndicesWithLabel(i);
			HashSet<Integer> pOfsiprime = new HashSet<Integer>();
			for (int j = 0; j < siprime.length; j++) 
				pOfsiprime.add(truth.getLabel(siprime[j]));
			numerator += siprime.length - pOfsiprime.size();
			denominator += siprime.length - 1;
		}
		precisionNumerator += numerator;
		precisionDenominator += denominator;
		double precision = (double)numerator / denominator;

		// Recall = \sum_i [ |si| - |pOfsi| ] / \sum_i [ |si| - 1 ]		
		// where si is a true cluster, pOfsi is the set of predicted
		// clusters that contain elements of si.
		numerator = denominator = 0;
		for (int i = 0; i < truth.getNumClusters(); i++) {
			int[] si = truth.getIndicesWithLabel(i);
			HashSet<Integer> pOfsi = new HashSet<Integer>();
			for (int j = 0; j < si.length; j++) 
				pOfsi.add(new Integer(predicted.getLabel(si[j])));
			numerator += si.length - pOfsi.size();
			denominator += si.length - 1;
		}
		recallNumerator += numerator;
		recallDenominator += denominator;
		double recall = (double)numerator / denominator;
		return new double[]{precision,recall,(2 * precision * recall / (precision + recall))};
	}
}
