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

import java.util.logging.*;
import java.util.Arrays;

import cc.mallet.types.Matrix;
import cc.mallet.util.MalletLogger;

@Deprecated // This class is very sparsely used, and I think we can get rid of it. -akm 1/2008
// TODO Remove this class
public final class Matrix2 extends DenseMatrix
{
	private static Logger logger = MalletLogger.getLogger(Matrix2.class.getName());
	int nr, nc;

	public Matrix2 (double[] values, int nr, int nc)
	{
		assert (values.length == nr * nc);
		this.values = values;
		this.nr = nr;
		this.nc = nc;
	}

	public Matrix2 (int nr, int nc)
	{
		this (new double[nr * nc], nr, nc);
	}

	public Matrix2 (double[][] values)
	{
		this.nr = values.length;
		this.nc = values[0].length;
		for (int i = 1; i < nr; i++)
			if (values[i].length != nc)
				throw new IllegalArgumentException
					("Trying to initialize Matrix with array having columns to different lengths.");
		this.values = new double[nr * nc];
		for (int i = 0; i < nr; i++)
			System.arraycopy (values[i], 0, values, i*nc, nc);
	}

	public Matrix2 (double value, int nr, int nc)
	{
		this.nr = nr;
		this.nc = nc;
		this.values = new double[nr * nc];
		Arrays.fill (this.values, value);
	}

	public int getNumDimensions () { return 2; }

	public int getDimensions (int[] sizes) { sizes[0] = nr; sizes[1] = nc; return 2; }

	public double value (int rowIndex, int colIndex)
	{
		return values[(nc * rowIndex) + colIndex];
	}

	public final void arrayCopyInto (double[] array, int startingArrayIndex)
	{
		System.arraycopy (values, 0, array, startingArrayIndex, values.length);
	}


	// Copy the contents of double[] array  into this Matrix2, starting
	// at index i in the array, and continuing to fill all of this Matrix2.
	public final void arrayCopyFrom (double[] array, int startingArrayIndex)
	{
		System.arraycopy (array, startingArrayIndex, values, 0, values.length);
	}
	
	public void setValue (int rowIndex, int colIndex, double value)
	{
		values[(nc * rowIndex) + colIndex] = value;
	}

	public boolean sizeMatches (ConstantMatrix m)
	{
		if (m instanceof Matrix2)
			return (((Matrix2)m).nr == nr && ((Matrix2)m).nc == nc);
		int[] otherDims = new int[10];
		int numDimensions = getDimensions (otherDims);
		return (numDimensions == 2 && otherDims[0] == nr && otherDims[1] == nc);
	}

	public boolean sizeMatches (Matrix2 m)
	{
		return (m.nr == nr && m.nc == nc);
	}
	
	
	public int getNumRows () { return nr; }
	public int getNumCols () { return nc; }


	public Matrix2 transpose ()
	{
		Matrix2 ret = new Matrix2 (nc, nr);
		for (int i = 0; i < nr; i++)
			for (int j = 0; j < nc; j++)
				ret.values[j*nr+i] = values[i*nc+j];
		return ret;
	}


	// The Matrix interface
	
	public final double value (int[] indices) {
		assert (indices.length == 2); return values[indices[0]*nc+indices[1]]; }
	public final void setValue (int[] indices, double val) {
		assert (indices.length == 2); values[indices[0]*nc+indices[1]] = val; }

	// Access using a single index
	public final int singleIndex (int[] indices) {
		assert (indices.length == 2); return indices[indices[0]*nc+indices[1]]; }
	public final void singleToIndices (int i, int[] indices) {
		assert (indices.length == 2);
		assert (i < nc * nr);
		indices[0] = i/nc;
		indices[1] = i%nc; }
	public final double singleValue (int i) { return values[i]; }
	public final void setSingle (int i, double value) { values[i] = value; }
	public final int singleSize () { return nc * nr; }

	
	public final ConstantMatrix cloneMatrix () { return cloneMatrix2 (); }
	public final Matrix2 cloneMatrix2 () {
		Matrix2 ret = new Matrix2 (nr, nc);
		System.arraycopy (values, 0, ret.values, 0, values.length);
		return ret;
	}

	public final void setAll (double v) {
		for (int i = 0; i < values.length; i++)
			values[i] = v;
	}

	/** If "ifSelected" is false, it reverses the selection.  If
			"fselection" is null, this implies that all features are
			selected; all values will be changed unless "ifSelected" is
			false. */
	public final void setAll (double v, FeatureSelection fselection, boolean ifSelected) {
		if (fselection == null) {
			if (ifSelected == true) {
				logger.info ("Matrix2.setAll using FeatureSelection==null");
				setAll (v);
			}
		} else {
			logger.info ("Matrix2.setAll using FeatureSelection");
			for (int i = 0; i < values.length; i++)
				if (fselection.contains(i) ^ !ifSelected)
					values[i] = v;
		}
	}

	/** If "ifSelected" is false, it reverses the selection.  If
			"fselection" is null, this implies that all features are
			selected; all values in the row will be changed unless
			"ifSelected" is false. */
	public final void rowSetAll (int ri, double v, FeatureSelection fselection, boolean ifSelected) {
		assert (ri < nr);
		if (fselection == null) {
			if (ifSelected == true) {
				for (int ci = 0; ci < nc; ci++)
					values[ri*nc+ci] = v;
			}
		} else {
			// xxx Temporary check for full selection
			//assert (fselection.nextDeselectedIndex (0) == nc);
			for (int ci = 0; ci < nc; ci++)
				if (fselection.contains(ci) ^ !ifSelected)
					values[ri*nc+ci] = v;
		}
	}
	
	public final void plusEquals (int ri, int ci, double value) {
		assert (ri < nr);
		assert (ci < nc);
		values[ri*nc+ci] += value;
	}

	public final void rowPlusEquals (int ri, Vector v, double factor) {
		assert (ri < nr);
		for (int vli = 0; vli < v.numLocations(); vli++) {
			//System.out.println ("Matrix2 values.length="+values.length+" index="+(ri*nc+v.indexAtLocation(vli))+" ri="+ri+" nc="+nc+" v.indexAtLocation("+vli+")="+v.indexAtLocation(vli));
			values[ri*nc+v.indexAtLocation(vli)] += v.valueAtLocation(vli) * factor;
		}
	}

	//added by Fuchun
	public final void rowPlusEquals (int ri, double v, double factor) {
		assert (ri < nr);
		for (int vli = 0; vli < nc; vli++) {
			values[ri*nc+vli] += v * factor;
		}
	}


	public final void columnPlusEquals (int ci, Vector v, double factor) {
		assert (ci < nc);
		for (int vli = 0; vli < v.numLocations(); vli++)
			values[v.indexAtLocation(vli)*nc+ci] += v.valueAtLocation(vli) * factor;
	}

	//added by Fuchun
	public final void columnPlusEquals (int ci, double v, double factor)
	{
		assert (ci < nc);
		for (int vli = 0; vli < nr; vli++)
			values[vli*nc+ci] += v* factor;

	}

	public final double rowDotProduct (int ri, Vector v)
	{
		double ret = 0;
		for (int cil = 0; cil < v.numLocations(); cil++) {
			int ci = v.indexAtLocation (cil);
			// Just skip it if ci is beyond the boundaries of this matrix;
			// everything outside is assumed to have zero value.
			if (ci < nc)
				ret += values[ri*nc+ci] * v.valueAtLocation(cil);
		}
		return ret;
	}

	/** Skip all column indices higher than "maxCi".  This lets you
			store non-vocabulary based parameters in the high column
			indices, without fearing that they may later be included by
			accident if the dictionary grows.  You may pass null for
			selection. */
	public final double rowDotProduct (int ri, Vector v, int maxCi, FeatureSelection selection)
	{
		double ret = 0;
		if (selection != null) {
			for (int cil = 0; cil < v.numLocations(); cil++) {
				int ci = v.indexAtLocation (cil);
				if (selection.contains(ci) && ci < nc && ci <= maxCi)
					ret += values[ri*nc+ci] * v.valueAtLocation(cil);
			}
		} else {
			for (int cil = 0; cil < v.numLocations(); cil++) {
				int ci = v.indexAtLocation (cil);
				if (ci < nc && ci <= maxCi)
					ret += values[ri*nc+ci] * v.valueAtLocation(cil);
			}
		}
		return ret;
	}
	
	public final double twoNormSquared ()
	{
		double ret = 0;
		for (int i = 0; i < values.length; i++)
			ret += values[i] * values[i];
		return ret;
	}
	
	public void print ()
	{
		for (int i = 0; i < nr; i++) {
			for (int j = 0; j < nc; j++)
				System.out.print (" " + values[i*nc+j]);
			System.out.println ("");
		}
	}

	public String toString ()
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < nr; i++) {
			for (int j = 0; j < nc; j++)
				sb.append (" " + values[i*nc+j]);
			sb.append ("\n");
		}
		return sb.toString();
	}

}
