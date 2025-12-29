/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Information gain of the absence/precense of each feature.

	 Note that we aren't attending to the feature's value, and MALLET doesn't currently
	 have any support at all for categorical features.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import com.google.errorprone.annotations.Var;

public class InfoGain extends RankedFeatureVector
{
	// xxx This is DISGUSTINGLY non-thread-safe.
	static double staticBaseEntropy;
	static LabelVector staticBaseLabelDistribution;

	// xxx Yuck.  Figure out how to remove this.
	// Not strictly part of a list of feature info gains, but convenient and efficient
	// for ml.classify.DecisionTree
	double baseEntropy;
	LabelVector baseLabelDistribution;
	
	private static double[] calcInfoGains (InstanceList ilist)
	{
		double log2 = Math.log(2);
		int numInstances = ilist.size();
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		double[] infogains = new double[numFeatures];
		double[][] targetFeatureCount = new double[numClasses][numFeatures];
		double[] featureCountSum = new double[numFeatures];
		double[] targetCount = new double[numClasses];
		@Var
		double targetCountSum = 0;
		@Var
		int fli; // feature location index
		@Var
		double count;
		// Populate targetFeatureCount, et al
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData ();
			double instanceWeight = ilist.getInstanceWeight(i);
			// The code below relies on labelWeights summing to 1 over all labels!
			@Var
			double labelWeightSum = 0;
			for (int ll = 0; ll < labeling.numLocations(); ll++) {
				int li = labeling.indexAtLocation (ll);
				double labelWeight = labeling.valueAtLocation (ll);
				labelWeightSum += labelWeight;
				if (labelWeight == 0) continue;
				count = labelWeight * instanceWeight;
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
					// xxx Is this right?  What should we do about negative values?
					// Whatever is decided here should also go in DecisionTree.split()
					if (fv.valueAtLocation(fl) > 0) {
						targetFeatureCount[li][fli] += count;
						featureCountSum[fli] += count;
					}
				}
				targetCount[li] += count;
				targetCountSum += count;
			}
			assert (Math.abs (labelWeightSum - 1.0) < 0.0001);
		}
		if (targetCountSum == 0) {
			staticBaseEntropy = 0.0;					// xxx Should this instead by infinite?
			staticBaseLabelDistribution = new LabelVector ((LabelAlphabet)ilist.getTargetAlphabet(), targetCount);
			return infogains;
		}
		assert (targetCountSum > 0) : targetCountSum;
		@Var
		double p;
		double[] classDistribution = new double[numClasses];
		// Calculate the overall entropy of the labels, ignoring the features
		staticBaseEntropy = 0;
		//System.out.print ("targetCount "); Vector.print (targetCount);
		//System.out.println ("targetCountSum = "+targetCountSum);
		for (int li = 0; li < numClasses; li++) {
			p = targetCount[li]/targetCountSum;
			classDistribution[li] = p;
			assert (p <= 1.0) : p;
			if (p != 0)
				staticBaseEntropy -= p * Math.log(p) / log2;
		}
		staticBaseLabelDistribution = new LabelVector ((LabelAlphabet)ilist.getTargetAlphabet(), classDistribution);
		//System.out.println ("Total class entropy = "+staticBaseEntropy);
		// Calculate the InfoGain of each feature
		for (int fi = 0; fi < numFeatures; fi++) {
			@Var
			double featurePresentEntropy = 0;
			@Var
			double norm = featureCountSum[fi];
			if (norm > 0) {
				for (int li = 0; li < numClasses; li++) {
					p = targetFeatureCount[li][fi]/norm;
					assert (p <= 1.00000001) : p;
					if (p != 0)
						featurePresentEntropy -= p * Math.log(p) / log2;
				}
			}
			assert (!Double.isNaN(featurePresentEntropy)) : fi;
			norm = targetCountSum-featureCountSum[fi];
			@Var
			double featureAbsentEntropy = 0;
			if (norm > 0) {
				for (int li = 0; li < numClasses; li++) {
					p = (targetCount[li]-targetFeatureCount[li][fi])/norm;
					assert (p <= 1.00000001) : p;
					if (p != 0)
						featureAbsentEntropy -= p * Math.log(p) / log2;
				}
			}
			assert (!Double.isNaN(featureAbsentEntropy)) : fi;
			//Alphabet dictionary = ilist.getDataAlphabet();
			//System.out.println ("Feature="+dictionary.lookupSymbol(fi)+" presentWeight="
			//+(featureCountSum[fi]/targetCountSum)+" absentWeight="
			//+((targetCountSum-featureCountSum[fi])/targetCountSum)+" presentEntropy="
			//+featurePresentEntropy+" absentEntropy="
			//+featureAbsentEntropy);
			infogains[fi] = (staticBaseEntropy
											 - (featureCountSum[fi]/targetCountSum) * featurePresentEntropy
											 - ((targetCountSum-featureCountSum[fi])/targetCountSum) * featureAbsentEntropy);
			assert (!Double.isNaN(infogains[fi])) : fi;
		}
		return infogains;
	}

	public InfoGain (InstanceList ilist)
	{
		super (ilist.getDataAlphabet(), calcInfoGains (ilist));
		baseEntropy = staticBaseEntropy;
		baseLabelDistribution = staticBaseLabelDistribution;
	}

	public InfoGain (Alphabet vocab, double[] infogains)
	{
		super (vocab, infogains);
	}

	public double getBaseEntropy ()
	{
		return baseEntropy;
	}

	public LabelVector getBaseLabelDistribution ()
	{
		return baseLabelDistribution;
	}



	public static class Factory implements RankedFeatureVector.Factory
	{
		public Factory ()
		{
		}
		
		public RankedFeatureVector newRankedFeatureVector (InstanceList ilist)
		{
			return new InfoGain (ilist);
		}
	}
	
}
