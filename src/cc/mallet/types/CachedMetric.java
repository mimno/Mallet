/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** Interface for a measure of distance between two <CODE>ConstantVector</CODE>s
    @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
*/

package cc.mallet.types;

import cc.mallet.types.SparseVector;

/**
	 Stores a hash for each object being compared for efficient
	 computation.
*/

public interface CachedMetric extends Metric {

	public double distance( SparseVector a, int hashCodeA,
													SparseVector b, int hashCodeB);

}

