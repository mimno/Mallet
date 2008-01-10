/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** Just like RankedFeatureVector, only NaNs are allowed and are unranked.

   @author Jerod Weinman <a href="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</a>
 */


package cc.mallet.types;

import cc.mallet.types.Label;
import cc.mallet.types.RankedFeatureVector;


public class PartiallyRankedFeatureVector extends RankedFeatureVector
{

    private static final int SORTINIT = -1;
    int numRanked = -1;

    public PartiallyRankedFeatureVector (Alphabet dict, int[] indices, 
					 double[] values)
    {
	super (dict, indices, values);
    }
    
    public PartiallyRankedFeatureVector (Alphabet dict, double[] values)
    {
	super (dict, values);
    }
    
    public PartiallyRankedFeatureVector (Alphabet dict, DenseVector v)
    {
	this (dict, v.values);
    }
    
    public PartiallyRankedFeatureVector (Alphabet dict, 
					 AugmentableFeatureVector v)
    {
	super (dict, v );
    }
    
    public PartiallyRankedFeatureVector (Alphabet dict, SparseVector v)
    {
	super (dict, v );
    }

    public int numRanked () {

	if (numRanked == -1)
	{
	    numRanked = 0;
	    for (int i=0; i<values.length ; i++) {
		if (!Double.isNaN(values[i])) 
		    numRanked++;
	    }
	}

	return numRanked;
    }

    protected void setRankOrder ( int extent, boolean reset)
    {

	int sortExtent;
	// Set the number of cells to sort, making sure we don't go past the max
	// Sorting n-1 sorts the whole array.

	sortExtent = (extent >= values.length) ? values.length - 1: extent;
	
	if (sortExtent>=numRanked())
	    return;

	if (sortedTo == SORTINIT || reset) { // reinitialize and sort
	    this.rankOrder = new int[values.length];
	    for (int i = 0; i < rankOrder.length; i++) {
		rankOrder[i] = i;
		
	    }
	}

	// Selection sort
	double max, front, next;
	int maxIndex;
	
	for (int i = sortedTo+1 ; i<=sortExtent ; i++ ) {
	    
	    front = values[rankOrder[i]];
	    
	    if (Double.isNaN( front ) )
		max = Double.NEGATIVE_INFINITY;
	    else
		max = front;

	    maxIndex = i;

	    for (int j=sortedTo+1 ; j<rankOrder.length ; j++ ) {
		
		next = values[rankOrder[j]];

		if (!Double.isNaN(next) && next>max )
		{
		    max = next;
		    maxIndex = j;
		}
	    }
	    // swap
	    int r = rankOrder[maxIndex];
	    rankOrder[maxIndex] = rankOrder[i];
	    rankOrder[i] = r;
	    sortedTo = i;
	}
    }


    public interface Factory
    {
	public PartiallyRankedFeatureVector newPartiallyRankedFeatureVector 
	    (InstanceList ilist, LabelVector[] posteriors);
    }

    public interface PerLabelFactory
    {
	public PartiallyRankedFeatureVector[] newPartiallyRankedFeatureVectors 
	    (InstanceList ilist, LabelVector[] posteriors);
    }

}
