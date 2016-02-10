/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.test;

import junit.framework.*;

import java.util.List;
import java.util.ArrayList;

import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.grmm.inference.TRP;
import cc.mallet.grmm.types.*;
import cc.mallet.util.Randoms;

/**
 * $Id: TestAbstractBeliefPropagation.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestAbstractBeliefPropagation extends TestCase {

  public TestAbstractBeliefPropagation (String name)
  {
    super (name);
  }

  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestAbstractBeliefPropagation.class);
  }

  public void testBadVariable ()
  {
    FactorGraph fg = createBoltzmannChain (5);
    Assignment assn = fg.sampleContinuousVars (new Randoms (23423));
    FactorGraph sliced = (FactorGraph) fg.slice (assn);
    Inferencer bp = new TRP ();
    bp.computeMarginals (sliced);

    try {
      bp.lookupMarginal (new Variable (2));
      fail ("Expected exception");
    } catch (IllegalArgumentException e) {
      // expected
      System.out.println ("OK: As expected, got exception "+e);
    }
  }

  static FactorGraph createBoltzmannChain (int len)
  {
    Randoms r = new Randoms (3241321);

    List<Variable> vars = new ArrayList<Variable> ();
    for (int i = 0; i < len; i++) {
      Variable x_i = new Variable (2);
      x_i.setLabel ("X_"+i);
      vars.add (x_i);
    }

    List<Factor> factors = new ArrayList<Factor> (vars.size ());

    // node factors
    for (int i = 0; i < len; i++) {
      double u = r.nextUniform (-4.0, 4.0);
      factors.add (new BoltzmannUnaryFactor (vars.get (i), u));
    }

    // edge factors
    for (int i = 0; i < len-1; i++) {
      Variable alpha = new Variable (Variable.CONTINUOUS);
      alpha.setLabel ("ALPHA_"+i);
      factors.add (new UniformFactor (alpha, -4.0, 4.0));
      factors.add (new PottsTableFactor (vars.get (i), vars.get(i+1), alpha));
    }

    return new FactorGraph (factors);
  }



  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestAbstractBeliefPropagation (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
