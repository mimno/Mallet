/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tests;


import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.util.Iterator;
import java.util.regex.Pattern;


public class TestRainbowStyle extends TestCase
{
	public TestRainbowStyle (String name) {
		super (name);
	}
	
	public void testThree ()
	{
		InstanceList il = new InstanceList (
			new SerialPipes(new Pipe[] {
				new Target2Label(),
				new CharSequence2TokenSequence(),
				new TokenSequenceLowercase(),
				new TokenSequenceRemoveStopwords(),
				new TokenSequence2FeatureSequence(),
				new FeatureSequence2FeatureVector()
			}));
		Iterator<Instance> pi = new FileIterator(new File("foo/bar"), null, Pattern.compile("^([^/]*)/"));
		il.addThruPipe (pi);
	}

	public static Test suite ()
	{
		return new TestSuite(TestRainbowStyle.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
