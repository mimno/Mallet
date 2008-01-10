/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import org._3pq.jgrapht.UndirectedGraph;
import org._3pq.jgrapht.GraphHelper;

import cc.mallet.grmm.inference.RandomGraphs;
import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.Graphs;
import cc.mallet.util.ArrayUtils;

/**
 * Created: Mar 17, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestUndirectedModel.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestUndirectedModel extends TestCase {

  public TestUndirectedModel (String name)
  {
    super (name);
  }


  public void testOutputToDot () throws IOException
  {
    FactorGraph mdl = TestInference.createRandomGrid (3, 4, 2, new Random (4234));
    PrintWriter out = new PrintWriter (new FileWriter (new File ("grmm-model.dot")));
    mdl.printAsDot (out);
    out.close ();
    System.out.println ("Now you can open up grmm-model.dot in Graphviz.");
  }

  /**
   * Tests that models can be created that have multiple factors over the same variable, and that
   * potentialOfVertex returns the product in that case.
   */
  public void testMultipleNodePotentials ()
  {
    Variable var = new Variable (2);
    FactorGraph mdl = new FactorGraph (new Variable[]{var});

    Factor ptl1 = new TableFactor (var, new double[]{0.5, 0.5});
    mdl.addFactor (ptl1);

    Factor ptl2 = new TableFactor (var, new double[]{0.25, 0.25});
    mdl.addFactor (ptl2);

    // verify that factorOf(var) doesn't work
    try {
      mdl.factorOf (var);
      fail ();
    } catch (RuntimeException e) {} // expected

    List factors = mdl.allFactorsOf (var);
    Factor total = TableFactor.multiplyAll (factors);
    double[] expected = {0.125, 0.125};
    assertTrue ("Arrays not equal\n  Expected " + ArrayUtils.toString (expected)
            + "\n  Actual " + ArrayUtils.toString (((TableFactor) total).toValueArray ()),
                Arrays.equals (expected, ((TableFactor) total).toValueArray ()));
  }

  /**
   * Tests that models can be created that have multiple factors over the same edge, and that
   * potentialOfEdge returns the product in that case.
   */
  public void testMultipleEdgePotentials ()
  {
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2};

    FactorGraph mdl = new FactorGraph (vars);

    Factor ptl1 = new TableFactor (vars, new double[]{0.5, 0.5, 0.5, 0.5});
    mdl.addFactor (ptl1);

    Factor ptl2 = new TableFactor (vars, new double[]{0.25, 0.25, 0.5, 0.5});
    mdl.addFactor (ptl2);

    try {
      mdl.factorOf (v1, v2);
      fail ();
    } catch (RuntimeException e) {}

    Collection factors = mdl.allFactorsContaining (new HashVarSet (vars));
    assertEquals (2, factors.size ());
    assertTrue (factors.contains (ptl1));
    assertTrue (factors.contains (ptl2));

    double[] vals = {0.125, 0.125, 0.25, 0.25};
    Factor total = TableFactor.multiplyAll (factors);
    Factor expected = new TableFactor (vars, vals);

    assertTrue ("Arrays not equal\n  Expected " + ArrayUtils.toString (vals)
            + "\n  Actual " + ArrayUtils.toString (((TableFactor) total).toValueArray ()),
                expected.almostEquals (total, 1e-10));
  }

  public void testPotentialConnections ()
  {
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable v3 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2, v3};
    FactorGraph mdl = new FactorGraph ();

    TableFactor ptl = new TableFactor (vars, new double [8]);
    mdl.addFactor (ptl);

    assertTrue (mdl.isAdjacent (v1, v2));
    assertTrue (mdl.isAdjacent (v2, v3));
    assertTrue (mdl.isAdjacent (v1, v3));
  }

  public void testThreeNodeModel ()
  {
    Random r = new Random (23534709);

    FactorGraph mdl = new FactorGraph ();
    Variable root = new Variable (2);
    Variable childL = new Variable (2);
    Variable childR = new Variable (2);

    mdl.addFactor (root, childL, RandomGraphs.generateMixedPotentialValues (r, 1.5));
    mdl.addFactor (root, childR, RandomGraphs.generateMixedPotentialValues (r, 1.5));

//    assertTrue (mdl.isConnected (root, childL));
//    assertTrue (mdl.isConnected (root, childR));
//    assertTrue (mdl.isConnected (childL, childR));
    assertTrue (mdl.isAdjacent (root, childR));
    assertTrue (mdl.isAdjacent (root, childL));
    assertTrue (!mdl.isAdjacent (childL, childR));

    assertTrue (mdl.factorOf (root, childL) != null);
    assertTrue (mdl.factorOf (root, childR) != null);
  }

  // Verify that potentialOfVertex and potentialOfEdge (which use
  // caches) are consistent with the potentials set.
  public void testUndirectedCaches ()
  {
    List models = TestInference.createTestModels ();
    for (Iterator it = models.iterator (); it.hasNext ();) {
      FactorGraph mdl = (FactorGraph) it.next ();
      verifyCachesConsistent (mdl);
    }
  }


  private void verifyCachesConsistent (FactorGraph mdl)
  {
    Factor pot, pot2, pot3;
    for (Iterator it = mdl.factors ().iterator (); it.hasNext ();) {
      pot = (Factor) it.next ();
      //				System.out.println("Testing model "+i+" potential "+pot);

      Object[] vars = pot.varSet ().toArray ();
      switch (vars.length) {
        case 1:
          pot2 = mdl.factorOf ((Variable) vars[0]);
          assertTrue (pot == pot2);
          break;


        case 2:
          Variable var1 = (Variable) vars[0];
          Variable var2 = (Variable) vars[1];
          pot2 = mdl.factorOf (var1, var2);
          pot3 = mdl.factorOf (var2, var1);
          assertTrue (pot == pot2);
          assertTrue (pot2 == pot3);
          break;

          // Factors of size > 2 aren't now cached.
        default:
          break;
      }
    }
  }

  // Verify that potentialOfVertex and potentialOfEdge (which use
  // caches) are consistent with the potentials set even if a vertex is removed.
  public void testUndirectedCachesAfterRemove ()
  {
    List models = TestInference.createTestModels ();
    for (Iterator mdlIt = models.iterator (); mdlIt.hasNext ();) {
      FactorGraph mdl = (FactorGraph) mdlIt.next ();
      mdl = (FactorGraph) mdl.duplicate ();
      mdl.remove (mdl.get (0));

      // Verify that indexing correct
      for (Iterator it = mdl.variablesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        int idx = mdl.getIndex (var);
        assertTrue (idx >= 0);
        assertTrue (idx < mdl.numVariables ());
      }

      // Verify that caches consistent
      verifyCachesConsistent (mdl);
    }
  }

  public void testMdlToGraph ()
  {
    List models = TestInference.createTestModels ();
    for (Iterator mdlIt = models.iterator (); mdlIt.hasNext ();) {
      UndirectedModel mdl = (UndirectedModel) mdlIt.next ();
      UndirectedGraph g = Graphs.mdlToGraph (mdl);
      Set vertices = g.vertexSet ();

      // check the number of vertices
      assertEquals (mdl.numVariables (), vertices.size ());

      // check the number of edges
      int numEdgePtls = 0;
      for (Iterator factorIt = mdl.factors ().iterator (); factorIt.hasNext ();) {
        Factor factor =  (Factor) factorIt.next ();
        if (factor.varSet ().size() == 2) numEdgePtls++;
      }
      assertEquals (numEdgePtls, g.edgeSet ().size ());

      // check that the neighbors of each vertex contain at least some of what they're supposed to
      Iterator it = vertices.iterator ();
      while (it.hasNext ()) {
        Variable var = (Variable) it.next ();
        assertTrue (vertices.contains (var));
        Set neighborsInG = new HashSet (GraphHelper.neighborListOf (g, var));
        neighborsInG.add (var);

        Iterator factorIt = mdl.allFactorsContaining (var).iterator ();
        while (factorIt.hasNext ()) {
          Factor factor = (Factor) factorIt.next ();
          assertTrue (neighborsInG.containsAll (factor.varSet ()));
        }
      }
    }
  }

  public void testFactorOfSet ()
  {
    Variable[] vars = new Variable [3];
    for (int i = 0; i < vars.length; i++) {
      vars[i] = new Variable (2);
    }
    Factor factor = new TableFactor (vars, new double[] { 0, 1, 2, 3, 4, 5, 6, 7 });
    
    FactorGraph fg = new FactorGraph (vars);
    fg.addFactor (factor);

    assertTrue (factor == fg.factorOf (factor.varSet ()));

    HashSet set = new HashSet (factor.varSet ());
    assertTrue (factor == fg.factorOf (set));
    set.remove (vars[0]);
    assertTrue (null == fg.factorOf (set));
  }

  public static Test suite ()
  {
    return new TestSuite (TestUndirectedModel.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestUndirectedModel (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
