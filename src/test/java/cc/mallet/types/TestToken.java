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

import java.net.URI;
import java.io.File;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Token;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestToken extends TestCase
{
	public TestToken (String name) {
		super (name);
	}
	
	public void testOne ()
	{
		Token t = new Token ("foo");

		t.setProperty ("color", "red");
		t.setProperty ("font", "TimesRoman");

		t.setFeatureValue ("length", 3);
		t.setFeatureValue ("containsVowel", 1);
		t.setFeatureValue ("in /usr/dict/words", 0);

		Alphabet dict = new Alphabet();
		FeatureVector fv = t.toFeatureVector (dict, false);
		assertTrue (fv.numLocations() == 2);
		assertTrue (fv.value (dict.lookupIndex("length")) == 3);
	}

	public void testTwo ()
	{
		try {
			URI uri = new URI ("file:/home/andrew/what-professors-do.html");
			System.out.println ("Scheme = " + uri.getScheme());
			File file = new File (uri);
			System.out.println (file.getCanonicalPath());

			file = new File ("what-professors-do.html");
			System.out.println ("Name: " + file.getName());
			System.out.println ("Parent: " + file.getParent());
			System.out.println ("Path: " + file.getPath());
			System.out.println ("Canonical: " + file.getCanonicalPath());
			System.out.println ("Absolute: " + file.getAbsolutePath());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Test suite ()
	{
		return new TestSuite(TestToken.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
