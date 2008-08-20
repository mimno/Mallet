/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Convert a String containing space-separated feature-name floating-point-value pairs
	 into a FeatureVector.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.FeatureVector;

public class FeatureValueString2FeatureVector extends Pipe implements Serializable
{
	public FeatureValueString2FeatureVector (Alphabet dataDict)
	{
		super (dataDict, null);
	}

	public FeatureValueString2FeatureVector ()
	{
		super(new Alphabet(), null);
	}
	
	public Instance pipe (Instance carrier)
	{
		String[] fields = carrier.getData().toString().split("\\s+");
		if (fields.length % 2 != 0) {
			throw new IllegalArgumentException("Input data must consist of an even number of feature and value pairs");
		}

		int numFields = fields.length / 2;
		
		Object[] featureNames = new Object[numFields];
		double[] featureValues = new double[numFields];

		for (int i = 0; i < numFields; i++) {
			featureNames[i] = fields[2 * i];
			featureValues[i] = Double.parseDouble(fields[(2 * i) + 1]);
		}

		carrier.setData(new FeatureVector(getDataAlphabet(), featureNames, featureValues));
		
		return carrier;
	}
	
}

