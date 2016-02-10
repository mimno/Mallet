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


import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;

import cc.mallet.grmm.inference.RandomGraphs;
import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.ModelReader;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.Randoms;
import cc.mallet.util.Timing;

/**
 * Created: Mar 17, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestFactorGraph.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestFactorGraph extends TestCase {

  private Variable[] vars;
  private TableFactor tbl1;
  private TableFactor tbl2;
  private TableFactor tbl3;
  private LogTableFactor ltbl1;
  private LogTableFactor ltbl2;

  public TestFactorGraph (String name)
  {
    super (name);
  }

  protected void setUp () throws Exception
  {
    vars = new Variable[] {
            new Variable (2),
            new Variable (2),
            new Variable (2),
            new Variable (2),
    };

    tbl1 = new TableFactor (new Variable[] { vars[0], vars[1] }, new double[] { 0.8, 0.1, 0.1, 0.8 });
    tbl2 = new TableFactor (new Variable[] { vars[1], vars[2] }, new double[] { 0.2, 0.7, 0.8, 0.2 });
    tbl3 = new TableFactor (new Variable[] { vars[2], vars[3] }, new double[] { 0.2, 0.4, 0.6, 0.4 });

    ltbl1 = LogTableFactor.makeFromValues (new Variable[] { vars[0], vars[1] }, new double[] { 0.8, 0.1, 0.1, 0.8 });
    ltbl2 = LogTableFactor.makeFromValues (new Variable[] { vars[1], vars[2] }, new double[] { 0.2, 0.7, 0.8, 0.2 });

  }

  public void testMultiplyBy ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    assertEquals (2, fg.factors ().size());
    assertTrue (fg.factors ().contains (tbl1));
    assertTrue (fg.factors ().contains (tbl2));

    assertEquals (3, fg.numVariables ());
    assertTrue (fg.variablesSet ().contains (vars[0]));
    assertTrue (fg.variablesSet ().contains (vars[1]));
    assertTrue (fg.variablesSet ().contains (vars[2]));
  }

  public void testNumVariables ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);
    assertEquals (3, fg.numVariables ());
  }

  public void testMultiply ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    FactorGraph fg2 = (FactorGraph) fg.multiply (tbl3);
    assertEquals (2, fg.factors ().size());
    assertEquals (3, fg2.factors ().size());
    assertTrue (!fg.factors ().contains (tbl3));
    assertTrue (fg2.factors ().contains (tbl3));
  }

  public void testValue ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);


    Assignment assn = new Assignment (fg.varSet ().toVariableArray (), new int[] { 0, 1, 0 });
    assertEquals (0.08, fg.value (assn), 1e-5);
  }

  public void testMarginalize ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    Factor marg = fg.marginalize (vars[1]);
    Factor expected = new TableFactor (vars[1], new double[] { 0.81, 0.9 });

    assertTrue (expected.almostEquals (marg));
  }

  public void testSum ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);
    assertEquals (1.71, fg.sum (), 1e-5);
  }

  public void testNormalize ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);
    fg.normalize ();
    assertEquals (1.0, fg.sum(), 1e-5);
  }

  public void testLogNormalize ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (ltbl1);
    fg.multiplyBy (ltbl2);
    fg.normalize ();
    assertEquals (1.0, fg.sum(), 1e-5);
  }

  public void testEmbeddedFactorGraph ()
  {
    FactorGraph embeddedFg = new FactorGraph ();
    embeddedFg.multiplyBy (tbl1);
    embeddedFg.multiplyBy (tbl2);

    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (embeddedFg);
    fg.multiplyBy (tbl3);

    assertEquals (4, fg.varSet ().size ());
    assertEquals (2, fg.factors ().size ());

    Assignment assn = new Assignment (fg.varSet ().toVariableArray (), new int [4]);
    assertEquals (0.032, fg.value (assn), 1e-5);

    AbstractTableFactor tbl = fg.asTable ();
    assertEquals (4, tbl.varSet ().size ());
    assertEquals (0.032, tbl.value (assn), 1e-5);

  }

  public void testAsTable ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    AbstractTableFactor actual = fg.asTable ();
    AbstractTableFactor expected = (AbstractTableFactor) tbl1.multiply (tbl2);

    assertTrue (expected.almostEquals (actual));
  }

  public void testTableTimesFg ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    Factor product = tbl3.multiply (fg);

    assertTrue (product instanceof AbstractTableFactor);
    assertEquals (4, product.varSet ().size ());

    Assignment assn = new Assignment (product.varSet ().toVariableArray (), new int [4]);
    assertEquals (0.032, product.value (assn), 1e-5);
  }

  public void testLogTableTimesFg ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    Factor product = ltbl1.multiply (fg);

    assertTrue (product instanceof AbstractTableFactor);
    assertEquals (3, product.varSet ().size ());

    Assignment assn = new Assignment (product.varSet ().toVariableArray (), new int [3]);
    assertEquals (0.128, product.value (assn), 1e-5);
  }

  public void testRemove ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);
    assertEquals (2, fg.getDegree (vars[1]));

    fg.divideBy (tbl1);

    assertEquals (2, fg.varSet ().size ());

    Assignment assn = new Assignment (fg.varSet ().toVariableArray (), new int [2]);
    assertEquals (0.2, fg.value (assn), 1e-5);

    int nvs = 0;
    for (Iterator it = fg.varSetIterator (); it.hasNext(); it.next ()) {
      nvs++;
    }
    assertEquals (1, nvs);

    assertEquals (1, fg.getDegree (vars[1]));

    assertTrue (fg.get (0) != fg.get (1));
    assertEquals (vars[1], fg.get (0));
    assertEquals (vars[2], fg.get (1));

  }

  public void testRedundantDomains ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);
    fg.multiplyBy (ltbl1);

    assertEquals (3, fg.varSet ().size ());
    assertEquals ("Wrong factors in FG, was "+fg.dumpToString (), 3, fg.factors ().size ());


    Assignment assn = new Assignment (fg.varSet ().toVariableArray (), new int [3]);
    assertEquals (0.128, fg.value (assn), 1e-5);
  }

  private static String uniformMdlstr =
          "VAR sigma u1 u2 : continuous\n" +
          "VAR x1 x2 : 2\n" +
          "sigma ~ Uniform -0.5 0.5\n" +
          "u1 ~ Uniform -0.5 0.5\n" +
          "u2 ~ Uniform -0.5 0.5\n" +
          "x1 x2 ~ BinaryPair sigma\n" +
          "x1 ~ Unary u1\n" +
          "x2 ~ Unary u2\n";

  public void testContinousSample () throws IOException
  {
    ModelReader reader = new ModelReader ();
    FactorGraph fg = reader.readModel (new BufferedReader (new StringReader (uniformMdlstr)));
    Randoms r = new Randoms (324143);
    Assignment allAssn = new Assignment ();
    for (int i = 0; i < 10000; i++) {
      Assignment row = fg.sample (r);
      allAssn.addRow (row);
    }

    Variable x1 = fg.findVariable ("x1");
    Assignment assn1 = (Assignment) allAssn.marginalize (x1);
    int[] col = assn1.getColumnInt (x1);
    double mean = MatrixOps.sum (col) / ((double)col.length);
    assertEquals (0.5, mean, 0.025);
  }

  private static String uniformMdlstr2 =
          "VAR sigma u1 u2 : continuous\n" +
          "VAR x1 x2 : 2\n" +
          "sigma ~ Normal 0.0 0.2\n" +
          "u1 ~ Normal 0.0 0.2\n" +
          "u2 ~ Normal 0.0 0.2\n" +
          "x1 x2 ~ BinaryPair sigma\n" +
          "x1 ~ Unary u1\n" +
          "x2 ~ Unary u2\n";

  public void testContinousSample2 () throws IOException
  {
    ModelReader reader = new ModelReader ();
    FactorGraph fg = reader.readModel (new BufferedReader (new StringReader (uniformMdlstr2)));
    Randoms r = new Randoms (324143);
    Assignment allAssn = new Assignment ();
    for (int i = 0; i < 10000; i++) {
      Assignment row = fg.sample (r);
      allAssn.addRow (row);
    }

    Variable x1 = fg.findVariable ("x2");
    Assignment assn1 = (Assignment) allAssn.marginalize (x1);
    int[] col = assn1.getColumnInt (x1);
    double mean = MatrixOps.sum (col) / ((double)col.length);
    assertEquals (0.5, mean, 0.01);

    Variable x2 = fg.findVariable ("x2");
    Assignment assn2 = (Assignment) allAssn.marginalize (x2);
    int[] col2 = assn2.getColumnInt (x2);
    double mean2 = MatrixOps.sum (col2) / ((double)col2.length);
    assertEquals (0.5, mean2, 0.025);
  }

  public void testAllFactorsOf () throws IOException
  {
    ModelReader reader = new ModelReader ();
    FactorGraph fg = reader.readModel (new BufferedReader (new StringReader (uniformMdlstr2)));
    Variable var = new Variable (2);
    var.setLabel ("v0");
    List lst = fg.allFactorsOf (var);
    assertEquals (0, lst.size ());
  }

  public void testAllFactorsOf2 () throws IOException
  {
    Variable x1 = new Variable (2);
    Variable x2 = new Variable (2);

    FactorGraph fg = new FactorGraph ();
    fg.addFactor (new TableFactor (x1));
    fg.addFactor (new TableFactor (x2));
    fg.addFactor (new TableFactor (new Variable[] { x1, x2 }));

    List lst = fg.allFactorsOf (x1);
    assertEquals (1, lst.size ());
    for (Iterator it = lst.iterator (); it.hasNext ();) {
      Factor f = (Factor) it.next ();
      assertEquals (1, f.varSet().size());
      assertTrue (f.varSet ().contains (x1));
    }

    HashVarSet vs = new HashVarSet (new Variable[]{x1, x2});
    List lst2 = fg.allFactorsOf (vs);
    assertEquals (1, lst2.size ());

    Factor f = (Factor) lst2.get (0);
    assertTrue (f.varSet ().equals (vs));
  }


  public void testAsTable2 ()
  {
    Factor f1 = new TableFactor (vars[0], new double[] { 0.6, 0.4 });
    Factor f2 = new ConstantFactor (2.0);
    FactorGraph fg = new FactorGraph (new Factor[] { f1, f2 });
    AbstractTableFactor tbl = fg.asTable ();
    assertTrue (Arrays.equals(new double[] { 0.6 * 2.0, 0.4 * 2.0 }, tbl.toValueArray ()));
  }

  public void testClear ()
  {
    FactorGraph fg = new FactorGraph ();
    fg.multiplyBy (tbl1);
    fg.multiplyBy (tbl2);

    assertEquals (3, fg.numVariables ());
    assertEquals (2, fg.factors ().size ());

    fg.clear ();
    assertEquals (0, fg.numVariables ());
    assertEquals (0, fg.factors ().size ());

    for (int vi = 0; vi < tbl1.varSet ().size (); vi++) {
      assertTrue (!fg.containsVar (tbl1.getVariable (vi)));
    }
    for (int vi = 0; vi < tbl2.varSet ().size (); vi++) {
      assertTrue (!fg.containsVar (tbl2.getVariable (vi)));
    }
  }

  public void testCacheExpanding ()
  {
    FactorGraph baseFg = RandomGraphs.randomFrustratedGrid (25, 1.0, new java.util.Random (3324879));
    Assignment assn = new Assignment (baseFg, new int[baseFg.numVariables ()]);
    double val = baseFg.logValue (assn);

    Timing timing = new Timing ();

    int numReps = 100;
    for (int rep = 0; rep < numReps; rep++) {
      FactorGraph fg = new FactorGraph (baseFg.numVariables ());
      for (int fi = 0; fi < baseFg.factors().size(); fi++) {
        fg.multiplyBy (baseFg.getFactor (fi));
      }
      assertEquals (val, fg.logValue (assn), 1e-5);
    }
    long time1 = timing.elapsedTime ();
    timing.tick ("No-expansion time");

    for (int rep = 0; rep < numReps; rep++) {
      FactorGraph fg = new FactorGraph ();
      for (int fi = 0; fi < baseFg.factors().size(); fi++) {
        fg.multiplyBy (baseFg.getFactor (fi));
      }
      assertEquals (val, fg.logValue (assn), 1e-5);
    }
    long time2 = timing.elapsedTime ();
    timing.tick ("With-expansion time");

    assertTrue (time1 < time2);
  }


  public static Test suite ()
  {
    return new TestSuite (TestFactorGraph.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestFactorGraph (args[i]));
      }
    } else {
      theSuite = (TestSuite) TestFactorGraph.suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
