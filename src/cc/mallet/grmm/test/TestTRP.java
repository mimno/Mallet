/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.test;

import junit.framework.*;

import java.util.Iterator;
import java.util.Random;

import cc.mallet.grmm.inference.RandomGraphs;
import cc.mallet.grmm.inference.TRP;
import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;

/**
 * $Id: TestTRP.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestTRP extends TestCase {

  public TestTRP (String name)
  {
    super (name);
  }

  public void testEarlyStopping ()
  {
    FactorGraph grid = RandomGraphs.randomAttractiveGrid (5, 0.5, new Random (2413421));
    TRP trp = new TRP (new TRP.IterationTerminator (1));
    trp.setRandomSeed (14312341);

    trp.computeMarginals (grid);

    boolean oneIsDifferent = false;

    // check no exceptions thrown when asking for all marginals,
    //  and check that at least one factors' belief has changed
    //  from the choice at zero iterations.
    for (Iterator it = grid.factorsIterator (); it.hasNext();) {
      Factor f = (Factor) it.next ();
      Factor marg = trp.lookupMarginal (f.varSet ());// test no exception thrown
      if (!marg.almostEquals (f.duplicate ().normalize ())) {
        oneIsDifferent = true;
      }
    }

    assertTrue (oneIsDifferent);
  }

  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestTRP.class);
  }

  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestTRP (args[i]));
      }
    } else {
      theSuite = suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
