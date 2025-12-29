/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestPropertyList {

	@Test
	public void testOne() {
		PropertyList pl = null;
		pl = PropertyList.add("one", 1.0, pl);
		pl = PropertyList.add("two", 2.0, pl);
		pl = PropertyList.add("three", 3, pl);

		assertTrue(pl.lookupNumber("one") == 1.0);
		pl = PropertyList.remove("three", pl);
		assertTrue(pl.lookupNumber("three") == 0.0);

		pl = PropertyList.add("color", "red", pl);
		assertTrue(pl.lookupObject("color").equals("red"));
	}

}
