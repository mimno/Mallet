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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import java.io.File;
import java.io.IOException;
import cc.mallet.types.HashedSparseVector;
import cc.mallet.types.SparseVector;
import cc.mallet.util.FileUtils;

public class TestHashedSparseVector extends TestCase
{
	public TestHashedSparseVector (String name) {
		super (name);
	}
	
	double[] dbl1 = new double[] {1, 2, 3, 4, 5};
	double[] dbl2 = new double[] {1, 1.5, 2, 1, 1};
	double[] dbl3 = new double[] { 2.0, 2.5, 3.0, 4.7, 3.5,
																 3.6, 0,   0,   0,   0,
																 0,   0,   0,   0,   0,
																 0, };
	int[] idxs = new int[] {3, 5, 7, 13, 15};
	HashedSparseVector s1 = new HashedSparseVector (idxs, dbl1, dbl1.length, dbl1.length,
																			true, true, true);
	HashedSparseVector s2 = new HashedSparseVector (idxs, dbl2, dbl2.length, dbl2.length,
																			true, true, true);
	SparseVector d1 = new SparseVector (dbl3, true);


	private void checkAnswer (HashedSparseVector actual, double[] ans)
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
		HashedSparseVector s = (HashedSparseVector) s1.cloneMatrix ();
		s.plusEqualsSparse (s2, 2.0);
		checkAnswer (s, new double[] { 3, 5, 7, 6, 7 }); 

		HashedSparseVector s2p = new HashedSparseVector
											 (new int[] { 13 },
												new double[] { 0.8 });
		s.plusEqualsSparse (s2p, 1.0);
		checkAnswer (s, new double[] { 3, 5, 7, 6.8, 7 }); 

		HashedSparseVector s3p = new HashedSparseVector
											 (new int[] { 14 },
												new double[] { 0.8 });
		s.plusEqualsSparse (s3p, 1.0);
		checkAnswer (s, new double[] { 3, 5, 7, 6.8, 7 }); 		// verify s unchanged

		HashedSparseVector s4 = new HashedSparseVector
											(new int[] { 7, 14, 15 },
											 new double[] { 0.2, 0.8, 1.2 });
		s.plusEqualsSparse (s4, 1.0);
		checkAnswer (s, new double[] { 3, 5, 7.2, 6.8, 8.2 });	

		HashedSparseVector s5 = new HashedSparseVector (new int[] { 7 }, new double[] { 0.2 });
		s5.plusEqualsSparse (s1);
		for (int i = 0; i < s5.numLocations(); i++) {
	    assertEquals (7, s5.indexAtLocation (i));
	    assertEquals (3.2, s5.valueAtLocation (i), 0.0);
		}

		HashedSparseVector s6 = new HashedSparseVector (new int[] { 7 }, new double[] { 0.2 });
		s6.plusEqualsSparse (s1, 3.5);
		for (int i = 0; i < s6.numLocations(); i++) {
	    assertEquals (7, s6.indexAtLocation (i));
	    assertEquals (10.7, s6.valueAtLocation (i), 0.0);
		}
	}

  public void testPlusEqualsAfterClone ()
  {
    s1.indexVector ();
    HashedSparseVector s = (HashedSparseVector) s1.cloneMatrixZeroed ();
    s.plusEqualsSparse (s1);
    s.plusEqualsSparse (s2, 2.0);
    checkAnswer (s, new double[] { 3, 5, 7, 6, 7 });
  }

	public void testDotProduct () {
		HashedSparseVector t1 = new HashedSparseVector (new int[] { 7 }, new double[] { 0.2 });
		assertEquals (0.6, t1.dotProduct (s1), 0.00001);
		assertEquals (0.6, s1.dotProduct (t1), 0.00001);
		
		assertEquals (19.0, s1.dotProduct (s2), 0.00001);
		assertEquals (19.0, s2.dotProduct (s1), 0.00001);

		assertEquals (11.9, s1.dotProduct (d1), 0.00001);
		assertEquals (10.1, s2.dotProduct (d1), 0.00001);
	}

	public void testIncrementValue ()
	{
		HashedSparseVector s = (HashedSparseVector) s1.cloneMatrix ();
		s.incrementValue (5, 0.75);

		double[] ans = new double[] {1, 2.75, 3, 4, 5};
		for (int i = 0; i < s.numLocations(); i++) {
	    assertTrue (s.valueAtLocation (i) == ans[i]);
		}
	}

	
	public void testSetValue ()
	{
		HashedSparseVector s = (HashedSparseVector) s1.cloneMatrix ();
		s.setValue (5, 0.3);

		double[] ans = new double[] {1, 0.3, 3, 4, 5};
		for (int i = 0; i < s.numLocations(); i++) {
	    assertTrue (s.valueAtLocation (i) == ans[i]);
		}
	}

	private static int[] idx2 = { 3, 7, 12, 15, 18 };

	public void testBinaryVector ()
	{
		HashedSparseVector binary1 = new HashedSparseVector (idxs, null, idxs.length, idxs.length,
																						 false, false, false);
		HashedSparseVector binary2 = new HashedSparseVector (idx2, null, idx2.length, idx2.length,
																						false, false, false);

		assertEquals (3, binary1.dotProduct (binary2), 0.0001);
		assertEquals (3, binary2.dotProduct (binary1), 0.0001);

		assertEquals (15.0, binary1.dotProduct (s1), 0.0001);
		assertEquals (15.0, s1.dotProduct (binary1), 0.0001);

		assertEquals (9.0, binary2.dotProduct (s1), 0.0001);
		assertEquals (9.0, s1.dotProduct (binary2), 0.0001);

		HashedSparseVector dblVec = (HashedSparseVector) s1.cloneMatrix ();
		dblVec.plusEqualsSparse (binary1);
		checkAnswer (dblVec, new double[] { 2, 3, 4, 5, 6 });

		HashedSparseVector dblVec2 = (HashedSparseVector) s1.cloneMatrix ();
		dblVec2.plusEqualsSparse (binary2);
		checkAnswer (dblVec2, new double[] { 2, 2, 4, 4, 6 });
	}

	public void testCloneMatrixZeroed ()
	{
		HashedSparseVector s = (HashedSparseVector) s1.cloneMatrixZeroed ();
		for (int i = 0; i < s.numLocations(); i++) {
	    assertTrue (s.valueAtLocation (i) == 0.0);
	    assertTrue (s.indexAtLocation (i) == idxs [i]);
		}
	}

  public void testSerializable () throws Exception
  {
    // Write out the sparse vector s1
    HashedSparseVector s2 = (HashedSparseVector) TestSerializable.cloneViaSerialization (s1);
    assertEquals (s1.numLocations (), s2.numLocations ());
    for (int loc = 0; loc < s1.numLocations (); loc++) {
      assertEquals (s1.valueAtLocation (loc), s2.valueAtLocation (loc), 0.001);
    }
  }

  // tests index2location getting screwed up when old (v 1.3) instances are de-serialized
  public void testPlusEqualsFromSaved () throws IOException, ClassNotFoundException
  {
    HashedSparseVector s1 = (HashedSparseVector) FileUtils.readObject (oldSv);
    HashedSparseVector s2 = new HashedSparseVector (new int[] { 1 }, new double[] { 1.0 });

    s1.plusEqualsSparse (s2, 1.0);
    assertEquals (1.0, s1.value (0), 1e-5);
    assertEquals (0.0, s1.value (1), 1e-5);
  }

  // This is a hashedSparseVector from cvs version 1.3.  It was saved by saveOldSv(), below.
  private static File oldSv = new File ("test/resources/edu/umass/cs/mallet/base/types/hashed.sv.old.ser");

  public static void saveOldSv ()
  {
    HashedSparseVector sv = new HashedSparseVector (new int[] { 0, 2 }, new double[] { 1.0, 2.0 });
    sv.indexVector ();
    FileUtils.writeObject (oldSv, sv);
  }

  public static Test suite ()
	{
		return new TestSuite (TestHashedSparseVector.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
//    saveOldSv ();
		junit.textui.TestRunner.run (suite());
	}
	
}
