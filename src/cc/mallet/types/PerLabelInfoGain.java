/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

public class PerLabelInfoGain
{
	final static float log2 = (float)Math.log(2);
	static boolean binary = true;
	static boolean print = false;

	InfoGain[] ig;

	public PerLabelInfoGain (InstanceList ilist)
	{
		double[][] pcig = calcPerLabelInfoGains (ilist);
		Alphabet v = ilist.getDataAlphabet();
		int numClasses = ilist.getTargetAlphabet().size();
		ig = new InfoGain[numClasses];
		for (int i = 0; i < numClasses; i++)
			ig[i] = new InfoGain (v, pcig[i]);
	}

	public InfoGain getInfoGain (int classIndex)
	{
		return ig[classIndex];
	}

	public int getNumClasses ()
	{
		return ig.length;
	}

	private static double entropy (double pc, double pnc)
	{
		assert (Math.abs((pc+pnc)-1) < 0.0001) : "pc="+pc+" pnc="+pnc;
		if (pc == 0 || pnc == 0)
			return (float) 0;
		else {
			float ret = (float) (- pc*Math.log(pc)/log2 - pnc*Math.log(pnc)/log2);
			assert (ret >= 0) : "pc="+pc+" pnc="+pnc;
			return ret;
		}
	}

	public static double[][] calcPerLabelInfoGains (InstanceList ilist)
	{
		assert (binary);
		double[][] classFeatureCounts;
		int[] featureCounts;
		int[] classCounts;
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		int numInstances = ilist.size();

		// Fill in the classFeatureCounts
		classFeatureCounts = new double[numClasses][numFeatures];
		featureCounts = new int[numFeatures];
		classCounts = new int[numClasses];
		/*
		for (int fi = 0; fi < numFeatures; fi++)
			featureCounts[fi] = 0;
		for (int ci = 0; ci < numClasses; ci++) {
			classCounts[ci] = 0;
			for (int fi = 0; fi < numFeatures; fi++)
				classFeatureCounts[ci][fi] = 0;
		}
		*/
		for (int i = 0; i < ilist.size(); i++) {
			Instance instance = ilist.get(i);
			FeatureVector fv = (FeatureVector) instance.getData();
			// xxx Note that this ignores uncertainly-labeled instances!
			int classIndex = instance.getLabeling().getBestIndex();
			classCounts[classIndex]++;
			for (int fvi = 0; fvi < fv.numLocations(); fvi++) {
				int featureIndex = fv.indexAtLocation(fvi);
				classFeatureCounts[classIndex][featureIndex]++;
				featureCounts[featureIndex]++;
				//System.out.println ("fi="+featureIndex+" ni="+numInstances+" fc="+featureCounts[featureIndex]+" i="+i);
				assert (featureCounts[featureIndex] <= numInstances)
					: "fi="+featureIndex+"ni="+numInstances+" fc="+featureCounts[featureIndex]+" i="+i;
			}
		}

		Alphabet v = ilist.getDataAlphabet();
		if (print)
			for (int ci = 0; ci < numClasses; ci++)
				System.out.println (ilist.getTargetAlphabet().lookupObject(ci).toString()+"="+ci);

		// Let C_i be a random variable on {c_i, !c_i}
		// per-class entropy of feature f_j = H(C_i|f_j)
		// H(C_i|f_j) = - P(c_i|f_j) log(P(c_i|f_j) - P(!c_i|f_j) log(P(!c_i|f_j)

		// First calculate the per-class entropy, not conditioned on any feature
		// and store it in classCounts[]
		double[] classEntropies = new double[numClasses];
		for (int ci = 0; ci < numClasses; ci++) {
			double pc, pnc;
			pc = ((double)classCounts[ci])/numInstances;
			pnc = ((double)numInstances-classCounts[ci])/numInstances;
			classEntropies[ci] = entropy (pc, pnc);
		}

		// Calculate per-class infogain of each feature, and store it in classFeatureCounts[]
		for (int fi = 0; fi < numFeatures; fi++) {
			double pf = ((double)featureCounts[fi])/numInstances;
			double pnf = ((double)numInstances-featureCounts[fi])/numInstances;
			assert (pf >= 0);
			assert (pnf >= 0);
			if (print && fi < 10000) {
				System.out.print (v.lookupObject(fi).toString());
				for (int ci = 0; ci < numClasses; ci++) {
					System.out.print (" "+classFeatureCounts[ci][fi]);
				}
				System.out.println ("");
			}
			//assert (sum == featureCounts[fi]);
			for (int ci = 0; ci < numClasses; ci++) {
				if (featureCounts[fi] == 0) {
					classFeatureCounts[ci][fi] = 0;
					continue;
				}
				double pc, pnc, ef;
				// Calculate the {ci,!ci}-entropy given that the feature does occur
				pc = ((double)classFeatureCounts[ci][fi]) / featureCounts[fi];
				pnc = ((double)featureCounts[fi]-classFeatureCounts[ci][fi]) / featureCounts[fi];
				ef = entropy (pc, pnc);
				// Calculate the {ci,!ci}-entropy given that the feature does not occur
				pc = ((double)classCounts[ci]-classFeatureCounts[ci][fi]) / (numInstances-featureCounts[fi]);
				pnc = ((double)(numInstances-featureCounts[fi])-(classCounts[ci]-classFeatureCounts[ci][fi])) / (numInstances-featureCounts[fi]);
				double enf = entropy(pc, pnc);
				classFeatureCounts[ci][fi] = classEntropies[ci] - (pf*ef + pnf*enf);
				if (print && fi < 10000)
					System.out.println ("pf="+pf+" ef="+ef+" pnf="+pnf+" enf="+enf+" e="+classEntropies[ci]+" cig="+classFeatureCounts[ci][fi]);
			}
		}

		// Print selected features
		if (print) {
			for (int fi = 0; fi < 100; fi++) {
				String featureName = v.lookupObject(fi).toString();
				for (int ci = 0; ci < numClasses; ci++) {
					String className = ilist.getTargetAlphabet().lookupObject(ci).toString();
					if (classFeatureCounts[ci][fi] > .1) {
						System.out.println (featureName+','+className+'='+classFeatureCounts[ci][fi]);
					}
				}
			}
		}
		return classFeatureCounts;
	}



	public static class Factory implements RankedFeatureVector.PerLabelFactory
	{
		public Factory ()
		{
		}
		
		public RankedFeatureVector[] newRankedFeatureVectors (InstanceList ilist)
		{
			PerLabelInfoGain x = new PerLabelInfoGain (ilist);
			return x.ig;
		}
	}
	
}
