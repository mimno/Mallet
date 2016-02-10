/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import cc.mallet.grmm.types.*;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.Randoms;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import gnu.trove.TDoubleArrayList;

/**
 * $Id: TestUniNormalFactor.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestUniNormalFactor extends TestCase {

  public TestUniNormalFactor (String name)
  {
    super (name);
  }

  public void testVarSet ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Factor f = new UniNormalFactor (var, -1.0, 1.5);
    assertEquals (1, f.varSet ().size ());
    assertTrue (f.varSet().contains (var));
  }


  public void testValue ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Factor f = new UniNormalFactor (var, -1.0, 2.0);

    Assignment assn1 = new Assignment (var, -1.0);
    assertEquals (0.2821, f.value (assn1), 1e-4);
    assertEquals (Math.log (0.2821), f.logValue (assn1), 1e-4);

    Assignment assn2 = new Assignment (var, 1.5);
    assertEquals (0.05913, f.value (assn2), 1e-4);
    assertEquals (Math.log (0.05913), f.logValue (assn2), 1e-4);

  }

  public void testSample ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Randoms r = new Randoms (2343);
    Factor f = new UniNormalFactor (var, -1.0, 2.0);
    TDoubleArrayList lst = new TDoubleArrayList ();
    for (int i = 0; i < 10000; i++) {
      Assignment assn = f.sample (r);
      lst.add (assn.getDouble (var));
    }

    double[] vals = lst.toNativeArray ();
    double mean = MatrixOps.mean (vals);
    double std = MatrixOps.stddev (vals);
    assertEquals (-1.0, mean, 0.025);
    assertEquals (Math.sqrt(2.0), std, 0.01);
  }

  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestUniNormalFactor.class);
  }

  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestUniNormalFactor (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
