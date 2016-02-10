/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** Interface for a measure of distance between two <CODE>SparseVector</CODE>s
    @author Aron Culotta <A HREF="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</A>
*/

package cc.mallet.types;

import java.util.HashMap;

import cc.mallet.types.SparseVector;

/**
	 Computes
	 1 - [<x,y> / sqrt (<x,x>*<y,y>)]
	 aka 1 - cosine similarity
 */

public class NormalizedDotProductMetric implements CachedMetric {

	HashMap hash; // stores the self dot-products used for normalization
	
	public NormalizedDotProductMetric () {
		this.hash = new HashMap ();
	}
	
	public double distance (SparseVector a, SparseVector b) {
	    //		double ret = a.dotProduct (b) /
	    //								 Math.sqrt (a.dotProduct (a) * b.dotProduct (b));
	    // gmann : twoNorm() more efficient than a.dotProduct(a)
	    double ret = a.dotProduct(b) / (a.twoNorm()*b.twoNorm());
	    return 1.0 - ret;
	}

	public double distance( SparseVector a, int hashCodeA,
													SparseVector b, int hashCodeB) {		
		Double cachedA = (Double) hash.get (new Integer (hashCodeA)); 
		Double cachedB = (Double) hash.get (new Integer (hashCodeB));
		if (a == null || b == null)
			return 1.0;
		if (cachedA == null) {
			cachedA = new Double (a.dotProduct (a));
 			hash.put (new Integer (hashCodeA), cachedA);
		}
		if (cachedB == null) {
			cachedB = new Double (b.dotProduct (b));
			hash.put (new Integer (hashCodeB), cachedB);
		}
		double ab = a.dotProduct (b);
		
		if (cachedA == null || cachedB == null) {
			throw new IllegalStateException ("cachedValues null");
		}
	 	double ret = a.dotProduct (b) / Math.sqrt (cachedA.doubleValue()*cachedB.doubleValue());
		return 1.0 - ret;
	}

}

