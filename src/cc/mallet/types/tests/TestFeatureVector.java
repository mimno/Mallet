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
import cc.mallet.types.FeatureVector;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.framework.Test;


public class TestFeatureVector extends TestCase
{
	public TestFeatureVector (String name)
	{
		super (name);
	}

	Alphabet dict;
	FeatureSequence fs;
	FeatureVector fv;
	
	protected void setUp ()
	{
		dict = new Alphabet ();
		fs = new FeatureSequence (dict, 2);
		fs.add (dict.lookupIndex ("a"));
		fs.add (dict.lookupIndex ("n"));
		fs.add (dict.lookupIndex ("d"));
		fs.add (dict.lookupIndex ("r"));
		fs.add (dict.lookupIndex ("e"));
		fs.add (dict.lookupIndex ("w"));
		fs.add (dict.lookupIndex ("m"));
		fs.add (dict.lookupIndex ("c"));
		fs.add (dict.lookupIndex ("c"));
		fs.add (dict.lookupIndex ("a"));
		fs.add (dict.lookupIndex ("l"));
		fs.add (dict.lookupIndex ("l"));
		fs.add (dict.lookupIndex ("u"));
		fs.add (dict.lookupIndex ("m"));
		//System.out.println (fs.toString());
		fv = new FeatureVector (fs);
		//System.out.println (fs.toString());
		//System.out.println (fv.toString());
	}

	public void testDuplicateValueFromFeatureSequence ()
	{
		assertTrue (fv.value (dict.lookupIndex ("a")) == 2.0);
	}

	public void testSingleValueFromFeatureSequence ()
	{
		assertTrue (fv.value (dict.lookupIndex ("n")) == 1.0);
	}

	public void testSizeFromFeatureSequence ()
	{
		assertTrue (fv.numLocations() == 10);
	}
	
	public static Test suite ()
	{
		return new TestSuite (TestFeatureVector.class);
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
