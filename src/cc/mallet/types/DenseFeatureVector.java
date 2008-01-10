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

import java.util.logging.*;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.util.MalletLogger;

public class DenseFeatureVector extends DenseVector 
{
	private static Logger logger = MalletLogger.getLogger(DenseFeatureVector.class.getName());

	Alphabet dictionary;

	public DenseFeatureVector (Alphabet dict, double[] values)
	{
		super (values);
		this.dictionary = dict;
		assert (dict == null || dict.size() == values.length);
	}

	private static double[] denseValues (SparseVector sv, int numColumns) {
		double[] v = new double[numColumns];
		for (int i = sv.numLocations()-1; i >= 0; i--)
			v[sv.indexAtLocation(i)] = sv.valueAtLocation(i);
		return v;
	}
	
	public DenseFeatureVector (FeatureVector sfv, int numColumns)
	{
		super (denseValues(sfv, numColumns));
		this.dictionary = sfv.getAlphabet();
	}


	public Alphabet getAlphabet ()
	{
		return dictionary;
	}

	// xxx remove?
	private Object objectAtLocation (int loc)
	{
		return dictionary.lookupObject (loc);
	}

	public int location (Object o)
	{
		int i = dictionary.lookupIndex (o, false);
		if (i == -1)
			throw new IllegalArgumentException ("Object not in dictionary.");
		return location (i);
	}

	public boolean contains (Object o)
	{
		return (location (o) >= 0);
	}

	public double value (Object o)
	{
		int i = dictionary.lookupIndex (o, false);
		if (i == -1)
			throw new IllegalArgumentException ("Object not in dictionary.");
		return value (i);
	}
	
	private static final long serialVersionUID = 1;

}
