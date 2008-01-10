/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.io.*;

public class DenseVector extends DenseMatrix implements Vector, Serializable
{

	public DenseVector (double[] values, boolean copy)
	{
		if (copy) {
			this.values = new double[values.length];
			System.arraycopy (values, 0, this.values, 0, values.length);
		} else
			this.values = values;
	}

	public DenseVector (double[] values) { this (values, true); }

	public DenseVector (int size) { this (new double[size], false); }
	
	public int getNumDimensions () { return 1; }
	public int getDimensions (int[] sizes) { sizes[0] = values.length; return 1; }
	
	public double value (int[] indices) {
		assert (indices.length == 1);
		return values[indices[0]];
	}

	public double value (int index) {
		return values[index];
	}
	
	public void setValue (int[] indices, double value) {
		assert (indices.length == 1);
		values[indices[0]] = value;
	}

	public void setValue (int index, double value) {
		values[index] = value;
	}

	public void columnPlusEquals (int columnIndex, double value)
	{
		values[columnIndex] += value;
	}
	
	public ConstantMatrix cloneMatrix () {
		return new DenseVector (values, true);
	}

	public int singleIndex (int[] indices) { assert (indices.length == 1); return indices[0]; }
	public void singleToIndices (int i, int[] indices) { indices[0] = i; }

    
	// Copy the contents of Matrix m into this Vector starting at index
	// i in this Vector, laying out Matrix m in "getSingle()" order.
	// Return the next index that could be set in this DenseVector after
	// the indices filled by Matrix m.
	public final int arrayCopyFrom (int i, Matrix m) {
		if (m instanceof DenseVector) {
			System.arraycopy (((DenseVector)m).values, 0, values, i, ((DenseVector)m).values.length);
			return i + ((DenseVector)m).values.length;
		} else if (m instanceof Matrix2) {
			((Matrix2)m).arrayCopyInto (values, i);
			return i + m.singleSize();
		} else {
			for (int j = 0; j < m.singleSize(); j++)
				values[i++] = m.singleValue (j);
			return i;
		}
	}
    
    /** Copy values from an array into this vector. The array should have the
     * same size as the vector */
    public final void arrayCopyFrom( double[] a ) 
    {
	arrayCopyFrom(a,0);
    }

    /** Copy values from an array starting at a particular index into this 
     * vector. The array must have at least as many values beyond the starting
     * index as there are in the vector.
     *
     * @return Next uncopied index in the array.
     */
    public final int arrayCopyFrom( double [] a , int startingArrayIndex )
    {
	System.arraycopy( a, startingArrayIndex, values, 0, values.length );

	return startingArrayIndex + values.length;
    }
	
	// Copy the contents of this Vector into Matrix m starting at index
	// i in this Vector, setting values in Matrix m in "setSingle()" order.
	// Return the next index that could be gotten after the indices copied 
	// into Matrix m.
	public final int arrayCopyTo (int i, Matrix m) {
		if (m instanceof DenseVector) {
			System.arraycopy (values, i, ((DenseVector)m).values, 0, ((DenseVector)m).values.length);
			return i + ((DenseVector)m).values.length;
		} else if (m instanceof Matrix2) {
			((Matrix2)m).arrayCopyFrom (values, i);
			return i + m.singleSize();
		} else {
			for (int j = 0; j < m.singleSize(); j++)
				m.setSingleValue (j, values[i++]);
			return i;
		}
	}

	public final int arrayCopyTo (int i, double[] a) {
		System.arraycopy (values, i, a, 0, a.length);
		return i + a.length;
	}

    
    /** Copy the contents of this vector into an array starting at a particular
     * index. 
     *
     * @return Next available index in the array 
     */
     public final int arrayCopyInto (double[] array, int startingArrayIndex)
	{
		System.arraycopy (values, 0, array, startingArrayIndex, values.length);
		return startingArrayIndex + values.length;
	}
	
	public void addTo (double[] v)
	{
		assert (v.length == values.length);
		for (int i = 0; i < values.length; i++)
			v[i] += values[i];
	}

	public void addTo (double[] v, double factor)
	{
		assert (v.length == values.length);
		for (int i = 0; i < values.length; i++)
			v[i] += values[i] * factor;
	}
	

	public static double sum (double[] v)
	{
		double sum = 0;
		for (int i = 0; i < v.length; i++)
			sum += v[i];
		return sum;
	}
	
	public static double normalize (double[] v)
	{
		double sum = 0;
		for (int i = 0; i < v.length; i++)
			sum += v[i];
		assert (sum != 0);
		for (int i = 0; i < v.length; i++)
			v[i] /= sum;
		return sum;
	}

	public static double max (double[] v)
	{
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < v.length; i++)
			if (v[i] > max)
				max = v[i];
		return max;
	}

	public static void print (double[] v)
	{
		System.out.print ("[");
		for (int i = 0; i < v.length; i++)
			System.out.print (" " + v[i]);
		System.out.println ("]");
	}
	
	public static void print (int[] v)
	{
		System.out.print ("[");
		for (int i = 0; i < v.length; i++)
			System.out.print (" " + v[i]);
		System.out.println ("]");
	}

	// Serialization
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
	}

}
