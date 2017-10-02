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


import cc.mallet.types.Alphabet;
import cc.mallet.types.RankedFeatureVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestRankedFeatureVector extends TestCase
{
	public TestRankedFeatureVector (String name) {
		super (name);
	}
	
	public void testSetRankOrder ()
	{
		Alphabet v = new Alphabet ();
		RankedFeatureVector rfv =
			new RankedFeatureVector (v, new int[] {v.lookupIndex ("a"), v.lookupIndex ("b"), v.lookupIndex ("c"), v.lookupIndex ("d") },
															new double[] {3.0, 1.0, 2.0, 6.0});
		System.out.println ("vector size ="+rfv.numLocations());
		for (int i = 0; i < rfv.numLocations(); i++)
			System.out.println ("Rank="+i+" value="+rfv.getValueAtRank(i));
	}

	public static Test suite ()
	{
		return new TestSuite(TestRankedFeatureVector.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
