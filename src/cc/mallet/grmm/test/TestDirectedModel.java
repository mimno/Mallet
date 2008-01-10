/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import cc.mallet.grmm.inference.BruteForceInferencer;
import cc.mallet.grmm.types.*;
import junit.framework.*;

/**
 * Created: Mar 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestDirectedModel.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestDirectedModel extends TestCase {

  private CPT pA;
  private CPT pB;
  private CPT pC;
  private DiscreteFactor fA;
  private DiscreteFactor fB;
  private DiscreteFactor fC;
  private Variable[] vars;
  private Variable A;
  private Variable B;
  private Variable C;

  public TestDirectedModel (String name)
  {
    super (name);
    A = new Variable (2);
    B = new Variable (2);
    C = new Variable (2);
    vars = new Variable[] { A, B, C };

    fA = LogTableFactor.makeFromValues (A, new double[] { 1, 4 });
    fB = LogTableFactor.makeFromValues (B, new double[] { 3, 2 });
    double[] vals = new double[] { 3, 7, 5, 5, 9, 1, 6, 4, };
    fC = new TableFactor (vars, vals);

    pA = Factors.normalizeAsCpt ((AbstractTableFactor) fA.duplicate (), A);
    pB = Factors.normalizeAsCpt ((AbstractTableFactor) fB.duplicate (), B);
    pC = Factors.normalizeAsCpt ((AbstractTableFactor) fC.duplicate (), C);
  }

  public void testSimpleModel ()
  {
    FactorGraph fg1 = new FactorGraph (vars);
    fg1.addFactor (pA);
    fg1.addFactor (pB);
    fg1.addFactor (fC);

    DirectedModel dm = new DirectedModel (vars);
    dm.addFactor (pA);
    dm.addFactor (pB);
    dm.addFactor (pC);

    BruteForceInferencer inf = new BruteForceInferencer ();
    DiscreteFactor joint1 = (DiscreteFactor) inf.joint (fg1);
    DiscreteFactor joint2 = (DiscreteFactor) inf.joint (dm);

    comparePotentials (joint1, joint2);
  }

  private void comparePotentials (DiscreteFactor fActual, DiscreteFactor fExpected)
  {
    double[] actual = fActual.toValueArray ();
    double[] expected = fExpected.toValueArray ();
    assertEquals (expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertEquals (expected[i], actual[i], 0.001);
    }
  }

  public void testCycleChecking ()
  {
    DirectedModel dm = new DirectedModel (vars);
    dm.addFactor (pA);
    dm.addFactor (pB);
    dm.addFactor (pC);

    try {
      TableFactor f1 = new TableFactor (new Variable[] { B, C });
      dm.addFactor (new CPT (f1, B));
      assertTrue ("Test failed: No exception thrown.", false);
    } catch (IllegalArgumentException e) {
      // Exception is expected
    }

    try {
      TableFactor f1 = new TableFactor (new Variable[] { A, C });
      dm.addFactor (new CPT (f1, A));
      assertTrue ("Test failed: No exception thrown.", false);
    } catch (IllegalArgumentException e) {
      // Exception is expected
    }
  }

  public void testCptOfVar ()
  {
    DirectedModel dm = new DirectedModel (vars);
    dm.addFactor (pA);
    dm.addFactor (pB);
    dm.addFactor (pC);
    assertTrue (pA == dm.getCptofVar (A));
    assertTrue (pB == dm.getCptofVar (B));
    assertTrue (pC == dm.getCptofVar (C));
  }

  public void testFactorReplace ()
  {
    DirectedModel dm = new DirectedModel (vars);
    dm.addFactor (pA);
    dm.addFactor (pB);
    dm.addFactor (pC);
    assertEquals (3, dm.factors ().size ());

    TableFactor f1 = new TableFactor (new Variable[] { B, C });
    CPT p1 = new CPT (f1, C);
    try {
      dm.addFactor (p1);
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public static Test suite ()
  {
    return new TestSuite (TestDirectedModel.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestDirectedModel (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
