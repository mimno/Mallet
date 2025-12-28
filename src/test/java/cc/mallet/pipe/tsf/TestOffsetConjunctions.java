/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import junit.framework.*;
import java.util.regex.*;

import cc.mallet.pipe.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;


public class TestOffsetConjunctions extends TestCase
{
	public TestOffsetConjunctions (String name) {
		super (name);
	}
	
	public void testOne ()
	{
		String input = "abcdefghijklmnopqrstuvwxyz";
		Pipe p =
			new SerialPipes (new Pipe[] {
				new CharSequence2TokenSequence ("."),
				//new PrintInput("1:"),
				new TokenSequenceLowercase (),
				//new PrintInput("2:"),
				new TokenText (),
				//new PrintInput("3:"),
				new RegexMatches ("V", Pattern.compile("[aeiou]")),
				//new PrintInput("4:"),
				new OffsetConjunctions (new int[][] {{0,0}, {0,1}, {-1,0,1}, {-1}, {-2}}),
				new PrintInput("5:"),
			});

		Instance carrier = p.instanceFrom(new Instance (input, null, null, null));
		TokenSequence ts = (TokenSequence) carrier.getData();
		assertTrue (ts.size() == 26);
		assertTrue (ts.get(0).getFeatureValue("a_&_b@1") == 1.0);
		assertTrue (ts.get(0).getFeatureValue("V_&_a") == 1.0);
		assertTrue (ts.get(2).getFeatureValue("b@-1_&_c_&_d@1") == 1.0);
	}


	public static Test suite ()
	{
		return new TestSuite (TestOffsetConjunctions.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
