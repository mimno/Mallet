/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Sparse, yet its (present) values can be changed.  You can't, however, add
	 values that were (zero and) missing.
	 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.logging.*;
import java.io.*;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Vector;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.PropertyList;
import gnu.trove.map.hash.TIntIntHashMap;

public class HashedSparseVector extends SparseVector implements Serializable 
{
	private static Logger logger = MalletLogger.getLogger(SparseVector.class.getName());

	
	TIntIntHashMap index2location;
	int maxIndex;
	
	public HashedSparseVector (int[] indices, double[] values, 
											 int capacity, int size,
											 boolean copy,
											 boolean checkIndicesSorted,
											 boolean removeDuplicates)
	{
		super (indices, values, capacity, size, copy, checkIndicesSorted, removeDuplicates);
		assert (indices != null);
	}

	/** Create an empty vector */
	public HashedSparseVector ()
	{
		super (new int[0], new double[0], 0, 0, false, false, false);
	}

	/** Create non-binary vector, possibly dense if "featureIndices" or possibly sparse, if not */
	public HashedSparseVector (int[] featureIndices,
											 double[] values)
	{
		super (featureIndices, values);
	}

	/** Create binary vector */
	public HashedSparseVector (int[] featureIndices)
	{
		super (featureIndices);
	}

	// xxx We need to implement this in FeatureVector subclasses
	public ConstantMatrix cloneMatrix ()
	{
		return new HashedSparseVector (indices, values);
	}

	public ConstantMatrix cloneMatrixZeroed () {
		assert (values != null);
		int[] newIndices = new int[indices.length];
		System.arraycopy (indices, 0, newIndices, 0, indices.length);
		HashedSparseVector sv = new HashedSparseVector (newIndices, new double[values.length],
														 values.length, values.length, false, false, false);
    // share index2location trick ala IndexedSparseVector
    if (index2location != null) {
      sv.index2location = index2location;
      sv.maxIndex = maxIndex;
    }
    return sv;
	}
	
	// Methods that change values

  public void indexVector ()
  {
    if ((index2location == null) && (indices.length > 0))
      setIndex2Location ();
  }

	private void setIndex2Location ()
	{
		//System.out.println ("HashedSparseVector setIndex2Location indices.length="+indices.length+" maxindex="+indices[indices.length-1]);
		assert (index2location == null);
		assert (indices.length > 0);
		this.maxIndex = indices[indices.length - 1];
		this.index2location = new TIntIntHashMap (numLocations ());
		//index2location.setDefaultValue (-1);
		for (int i = 0; i < indices.length; i++)
			index2location.put (indices[i], i);
	}

	public final void setValue (int index, double value) {
		if (index2location == null)
			setIndex2Location ();
		int location = index2location.get(index);
		if (index2location.contains (index))
			values[location] = value;
		else
			throw new IllegalArgumentException ("Trying to set value that isn't present in HashedSparseVector");
	}

	public final void setValueAtLocation (int location, double value)
	{
		values[location] = value;
	}

	// I dislike this name, but it's consistent with DenseVector. -cas
	public void columnPlusEquals (int index, double value) {
		if (index2location == null)
			setIndex2Location ();
		int location = index2location.get(index);
		if (index2location.contains (index))
			values[location] += value;
		else
			throw new IllegalArgumentException ("Trying to set value that isn't present in HashedSparseVector");
	}
		
	public final double dotProduct (DenseVector v) {
		double ret = 0;
		if (values == null)
			for (int i = 0; i < indices.length; i++)
				ret += v.value(indices[i]);
		else
			for (int i = 0; i < indices.length; i++)
				ret += values[i] * v.value(indices[i]);
		return ret;
	}
		
    public final double dotProduct (SparseVector v)
    {
	if (indices.length == 0)
	    return 0;
	if (index2location == null)
	    setIndex2Location ();
	double ret = 0;
	int vNumLocs = v.numLocations();
	if (values == null) {
	    // this vector is binary
	    for (int i = 0; i < vNumLocs; i++) {
		int index = v.indexAtLocation(i);
		if (index > maxIndex)
		    break;
		if (index2location.contains(index))
		    ret += v.valueAtLocation (i);
	    }
	} else {
	    for (int i = 0; i < vNumLocs; i++) {
		int index = v.indexAtLocation(i);
		if (index > maxIndex)
		    break;
		
		if (index2location.containsKey(index)) {
		    ret += values[ index2location.get(index) ] * v.valueAtLocation (i);
		}
		
		
		//int location = index2location.get(index);
		//if (location >= 0)
		//	ret += values[location] * v.valueAtLocation (i);
	    }
	}
	return ret;
    }
	
    public final void plusEqualsSparse (SparseVector v, double factor)
    {
	if (indices.length == 0)
	    return;
	if (index2location == null)
	    setIndex2Location ();
	int vNumLocs = v.numLocations();
	for (int i = 0; i < vNumLocs; i++) {
	    int index = v.indexAtLocation(i);
	    if (index > maxIndex)
		break;
	    
	    if (index2location.containsKey(index)) {
		values[ index2location.get(index) ] += v.valueAtLocation (i) * factor;
	    }
	    
	    //int location = index2location.get(index);
	    //if (location >= 0)
	    //			values[location] += v.valueAtLocation (i) * factor;
	}
    }

	public final void plusEqualsSparse (SparseVector v)
	{
		if (indices.length == 0)
	    return;
		if (index2location == null)
	    setIndex2Location ();
		for (int i = 0; i < v.numLocations(); i++) {
	    int index = v.indexAtLocation(i);
	    if (index > maxIndex)
				break;
	    int location = index2location.get(index);
	    if (index2location.contains (index))
				values[location] += v.valueAtLocation (i);
		}
	}
	
	public final void setAll (double v)
	{
		for (int i = 0; i < values.length; i++)
			values[i] = v;
	}


	
	//Serialization

	private static final long serialVersionUID = 1;

  // Version history:
  //   0 == Wrote out index2location.  Probably a bad idea.
	private static final int CURRENT_SERIAL_VERSION = 1;
	static final int NULL_INTEGER = -1;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeInt (maxIndex);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
    maxIndex = in.readInt ();

    if (version == 0) {
      // gobble up index2location
      Object obj = in.readObject ();
      if (obj != null && !(obj instanceof TIntIntHashMap)) {
        throw new IOException ("Unexpected object in de-serialization: "+obj);
      }
    }

	}

}
