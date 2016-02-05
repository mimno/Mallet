/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 The number of instances in which each feature occurs.

	 Note that we aren't attending to the feature's value, and MALLET doesn't currently
	 have any support at all for categorical features.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

public class FeatureCounts extends RankedFeatureVector
{
	// increment by 1 for each instance that has the feature, ignoring the feature's value
	static boolean countInstances = true;
	
	private static double[] calcFeatureCounts (InstanceList ilist)
	{
		int numInstances = ilist.size();
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		double[] counts = new double[numFeatures];
		double count;
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			if (!(inst.getData() instanceof FeatureVector))
				throw new IllegalArgumentException ("Currently only handles FeatureVector data");
			FeatureVector fv = (FeatureVector) inst.getData ();
			if (ilist.getInstanceWeight(i) == 0)
				continue;
			for (int j = 0; j < fv.numLocations(); j++) {
				if (countInstances)
					counts[fv.indexAtLocation(j)] += 1;
				else
					counts[fv.indexAtLocation(j)] += fv.valueAtLocation(j);
			}					
		}
		return counts;
	}

	public FeatureCounts (InstanceList ilist)
	{
		super (ilist.getDataAlphabet(), calcFeatureCounts (ilist));
	}

	public FeatureCounts (Alphabet vocab, double[] counts)
	{
		super (vocab, counts);
	}

	public static class Factory implements RankedFeatureVector.Factory
	{
		public Factory ()
		{
		}
		
		public RankedFeatureVector newRankedFeatureVector (InstanceList ilist)
		{
			return new FeatureCounts (ilist);
		}
	}
	
}
