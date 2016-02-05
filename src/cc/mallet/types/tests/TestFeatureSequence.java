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
import cc.mallet.types.FeatureSequence;
import junit.framework.*;

public class TestFeatureSequence extends TestCase
{
	public TestFeatureSequence (String name) {
		super (name);
	}
	
	public void testNewPutSizeFreeze ()
	{
		Alphabet dict = new Alphabet ();
		FeatureSequence fs = new FeatureSequence (dict, 10);
		fs.add (dict.lookupIndex ("apple"));
		fs.add (dict.lookupIndex ("bear"));
		fs.add (dict.lookupIndex ("car"));
		fs.add (dict.lookupIndex ("door"));
		assertTrue (fs.size() == 4);
		double[] weights = new double[4];
		fs.addFeatureWeightsTo (weights);
		assertTrue (weights[1] == 1.0);

		fs.add (dict.lookupIndex ("bear"));
		int[] feats = fs.toFeatureIndexSequence();
		assertTrue (feats[0] == 0);
		assertTrue (feats[1] == 1);
		assertTrue (feats[2] == 2);
		assertTrue (feats[3] == 3);
		assertTrue (feats[4] == 1);
	}

	public static Test suite ()
	{
		return new TestSuite (TestFeatureSequence.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
