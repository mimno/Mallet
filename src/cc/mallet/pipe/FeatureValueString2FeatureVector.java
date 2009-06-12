/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Convert a String containing space-separated feature-name floating-point-value pairs
	 into a FeatureVector. For example:
     <pre>length=12  width=1.75  blue  temperature=-17.2</pre>
	 Features without a corresponding value (ie those not including the character "=",
	 such as the feature <code>blue</code> here) will be set to 1.0.

	 <p>If a feature occurs more than once in the input string, the values of each 
	 occurrence will be added.</p>

   @author David Mimno and Andrew McCallum
 */

package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.FeatureVector;

public class FeatureValueString2FeatureVector extends Pipe implements Serializable {

	public FeatureValueString2FeatureVector (Alphabet dataDict) {
		super (dataDict, null);
	}

	public FeatureValueString2FeatureVector () {
		super(new Alphabet(), null);
	}
	
	public Instance pipe (Instance carrier) {

		String[] fields = carrier.getData().toString().split("\\s+");

		int numFields = fields.length;
		
		Object[] featureNames = new Object[numFields];
		double[] featureValues = new double[numFields];

		for (int i = 0; i < numFields; i++) {
			if (fields[i].contains("=")) {
				String[] subFields = fields[i].split("=");
				featureNames[i] = subFields[0];
				featureValues[i] = Double.parseDouble(subFields[1]);
			}
			else {
				featureNames[i] = fields[i];
				featureValues[i] = 1.0;
			}
		}

		carrier.setData(new FeatureVector(getDataAlphabet(), featureNames, featureValues));
		
		return carrier;
	}
	
}

