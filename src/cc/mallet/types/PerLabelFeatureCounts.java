/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 The number of instances of each class in which each feature occurs.

	 Note that we aren't attending to the feature's value, and MALLET doesn't currently
	 have any support at all for categorical features.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import com.google.errorprone.annotations.Var;

public class PerLabelFeatureCounts
{
	Alphabet dataAlphabet, targetAlphabet;
	FeatureCounts[] fc;
	static boolean countInstances = true;
	
	/* xxx This should use memory more sparsely!!! */
	private static double[][] calcFeatureCounts (InstanceList ilist)
	{
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		double[][] featureCounts = new double[numClasses][numFeatures];

		// Count features across all classes
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			if (!(inst.getData() instanceof FeatureVector))
				throw new IllegalArgumentException ("Currently only handles FeatureVector data");
			FeatureVector fv = (FeatureVector) inst.getData ();
			// xxx Note that this ignores uncertain-labels.
			int labelIndex = inst.getLabeling ().getBestIndex();
			@Var
			int fli;
			for (int fl = 0; fl < fv.numLocations(); fl++) {
				fli = fv.indexAtLocation(fl);
				if (countInstances)
					featureCounts[labelIndex][fli]++;
				else
					featureCounts[labelIndex][fli] += fv.valueAtLocation(fl);
			}
		}
		return featureCounts;
	}
		
	public PerLabelFeatureCounts (InstanceList ilist)
	{
		dataAlphabet = ilist.getDataAlphabet();
		targetAlphabet = ilist.getTargetAlphabet();
		double[][] counts = calcFeatureCounts (ilist);
		fc = new FeatureCounts[targetAlphabet.size()];
		for (int i = 0; i < fc.length; i++)
			fc[i] = new FeatureCounts (dataAlphabet, counts[i]);
	}

	public static class Factory implements RankedFeatureVector.PerLabelFactory
	{
		public Factory ()
		{
		}
		
		public RankedFeatureVector[] newRankedFeatureVectors (InstanceList ilist)
		{
			PerLabelFeatureCounts x = new PerLabelFeatureCounts (ilist);
			return x.fc;
		}
	}
	
}
