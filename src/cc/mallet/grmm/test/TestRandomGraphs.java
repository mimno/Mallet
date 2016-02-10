/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import junit.framework.*;
import junit.textui.TestRunner;

import java.util.Random;
import java.util.Iterator;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

import cc.mallet.grmm.inference.*;
import cc.mallet.grmm.types.*;


/**
 * Created: Mar 26, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestRandomGraphs.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestRandomGraphs extends TestCase {

  public TestRandomGraphs (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite (TestRandomGraphs.class);
  }

  public void testAttractiveGraphs () throws IOException
  {
    Random r = new Random (31421);
    for (int rep = 0; rep < 5; rep++) {
      FactorGraph mdl = RandomGraphs.randomAttractiveGrid (5, 0.5, r);
      System.out.println ("************"); mdl.dump ();

      TRP trp = TRP.createForMaxProduct ();
      trp.computeMarginals (mdl);
      Assignment assn = trp.bestAssignment ();

      PrintWriter out = new PrintWriter (new FileWriter (new File ("attract."+rep+".dot")));
      mdl.printAsDot (out, assn);
      out.close ();
    }
  }

  public void testRepulsiveGraphs () throws IOException
  {
    Random r = new Random (31421);
    for (int rep = 0; rep < 5; rep++) {
      FactorGraph mdl = RandomGraphs.randomRepulsiveGrid (5, 0.5, r);
      TRP trp = TRP.createForMaxProduct ();
      trp.computeMarginals (mdl);
      Assignment assn = trp.bestAssignment ();

      PrintWriter out = new PrintWriter (new FileWriter (new File ("repulse."+rep+".dot")));
      mdl.printAsDot (out, assn);
      out.close ();
    }
  }

  public void testFrustratedGraphs () throws IOException
  {
    Random r = new Random (31421);
    for (int rep = 0; rep < 5; rep++) {
      FactorGraph mdl = RandomGraphs.randomFrustratedGrid (5, 0.5, r);
      TRP trp = TRP.createForMaxProduct ();
      trp.computeMarginals (mdl);
      Assignment assn = trp.bestAssignment ();

      PrintWriter out = new PrintWriter (new FileWriter (new File ("mixed."+rep+".dot")));
      mdl.printAsDot (out, assn);
      out.close ();
    }
  }

  public void testFrustratedIsGrid () throws IOException
  {
    Random r = new Random (0);
    for (int rep = 0; rep < 100; rep++) {
      FactorGraph mdl = RandomGraphs.randomFrustratedGrid (10, 1.0, r);
      // 100 variable factors + 180 edge factors
      assertEquals (280, mdl.factors ().size ());
      assertEquals (100, mdl.numVariables ());

      int[] counts = new int [6];
      for (int i = 0; i < mdl.numVariables (); i++) {
        Variable var = mdl.get (i);
        int degree = mdl.getDegree (var);
        assertTrue ("Variable "+var+" has degree "+degree, (degree >= 3) && (degree <= 5));
        counts[degree]++;
      }

      assertEquals (counts[0], 0);
      assertEquals (counts[1], 0);
      assertEquals (counts[2], 0);
      assertEquals (counts[3], 4);
      assertEquals (counts[4], 32);
      assertEquals (counts[5], 64);
    }
  }

  public void testUniformGrid ()
  {
    UndirectedGrid grid = (UndirectedGrid) RandomGraphs.createUniformGrid (3);
    assertEquals (9, grid.numVariables ());
    assertEquals (12, grid.factors ().size());
    BruteForceInferencer inf = new BruteForceInferencer ();
    TableFactor joint = (TableFactor) inf.joint (grid);
    for (AssignmentIterator it = joint.assignmentIterator (); it.hasNext(); it.advance ()) {
      assertEquals (-9 * Math.log (2), joint.logValue (it), 1e-3);
    }
  }

  public void testUniformGridWithObservations ()
  {
    FactorGraph grid = RandomGraphs.createGridWithObs (
            new RandomGraphs.UniformFactorGenerator (),
            new RandomGraphs.UniformFactorGenerator (),
            3);

    assertEquals (18, grid.numVariables ());
    assertEquals (12 + 9, grid.factors ().size());
    Inferencer inf = new LoopyBP ();
    inf.computeMarginals (grid);
    for (Iterator it = grid.variablesIterator (); it.hasNext ();) {
      Variable var = (Variable) it.next ();
      Factor marg = inf.lookupMarginal (var);
      for (AssignmentIterator assnIt = marg.assignmentIterator (); assnIt.hasNext();) {
        assertEquals (-Math.log (2), marg.logValue (assnIt), 1e-3);
        assnIt.advance ();
      }
    }
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestRandomGraphs (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    TestRunner.run (theSuite);
  }

}
