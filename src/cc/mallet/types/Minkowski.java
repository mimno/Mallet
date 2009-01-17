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


import java.lang.Math;

import cc.mallet.types.SparseVector;


public class Minkowski implements Metric {

    double q;

    /** Constructor for Minkowski metric.
     *
     * @param q Power of component wise absolute difference; must be at least 1
     */
    public Minkowski( double q )
    {
	if (q<1)
	    throw new IllegalArgumentException("Argument q must be at least 1.");
	
	//assert( q>= 1 );
	
	this.q = q;

    }
    
    /**  Gives the Minkowski distance between two vectors.
     *
     *  distance(x,y) := \left( \Sum_i=0^d-1 \left| x_i - y_i \right|^q \right)^\frac{1}{q}
     *  
     *  for 1<=q<infinity. For q=infinity
     *
     *  distance(x,y) := max_i \left| x_i - y_i \right|
     */
    public double distance( SparseVector a, SparseVector b)
    {

	double dist = 0;
	double diff;
	
	if (a==null || b==null)
	    throw new IllegalArgumentException("Distance from a null vector is undefined.");
	
	//assert (a != null);
	//assert (b != null);

	if (a.numLocations() != b.numLocations() )
	    throw new IllegalArgumentException("Vectors must be of the same dimension.");

	//assert (a.numLocations() == b.numLocations() );

	for (int i=0 ; i< a.numLocations() ; i++ )
	{
	    diff = Math.abs( a.valueAtLocation(i) - b.valueAtLocation(i));

	    if (q==1)
		dist += diff;
	    else if (q==2)
		dist += diff*diff;
	    else if (q==Double.POSITIVE_INFINITY)
		if ( diff > dist)
		    dist = diff;
	    else
		dist += Math.pow( diff, q );
	    
	}

	if (q==1 || q==Double.POSITIVE_INFINITY)
	    return dist;
	else if (q==2)
	    return Math.sqrt( dist );
	else
	    return Math.pow( dist, 1/q);
    }

	public double euclideanDistance(SparseVector a, SparseVector b)    {
		double dist = 0;
		double diff;
		
		if (a==null || b==null)
		    throw new IllegalArgumentException("Distance from a null vector is undefined.");
		int aLen = a.numLocations();
		int bLen = b.numLocations();
		int ia = 0;
		int ib = 0;
		int indicea, indiceb;
		while (ia < aLen && ib < bLen) {
			indicea = a.indexAtLocation(ia);
			indiceb = b.indexAtLocation(ib);
			if(indicea < indiceb) {
				diff = a.valueAtLocation(ia);
				ia ++;
			}
			else {
				if(indicea == indiceb) {
					diff = Math.abs(a.valueAtLocation(ia) - b.valueAtLocation(ib));
					ia ++;
					ib ++;
				}
				else
				{
					diff = b.valueAtLocation(ib);
					ib ++;
				}
			}
			dist += diff * diff;
		}
		while(ia < aLen) {
			diff = a.valueAtLocation(ia);
			dist += diff * diff;
		}
		while(ib < bLen) {
			diff = b.valueAtLocation(ib);
			dist += diff * diff;
		}
		dist = Math.sqrt(dist);
		return dist;
	}
}
