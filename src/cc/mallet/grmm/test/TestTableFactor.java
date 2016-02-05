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

import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import cc.mallet.grmm.types.*;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.SparseMatrixn;
import cc.mallet.types.tests.TestSerializable;
import cc.mallet.util.ArrayUtils;
import cc.mallet.util.Maths;
import cc.mallet.util.Randoms;

/**
 * Created: Aug 17, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestTableFactor.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestTableFactor extends TestCase {

  public TestTableFactor (String name)
  {
    super (name);
  }

  public void testMultiplyMultiplyBy ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.5, 0.5, 0.5, 0.5 };

    double[] vals3 = new double [] { 1, 2, 3, 4, };
    TableFactor ans = new TableFactor (var, vals3);

    TableFactor ptl1 = new TableFactor (var, vals);
    TableFactor ptl2 = new TableFactor (var, vals2);
    Factor ptl3 = ptl1.multiply (ptl2);
    ptl1.multiplyBy (ptl2);

    assertTrue (ans.almostEquals (ptl1));
    assertTrue (ans.almostEquals (ptl3));
  }


  public void testTblTblPlusEquals ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.25, 0.5, 0.75, 1.0 };

    double[] vals3 = new double [] { 2.25, 4.5, 6.75, 9.0, };
    TableFactor ans = new TableFactor (var, vals3);

    TableFactor ptl1 = new TableFactor (var, vals);
    TableFactor ptl2 = new TableFactor (var, vals2);
    ptl1.plusEquals (ptl2);

    assertTrue (ans.almostEquals (ptl1));
  }

  public void testEntropy ()
  {
    Variable v1 = new Variable (2);
    TableFactor ptl = new TableFactor (v1, new double[] { 0.3, 0.7 });

    double entropy = ptl.entropy ();
    assertEquals (0.61086, entropy, 1e-3);

    LogTableFactor logFactor = LogTableFactor.makeFromValues (v1, new double[] { 0.3, 0.7 });
    double entropy2 = logFactor.entropy ();
    assertEquals (0.61086, entropy2, 1e-3);
  }

  // fails
  public void ignoreTestSerialization () throws IOException, ClassNotFoundException
  {
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (3);
    Variable[] vars = { v1, v2 };
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 3, 5, 7 };
    TableFactor ptl = new TableFactor (vars, vals);
    TableFactor ptl2 = (TableFactor) TestSerializable.cloneViaSerialization (ptl);

    Set varset1 = ptl.varSet();
    Set varset2 = ptl2.varSet();
    assertTrue (!varset1.contains (varset2)); // Variables deep-cloned

    // There's not way to get directly at the matrices...!
    comparePotentialValues (ptl, ptl2);

    TableFactor marg1 = (TableFactor) ptl.marginalize (v1);
    TableFactor marg2 = (TableFactor) ptl2.marginalize (ptl2.findVariable (v1.getLabel ()));
    comparePotentialValues (marg1, marg2);
  }

  private void comparePotentialValues (TableFactor ptl, TableFactor ptl2)
  {
    AssignmentIterator it1 = ptl.assignmentIterator ();
    AssignmentIterator it2 = ptl2.assignmentIterator ();
    while (it1.hasNext ()) {
      assertTrue (ptl.value (it1) == ptl.value (it2));
      it1.advance (); it2.advance ();
    }
  }

  public void testSample ()
  {
    Variable v = new Variable (3);
    double[] vals = new double[] { 1, 3, 2 };
    TableFactor ptl = new TableFactor (v, vals);
    int[] sampled = new int [100];

    Randoms r = new Randoms (32423);
    for (int i = 0; i < sampled.length; i++) {
      sampled[i] = ptl.sampleLocation (r);
    }

    double sum = MatrixOps.sum (vals);
    double[] counts = new double [vals.length];
    for (int i = 0; i < vals.length; i++) {
      counts[i] = ArrayUtils.count (sampled, i);
    }

    MatrixOps.print (counts);
    for (int i = 0; i < vals.length; i++) {
      double prp = counts[i] / ((double) sampled.length);
      assertEquals (vals[i] / sum, prp, 0.1);
    }
  }

  public void testMarginalize ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    TableFactor ptl = new TableFactor (vars, new double[] { 1, 2, 3, 4});
    TableFactor ptl2 = (TableFactor) ptl.marginalize (vars[1]);
    assertEquals ("FAILURE: Potential has too many vars.\n  "+ptl2, 1, ptl2.varSet ().size ());
    assertTrue ("FAILURE: Potential does not contain "+vars[1]+":\n  "+ptl2, ptl2.varSet ().contains (vars[1]));

    double[] expected = new double[] { 4, 6 };
    assertTrue ("FAILURE: Potential has incorrect values.  Expected "+ArrayUtils.toString (expected)+"was "+ptl2,
          Maths.almostEquals (ptl2.toValueArray (), expected, 1e-5));
  }

  public void testMarginalizeOut ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    TableFactor ptl = new TableFactor (vars, new double[] { 1, 2, 3, 4});
    TableFactor ptl2 = (TableFactor) ptl.marginalizeOut (vars[0]);
    assertEquals ("FAILURE: Potential has too many vars.\n  "+ptl2, 1, ptl2.varSet ().size ());
    assertTrue ("FAILURE: Potential does not contain "+vars[1]+":\n  "+ptl2, ptl2.varSet ().contains (vars[1]));

    double[] expected = new double[] { 4, 6 };
    assertTrue ("FAILURE: Potential has incorrect values.  Expected "+ArrayUtils.toString (expected)+"was "+ptl2,
          Maths.almostEquals (ptl2.toValueArray (), expected, 1e-5));
  }


  public void testOneVarSlice ()
  {
    double[] vals = { 0.0, 1.3862943611198906, 0.6931471805599453, 1.791759469228055 };
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2};
    Factor ptl = new TableFactor (vars, vals);

    Assignment assn = new Assignment (v1, 0);
    TableFactor sliced = (TableFactor) ptl.slice (assn);

    TableFactor expected = new TableFactor (v2, new double[] { 1.0, 4.0 });
    comparePotentialValues (sliced, expected);
  }

  public void testTwoVarSlice ()
  {
    double[] vals = { 0.0, 1, 2, 3, 4, 5, 6, 7 };
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable v3 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2, v3};
    Factor ptl = new TableFactor (vars, vals);

    Assignment assn = new Assignment (v3, 0);
    TableFactor sliced = (TableFactor) ptl.slice (assn);

    TableFactor expected = new TableFactor (new Variable[] {v1, v2}, new double[] { 0, 2, 4, 6 });
    comparePotentialValues (sliced, expected);
  }

  public void testMultiVarSlice ()
  {
    double[] vals = { 0.0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable v3 = new Variable (2);
    Variable v4 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2, v3, v4};
    Factor ptl = new TableFactor (vars, vals);

    System.out.println (ptl);
    Assignment assn = new Assignment (v4, 0);
    TableFactor sliced = (TableFactor) ptl.slice (assn);
    System.out.println (new TableFactor ((AbstractTableFactor) sliced));

    TableFactor expected = new TableFactor (new Variable[] { v1,v2,v3 }, new double[] { 0, 2, 4, 6, 8, 10, 12, 14 });
    comparePotentialValues (sliced, expected);
  }

  public void testLogMultiVarSlice ()
  {
    double[] vals = { 0.0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable v3 = new Variable (2);
    Variable v4 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2, v3, v4};
    Factor ptl = LogTableFactor.makeFromValues (vars, vals);

    System.out.println (ptl.dumpToString ());
    Assignment assn = new Assignment (v4, 0);
    LogTableFactor sliced = (LogTableFactor) ptl.slice (assn);

    LogTableFactor expected = LogTableFactor.makeFromValues (new Variable[] { v1,v2,v3 }, new double[] { 0, 2, 4, 6, 8, 10, 12, 14 });
    assertTrue ("Test failed. Expected: "+expected.dumpToString ()+"\nActual: "+sliced.dumpToString (),
                expected.almostEquals (sliced));
  }

  public void testSparseMultiply ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    int[] idxs2 = new int[] { 0, 3 };
    double[] vals2 = new double [] { 0.5, 0.5 };

    double[] vals3 = new double [] { 1.0, 0, 4.0 };

    TableFactor ptl1 = new TableFactor (vars);
    ptl1.setValues (new SparseMatrixn (szs, idxs1, vals1));

    TableFactor ptl2 = new TableFactor (vars);
    ptl2.setValues (new SparseMatrixn (szs, idxs2, vals2));

    TableFactor ans = new TableFactor (vars);
    ans.setValues (new SparseMatrixn (szs, idxs1, vals3));

    Factor ptl3 = ptl1.multiply (ptl2);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl3, ans.almostEquals (ptl3));
  }

  public void testSparseDivide ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    int[] idxs2 = new int[] { 0, 3 };
    double[] vals2 = new double [] { 0.5, 0.5 };

    double[] vals3 = new double [] { 4.0, 0, 16.0 };

    TableFactor ptl1 = new TableFactor (vars);
    ptl1.setValues (new SparseMatrixn (szs, idxs1, vals1));

    TableFactor ptl2 = new TableFactor (vars);
    ptl2.setValues (new SparseMatrixn (szs, idxs2, vals2));

    TableFactor ans = new TableFactor (vars);
    ans.setValues (new SparseMatrixn (szs, idxs1, vals3));

    ptl1.divideBy (ptl2);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl1, ans.almostEquals (ptl1));
  }

  public void testSparseMarginalize ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    TableFactor ptl1 = new TableFactor (vars);
    ptl1.setValues (new SparseMatrixn (szs, idxs1, vals1));

    TableFactor ans = new TableFactor (vars[0], new double[] { 6, 8 });

    Factor ptl2 = ptl1.marginalize (vars[0]);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl2+" Orig: "+ptl1, ans.almostEquals (ptl2));
  }

  public void testSparseExtractMax ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    TableFactor ptl1 = new TableFactor (vars);
    ptl1.setValues (new SparseMatrixn (szs, idxs1, vals1));

    TableFactor ans = new TableFactor (vars[0], new double[] { 4, 8 });

    Factor ptl2 = ptl1.extractMax (vars[0]);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl2+ "Orig: "+ptl1, ans.almostEquals (ptl2));
  }

  public void testLogSample ()
  {
    Variable v = new Variable (2);
    double[] vals = new double[] { -30, 0 };
    LogTableFactor ptl = LogTableFactor.makeFromLogValues (v, vals);
    int idx = ptl.sampleLocation (new Randoms (43));
    assertEquals (1, idx);
  }

  public void testExp ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[] {2.0, 4.0, 6.0, 8.0};
    double[] vals3 = new double [] { 4.0, 16.0, 36.0, 64.0 };
    TableFactor ans = new TableFactor (var, vals3);

    TableFactor ptl1 = new TableFactor (var, vals);
    ptl1.exponentiate (2.0);

    assertTrue ("Error: expected "+ans.dumpToString ()+" but was "+ptl1.dumpToString (), ptl1.almostEquals (ans));
  }

  public void testPlusEquals ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };

    TableFactor factor = new TableFactor (var, vals);
    factor.plusEquals (0.1);

    double[] expected = new double[] { 2.1, 4.1, 6.1, 8.1 };
    TableFactor ans = new TableFactor (var, expected);

    assertTrue ("Error: expected "+ans.dumpToString ()+" but was "+factor.dumpToString (), factor.almostEquals (ans));
  }

  public void testMultiplyAll ()
  {
    for (int rep = 0; rep < 100; rep++) {
      Universe.resetUniverse();

      Variable v1 = new Variable (2);
      Variable v2 = new Variable (2);
      Variable[] vars = new Variable[] { v1, v2 };
      double[] vals = new double[] { 2.0, 4.0, 6.0, 8.0 };
      double[] vals2 = new double [] { 0.5, 0.5, 0.5, 0.5 };

      double[] vals3 = new double [] { 1, 2, 3, 4,};
      TableFactor ans = new TableFactor (vars, vals3);

      TableFactor ptl1 = new TableFactor (vars, vals);
      TableFactor ptl2 = new TableFactor (vars, vals2);
      Factor ptl3 = TableFactor.multiplyAll (new Factor[] { ptl1, ptl2 });

      VarSet vs = ptl3.varSet ();
      for (int i = 0; i < vars.length; i++) {
        assertEquals (vars[i], vs.get (i));
      }

      assertTrue (ans.almostEquals (ptl3));
    }
  }

  public void testExpandToContain ()
  {
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable v3 = new Variable (2);
    Variable[] vars = new Variable[] { v1, v2 };

    double[] vals = new double[] { 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.5, 0.5 };

    TableFactor f1 = new TableFactor (vars, vals);
    TableFactor f2 = new TableFactor (v3, vals2);

    f1.multiplyBy (f2);
    Variable[] allV = new Variable[] { v1, v2, v3 };
    double[] exp = new double[] { 1, 1, 2, 2, 3, 3, 4, 4, };
    TableFactor ans = new TableFactor (allV, exp);

    System.out.println (f1.dumpToString ());
    System.out.println (ans.dumpToString ());

    assertTrue (ans.almostEquals (f1));
  }

  // thanks to John Pate <j.k.pate@sms.ed.ac.uk>
  public void testVariableReordering () {

    Variable var0 = new Variable (2);
    Variable var1 = new Variable (3);

    Randoms r = new Randoms (17671);

    double[] probs = new double[] {
      r.nextDouble(),
      r.nextDouble(),
      r.nextDouble(),
      r.nextDouble(),
      r.nextDouble(),
      r.nextDouble()
    };

    TableFactor nothingReordered = new TableFactor(
      new Variable[] { var0, var1 },
      probs
    );


    double[] probsToReorder = new double[] {
      probs[0],
      probs[3],
      probs[1],
      probs[4],
      probs[2],
      probs[5]
    };

    TableFactor reOrderedToMatch = new TableFactor(
      new Variable[] { var1, var0 },
      probsToReorder
    );

    TableFactor reOrderedToMisMatch = new TableFactor(
      new Variable[] { var1, var0 },
      probs
    );

    System.out.println( "\nShould be true: " + nothingReordered.almostEquals( reOrderedToMatch ) );
    System.out.println( "Should be false: " + nothingReordered.almostEquals( reOrderedToMisMatch ) );

    assertTrue( nothingReordered.almostEquals( reOrderedToMatch ) );
    assertFalse( nothingReordered.almostEquals( reOrderedToMisMatch ) );
  }

  public static Test suite ()
  {
    return new TestSuite (TestTableFactor.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestTableFactor (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
