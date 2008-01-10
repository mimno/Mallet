/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;


import java.util.Arrays;

import cc.mallet.types.Matrix;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Matrixn;
import cc.mallet.types.SparseMatrixn;

/**
 * Static Matrix constructors.
 * $Id: Matrices.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class Matrices {


  /* Returns a diagonal matrix of the given dimensions.  It need not be square. */
  public static Matrix diag (int[] sizes, double v)
  {
    int maxN = MatrixOps.max (sizes);
    double[] vals = new double[maxN];
    Arrays.fill (vals, v);

    /* Compute indices of diagonals */
    int[] idxs = new int [maxN];
    for (int i = 0; i < idxs.length; i++) {
      int[] oneIdx = new int [sizes.length];
      Arrays.fill (oneIdx, i);
      idxs[i] = Matrixn.singleIndex (sizes, oneIdx);
    }

    return new SparseMatrixn (sizes, idxs, vals);
  }


  /* Returns a diagonal matrix of the given dimensions.  It need not be square. */
  public static Matrix constant (int[] sizes, double v)
  {
    int singleSize = 1;
    for (int i = 0; i < sizes.length; i++) {
      singleSize *= sizes[i];
    }

    double[] vals = new double [singleSize];
    Arrays.fill (vals, v);

    return new SparseMatrixn (sizes, vals);
  }

}
