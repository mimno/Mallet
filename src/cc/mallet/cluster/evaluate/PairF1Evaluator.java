package cc.mallet.cluster.evaluate;

import cc.mallet.cluster.Clustering;

/**
 * Evaluates two clustering using pairwise comparisons. For each pair
 * of Instances, compute false positives and false negatives as in
 * classification performance, determined by whether the pair should
 * be in the same cluster or not.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see ClusteringEvaluator
 */
public class PairF1Evaluator extends ClusteringEvaluator {
	
	int tpTotal, fnTotal, fpTotal;
	
	public PairF1Evaluator () {
		tpTotal = fnTotal = fpTotal = 0;
	}

	public String evaluate (Clustering truth, Clustering predicted) {
		double[] vals = getEvaluationScores(truth, predicted);
		return "pr=" + vals[0] + " re=" + vals[1] + " f1=" + vals[2];
	}

	public String evaluateTotals () {
		double prTotal = (double)tpTotal / (tpTotal+fpTotal);
		double recTotal = (double)tpTotal / (tpTotal+fnTotal);
		double f1Total = 2*prTotal*recTotal/(prTotal+recTotal);
		return "pr=" + prTotal + " re=" + recTotal + " f1=" + f1Total;
	}

	@Override
	public double[] getEvaluationScores(Clustering truth, Clustering predicted) {
		int tp, fn, fp;
		tp = fn = fp = 0;
		
		for (int i = 0; i < predicted.getNumClusters(); i++) {
			int[] predIndices = predicted.getIndicesWithLabel(i);
			
			for (int j = 0; j < predIndices.length; j++) 
				for (int k = j + 1; k < predIndices.length; k++) 
					if (truth.getLabel(predIndices[j]) == truth.getLabel(predIndices[k]))
						tp++;
					else 
						fp++;
		}

		for (int i = 0; i < truth.getNumClusters(); i++) {
			int[] trueIndices = truth.getIndicesWithLabel(i);
			for (int j = 0; j < trueIndices.length; j++) 
				for (int k = j + 1; k < trueIndices.length; k++) 
					if (predicted.getLabel(trueIndices[j]) != predicted.getLabel(trueIndices[k]))
						fn++;
		}

		double pr = (double)tp / (tp+fp);
		double rec = (double)tp / (tp+fn);
		double f1 = 2*pr*rec/(pr+rec);
		this.tpTotal += tp;
		this.fpTotal += fp;
		this.fnTotal += fn;

		return new double[]{pr, rec, f1};
	}
}
