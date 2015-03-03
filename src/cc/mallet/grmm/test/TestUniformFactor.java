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
 * $Id: TestUniformFactor.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestUniformFactor extends TestCase {

  public TestUniformFactor (String name)
  {
    super (name);
  }

  public void testVarSet ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Factor f = new UniformFactor (var, -1.0, 1.5);
    assertEquals (1, f.varSet ().size ());
    assertTrue (f.varSet().contains (var));
  }

  public void testSample ()
  {
    Variable var = new Variable (Variable.CONTINUOUS);
    Randoms r = new Randoms (2343);
    Factor f = new UniformFactor (var, -1.0, 1.5);
    TDoubleArrayList lst = new TDoubleArrayList();
    for (int i = 0; i < 10000; i++) {
      Assignment assn = f.sample (r);
      lst.add (assn.getDouble (var));
    }

    double[] vals = lst.toArray();
    double mean = MatrixOps.mean (vals);
    assertEquals (0.25, mean, 0.01);
  }

  static String mdlstr = "VAR u1 u2 : continuous\n" +
          "u1 ~ Uniform 0.0 10.0\n" +
          "u2 ~ Uniform 5.0 7.0\n";

  public void testSliceInFg () throws IOException
  {
    ModelReader reader = new ModelReader ();
    FactorGraph fg = reader.readModel (new BufferedReader (new StringReader (mdlstr)));
    Variable u1 = fg.findVariable ("u1");
    Variable u2 = fg.findVariable ("u2");
    Assignment assn = new Assignment (new Variable[] { u1, u2 }, new double[] { 6.0, 6.0 });

    FactorGraph fg2 = (FactorGraph) fg.slice (assn);
    fg2.dump ();
    assertEquals (2, fg2.factors ().size ());
    assertEquals (1.0/20, fg2.value (new Assignment ()), 1e-5);

    fg2.addFactor (new ConstantFactor (10.0));
    assertEquals (0.5, fg2.value (new Assignment ()), 1e-5);
  }

  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestUniformFactor.class);
  }

  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestUniformFactor (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
