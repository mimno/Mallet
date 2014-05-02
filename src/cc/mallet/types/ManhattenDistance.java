/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/* Manhatten distance -- the sum of the absolute values of the differences at each dimension.
 *  @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 */

package cc.mallet.types;

import cc.mallet.types.SparseVector;

public class ManhattenDistance implements Metric {

	public double distance(SparseVector a, SparseVector b)    {
		double dist = 0.0;
		double diff = 0.0;
		
		if (a==null || b==null) {
		    throw new IllegalArgumentException("Distance from a null vector is undefined.");
		}

		int leftLength = a.numLocations();
		int rightLength = b.numLocations();
		int leftIndex = 0;
		int rightIndex = 0;
		int leftFeature, rightFeature;

		// We assume that features are sorted in ascending order.
		// We'll walk through the two feature lists in order, checking
		//  whether the two features are the same.

		while (leftIndex < leftLength && rightIndex < rightLength) {

			leftFeature = a.indexAtLocation(leftIndex);
			rightFeature = b.indexAtLocation(rightIndex);

			if (leftFeature < rightFeature) {
				diff = Math.abs(a.valueAtLocation(leftIndex));
				leftIndex ++;
			}
			else if (leftFeature == rightFeature) {
				diff = Math.abs(a.valueAtLocation(leftIndex) - b.valueAtLocation(rightIndex));
				leftIndex ++;
				rightIndex ++;
			}
			else {
				diff = Math.abs(b.valueAtLocation(rightIndex));
				rightIndex ++;
			}

			dist += diff;
		}

		// Pick up any additional features at the end of the two lists.
		while (leftIndex < leftLength) {
			diff = Math.abs(a.valueAtLocation(leftIndex));
			dist += diff;
			leftIndex++;
		}

		while (rightIndex < rightLength) {
			diff = Math.abs(b.valueAtLocation(rightIndex));
			dist += diff;
			rightIndex++;
		}

		return diff;
	}
}
