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

public class IndexedSparseVector extends SparseVector implements Serializable 
{
	private static Logger logger = MalletLogger.getLogger(IndexedSparseVector.class.getName());

	
	transient private int[] index2location;

	public IndexedSparseVector (int[] indices, double[] values, 
											 int capacity, int size,
											 boolean copy,
											 boolean checkIndicesSorted,
											 boolean removeDuplicates)
	{
		super (indices, values, capacity, size, copy, checkIndicesSorted, removeDuplicates);
		assert (indices != null);
	}

	/** Create an empty vector */
	public IndexedSparseVector ()
	{
		super (new int[0], new double[0], 0, 0, false, false, false);
	}

	/** Create non-binary vector, possibly dense if "featureIndices" or possibly sparse, if not */
	public IndexedSparseVector (int[] featureIndices,
											 double[] values)
	{
		super (featureIndices, values);
	}

	/** Create binary vector */
	public IndexedSparseVector (int[] featureIndices)
	{
		super (featureIndices);
	}

	// xxx We need to implement this in FeatureVector subclasses
	public ConstantMatrix cloneMatrix ()
	{
		return new IndexedSparseVector (indices, values);
	}

	public ConstantMatrix cloneMatrixZeroed () {
		assert (values != null);
		int[] newIndices = new int[indices.length];
		System.arraycopy (indices, 0, newIndices, 0, indices.length);
		IndexedSparseVector sv = new IndexedSparseVector
														   (newIndices, new double[values.length],
																values.length, values.length, false, false, false);
		// Share the index2location array.  This will be unsafe if
		// IndexedSparseVectors are ever allowed to be modifiable, but I
		// don't think that this will be the case.
		if (index2location != null)
			sv.index2location = index2location;
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
		//System.out.println ("IndexedSparseVector setIndex2Location indices.length="+indices.length+" maxindex="+indices[indices.length-1]);
    assert (indices != null);
		assert (index2location == null);

    int size;
    if (indices.length == 0)
      size = 0;
    else size = indices[indices.length-1]+1;

		assert (size >= indices.length);
		this.index2location = new int[size];
		Arrays.fill (index2location, -1);
		for (int i = 0; i < indices.length; i++)
			index2location[indices[i]] = i;
	}

	public final void setValue (int index, double value) {
		if (index2location == null)
			setIndex2Location ();
		int location = index < index2location.length ? index2location[index] : -1;
		if (location >= 0)
			values[location] = value;
		else
			throw new IllegalArgumentException ("Trying to set value that isn't present in IndexedSparseVector");
	}

	public final void setValueAtLocation (int location, double value)
	{
		values[location] = value;
	}

	// I dislike this name, but it's consistent with DenseVector. -cas
	public void columnPlusEquals (int index, double value) {
		if (index2location == null)
			setIndex2Location ();
		int location = index < index2location.length ? index2location[index] : -1;
		if (location >= 0)
			values[location] += value;
		else
			throw new IllegalArgumentException ("Trying to set value that isn't present in IndexedSparseVector");
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
    int vNumLocs = v.numLocations ();
		if (isBinary ()) {
			// this vector is binary
      for (int i = 0; i < vNumLocs; i++) {
				int index = v.indexAtLocation(i);
				if (index >= index2location.length)
					break;
				if (index2location [index] >= 0)
					ret += v.valueAtLocation (i);
	    }
    } else if (v.isBinary ()) {
      // the other vector is binary
      for (int i = 0; i < vNumLocs; i++) {
        int index = v.indexAtLocation(i);
        if (index >= index2location.length)
          break;
        int location = index2location[index];
        if (location >= 0)
          ret += values[location];
      }
		} else {
	    for (int i = 0; i < vNumLocs; i++) {
				int index = v.indexAtLocation(i);
				if (index >= index2location.length)
					break;
				int location = index2location[index];
				if (location >= 0)
					ret += values[location] * v.valueAtLocation (i);
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
		for (int i = 0; i < v.numLocations(); i++) {
	    int index = v.indexAtLocation(i);
	    if (index >= index2location.length)
				break;
	    int location = index2location[index];
	    if (location >= 0)
				values[location] += v.valueAtLocation (i) * factor;
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
	    if (index >= index2location.length)
				break;
	    int location = index2location[index];
	    if (location >= 0)
				values[location] += v.valueAtLocation (i);
		}
	}
	
	public final void setAll (double v)
	{
		for (int i = 0; i < values.length; i++)
			values[i] = v;
	}


  public int location (int index)
  {
    // No test for indices == null, for this is not allowed in an IndexedSparseVector
    if (index2location == null)
      setIndex2Location ();
    if (index >= index2location.length)
      return -1;
    return index2location [index];
  }


	//Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject (ObjectOutputStream out) throws IOException
	{
		// index2location is considered transient to save disk space
		out.writeInt (CURRENT_SERIAL_VERSION);
	}

	private void readObject (ObjectInputStream in)
		throws IOException, ClassNotFoundException
	{
		int version = in.readInt ();
	}

}
