/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.io.IOException;
import cc.mallet.types.SparseMatrixn;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.DoubleArrayList;

/**
 * Created: Aug 30, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestSparseMatrixn.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestSparseMatrixn {

  @Test
  public void testIndexing1d ()
  {
    double m1[] = new double[]{1.0, 2.0, 3.0, 4.0};
    int idx1[] = new int[1];
    SparseMatrixn a = new SparseMatrixn (m1);
    a.singleToIndices (3, idx1);
    assertEquals (3, idx1[0]);
    assertEquals (3, a.singleIndex (idx1));
  }

  @Test
  public void testIndexing2d ()
  {
    int[] sizes = new int[]{2, 3};
    double[] m1 = new double[6];
    for (int i = 0; i < 6; i++) {
      m1[i] = 2.0 * i;
    }
    SparseMatrixn a = new SparseMatrixn (sizes, m1);
    int[] idx1 = new int[2];
    a.singleToIndices (5, idx1);
    System.out.println (idx1[0]+" , "+idx1[1]);

    int[] trueIdx = new int[] {1, 2};
    assertTrue (Arrays.equals (trueIdx, idx1));
    assertEquals (5, a.singleIndex (idx1));
    assertEquals (10.0, a.value (idx1), 1e-12);
  }

  @Test
  public void testIndexing3d ()
  {
    SparseMatrixn a = make3dMatrix ();
    int[] idx1 = new int[3];
    a.singleToIndices (21, idx1);

    int[] trueIdx = new int[]{1, 2, 1};
    assertTrue (Arrays.equals (trueIdx, idx1));
    assertEquals (21, a.singleIndex (idx1));
    assertEquals (0, a.value (idx1), 1e-12);

    int[] idx2 = new int[]{1, 2, 2};
    assertEquals (22, a.singleIndex (idx2));
    assertEquals (44.0, a.value (idx2), 1e-12);
  }

  private SparseMatrixn make3dMatrix ()
  {
    int[] sizes = new int[]{2, 3, 4};
    IntArrayList idxs = new IntArrayList ();
    DoubleArrayList vals = new DoubleArrayList ();

    for (int i = 0; i < 24; i++) {
      if (i % 3 != 0) {
        idxs.add (i);
        vals.add (2.0 * i);
      }
    }

    SparseMatrixn a = new SparseMatrixn (sizes, idxs.toArray (), vals.toArray ());
    return a;
  }

  @Test
  public void testSparseMatrixnSerializable () throws IOException, ClassNotFoundException
  {
    SparseMatrixn a = make3dMatrix ();
    SparseMatrixn b = (SparseMatrixn) TestSerializable.cloneViaSerialization (a);

    assertEquals (a.singleSize(), b.singleSize());
    for (int i = 0; i < a.singleSize (); i++) {
      int[] idxa = new int [a.getNumDimensions ()];
      int[] idxb = new int [a.getNumDimensions ()];

      a.singleToIndices (i, idxa);
      b.singleToIndices (i, idxb);

      assertTrue (Arrays.equals (idxa, idxb));
      assertEquals (a.value (idxa), b.value (idxb), 1e-12);
    }
  }

}
