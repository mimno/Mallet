/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** Minkowski Metric, also known as the L_p norm. Special cases include the 
 *  Manhatten city-block distance with q=1 and the Euclidean distance with
 *  q=2. The special case with q equal to positive infinity is supported.
 *
 *  @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 */

package cc.mallet.types;

import cc.mallet.types.SparseVector;

public class Minkowski implements Metric {

    double q;
	double oneOverQ;

    /** Constructor for Minkowski metric.
     *
     * @param q Power of component wise absolute difference; must be at least 1
     */
    public Minkowski( double q ) {
		this.q = q;
		this.oneOverQ = 1.0 / q;
    }

	public static Metric getMetric(double q) {
		if (q == 1.0) { return new ManhattenDistance(); }
		else if (q == 2.0) { return new EuclideanDistance(); }
		else if (q == Double.POSITIVE_INFINITY) { return new InfiniteDistance(); }
		else {
			return new Minkowski(q);
		}
	}
    
    /**  Gives the Minkowski distance between two vectors.
     *
     *  distance(x,y) := \left( \Sum_i=0^d-1 \left| x_i - y_i \right|^q \right)^\frac{1}{q}
     */
    public double distance( SparseVector a, SparseVector b) {
		double dist = 0;
		double diff;
		
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

			dist += Math.pow(diff, q);
		}

		// Pick up any additional features at the end of the two lists.
		while (leftIndex < leftLength) {
			diff = Math.abs(a.valueAtLocation(leftIndex));
			dist += Math.pow(diff, q);
			leftIndex++;
		}

		while (rightIndex < rightLength) {
			diff = Math.abs(b.valueAtLocation(rightIndex));
			dist += Math.pow(diff, q);
			rightIndex++;
		}

		return Math.pow(dist, oneOverQ);
	}
}
