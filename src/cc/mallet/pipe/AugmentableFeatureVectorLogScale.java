/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import cc.mallet.types.Alphabet;
import cc.mallet.types.AugmentableFeatureVector;
import cc.mallet.types.Instance;

/** Given an AugmentableFeatureVector, set those values greater than
		or equal to 1 to log(value)+1.  This is useful when multiple
		counts should not be treated as independent evidence. */

public class AugmentableFeatureVectorLogScale extends Pipe
{
	public AugmentableFeatureVectorLogScale ()
	{
		super ((Alphabet)null, null);
	}

	public Instance pipe (Instance carrier)
	{
		AugmentableFeatureVector afv = (AugmentableFeatureVector)carrier.getData();
		double v;
		for (int i = afv.numLocations() - 1; i >= 0; i--) {
			v = afv.valueAtLocation (i);
			if (v >= 1)
				afv.setValueAtLocation (i, Math.log(v)+1);
		}
		return carrier;
	}

}
