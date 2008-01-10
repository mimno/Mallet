/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.types;

import java.util.Arrays;
import java.io.*;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import cc.mallet.util.PropertyList;
/**
	 A vector that allocates memory only for non-zero values.

	 When you create a SparseVector, you pass in a list of indices.
	  These are the only elements of the vector you will be allowed
		to change.  The rest are fixed at 0.

     The interface to Sparse vector uses the concept of a location, which
     is an integer in the range 0..numLocations which can be mapped to the
     index (and value) of a non zero element of the vector.

     A SparseVector can be sparse or dense depending on whether or not
    an array if indices is specified at construction time.  If the SparseVector is dense,
    the mapping from location to index is the identity mapping.
    The type of the value an element in a SparseVector (or FeatureVector) can be
    double or binary (0.0 or 1.0), depending on whether an array of doubles is specified at
    contruction time.


   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/
public class SparseVector implements ConstantMatrix, Vector, Serializable
{
	/** 
			If the vector is sparse, then both indices and values are
			sparse.  Indices into these arrays are called ``locations'' in
			the below. The indices[] array maps locations to indices of the
			(virtual) dense array that's being represented.  value[] maps
			locations to values.
	*/
	protected int[] indices;												// if this is null, then the vector is dense
	protected double[] values;											// if this is null, then the vector is binary
	protected boolean hasInfinite;                  // if true, at least one value =  -Inf or +Inf
	
	/** If "indices" is null, the vector will be dense.  If "values" is
			null, the vector will be binary.  The capacity and size arguments are
			used by AugmentableFeatureVector. */
	public SparseVector (int[] indices, double[] values, 
													int capacity, int size,
													boolean copy,
													boolean checkIndicesSorted,
													boolean removeDuplicates)
	{
		// "size" was pretty much ignored??? Why?
		int length;
		length = size;
		if (capacity < length)
			capacity = length;
		assert (size <= length);
		if (!(values == null || indices == null || indices.length == values.length))
      throw new IllegalArgumentException
            ("Attempt to create sparse non-binary SparseVector with mismatching values & indices\n"
            +"  indices.length = "+indices.length+"   values.length = "+values.length);
		if (copy || capacity > length) {
			if (indices == null)
				this.indices = null;
			else {
				this.indices = new int[capacity];
				System.arraycopy (indices, 0, this.indices, 0, length);
			}
			if (values == null)
				this.values = null;
			else {
				this.values = new double[capacity];
				System.arraycopy (values, 0, this.values, 0, length);
			}
		} else {
			this.indices = indices;
			this.values = values;
		}
		if (checkIndicesSorted)
			sortIndices ();										// This also removes duplicates
		else if (removeDuplicates)
			removeDuplicates (0);
	}


	// Create a dense Vector
	public SparseVector (double[] values, boolean copy)
	{
		this (null, values, values.length, values.length, copy, false, false);
	}

	public SparseVector (double[] values) { this (values, true); }

	public SparseVector (int size, double fillValue) {
		this (newArrayOfValue (size, fillValue), false); }

	
	public SparseVector (int[] indices, double[] values, 
											 boolean copy, boolean checkIndicesSorted,
											 boolean removeDuplicates)
	{
		this (indices, values,
					(indices != null) ? indices.length : values.length,
					(indices != null) ? indices.length : values.length,
					copy, checkIndicesSorted, removeDuplicates);
	}

	public SparseVector (int[] indices, double[] values)
	{
		this (indices, values, true, true, true);
	}
	
	public SparseVector (int[] indices, double[] values, boolean copy)
	{
		this (indices, values, copy, true, true);
	}
	
	public SparseVector (int[] indices, double[] values, boolean copy,
											 boolean checkIndicesSorted)
	{
		this (indices, values, copy, checkIndicesSorted, true);
	}

	// Create a vector that is possibly binary or non-binary
	public SparseVector (int[] indices,
											 boolean copy,
											 boolean checkIndicesSorted,
											 boolean removeDuplicates,
											 boolean binary)
	{
		this (indices, binary ? null : newArrayOfValue(indices.length,1.0), indices.length, indices.length,
					copy, checkIndicesSorted, removeDuplicates);
	}

	// Create a binary vector
	public SparseVector (int[] indices,
											 int capacity, int size,
											 boolean copy,
											 boolean checkIndicesSorted,
											 boolean removeDuplicates)
	{
		this (indices, null, capacity, size, copy, checkIndicesSorted, removeDuplicates);
	}

	public SparseVector (int[] indices, boolean copy, boolean checkIndicesSorted) {
		this (indices, null, copy, checkIndicesSorted, true);	}
	public SparseVector (int[] indices, boolean copy) {
		this (indices, null, copy, true, true); }
	public SparseVector (int[] indices) {
		this (indices, null, true, true, true); }
	/** An empty vector, with all zero values */
	public SparseVector () {
		this (new int[0], new double[0], false, false); }

	public SparseVector (Alphabet dict, PropertyList pl, boolean binary,
											 boolean growAlphabet)
	{
		if (pl == null) {
			// xxx Fix SparseVector so that it can properly represent a vector that has all zeros.
			// Does this work?
			indices = new int[0];
			values = null;
			return;
		}

		PropertyList.Iterator iter;
		if (binary == false) {
			binary = true;
			// If all the property list features are binary, make a binary SparseVector even if the constructor argument "binary" is false.
			// This will significantly save space, as well as multiplication time later!  -akm 12/2007
			iter = pl.numericIterator();
			while (iter.hasNext()) {
				iter.nextProperty();
				if (iter.getNumericValue() != 1.0) {
					binary = false;
					break;
				}
			}
		}
		
		AugmentableFeatureVector afv = new AugmentableFeatureVector (dict, binary);
		//afv.print();
		//System.out.println ("SparseVector binary="+binary);
		//pl.print();
		iter = pl.numericIterator();
		while (iter.hasNext()) {
			iter.nextProperty();
			//System.out.println ("SparseVector adding "+iter.getKey()+" "+iter.getNumericValue());
			int index = dict.lookupIndex(iter.getKey(), growAlphabet);
			if (index >=0) {
				afv.add (index, iter.getNumericValue());
			}
			//System.out.println ("SparseVector afv adding "+iter.getKey()+" afv.numLocations="+afv.numLocations());
		}
		//afv.print();
		// xxx Not so efficient?
		SparseVector sv = afv.toSparseVector();
		//System.out.println ("SparseVector sv.numLocations="+sv.numLocations());
		this.indices = sv.indices;
		this.values = sv.values;
	}
	public SparseVector (Alphabet dict, PropertyList pl, boolean binary)
	{
		this(dict, pl, binary, true);
	}

	private static double[] newArrayOfValue (int length, double value)
	{
		double[] ret = new double[length];
		Arrays.fill (ret, value);
		return ret;
	}

	public boolean isBinary () { return values == null; }
	public void makeBinary () { throw new UnsupportedOperationException ("Not yet implemented"); }
	public void makeNonBinary () { throw new UnsupportedOperationException ("Not yet implemented"); }

	
	/***********************************************************************
	 *  ACCESSORS
	 ***********************************************************************/
	
	public int getNumDimensions () { return 1; }
	
	// xxx What do we return for the length?  It could be higher than this index.
	public int getDimensions (int[] sizes)
	{
		if (indices == null)
			sizes[0] = values.length;
		else
			// xxx This is pretty unsatisfactory, since there may be zero
			// values above this location.
			sizes[0] = indices[indices.length-1];
		return 1;
	}

	// necessary for the SVM implementation! -dmetzler
	// ...but be careful, this is allowed to be null! -cas
	public int [] getIndices() {
		return indices;
	}
	
	// necessary for the SVM implementation! -dmetzler
	// ...but be careful, this is allowed to be null! -cas
	public double [] getValues()
	{
		return values;
	}	

	// xxx This is just the number of non-zero entries...
	// This is different behavior than Matrix2!!
	public int numLocations ()
	{
		return (values == null
						? (indices == null
							 ? 0
							 : indices.length)
						: values.length);
	}

	public int location (int index) {
		if (indices == null)
			return index;
		else
			return Arrays.binarySearch (indices, index);
	}

	public double valueAtLocation (int location) { return values == null ? 1.0 : values[location]; }
	public int indexAtLocation (int location) { return indices == null ? location : indices[location]; }
	
	public double value (int[] indices)
	{
		assert (indices.length == 1);
		if (indices == null)
			return values[indices[0]];
		else
			return values[location(indices[0])];
	}
		
	public double value (int index)
	{
		if (indices == null)
			try {
				return values[index];
			} catch (ArrayIndexOutOfBoundsException e) {
				return 0.0;
			}
		else {
			int loc = location(index);
			if (loc < 0)
				return 0.0;
			else if (values == null)
				return 1.0;
			else
				return values[loc];
		}
	}

	public void addTo (double[] accumulator, double scale)
	{
		if (indices == null) {
			for (int i = 0; i < values.length; i++)
				accumulator[i] += values[i] * scale;
		} else if (values == null) {
			for (int i = 0; i < indices.length; i++)
				accumulator[indices[i]] += scale;
		} else {
			for (int i = 0; i < indices.length; i++)
				accumulator[indices[i]] += values[i] * scale;
		}
	}

	public void addTo (double[] accumulator)
	{
		addTo (accumulator, 1.0);
	}
	
	public int singleIndex (int[] indices) { assert (indices.length == 1); return indices[0]; }
	
	public void singleToIndices (int i, int[] indices) { indices[0] = i; }
	
	public double singleValue (int i) { return value(i); }
	
	public int singleSize ()
	{
		if (indices == null)
			return values.length;
		else if (indices.length == 0)
			return 0;
		else
			// This is just the highest index that will have non-zero value.
			// The full size of this dimension is "unknown"
			return indices[indices.length-1];
	}



    public String toString() {
	return this.toString(false);
    }

    public String toString(boolean onOneLine) {

	StringBuffer sb = new StringBuffer ();
	
	for (int i = 0; i < values.length; i++)
	{
	    sb.append((indices == null ? i : indices[i]));
	    sb.append ("=");
	    sb.append (values[i]);
	    if (!onOneLine)
		sb.append ("\n");
	    else
		sb.append (' ');
	}

	return sb.toString();
    }
	
  /***********************************************************************
	 *  CLONING
	 ***********************************************************************/

	public ConstantMatrix cloneMatrix () {
		if (indices == null)
			return new SparseVector (values);
		else
			return new SparseVector (indices, values, true, false, false);
	}

	public ConstantMatrix cloneMatrixZeroed () {
		if (indices == null)
			return new SparseVector (new double[values.length]);
		else {
			int[] newIndices = new int[indices.length];
			System.arraycopy (indices, 0, newIndices, 0, indices.length);
			return new SparseVector (newIndices, new double[values.length], true, false, false);
		}
	}
	
	
	/***********************************************************************
	 *  MUTATORS
	 ***********************************************************************/

	/**
	 *  For each index i that is present in this vector,
	 *   set this[i] += v[i].
	 *  If v has indices that are not present in this,
	 *   these are just ignored.
	 */
	public void plusEqualsSparse (SparseVector v) {
		plusEqualsSparse (v, 1.0);
	}

	/**
	 *  For each index i that is present in this vector,
	 *   set this[i] += factor * v[i].
	 *  If v has indices that are not present in this,
	 *   these are just ignored.
	 */
	public void plusEqualsSparse (SparseVector v, double factor)
	{
    // Special case for dense sparse vector
    if (indices == null) { densePlusEqualsSparse (v, factor); return; }

		int loc1 = 0;
		int loc2 = 0;
		int numLocations1 = numLocations();
		int numLocations2 = v.numLocations();
		
		while ((loc1 < numLocations1) && (loc2 < numLocations2)) {
			int idx1 = indexAtLocation (loc1);
			int idx2 = v.indexAtLocation (loc2);
			if (idx1 == idx2) {
				values [loc1] += v.valueAtLocation (loc2) * factor;
				++loc1; ++loc2;
			} else if (idx1 < idx2) {
				++loc1;
			} else {
        // idx2 not present in this. Ignore.
				++loc2;
			}
		}
	}


	/**
	 *  For each index i that is present in this vector,
	 *   set this[i] *= v[i].
	 *  If v has indices that are not present in this,
	 *   these are just ignored.
	 */
	public void timesEqualsSparse (SparseVector v) {
		timesEqualsSparse (v, 1.0);
	}

	/**
	 *  For each index i that is present in this vector,
	 *   set this[i] *= factor * v[i].
	 *  If v has indices that are not present in this,
	 *   these are just ignored.
	 */
	public void timesEqualsSparse (SparseVector v, double factor)
	{
    // Special case for dense sparse vector
    if (indices == null) { denseTimesEqualsSparse (v, factor); return; }

		int loc1 = 0;
		int loc2 = 0;
		
		while ((loc1 < numLocations()) && (loc2 < v.numLocations())) {
			int idx1 = indexAtLocation (loc1);
			int idx2 = v.indexAtLocation (loc2);
			if (idx1 == idx2) {
				values [loc1] *= v.valueAtLocation (loc2) * factor;
				++loc1; ++loc2;
			} else if (idx1 < idx2) {
				++loc1;
			} else {
        // idx2 not present in this. Ignore.
				++loc2;
			}
		}
	}

	/**
	 *  For each index i that is present in this vector,
	 *   set this[i] *= factor * v[i].
	 *  If v has indices that are not present in this,
	 *   these are set to zero
	 */
	public void timesEqualsSparseZero (SparseVector v, double factor)
	{
    // Special case for dense sparse vector
    if (indices == null) { denseTimesEqualsSparse (v, factor); return; }

		int loc1 = 0;
		int loc2 = 0;
		
		while ((loc1 < numLocations()) && (loc2 < v.numLocations())) {
			int idx1 = indexAtLocation (loc1);
			int idx2 = v.indexAtLocation (loc2);
			if (idx1 == idx2) {
				values [loc1] *= v.valueAtLocation (loc2) * factor;
				++loc1; ++loc2;
			} else if (idx1 < idx2) {
				// idx1 not present in v.  Zero.
				values[loc1] = 0;
				++loc1;
			} else {
        // idx2 not present in this. Ignore
				++loc2;
			}
		}
	}
    

        /**
	 * Scale all elements by the same factor.
	 */
        public void timesEquals( double factor )
        {
	    for (int i = 0; i < values.length; i++)
		values[i] *= factor;
	}


  private void densePlusEqualsSparse (SparseVector v, double factor)
  {
    int maxloc = v.numLocations();
    for (int loc = 0; loc < maxloc; loc++) {
      int idx = v.indexAtLocation (loc);
      if (idx >= values.length) break;
      values [idx] += v.valueAtLocation (loc) * factor;
    }
  }


  private void denseTimesEqualsSparse (SparseVector v, double factor)
  {
    int maxloc = v.numLocations();
    for (int loc = 0; loc < maxloc; loc++) {
      int idx = v.indexAtLocation (loc);
      if (idx >= values.length) break;
      values [idx] *= v.valueAtLocation (loc) * factor;
    }
  }

    /**
	 * Increments this[index] by value.
	 * @throws IllegalArgumentException If index is not present.
	 */
	public void incrementValue (int index, double value)
		throws IllegalArgumentException
	{
		int loc = location (index);
		if (loc >= 0)
			values[loc] += value;
		else
			throw new IllegalArgumentException ("Trying to set value that isn't present in SparseVector");
	}

	/** Sets every present index in the vector to v. */
	public void setAll (double v)
	{
		for (int i = 0; i < values.length; i++)
			values[i] = v;
	}

	/**
	 * Sets the value at the given index.
	 *  @throws IllegalArgumentException If index is not present.
	 */
	public void setValue (int index, double value)
		throws IllegalArgumentException
	{
		if (indices == null)
			values[index] = value;
		else {
			int loc = location(index);
			if (loc < 0)
				throw new IllegalArgumentException ("Can't insert values into a sparse Vector.");
			else
				values[loc] = value;
		}
	}

	/** Sets the value at the given location. */
	public void setValueAtLocation (int location, double value)
	{
		values[location] = value;
	}

        /** Copy values from an array into this vector. The array should have the
	 * same size as the vector */
    // yanked from DenseVector
        public final void arrayCopyFrom( double[] a ) 
        {
	    arrayCopyFrom(a,0);
	}

        /** Copy values from an array starting at a particular location into this
	 * vector. The array must have at least as many values beyond the 
	 * starting location as there are in the vector.
	 *
	 * @return Next uncopied location in the array.
	 */
        public final int arrayCopyFrom( double [] a , int startingArrayLocation )
        {
	    System.arraycopy( a, startingArrayLocation, 
			      values, 0, values.length );
	    
	    return startingArrayLocation + values.length;
	}
	

    /** 
     * Applies the method argument to each value in a non-binary vector. 
     * The method should both accept a Double as an argument and return a Double.
     *
     * @throws IllegalArgumentException If the method argument has an 
     *                                  inappropriate signature.
     * @throws UnsupportedOperationException If vector is binary 
     * @throws IllegalAccessException If the method is inaccessible
     * @throws Throwable If the method throws an exception it is relayed
     */
    public final void map (Method f) throws IllegalAccessException, Throwable
    {
	if (values == null)
	    throw new UnsupportedOperationException
		("Binary values may not be altered via map");

	if (f.getParameterTypes().length!=1 ||
	    f.getParameterTypes()[0] != Double.class ||
	    f.getReturnType() != Double.class )
	    throw new IllegalArgumentException
		("Method signature must be \"Double f (Double x)\"");

	try {
	    for (int i=0 ; i<values.length ; i++)
		values[i] = ((Double)f.invoke 
			     (null, 
			      new Object[]
				 {new Double(values[i])})).doubleValue ();
	} catch (InvocationTargetException e) {
	    throw e.getTargetException();
	}
    }


        /** Copy the contents of this vector into an array starting at a 
	 * particular location. 
	 *
	 * @return Next available location in the array 
	 */
        public final int arrayCopyInto (double[] array, int startingArrayLocation)
	{
		System.arraycopy (values, 0, array, startingArrayLocation, 
				  values.length);
		return startingArrayLocation + values.length;
	}



	/***********************************************************************
	 *  VECTOR OPERATIONS
	 ***********************************************************************/

	public double dotProduct (double[] v) {
		double ret = 0;
		if (values == null)
			for (int i = 0; i < indices.length; i++)
				ret += v[indices[i]];
		else
			for (int i = 0; i < indices.length; i++)
				ret += values[i] * v[indices[i]];
		return ret;
	}

	public double dotProduct (ConstantMatrix m) {
		if (m instanceof SparseVector) return dotProduct ((SparseVector)m);
		else if (m instanceof DenseVector) return dotProduct ((DenseVector)m);
		else throw new IllegalArgumentException ("Unrecognized Matrix type "+m.getClass());
	}

	public double dotProduct (DenseVector v) {
		if (v.hasInfinite || this.hasInfinite)
			return extendedDotProduct(v);
		double ret = 0;
		if (values == null)
			for (int i = 0; i < indices.length; i++)
				ret += v.value(indices[i]);
		else
			for (int i = 0; i < indices.length; i++) 
				ret += values[i] * v.value(indices[i]);
		if (Double.isNaN(ret)) 
			return extendedDotProduct(v);		
		return ret;
	}

	// sets -Inf * 0 = 0; Inf * 0 = 0
	public double extendedDotProduct (DenseVector v) {
		double ret = 0;
		if (values == null)
			for (int i = 0; i < indices.length; i++)
				ret += v.value(indices[i]);
		else
			for (int i = 0; i < indices.length; i++) {
				if (Double.isInfinite(values[i]) && v.value(indices[i])==0.0) {
					this.hasInfinite = true;
					continue;
				}
				else if (Double.isInfinite(v.value(indices[i])) && values[i]==0.0) {
					v.hasInfinite = true;
					continue;
				}
				ret += values[i] * v.value(indices[i]);
			}
		return ret;
	}
	
	public double dotProduct (SparseVector v)
	{
		if (v.hasInfinite || hasInfinite)
			return extendedDotProduct(v);

    double ret;

    // Decide in which direction to do the dot product.
    //  This is a heuristic choice based on efficiency, and it could certainly
    //   be more complicated.
    if (v instanceof IndexedSparseVector) {
      ret = v.dotProduct (this);
    } else if(numLocations() > v.numLocations ()) {
      ret = dotProductInternal (v, this);
		} else {
			ret = dotProductInternal (this, v);
		}

    if (Double.isNaN (ret))
			return extendedDotProduct (v);

		return ret;
	}


  private double dotProductInternal (SparseVector vShort, SparseVector vLong)
  {
    double ret = 0;
    int numShortLocs = vShort.numLocations();
    if (vShort.isBinary ()) {
      for(int i = 0; i < numShortLocs; i++) {
	  		ret += vLong.value (vShort.indexAtLocation(i));
		  }
    } else {
      for(int i = 0; i < numShortLocs; i++) {
	  		double v1 = vShort.valueAtLocation(i);
		  	double v2 = vLong.value (vShort.indexAtLocation(i));
			  ret += v1*v2;
		  }
    }
    return ret;
  }


  // sets -Inf * 0 = 0, Inf * 0 = 0
	public double extendedDotProduct (SparseVector v)
	{
		double ret = 0.0;
		SparseVector vShort = null;
		SparseVector vLong = null;
		// this ensures minimal computational effort
		if(numLocations() > v.numLocations ()) {
			vShort = v;
			vLong = this;
		} else {
			vShort = this;
			vLong = v;
		}

		for(int i = 0; i < vShort.numLocations(); i++) {
			double v1 = vShort.valueAtLocation(i);
			double v2 = vLong.value (vShort.indexAtLocation(i));
			if (Double.isInfinite(v1) && v2==0.0) {
			 vShort.hasInfinite = true;
				continue;
			}
			else if (Double.isInfinite(v2) && v1==0.0) {
				vLong.hasInfinite = true;
				continue;
			}
			ret += v1*v2;
		}
	
		return ret;
	}
	
	public SparseVector vectorAdd(SparseVector v, double scale) {
		if(indices != null) { // sparse SparseVector
			int [] ind = v.getIndices();
			double [] val = v.getValues();
			int [] newIndices = new int[ind.length+indices.length];
			double [] newVals = new double[ind.length+indices.length];
			for(int i = 0; i < indices.length; i++) {
		    newIndices[i] = indices[i];
		    newVals[i] = values[i];
			}
			for(int i = 0; i < ind.length; i++) {
		    newIndices[i+indices.length] = ind[i];
		    newVals[i+indices.length] = scale*val[i];
			}
			return new SparseVector(newIndices, newVals, true, true, false);
		}
		int [] newIndices = new int[values.length];
		double [] newVals = new double[values.length]; // dense SparseVector
		int curPos = 0;
		for(int i = 0; i < values.length; i++) {
			double val = values[i]+scale*v.value(i);
			if(val != 0.0) {
		    newIndices[curPos] = i;
		    newVals[curPos++] = val;
			}
		}
		return new SparseVector(newIndices, newVals, true, true, false);
	}

	public double oneNorm () {
		double ret = 0;
		if (values == null)
			return indices.length;
		for (int i = 0; i < values.length; i++)
			ret += values[i];
		return ret;
	}

	public double absNorm () {
		double ret = 0;
		if (values == null)
			return indices.length;
		for (int i = 0; i < values.length; i++)
			ret += Math.abs(values[i]);
		return ret;
	}
	
	public double twoNorm () {
		double ret = 0;
		if (values == null)
			return Math.sqrt (indices.length);
		for (int i = 0; i < values.length; i++)
			ret += values[i] * values[i];
		return Math.sqrt (ret);
	}
	
	public double infinityNorm () {
		if (values == null)
			return 1.0;
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < values.length; i++)
			if (Math.abs(values[i]) > max)
				max = Math.abs(values[i]);
		return max;
	}		

	public void print() 
	{
		if (values == null) {
			// binary sparsevector
			for (int i = 0; i < indices.length; i++)
				System.out.println ("SparseVector["+indices[i]+"] = 1.0");
		} else {
			for (int i = 0; i < values.length; i++) {
				int idx = (indices == null) ? i : indices [i];
				System.out.println ("SparseVector["+idx+"] = "+values[i]);
			}
		}
	}
		
	public boolean isNaN() {
		if (values == null)
			return false;
		return MatrixOps.isNaN(values);
//		for (int i = 0; i < values.length; i++)
//			if (Double.isNaN(values[i]))
//				return true;
//		return false;
	}

	// gsc: similar to isNaN but checks for infinite values
	public boolean isInfinite() {
		if (values == null)
			return false;
		return MatrixOps.isInfinite(values);
	}
	
	// gsc: returns true if any value is either NaN or infinite
	public boolean isNaNOrInfinite() {
		if (values == null)
			return false;
		return MatrixOps.isNaNOrInfinite(values);
	}
	
	
	protected void sortIndices ()
	{
		if (indices == null)
			// It's dense, and thus by definition sorted.
			return;
		if (values == null)
			java.util.Arrays.sort (indices);
		else {
			// Just BubbleSort; this is efficient when already mostly sorted.
			// Note that we BubbleSort from the the end forward; this is most efficient
			//  when we have added a few additional items to the end of a previously sorted list.
			//  We could be much smarter if we remembered the highest index that was already sorted
			for (int i = indices.length-1; i >= 0; i--) {
				boolean swapped = false;
				for (int j = 0; j < i; j++)
					if (indices[j] > indices[j+1]) {
						// Swap both indices and values
						int f;
						f = indices[j];
						indices[j] = indices[j+1];
						indices[j+1] = f;
						if (values != null) {
							double v;
							v = values[j];
							values[j] = values[j+1];
							values[j+1] = v;
						}
						swapped = true;
					}
				if (!swapped)
					break;
			}
		}

		//if (values == null)
		int numDuplicates = 0;
		for (int i = 1; i < indices.length; i++)
			if (indices[i-1] == indices[i])
				numDuplicates++;

		if (numDuplicates > 0)
			removeDuplicates (numDuplicates);
	}

	// Argument zero is special value meaning that this function should count them.
	protected void removeDuplicates (int numDuplicates)
	{
		if (numDuplicates == 0)
			for (int i = 1; i < indices.length; i++)
				if (indices[i-1] == indices[i])
					numDuplicates++;
		if (numDuplicates == 0)
			return;
		int[] newIndices = new int[indices.length - numDuplicates];
		double[] newValues = values == null ? null : new double[indices.length - numDuplicates];
		newIndices[0] = indices[0];
		if (values != null) newValues[0] = values[0];
		for (int i = 1, j = 1; i < indices.length; i++) {
			if (indices[i] == indices[i-1]) {
				if (newValues != null)
					newValues[j-1] += values[i];
			} else {
				newIndices[j] = indices[i];
				if (values != null)
					newValues[j] = values[i];
				j++;
			}
		}
		this.indices = newIndices;
		this.values = newValues;
	}


	/// Serialization

	private static final long serialVersionUID = 2;  
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	
	private void writeObject (ObjectOutputStream out) throws IOException
	{
    if (this instanceof AugmentableFeatureVector)
      // Be sure to sort/compress our data before we write it
      ((AugmentableFeatureVector)this).sortIndices();
    out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeInt (indices == null ? -1 : indices.length);
		out.writeInt (values == null ? -1 : values.length);
		if (indices != null)
			for (int i = 0; i < indices.length; i++)
				out.writeInt (indices[i]);
		if (values != null)
			for (int i = 0; i < values.length; i++)
				out.writeDouble (values[i]);
	}

	private void readObject (ObjectInputStream in)
		throws IOException, ClassNotFoundException
	{
		int version = in.readInt ();
		int indicesSize = in.readInt();
		int valuesSize = in.readInt();
		this.hasInfinite = false;
		if (indicesSize >= 0) {
			indices = new int[indicesSize];
			for (int i = 0; i < indicesSize; i++) {
				indices[i] = in.readInt();
			}
		}
		if (valuesSize >= 0) {
				values = new double[valuesSize];
			for (int i = 0; i < valuesSize; i++) {
				values[i] = in.readDouble();
				if (Double.isInfinite (values[i]))
					this.hasInfinite = true;
			}
		}
	}
}
