/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.types;


import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import cc.mallet.util.ArrayUtils;

// Generated package name


/**
 *  Implementation of Matrix that allows arbitrary
 *   number of dimensions.  This implementation
 *   simply uses a flat array.
 *
 * Created: Tue Sep 16 14:52:37 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: SparseMatrixn.java,v 1.1 2007/10/22 21:37:39 mccallum Exp $
 */
public class SparseMatrixn implements Matrix, Cloneable, Serializable {

    private SparseVector values;
    private int numDimensions;
    private int[] sizes;
    private int singleSize;

    /**
     *  Create a 1-d dense matrix with the given values.
     */
    public SparseMatrixn(double[] vals) {
        numDimensions = 1;
        sizes = new int[1];
        sizes [0] = vals.length;
        values = new SparseVector (vals);
        computeSingleSIze ();
    }
    
    /**
     *  Create a dense matrix with the given dimensions.
     *
     *  @param szs An array containing the maximum for
     *      each dimension.
     */
    public SparseMatrixn (int szs[]) {
        numDimensions = szs.length;
        sizes = (int[])szs.clone();
        int total = 1;
        for (int j = 0; j < numDimensions; j++) {
        total *= sizes [j];
        }
        values = new SparseVector (new double [total]);
        computeSingleSIze ();
    }


  public SparseMatrixn (int[] szs, double[] vals) {
    numDimensions = szs.length;
    sizes = (int[])szs.clone ();
    values = new SparseVector (vals);
    computeSingleSIze ();
  }

    /**
     *  Create a sparse matrix with the given dimensions and
     *   the given values.
     *
     *  @param szs An array containing the maximum for
     *      each dimension.
   *  @param idxs An array containing the single index
   *     for each entry of the matrix.  A single index is
   *     an integer computed from the indices of each dimension.
   *     as returned by {@link Matrixn#singleIndex}.
     *  @param vals A flat array of the entries of the
     *      matrix, in row-major order.
     */
    public SparseMatrixn (int[] szs, int[] idxs, double[] vals) {
        numDimensions = szs.length;
        sizes = (int[])szs.clone();
    values = new SparseVector (idxs, vals, true, true);
    computeSingleSIze ();
    }

  private void computeSingleSIze ()
  {
    int product = 1;
    for (int i = 0; i < sizes.length; i++) {
      int size = sizes[i];
      product *= size;
    }
    singleSize = product;
  }

  @Override public int getNumDimensions () { return numDimensions; };
    
    @Override public int getDimensions (int [] szs) {
        for ( int i = 0; i < numDimensions; i++ ) {
        szs [i] = this.sizes [i];
        } 
        return numDimensions;
    }
    
    @Override public double value (int[] indices) {
        return values.value (singleIndex (indices));
    }
    
    @Override public void setValue (int[] indices, double value) {
        values.setValue (singleIndex (indices), value);
    }

  /**
   * Returns an array of all the present indices.
   * Callers must not modify the return value.
   */
  public int[] getIndices () {
    return values.getIndices ();
  }

  @Override public ConstantMatrix cloneMatrix () {
        /* The Matrixn constructor will clone the arrays. */
        return new SparseMatrixn (sizes, values.getIndices (), values.getValues ());
    }

    @Override public Object clone () {
        return cloneMatrix(); 
    }

    @Override public int singleIndex (int[] indices) 
    {
        return Matrixn.singleIndex (sizes, indices);
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

    @Override public void singleToIndices (int single, int[] indices) {
        Matrixn.singleToIndices (single, indices, sizes);
    }

    @Override public boolean equals (Object o) {
        if (o instanceof SparseMatrixn) {
            /* This could be extended to work for all Matrixes. */
            SparseMatrixn m2 = (SparseMatrixn) o;
            return 
                (numDimensions == m2.numDimensions) &&
                (Arrays.equals(sizes, m2.sizes)) &&
                (values.equals(m2.values));
        } else {
            return false;
        }
    }
    
    @Override public int hashCode() {
        return values.numLocations();
    }

  /**
   * Returns a one-dimensional array representation of the matrix.
   *   Caller must not modify the return value.
   * @return An array of the values where index 0 is the major index, etc.
   */
  public double[] toArray () {
    return values.getValues ();
  }

  // Methods from Matrix

  @Override public double singleValue (int i)
  {
    return values.singleValue (i);
  }

  @Override public int singleSize ()
  {
    return singleSize;
  }

  // Access by index into sparse array, efficient for sparse and dense matrices
  @Override public int numLocations ()
  {
    return values.numLocations ();
  }

  @Override public int location (int index)
  {
    return values.location (index);
  }

  @Override public double valueAtLocation (int location)
  {
    return values.valueAtLocation (location);
  }

  @Override public void setValueAtLocation (int location, double value)
  {
    values.setValueAtLocation (location, value);
  }

  // Returns a "singleIndex"
  @Override public int indexAtLocation (int location)
  {
    return values.indexAtLocation (location);
  }

  @Override public double dotProduct (ConstantMatrix m)
  {
    return values.dotProduct (m);
  }

  @Override public double absNorm ()
  {
    return values.absNorm ();
  }

  @Override public double oneNorm ()
  {
    return values.oneNorm ();
  }

  @Override public double twoNorm ()
  {
    return values.twoNorm ();
  }

  @Override public double infinityNorm ()
  {
    return values.infinityNorm ();
  }

  @Override public void print()
  {
    values.print ();
  }

  @Override public boolean isNaN()
  {
    return values.isNaN ();
  }

  @Override public void setSingleValue (int i, double value)
  {
    values.setValue (i, value);
  }

  @Override public void incrementSingleValue (int i, double delta)
  {
    double value = values.value (i);
    values.setValue (i, value + delta);

  }

  @Override public void setAll (double v)
  {
    values.setAll (v);
  }

  @Override public void set (ConstantMatrix m)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void setWithAddend (ConstantMatrix m, double addend)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void setWithFactor (ConstantMatrix m, double factor)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void plusEquals (ConstantMatrix m)
  {
    plusEquals (m, 1.0);
  }

  // sucks, but so does the visitor pattern.  not often used.
  @Override public void plusEquals (ConstantMatrix m, double factor)
  {
    if (m instanceof SparseVector) {
      values.plusEqualsSparse ((SparseVector) m, factor);
    } else if (m instanceof SparseMatrixn) {
      SparseMatrixn smn = (SparseMatrixn) m;
      if (Arrays.equals (sizes, smn.sizes)) {
        values.plusEqualsSparse (smn.values, factor);
      } else {
        throw new UnsupportedOperationException ("sizes of " + m + " do not match " + this);
      }
    } else {
      throw new UnsupportedOperationException ("Can't add " + m + " to " + this);
    }
  }

  @Override public void equalsPlus (double factor, ConstantMatrix m)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void timesEquals (double factor)
  {
    values.timesEquals (factor);
  }

  @Override public void elementwiseTimesEquals (ConstantMatrix m)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void elementwiseTimesEquals (ConstantMatrix m, double factor)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void divideEquals (double factor)
  {
    values.timesEquals (1 / factor);
  }

  @Override public void elementwiseDivideEquals (ConstantMatrix m)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public void elementwiseDivideEquals (ConstantMatrix m, double factor)
  {
    throw new UnsupportedOperationException ("Not yet implemented.");
  }

  @Override public double oneNormalize ()
  {
    double norm = values.oneNorm();
    values.timesEquals (1 / norm);
    return norm;
  }

  @Override public double twoNormalize ()
  {
    double norm = values.twoNorm();
    values.timesEquals (1 / norm);
    return norm;
  }

  @Override public double absNormalize ()
  {
    double norm = values.absNorm();
    values.timesEquals (1 / norm);
    return norm;
  }

  @Override public double infinityNormalize ()
  {
    double norm = values.infinityNorm();
    values.timesEquals (1 / norm);
    return norm;
  }

      // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
  }

}
