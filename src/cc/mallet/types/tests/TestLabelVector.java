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

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import junit.framework.*;

public class TestLabelVector extends TestCase
{
	public TestLabelVector (String name)
	{
		super (name);
	}

	private LabelAlphabet ld;
	private LabelVector lv;

	protected void setUp ()
	{
		ld = new LabelAlphabet ();
		lv = new LabelVector (ld,
													new int[] {
														ld.lookupIndex ("a"),
														ld.lookupIndex ("b"),
														ld.lookupIndex ("c"),
														ld.lookupIndex ("d")},
													new double[] {3, 4, 2, 1});
	}
	
	public void testGetBestLabel ()
	{
		assertTrue (lv.getBestLabel() == ld.lookupLabel ("b"));
	}

	public void testGetLabelAtRank ()
	{
		assertTrue (lv.getLabelAtRank(1) == ld.lookupLabel ("a"));
	}

  public void testValue ()
  {
    assertEquals (4.0, lv.value (ld.lookupLabel ("b")), 1e-5);
  }

	public static Test suite ()
	{
		return new TestSuite (TestLabelVector.class);
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
