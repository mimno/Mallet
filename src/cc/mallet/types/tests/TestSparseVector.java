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

import java.io.*;
import cc.mallet.types.DenseVector;
import cc.mallet.types.SparseVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestSparseVector extends TestCase
{
	public TestSparseVector (String name) {
		super (name);
	}
	
	double[] dbl1 = new double[] {1, 2, 3, 4, 5};
	double[] dbl2 = new double[] {1, 1.5, 2, 1, 1};
	double[] dbl3 = new double[] { 2.0, 2.5, 3.0, 4.7, 3.5,
																 3.6, 0,   0,   0,   0,
																 0,   0,   0,   0,   0,
																 0, };
	double[] dbl4 = new double[] {1,2,3,4,Double.NEGATIVE_INFINITY};
	int[] idxs = new int[] {3, 5, 7, 13, 15};
	SparseVector s1 = new SparseVector (idxs, dbl1, dbl1.length, dbl1.length,
																			true, true, true);
	SparseVector s2 = new SparseVector (idxs, dbl2, dbl2.length, dbl2.length,
																			true, true, true);
	DenseVector d1 = new DenseVector (dbl3, true);


	private void checkAnswer (SparseVector actual, double[] ans)
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
		SparseVector s = (SparseVector) s1.cloneMatrix ();
		s.plusEqualsSparse (s2, 2.0);
		checkAnswer (s, new double[] { 3, 5, 7, 6, 7 }); 

		SparseVector s2p = new SparseVector
											 (new int[] { 13 },
												new double[] { 0.8 });
		s.plusEqualsSparse (s2p, 1.0);
		checkAnswer (s, new double[] { 3, 5, 7, 6.8, 7 }); 

		SparseVector s3p = new SparseVector
											 (new int[] { 14 },
												new double[] { 0.8 });
		s.plusEqualsSparse (s3p, 1.0);
		checkAnswer (s, new double[] { 3, 5, 7, 6.8, 7 }); 		// verify s unchanged

		SparseVector s4 = new SparseVector
											(new int[] { 7, 14, 15 },
											 new double[] { 0.2, 0.8, 1.2 });
		s.plusEqualsSparse (s4, 1.0);
		checkAnswer (s, new double[] { 3, 5, 7.2, 6.8, 8.2 });	

		SparseVector s5 = new SparseVector (new int[] { 7 }, new double[] { 0.2 });
		s5.plusEqualsSparse (s1);
		for (int i = 0; i < s5.numLocations(); i++) {
	    assertEquals (7, s5.indexAtLocation (i));
	    assertEquals (3.2, s5.valueAtLocation (i), 0.0);
		}

		SparseVector s6 = new SparseVector (new int[] { 7 }, new double[] { 0.2 });
		s6.plusEqualsSparse (s1, 3.5);
		for (int i = 0; i < s6.numLocations(); i++) {
	    assertEquals (7, s6.indexAtLocation (i));
	    assertEquals (10.7, s6.valueAtLocation (i), 0.0);
		}
	}

	public void testDotProduct () {
		SparseVector t1 = new SparseVector (new int[] { 7 }, new double[] { 0.2 });
		assertEquals (0.6, t1.dotProduct (s1), 0.00001);
		assertEquals (0.6, s1.dotProduct (t1), 0.00001);
		
		assertEquals (19.0, s1.dotProduct (s2), 0.00001);
		assertEquals (19.0, s2.dotProduct (s1), 0.00001);

		assertEquals (11.9, s1.dotProduct (d1), 0.00001);
		assertEquals (10.1, s2.dotProduct (d1), 0.00001);

		// test dotproduct when vector with more locations has a lower
		//   max-index than short vector
		SparseVector t2 = new SparseVector (new int[] { 3, 30 }, new double[] { 0.2, 3.5 });
		SparseVector t3 = new SparseVector (null, new double[] { 1, 1, 1, 1, });
		assertEquals (0.2, t3.dotProduct (t2), 0.00001); 
	}

	public void testIncrementValue ()
	{
		SparseVector s = (SparseVector) s1.cloneMatrix ();
		s.incrementValue (5, 0.75);

		double[] ans = new double[] {1, 2.75, 3, 4, 5};
		for (int i = 0; i < s.numLocations(); i++) {
	    assertTrue (s.valueAtLocation (i) == ans[i]);
		}
	}

	
	public void testSetValue ()
	{
		SparseVector s = (SparseVector) s1.cloneMatrix ();
		s.setValue (5, 0.3);

		double[] ans = new double[] {1, 0.3, 3, 4, 5};
		for (int i = 0; i < s.numLocations(); i++) {
	    assertTrue (s.valueAtLocation (i) == ans[i]);
		}
	}

	public void testDenseSparseVector ()
	{
		SparseVector svDense = new SparseVector (null, dbl3);
		double sdot = svDense.dotProduct (svDense);
		double ddot = d1.dotProduct (d1);
		assertEquals (sdot, ddot, 0.0001);

		svDense.plusEqualsSparse (s1);
		checkAnswer (svDense, new double[] { 2.0, 2.5, 3.0, 5.7, 3.5,
																				 5.6, 0,   3,   0,   0,
																				 0,   0,   0,   4,   0,
																				 5, });

		svDense.plusEqualsSparse (s1, 2.0);
		checkAnswer (svDense, new double[] { 2.0, 2.5, 3.0, 7.7, 3.5,
																				 9.6, 0,   9,   0,   0,
																				 0,   0,   0,   12,   0,
																				 15, });
		
		double[] dbl4 = new double [dbl3.length + 1];
		for (int i = 0; i < dbl4.length; i++) dbl4[i] = 2.0;
		SparseVector sv4 = new SparseVector (null, dbl4);
		svDense.plusEqualsSparse (sv4);
		checkAnswer (svDense, new double[] { 4.0,  4.5,    5.0,  9.7,   5.5,
																				 11.6, 2.0,   11.0,  2.0,   2.0,
																				 2,   2,   2,   14,   2.0,
																				 17, });
	}

	private static int[] idx2 = { 3, 7, 12, 15, 18 };

	public void testBinaryVector ()
	{
		SparseVector binary1 = new SparseVector (idxs, null, idxs.length, idxs.length,
																						 false, false, false);
		SparseVector binary2 = new SparseVector (idx2, null, idx2.length, idx2.length,
																						false, false, false);

		assertEquals (3, binary1.dotProduct (binary2), 0.0001);
		assertEquals (3, binary2.dotProduct (binary1), 0.0001);

		assertEquals (15.0, binary1.dotProduct (s1), 0.0001);
		assertEquals (15.0, s1.dotProduct (binary1), 0.0001);

		assertEquals (9.0, binary2.dotProduct (s1), 0.0001);
		assertEquals (9.0, s1.dotProduct (binary2), 0.0001);

		SparseVector dblVec = (SparseVector) s1.cloneMatrix ();
		dblVec.plusEqualsSparse (binary1);
		checkAnswer (dblVec, new double[] { 2, 3, 4, 5, 6 });

		SparseVector dblVec2 = (SparseVector) s1.cloneMatrix ();
		dblVec2.plusEqualsSparse (binary2);
		checkAnswer (dblVec2, new double[] { 2, 2, 4, 4, 6 });
	}
	
	public void testCloneMatrixZeroed ()
	{
		SparseVector s = (SparseVector) s1.cloneMatrixZeroed ();
		for (int i = 0; i < s.numLocations(); i++) {
	    assertTrue (s.valueAtLocation (i) == 0.0);
	    assertTrue (s.indexAtLocation (i) == idxs [i]);
		}
	}

	public void testPrint ()
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream ();
		PrintStream out = new PrintStream (baos);
		PrintStream oldOut = System.out;
		System.setOut (out);

		SparseVector standard = new SparseVector (idxs, dbl2);
		standard.print ();
		assertEquals ("SparseVector[3] = 1.0\nSparseVector[5] = 1.5\nSparseVector[7] = 2.0\nSparseVector[13] = 1.0\nSparseVector[15] = 1.0\n", baos.toString ());
		baos.reset ();

		SparseVector dense = new SparseVector (null, dbl2);
		dense.print ();
		assertEquals ("SparseVector[0] = 1.0\nSparseVector[1] = 1.5\nSparseVector[2] = 2.0\nSparseVector[3] = 1.0\nSparseVector[4] = 1.0\n", baos.toString ());
		baos.reset ();

		SparseVector binary = new SparseVector (idxs, null, idxs.length, idxs.length,
																						false, false, false);
		binary.print ();
		assertEquals ("SparseVector[3] = 1.0\nSparseVector[5] = 1.0\nSparseVector[7] = 1.0\nSparseVector[13] = 1.0\nSparseVector[15] = 1.0\n", baos.toString ());
		baos.reset ();
	}

	public void testExtendedDotProduct () {
		SparseVector v1 = new SparseVector (null, dbl3);
		SparseVector vInf = new SparseVector (null, dbl4);
		double dp = v1.dotProduct (vInf);
		assertTrue (!Double.isNaN(dp));
		dp = vInf.dotProduct (v1);
		assertTrue (!Double.isNaN(dp));
	}
	
	public static Test suite ()
	{
		return new TestSuite(TestSparseVector.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
