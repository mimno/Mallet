/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Arrays;
import java.util.ArrayList;

import cc.mallet.grmm.types.*;

/**
 * Created: Aug 22, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestBitVarSet.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestBitVarSet extends TestCase {

  public TestBitVarSet (String name)
  {
    super (name);
  }

  public void testEqualsHashCode ()
  {
    Variable[] vars = new Variable [4];
    for (int i = 0; i < vars.length; i++) {
      vars[i] = new Variable(3);
    }

    Universe uni = vars[0].getUniverse ();
    BitVarSet c1 = new BitVarSet (uni, Arrays.asList (vars));
    BitVarSet c2 = new BitVarSet (uni, Arrays.asList (vars));

    assertTrue(c1.equals (c2));
    assertTrue(c2.equals (c1));
    assertEquals (c1.hashCode(), c2.hashCode ());
  }

  public void testAddAllOrdering ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] vars = new Variable[] { new Variable(2), new Variable (2) };
      Universe uni = vars[0].getUniverse ();
      BitVarSet vs = new BitVarSet (uni, Arrays.asList (vars));
      checkOrdering (vs, vars);
    }
  }

  public void testAddAllOrdering2 ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] vars = new Variable[] { new Variable(2), new Variable (2) };
      Universe uni = vars[0].getUniverse ();
      BitVarSet vs = new BitVarSet (uni, new ArrayList ());
      vs.addAll (Arrays.asList (vars));
      checkOrdering (vs, vars);
    }
  }

  public void testAddAllOrdering3 ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] vars = new Variable[] { new Variable(2), new Variable (2) };
      Universe uni = vars[0].getUniverse ();
      BitVarSet vsOld = new BitVarSet (uni, Arrays.asList (vars));
      BitVarSet vs = new BitVarSet (vsOld);
      checkOrdering (vs, vars);
    }
  }

  private void checkOrdering (VarSet vs, Variable[] vars)
  {
    assertEquals (vars.length, vs.size ());
    for (int i = 0; i < vars.length; i++) {
      assertEquals (vars[i], vs.get (i));
    }
  }

  public static Test suite ()
  {
    return new TestSuite (TestBitVarSet.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestBitVarSet (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
