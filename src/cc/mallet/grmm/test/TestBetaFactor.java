/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.ModelReader;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.Randoms;
import gnu.trove.list.array.TDoubleArrayList;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * $Id: TestBetaFactor.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestBetaFactor extends TestCase {

  public TestBetaFactor (String name)
  {
    super (name);
  }

  public void testVarSet ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Factor f = new BetaFactor (var, 0.5, 0.5);
    assertEquals (1, f.varSet ().size ());
    assertTrue (f.varSet().contains (var));
  }

  public void testValue ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Factor f = new BetaFactor (var, 1.0, 1.2);
    Assignment assn = new Assignment (var, 0.7);
    assertEquals (0.94321, f.value(assn), 1e-5);
  }

  public void testSample ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Randoms r = new Randoms (2343);
    Factor f = new BetaFactor (var, 0.7, 0.5);
    TDoubleArrayList lst = new TDoubleArrayList ();
    for (int i = 0; i < 100000; i++) {
      Assignment assn = f.sample (r);
      lst.add (assn.getDouble (var));
    }

    double[] vals = lst.toArray ();
    double mean = MatrixOps.mean (vals);
    assertEquals (0.7 / (0.5 + 0.7), mean, 0.01);
  }


  public void testSample2 ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Randoms r = new Randoms (2343);
    Factor f = new BetaFactor (var, 0.7, 0.5, 3.0, 8.0);
    TDoubleArrayList lst = new TDoubleArrayList ();
    for (int i = 0; i < 100000; i++) {
      Assignment assn = f.sample (r);
      lst.add (assn.getDouble (var));
    }

    double[] vals = lst.toArray ();
    double mean = MatrixOps.mean (vals);
    assertEquals (5.92, mean, 0.01);
  }

  static String mdlstr = "VAR u1 u2 : continuous\n" +
          "u1 ~ Beta 0.2 0.7\n" +
          "u2 ~ Beta 1.0 0.3\n";

  public void testSliceInFg () throws IOException
  {
    ModelReader reader = new ModelReader ();
    FactorGraph fg = reader.readModel (new BufferedReader (new StringReader (TestBetaFactor.mdlstr)));
    Variable u1 = fg.findVariable ("u1");
    Variable u2 = fg.findVariable ("u2");
    Assignment assn = new Assignment (new Variable[] { u1, u2 }, new double[] { 0.25, 0.85 });

    FactorGraph fg2 = (FactorGraph) fg.slice (assn);
    assertEquals (2, fg2.factors ().size ());
    assertEquals (0.59261 * 1.13202, fg2.value (new Assignment ()), 1e-5);
  }

  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestBetaFactor.class);
  }

  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestBetaFactor (args[i]));
      }
    } else {
      theSuite = (TestSuite) TestBetaFactor.suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
