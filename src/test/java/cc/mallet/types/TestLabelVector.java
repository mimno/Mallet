/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestLabelVector
{
	private LabelAlphabet ld;
	private LabelVector lv;

	@Before
	public void setUp ()
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

	@Test
	public void testGetBestLabel ()
	{
		assertTrue (lv.getBestLabel() == ld.lookupLabel ("b"));
	}

	@Test
	public void testGetLabelAtRank ()
	{
		assertTrue (lv.getLabelAtRank(1) == ld.lookupLabel ("a"));
	}

	@Test
  public void testValue ()
  {
    assertEquals (4.0, lv.value (ld.lookupLabel ("b")), 1e-5);
  }

}
