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
import java.io.IOException;

import cc.mallet.grmm.types.*;
import cc.mallet.types.tests.TestSerializable;

/**
 * Created: Aug 22, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestListVarSet.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestListVarSet extends TestCase {

  public TestListVarSet (String name)
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
    ListVarSet c1 = new ListVarSet (uni, Arrays.asList (vars));
    ListVarSet c2 = new ListVarSet (uni, Arrays.asList (vars));

    assertTrue(c1.equals (c2));
    assertTrue(c2.equals (c1));
    assertEquals (c1.hashCode(), c2.hashCode ());
  }

  public void testHashCodeByHashVarSet ()
  {
    Variable[] vars = new Variable [2];
    for (int i = 0; i < vars.length; i++) {
      vars[i] = new Variable(3);
    }

    Universe uni = vars[0].getUniverse ();
    ListVarSet c1 = new ListVarSet (uni, Arrays.asList (vars));
    HashVarSet c2 = new HashVarSet (vars);

    assertTrue (c1.equals (c2));
    assertEquals (c1.hashCode (), c2.hashCode ());
  }

  public void testEquals ()
  {
    Variable[] vars = new Variable [4];
    for (int i = 0; i < vars.length; i++) {
      vars[i] = new Variable(3);
    }

    Universe uni = vars[0].getUniverse ();
    ListVarSet c = new ListVarSet (uni, Arrays.asList (new Variable[] { vars[0], vars[3] }));
    HashVarSet c2 = new HashVarSet (c);

    assertTrue (c2.equals (c));
    assertTrue (c.equals (c2));
  }

  public void testContains ()
  {
    Variable[] vars = new Variable [4];
    for (int i = 0; i < vars.length; i++) {
      vars[i] = new Variable(3);
    }

    Universe uni = vars[0].getUniverse ();
    ListVarSet c = new ListVarSet (uni, Arrays.asList (new Variable[] { vars[0], vars[3] }));

    assertTrue (c.contains (vars[0]));
    assertTrue (!c.contains (vars[1]));
    assertTrue (!c.contains (vars[2]));
    assertTrue (c.contains (vars[3]));

    assertEquals (vars[0], c.get (0));
    assertEquals (vars[3], c.get (1));
    assertEquals (2, c.size ());
  }

  public void testSerialization () throws IOException, ClassNotFoundException
  {
    Variable[] vars_orig = new Variable [4];
    for (int i = 0; i < vars_orig.length; i++) {
      vars_orig[i] = new Variable(3);
    }

    Universe uni = vars_orig[0].getUniverse ();
    ListVarSet c_orig = new ListVarSet (uni, Arrays.asList (new Variable[] { vars_orig[0], vars_orig[3] }));

    ListVarSet c = (ListVarSet) TestSerializable.cloneViaSerialization (c_orig);
    Universe uni_new = c.get (0).getUniverse ();

    Variable[] vars = new Variable[] {
            uni_new.get (0),
            uni_new.get (1),
            uni_new.get (2),
            uni_new.get (3),
    };

    assertTrue (c.contains (vars[0]));
    assertTrue (!c.contains (vars[1]));
    assertTrue (!c.contains (vars[2]));
    assertTrue (c.contains (vars[3]));

    assertEquals (vars[0], c.get (0));
    assertEquals (vars[3], c.get (1));
    assertEquals (2, c.size ());
  }

  public void testAddAllOrdering ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] vars = new Variable[] { new Variable(2), new Variable (2) };
      Universe uni = vars[0].getUniverse ();
      ListVarSet vs = new ListVarSet (uni, Arrays.asList (vars));
      checkOrdering (vs, vars);
    }
  }

  public void testAddAllOrdering2 ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] vars = new Variable[] { new Variable(2), new Variable (2) };
      Universe uni = vars[0].getUniverse ();
      ListVarSet vs = new ListVarSet (uni, new ArrayList ());
      vs.addAll (Arrays.asList (vars));
      checkOrdering (vs, vars);
    }
  }

  public void testAddAllOrdering3 ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] vars = new Variable[] { new Variable(2), new Variable (2) };
      Universe uni = vars[0].getUniverse ();
      ListVarSet vsOld = new ListVarSet (uni, Arrays.asList (vars));
      ListVarSet vs = new ListVarSet (vsOld);
      checkOrdering (vs, vars);
    }
  }

  public void testIntersectionOrdering ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] varr1 = new Variable[] { new Variable(2), new Variable (2), new Variable (2) };
      Variable[] varr2 = new Variable[] { varr1[0], varr1[1] };

      Universe uni = varr1[0].getUniverse ();
      ListVarSet vs1 = new ListVarSet (uni, Arrays.asList (varr1));
      ListVarSet vs2 = new ListVarSet (uni, Arrays.asList (varr2));
      VarSet vs_inter = new HashVarSet (vs1.intersection (vs2));
      checkOrdering (vs_inter, varr2);

      VarSet vs_inter2 = new HashVarSet (vs2.intersection (vs1));
      checkOrdering (vs_inter2, varr2);
    }

  }

  public void testIntersectionOrderingToHash ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] varr1 = new Variable[] { new Variable(2), new Variable (2), new Variable (2) };
      Variable[] varr2 = new Variable[] { varr1[0], varr1[1] };

      Universe uni = varr1[0].getUniverse ();
      ListVarSet vs1 = new ListVarSet (uni, Arrays.asList (varr1));
      VarSet vs2 = new HashVarSet (Arrays.asList (varr2));
      VarSet vs_inter = new HashVarSet (vs1.intersection (vs2));
      checkOrdering (vs_inter, varr2);

      VarSet vs_inter2 = new HashVarSet (vs2.intersection (vs1));
      checkOrdering (vs_inter2, varr2);
    }

  }

  public void testIntersectionOrderingToBit ()
  {
    for (int rep = 0; rep < 1000; rep++) {
      Variable[] varr1 = new Variable[] { new Variable(2), new Variable (2), new Variable (2) };
      Variable[] varr2 = new Variable[] { varr1[0], varr1[1] };

      Universe uni = varr1[0].getUniverse ();
      ListVarSet vs1 = new ListVarSet (uni, Arrays.asList (varr1));
      VarSet vs2 = new BitVarSet (uni, Arrays.asList (varr2));
      VarSet vs_inter = new HashVarSet (vs1.intersection (vs2));
      checkOrdering (vs_inter, varr2);

      VarSet vs_inter2 = new HashVarSet (vs2.intersection (vs1));
      checkOrdering (vs_inter2, varr2);
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
    return new TestSuite (TestListVarSet.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestListVarSet (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
