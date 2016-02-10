/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.types; // Generated package name


/**
 *  Implementation of Matrix that allows arbitrary
 *   number of dimensions.  This implementation
 *   simply uses a flat array.
 *
 *  This also provides static utilities for doing
 *   arbitrary-dimensional array indexing (see
 *   {@link #singleIndex}, {@link #singleToIndices}).
 *
 * Created: Tue Sep 16 14:52:37 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: Matrixn.java,v 1.1 2007/10/22 21:37:39 mccallum Exp $
 */
public class Matrixn extends DenseMatrix implements Cloneable {

	int numDimensions;
	int[] sizes;

	/**
	 *  Create a 1-d matrix with the given values.
	 */
	public Matrixn(double[] vals) {
		numDimensions = 1;
		sizes = new int[1];
		sizes [0] = vals.length;
		values = (double[]) vals.clone();
	} 
	
	/**
	 *  Create a matrix with the given dimensions.
	 *
	 *  @param szs An array containing the maximum for
	 *      each dimension.
	 */
	public Matrixn (int szs[]) {
		numDimensions = szs.length;
//		sizes = (int[])szs.clone();
    sizes = szs;
    int total = 1;
		for (int j = 0; j < numDimensions; j++) {
	    total *= sizes [j];
		}
		values = new double [total];
	}
	
	/**
	 *  Create a matrix with the given dimensions and 
	 *   the given values.
	 *
	 *  @param szs An array containing the maximum for
	 *      each dimension.
	 *  @param vals A flat array of the entries of the
	 *      matrix, in row-major order.
	 */
	public Matrixn (int[] szs, double[] vals) {
		numDimensions = szs.length;
		sizes = (int[])szs.clone();
		values = (double[])vals.clone();
	}
	
	public int getNumDimensions () { return numDimensions; };
	
	public int getDimensions (int [] szs) {
		for ( int i = 0; i < numDimensions; i++ ) {
	    szs [i] = this.sizes [i];
		} 
		return numDimensions;
	}
	
	public double value (int[] indices) {
		return values [singleIndex (indices)];
	}
	
	public void setValue (int[] indices, double value) {
		values [singleIndex (indices)] = value;
	}

	public ConstantMatrix cloneMatrix () {
		/* The Matrixn constructor will clone the arrays. */
		return new Matrixn (sizes, values);
	}

	public Object clone () {
		return cloneMatrix(); 
	}

	public int singleIndex (int[] indices) 
	{
		return singleIndex (sizes, indices);
	}

	// This is public static so it will be useful as a general
	// dereferencing utility for multidimensional arrays.
	public static int singleIndex (int[] szs, int[] indices)
	{
		int idx = 0;
		for ( int dim = 0; dim < indices.length; dim++ ) {
			idx = (idx * szs[dim]) + indices [dim];	   
		} 
		return idx;
	}

	// NOTE: Cut-n-pasted to other singleToIndices method!!
	public void singleToIndices (int single, int[] indices) {
		/* must be a better way to do this... */
		int size = 1;
		for (int i = 0; i < numDimensions; i++) {
	    size *= sizes[i];
		}
		for ( int dim = 0; dim < numDimensions; dim++) {
	    size /= sizes [dim];
	    indices [dim] = single / size;
	    single = single % size;
		} 
	}

	/** Just a utility function for arbitrary-dimensional matrix
	 * dereferencing. 
	 */
	// NOTE: Cut-n-paste from other singleToIndices method!!
	public static void singleToIndices (int single, int[] indices, int[] szs) {
		int numd = indices.length;
		assert numd == szs.length;
		/* must be a better way to do this... */
		int size = 1;
		for (int i = 0; i < numd; i++) {
	    size *= szs[i];
		}
		for ( int dim = 0; dim < numd; dim++) {
	    size /= szs [dim];
	    indices [dim] = single / size;
	    single = single % size;
		} 
	}

	public boolean equals (Object o) {
		if (o instanceof Matrixn) {
			/* This could be extended to work for all Matrixes. */
			Matrixn m2 = (Matrixn) o;  
			return 
				(numDimensions == m2.numDimensions) &&
				(sizes.equals (m2.sizes)) &&
				(values.equals (m2.values));
		} else {
			return false;
		}
	}

  /**
   * Returns a one-dimensional array representation of the matrix.
   *   Caller must not modify the return value.
   * @return An array of the values where index 0 is the major index, etc.
   */
  public double[] toArray () {
    return values;
  }
  
		/* Test array referencing and dereferencing */
		public static void main(String[] args) {
			double m1[] = new double[] { 1.0, 2.0, 3.0, 4.0 };
			int idx1[] = new int[1];
			Matrixn a = new Matrixn (m1);
			System.out.println("Checking 1-D case");
			a.singleToIndices (3, idx1);
			System.out.println(idx1[0]);
			System.out.println (a.singleIndex (idx1));
		
			System.out.println ("Checking 2-D case");
			int sizes[] = new int[] { 2, 3 };
			m1 = new double [6];
			for (int i = 0; i < 6; i++) {
				m1 [i] = 2.0 * i;
			}
			a = new Matrixn (sizes, m1);
			idx1 = new int [2];
			a.singleToIndices (5, idx1);
			System.out.println("5 => (" + idx1[0] + ", " + idx1[1] + ") => " + 
												 a.singleIndex (idx1) );
			System.out.println(a.value (idx1));
		
			System.out.println("Checking 3-D case");
			sizes = new int[] { 2, 3, 4 };
			idx1 = new int[3];
			m1 = new double [24];
			for (int i = 0; i < 24; i++) {
				m1 [i] = 2.0 * i;
			}
			a = new Matrixn (sizes, m1);
			a.singleToIndices (21, idx1);
			System.out.println ("21 => (" + idx1[0] + " " + idx1[1] + " " +
													idx1[2] + ") =>" + a.singleIndex (idx1));
			System.out.println(a.value (idx1));
		} 

    // serialization garbage
    private static final long serialVersionUID = 7963668115823191655L;
  
  }
