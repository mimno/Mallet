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

import cc.mallet.types.DenseVector;
import cc.mallet.types.SparseVector;
import junit.framework.*;

public class TestMatrix extends TestCase
{
	public TestMatrix (String name) {
		super (name);
	}
	
	public void testTimesEquals ()
	{
		double[] d1 = new double[] {1, 2, 3, 4, 5};
		SparseVector m1 = new SparseVector (d1);
		SparseVector m2 = new SparseVector (d1);
		m2.timesEqualsSparse(m1);
		m2.print();
	}

	public static Test suite ()
	{
		return new TestSuite (TestMatrix.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
