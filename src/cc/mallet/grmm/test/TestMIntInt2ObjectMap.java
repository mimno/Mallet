/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import junit.framework.*;

import java.io.IOException;

import cc.mallet.grmm.util.MIntInt2ObjectMap;
import cc.mallet.types.tests.TestSerializable;

/**
 * Created: Dec 14, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestMIntInt2ObjectMap.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestMIntInt2ObjectMap extends TestCase {

  public TestMIntInt2ObjectMap (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite (TestMIntInt2ObjectMap.class);
  }

  public void testReverse ()
  {
    MIntInt2ObjectMap map = new MIntInt2ObjectMap ();
    map.put (0, 2, "A");
    map.put (2, 0, "a");
    map.put (0, 5, "C");
    map.put (3, 1, "D");
    map.put (2, 0, "aa");

    assertEquals (4, map.size ());
    assertEquals ("A", map.get (0, 2));
    assertEquals ("aa", map.get (2, 0));
  }

  public void testSerializable () throws IOException, ClassNotFoundException
  {
    MIntInt2ObjectMap map = new MIntInt2ObjectMap ();
    map.put (0, 2, "A");
    map.put (2, 0, "a");
    map.put (0, 5, "C");
    map.put (3, 1, "D");
    map.put (2, 0, "aa");

    MIntInt2ObjectMap map2 = (MIntInt2ObjectMap) TestSerializable.cloneViaSerialization (map);
    assertEquals (4, map2.size ());
    assertEquals ("A", map2.get (0, 2));
    assertEquals ("aa", map2.get (2, 0));

  }
  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestMIntInt2ObjectMap (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
