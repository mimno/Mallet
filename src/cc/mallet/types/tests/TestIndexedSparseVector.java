/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/

package cc.mallet.types.tests;

import junit.framework.*;

import java.io.IOException;
import java.util.Arrays;

import cc.mallet.types.DenseVector;
import cc.mallet.types.IndexedSparseVector;
import cc.mallet.types.SparseVector;

public class TestIndexedSparseVector extends TestCase
{
  public TestIndexedSparseVector (String name) {
    super (name);
  }

  double[] dbl1 = new double[] {1, 2, 3, 4, 5};
  double[] dbl2 = new double[] {1, 1.5, 2, 1, 1};
  double[] dbl3 = new double[] { 2.0, 2.5, 3.0, 4.7, 3.5,
                                 3.6, 0,   0,   0,   0,
                                 0,   0,   0,   0,   0,
                                 0, };
  int[] idxs = new int[] {3, 5, 7, 13, 15};
  IndexedSparseVector s1 = new IndexedSparseVector (idxs, dbl1, dbl1.length, dbl1.length,
                                      true, true, true);
  IndexedSparseVector s2 = new IndexedSparseVector (idxs, dbl2, dbl2.length, dbl2.length,
                                      true, true, true);
  DenseVector d1 = new DenseVector (dbl3, true);

 public void testLocation ()
 {
   int curidx = 0;
   int max = idxs[idxs.length - 1];
   for (int idx = 0; idx < max; idx++) {
     if (idx == idxs[curidx]) {
       assertEquals (dbl1[curidx], s1.value (idx), 1e-10);
       curidx++;
     } else {
       assertEquals (0, s1.value (idx), 1e-10);
     }
   }
   assertEquals (0, s1.value (max+1), 1e-10);
 }

  private void checkAnswer (IndexedSparseVector actual, double[] ans)
  {
    assertEquals ("Wrong number of locations:",
                  ans.length, actual.numLocations());
    for (int i = 0; i < actual.numLocations(); i++) {
      assertEquals ("Value incorrect at location "+i+": ",
                    ans[i], actual.valueAtLocation (i) , 0.0);
    }
  }

  public void testPlusEquals ()
  {
    IndexedSparseVector s = (IndexedSparseVector) s1.cloneMatrix ();
    s.plusEqualsSparse (s2, 2.0);
    checkAnswer (s, new double[] { 3, 5, 7, 6, 7 });

    IndexedSparseVector s2p = new IndexedSparseVector
                       (new int[] { 13 },
                        new double[] { 0.8 });
    s.plusEqualsSparse (s2p, 1.0);
    checkAnswer (s, new double[] { 3, 5, 7, 6.8, 7 });

    IndexedSparseVector s3p = new IndexedSparseVector
                       (new int[] { 14 },
                        new double[] { 0.8 });
    s.plusEqualsSparse (s3p, 1.0);
    checkAnswer (s, new double[] { 3, 5, 7, 6.8, 7 }); 		// verify s unchanged

    IndexedSparseVector s4 = new IndexedSparseVector
                      (new int[] { 7, 14, 15 },
                       new double[] { 0.2, 0.8, 1.2 });
    s.plusEqualsSparse (s4, 1.0);
    checkAnswer (s, new double[] { 3, 5, 7.2, 6.8, 8.2 });

    IndexedSparseVector s5 = new IndexedSparseVector (new int[] { 7 }, new double[] { 0.2 });
    s5.plusEqualsSparse (s1);
    for (int i = 0; i < s5.numLocations(); i++) {
      assertEquals (7, s5.indexAtLocation (i));
      assertEquals (3.2, s5.valueAtLocation (i), 0.0);
    }

    IndexedSparseVector s6 = new IndexedSparseVector (new int[] { 7 }, new double[] { 0.2 });
    s6.plusEqualsSparse (s1, 3.5);
    for (int i = 0; i < s6.numLocations(); i++) {
      assertEquals (7, s6.indexAtLocation (i));
      assertEquals (10.7, s6.valueAtLocation (i), 0.0);
    }
  }

  public void testDotProduct () {
    IndexedSparseVector t1 = new IndexedSparseVector (new int[] { 7 }, new double[] { 0.2 });
    assertEquals (0.6, t1.dotProduct (s1), 0.00001);
    assertEquals (0.6, s1.dotProduct (t1), 0.00001);

    assertEquals (19.0, s1.dotProduct (s2), 0.00001);
    assertEquals (19.0, s2.dotProduct (s1), 0.00001);

    assertEquals (11.9, s1.dotProduct (d1), 0.00001);
    assertEquals (10.1, s2.dotProduct (d1), 0.00001);
  }

  public void testIncrementValue ()
  {
    IndexedSparseVector s = (IndexedSparseVector) s1.cloneMatrix ();
    s.incrementValue (5, 0.75);

    double[] ans = new double[] {1, 2.75, 3, 4, 5};
    for (int i = 0; i < s.numLocations(); i++) {
      assertTrue (s.valueAtLocation (i) == ans[i]);
    }
  }


  public void testSetValue ()
  {
    IndexedSparseVector s = (IndexedSparseVector) s1.cloneMatrix ();
    s.setValue (5, 0.3);

    double[] ans = new double[] {1, 0.3, 3, 4, 5};
    for (int i = 0; i < s.numLocations(); i++) {
      assertTrue (s.valueAtLocation (i) == ans[i]);
    }
  }

  private static int[] idx2 = { 3, 7, 12, 15, 18 };

  public void testBinaryVector ()
  {
    IndexedSparseVector binary1 = new IndexedSparseVector (idxs, null, idxs.length, idxs.length,
                                             false, false, false);
    IndexedSparseVector binary2 = new IndexedSparseVector (idx2, null, idx2.length, idx2.length,
                                            false, false, false);

    assertEquals (3, binary1.dotProduct (binary2), 0.0001);
    assertEquals (3, binary2.dotProduct (binary1), 0.0001);

    assertEquals (15.0, binary1.dotProduct (s1), 0.0001);
    assertEquals (15.0, s1.dotProduct (binary1), 0.0001);

    assertEquals (9.0, binary2.dotProduct (s1), 0.0001);
    assertEquals (9.0, s1.dotProduct (binary2), 0.0001);

    IndexedSparseVector dblVec = (IndexedSparseVector) s1.cloneMatrix ();
    dblVec.plusEqualsSparse (binary1);
    checkAnswer (dblVec, new double[] { 2, 3, 4, 5, 6 });

    IndexedSparseVector dblVec2 = (IndexedSparseVector) s1.cloneMatrix ();
    dblVec2.plusEqualsSparse (binary2);
    checkAnswer (dblVec2, new double[] { 2, 2, 4, 4, 6 });
  }

  public void testCloneMatrixZeroed ()
  {
    IndexedSparseVector s = (IndexedSparseVector) s1.cloneMatrixZeroed ();
    for (int i = 0; i < s.numLocations(); i++) {
      assertTrue (s.valueAtLocation (i) == 0.0);
      assertTrue (s.indexAtLocation (i) == idxs [i]);
    }
  }

  public void testEmptyLocations ()
  {
    IndexedSparseVector s = new IndexedSparseVector (new int[0], new double [0]);
    assertEquals (0.0, s.value (38), 1e-10);
    assertEquals (0.0, s.dotProduct (s1), 1e-10);
  }

  public void testSerializable () throws IOException, ClassNotFoundException
  {
    IndexedSparseVector s = (IndexedSparseVector) s1.cloneMatrix ();
    IndexedSparseVector sPrime = (IndexedSparseVector) TestSerializable.cloneViaSerialization (s);
    assertEquals (s.numLocations (), sPrime.numLocations ());
    assertTrue (Arrays.equals (s.getIndices (), sPrime.getIndices ()));
    assertTrue (Arrays.equals (s.getValues (), sPrime.getValues ()));
  }

  public void testSerializable2 () throws IOException, ClassNotFoundException
  {
    SparseVector[][] vecs = new SparseVector[2][];

    vecs[0] = new SparseVector[] {
              (SparseVector) s1.cloneMatrix (),
              (SparseVector) s1.cloneMatrix (),
    };
    vecs[1] = new SparseVector[] {
              (SparseVector) s1.cloneMatrix (),
    };

    SparseVector[][] vecsPrime = (SparseVector[][]) TestSerializable.cloneViaSerialization (vecs);
    assertEquals (vecs.length, vecsPrime.length);
  }

  public static Test suite ()
  {
    return new TestSuite (TestIndexedSparseVector.class);
  }

  protected void setUp ()
  {
  }

  public static void main (String[] args)
  {
    junit.textui.TestRunner.run (suite());
  }

}
