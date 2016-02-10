/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.test;

import cc.mallet.grmm.inference.GibbsSampler;
import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.TableFactor;
import cc.mallet.grmm.types.Variable;
import cc.mallet.util.Randoms;
import junit.framework.*;

/**
 * $Id: TestGibbsSampler.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestGibbsSampler extends TestCase {

  public TestGibbsSampler (String name)
  {
    super (name);
  }

  // Tests finding a feasible initial assignment in a sparse model
  public void testInitialAssignment ()
  {
    Variable[] vars = new Variable[] { new Variable (3), new Variable (3), new Variable (3) };

    Variable[] vars1 = new Variable[]{ vars[0], vars[1] };
    double[] vals1 = new double[] { 0, 0.2, 0.8, 0, 0.7, 0.3, 0, 0.5, 0.5 };
    Factor tbl1 = new TableFactor (vars1, vals1);

    Variable[] vars2 = new Variable[]{ vars[1], vars[2] };
    double[] vals2 = new double[] { 0.2, 0.2, 0.8, 0.7, 0, 0.7, 0.3, 0, 0.5 };
    Factor tbl2 = new TableFactor (vars2, vals2);

    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);
    System.out.println (fg.dumpToString ());

    GibbsSampler gs = new GibbsSampler (new Randoms (324123), 10);
    gs.sample (fg, 10);  // assert no exception
  }


  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestGibbsSampler.class);
  }

  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestGibbsSampler (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
