/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.util.tests;

import cc.mallet.util.Strings;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Created: Jan 19, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestStrings.java,v 1.1 2007/10/22 21:37:57 mccallum Exp $
 */
public class TestStrings extends TestCase {

  public TestStrings (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite(TestStrings.class);
  }

  public static void testCount ()
  {
    assertEquals (5, Strings.count ("abracadabra", 'a'));
    assertEquals (0, Strings.count ("hocus pocus", 'z'));
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestStrings (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
