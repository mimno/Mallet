/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Simple, in-memory inverted index that stores a list of instances having each feature, but not
	 a value associated with each.  Currently only works with FeatureVectors.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.*;

public class InvertedIndex
{
	InstanceList ilist;
	ArrayList[] ii;

	public InvertedIndex (InstanceList ilist)
	{
		int numFeatures = ilist.getDataAlphabet().size();
		ii = new ArrayList[numFeatures];
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			if (!(inst.getData() instanceof FeatureVector))
				throw new IllegalArgumentException (this.getClass().getName() +
																						" currently only handles FeatureVector data");
			FeatureVector fv = (FeatureVector) inst.getData ();
			for (int fl = 0; fl < fv.numLocations(); fl++) {
				if (fv.valueAtLocation(fl) != 0)
					addEntry (fv.indexAtLocation(fl), inst);
			}
		}
	}

	private void addEntry (int featureIndex, Instance instance)
	{
		if (ii[featureIndex] == null)
			ii[featureIndex] = new ArrayList(2);
		ii[featureIndex].add (instance);
	}

	public InstanceList getInstanceList () { return ilist; }

	public ArrayList getInstancesWithFeature (int featureIndex)
	{
		return ii[featureIndex];
	}

	public ArrayList getInstancesWithFeature (Object feature)
	{
		int index = ilist.getDataAlphabet().lookupIndex (feature, false);
		if (index == -1)
			throw new IllegalArgumentException ("Feature "+feature+" not contained in InvertedIndex");
		return getInstancesWithFeature (index);
	}

	public int getCountWithFeature (int featureIndex)
	{
		ArrayList a = ii[featureIndex];
		return a == null ? 0 : a.size();
	}

	public int getCountWithFeature (Object feature)
	{
		int index = ilist.getDataAlphabet().lookupIndex (feature, false);
		if (index == -1)
			throw new IllegalArgumentException ("Feature "+feature+" not contained in InvertedIndex");
		ArrayList a = ii[index];
		return a == null ? 0 : a.size();
	}
	
}
