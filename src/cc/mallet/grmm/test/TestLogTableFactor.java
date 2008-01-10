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

import cc.mallet.grmm.types.*;
import cc.mallet.types.SparseMatrixn;
import cc.mallet.types.tests.TestSerializable;
import cc.mallet.util.ArrayUtils;
import cc.mallet.util.Maths;
import cc.mallet.util.Randoms;

/**
 * Created: Aug 17, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestLogTableFactor.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestLogTableFactor extends TestCase {

  public TestLogTableFactor (String name)
  {
    super (name);
  }

  public void testTimesTableFactor ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.5, 0.5, 0.5, 0.5 };

    double[] vals3 = new double [] { 1, 2, 3, 4, };
    TableFactor ans = new TableFactor (var, vals3);

    TableFactor ptl1 = new TableFactor (var, vals);
    LogTableFactor lptl2 = LogTableFactor.makeFromValues (var, vals2);
    ptl1.multiplyBy (lptl2);
    assertTrue (ans.almostEquals (ptl1));
  }


  public void testTblTblPlusEquals ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.25, 0.5, 0.75, 1.0 };

    double[] vals3 = new double [] { 2.25, 4.5, 6.75, 9.0, };
    LogTableFactor ans = LogTableFactor.makeFromValues (var, vals3);

    LogTableFactor ptl1 = LogTableFactor.makeFromValues (var, vals);
    LogTableFactor ptl2 = LogTableFactor.makeFromValues (var, vals2);
    ptl1.plusEquals (ptl2);

    assertTrue (ans.almostEquals (ptl1));
  }

  public void testMultiplyByLogSpace ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.5, 0.5, 0.5, 0.5 };

    double[] vals3 = new double [] { 1, 2, 3, 4, };
    TableFactor ans = new TableFactor (var, vals3);

    TableFactor ptl1 = new TableFactor (var, vals);
    TableFactor ptl2 = new TableFactor (var, vals2);
    ptl1.multiplyBy (ptl2);
    assertTrue (ans.almostEquals (ptl1));

    TableFactor ptl3 = new TableFactor (var, vals);
    LogTableFactor ptl4 = LogTableFactor.makeFromValues (var, vals2);
    ptl3.multiplyBy (ptl4);
    assertTrue (ptl3.almostEquals (ptl1));

    TableFactor ptl5 = new TableFactor (var, vals);
    LogTableFactor ptl6 = LogTableFactor.makeFromValues (var, vals2);
    ptl6.multiplyBy (ptl5);
    assertTrue (ptl6.almostEquals (ans));

    LogTableFactor ptl7 = LogTableFactor.makeFromValues (var, vals);
    LogTableFactor ptl8 = LogTableFactor.makeFromValues (var, vals2);
    ptl8.multiplyBy (ptl7);
    assertTrue (ptl8.almostEquals (ans));
  }

  public void testDivideByLogSpace ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };
    double[] vals2 = new double [] { 0.5, 0.5, 0.5, 0.5 };

    double[] vals3 = new double [] { 4, 8, 12, 16, };
    TableFactor ans = new TableFactor (var, vals3);

    TableFactor ptl1 = new TableFactor (var, vals);
    TableFactor ptl2 = new TableFactor (var, vals2);
    ptl1.divideBy (ptl2);
    assertTrue (ans.almostEquals (ptl1));

    TableFactor ptl3 = new TableFactor (var, vals);
    LogTableFactor ptl4 = LogTableFactor.makeFromValues (var, vals2);
    ptl3.divideBy (ptl4);
    assertTrue (ptl3.almostEquals (ans));

    LogTableFactor ptl5 = LogTableFactor.makeFromValues (var, vals);
    TableFactor ptl6 = new TableFactor (var, vals2);
    ptl5.divideBy (ptl6);
    assertTrue (ptl5.almostEquals (ans));

    LogTableFactor ptl7 = LogTableFactor.makeFromValues (var, vals);
    LogTableFactor ptl8 = LogTableFactor.makeFromValues (var, vals2);
    ptl7.divideBy (ptl8);
    assertTrue (ptl7.almostEquals (ans));
  }

  public void testEntropyLogSpace ()
  {
    Variable v1 = new Variable (2);
    TableFactor ptl = new TableFactor (v1, new double[] { 0.3, 0.7 });

    double entropy = ptl.entropy ();
    assertEquals (0.61086, entropy, 1e-3);

    LogTableFactor ptl2 = LogTableFactor.makeFromValues (v1, new double[] { 0.3, 0.7 });
    double entropy2 = ptl2.entropy ();
    assertEquals (0.61086, entropy2, 1e-3);
  }

  // fails
  public void ignoreTestSerialization () throws IOException, ClassNotFoundException
  {
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (3);
    Variable[] vars = { v1, v2 };
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 3, 5, 7 };
    LogTableFactor ptl = LogTableFactor.makeFromLogValues (vars, vals);
    LogTableFactor ptl2 = (LogTableFactor) TestSerializable.cloneViaSerialization (ptl);

    Set varset1 = ptl.varSet();
    Set varset2 = ptl2.varSet();
    assertTrue (!varset1.contains (varset2)); // Variables deep-cloned

    // There's not way to get directly at the matrices...!
    comparePotentialValues (ptl, ptl2);

    LogTableFactor marg1 = (LogTableFactor) ptl.marginalize (v1);
    LogTableFactor marg2 = (LogTableFactor) ptl2.marginalize (ptl2.findVariable (v1.getLabel ()));
    comparePotentialValues (marg1, marg2);
  }

  private void comparePotentialValues (LogTableFactor ptl, LogTableFactor ptl2)
  {
    AssignmentIterator it1 = ptl.assignmentIterator ();
    AssignmentIterator it2 = ptl2.assignmentIterator ();
    while (it1.hasNext ()) {
      assertTrue (ptl.value (it1) == ptl.value (it2));
      it1.advance (); it2.advance ();
    }
  }

  public void testExtractMaxLogSpace ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    LogTableFactor ptl = LogTableFactor.makeFromValues (vars, new double[]{1, 2, 3, 4});
    LogTableFactor ptl2 = (LogTableFactor) ptl.extractMax (vars[1]);

    assertEquals ("FAILURE: Potential has too many vars.\n  "+ptl2, 1, ptl2.varSet ().size ());
    assertTrue ("FAILURE: Potential does not contain "+vars[1]+":\n  "+ptl2, ptl2.varSet ().contains (vars[1]));

    double[] expected = new double[] { 3, 4 };
    assertTrue ("FAILURE: Potential has incorrect values.  Expected "+ArrayUtils.toString (expected)+"was "+ptl2,
          Maths.almostEquals (ptl2.toValueArray (), expected, 1e-5));
  }

  public void testLogValue ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    LogTableFactor ptl = LogTableFactor.makeFromValues (vars, new double[] { 1, 2, 3, 4 });

    Assignment assn = new Assignment (vars, new int [vars.length]);
    assertEquals (0, ptl.logValue (assn), 1e-5);
    assertEquals (0, ptl.logValue (ptl.assignmentIterator()), 1e-5);
    assertEquals (0, ptl.logValue (0), 1e-5);
    assertEquals (1, ptl.value (assn), 1e-5);
    assertEquals (1, ptl.value (ptl.assignmentIterator()), 1e-5);
    assertEquals (1, ptl.value (0), 1e-5);

    LogTableFactor ptl2 = LogTableFactor.makeFromLogValues (vars, new double[] { 0, Math.log (2), Math.log (3), Math.log (4) });

    Assignment assn2 = new Assignment (vars, new int [vars.length]);
    assertEquals (0, ptl2.logValue (assn2), 1e-5);
    assertEquals (0, ptl2.logValue (ptl2.assignmentIterator()), 1e-5);
    assertEquals (0, ptl2.logValue (0), 1e-5);
    assertEquals (1, ptl2.value (assn2), 1e-5);
    assertEquals (1, ptl2.value (ptl2.assignmentIterator()), 1e-5);
    assertEquals (1, ptl2.value (0), 1e-5);
  }


  public void testOneVarSlice ()
  {
    double[] vals = { 0.0, 1.3862943611198906, 0.6931471805599453, 1.791759469228055 };
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2};
    Factor ptl = LogTableFactor.makeFromLogValues(vars, vals);

    Assignment assn = new Assignment (v1, 0);
    LogTableFactor sliced = (LogTableFactor) ptl.slice (assn);

    LogTableFactor expected = LogTableFactor.makeFromValues (v2, new double[] { 1.0, 4.0 });
    comparePotentialValues (sliced, expected);

    assertEquals (1, assn.varSet ().size ());
  }

  public void testTwoVarSlice ()
  {
    double[] vals = { 0.0, 1, 2, 3, 4, 5, 6, 7 };
    Variable v1 = new Variable (2);
    Variable v2 = new Variable (2);
    Variable v3 = new Variable (2);
    Variable[] vars = new Variable[]{v1, v2, v3};
    Factor ptl = LogTableFactor.makeFromValues(vars, vals);

    Assignment assn = new Assignment (v3, 0);
    LogTableFactor sliced = (LogTableFactor) ptl.slice (assn);

    LogTableFactor expected = LogTableFactor.makeFromValues (new Variable[] { v1, v2 }, new double[] { 0, 2, 4, 6 });
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
    Factor ptl = LogTableFactor.makeFromValues(vars, vals);

    System.out.println (ptl);
    Assignment assn = new Assignment (v4, 0);
    LogTableFactor sliced = (LogTableFactor) ptl.slice (assn);

    LogTableFactor expected = LogTableFactor.makeFromValues (new Variable[] { v1, v2, v3 }, new double[] { 0, 2, 4, 6, 8, 10, 12, 14 });
    comparePotentialValues (sliced, expected);
  }
  public void testSparseValueAndLogValue ()
  {
     Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
     int[] szs = { 2, 2 };

     int[] idxs1 = new int[] { 1, 3 };
     double[] vals1 = new double[]{ 4.0, 8.0 };

    LogTableFactor ptl1 = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs1, vals1));

    AssignmentIterator it = ptl1.assignmentIterator ();
    assertEquals (1, it.indexOfCurrentAssn ());
    assertEquals (Math.log (4), ptl1.logValue (it), 1e-5);
    assertEquals (Math.log (4), ptl1.logValue (it.assignment ()), 1e-5);
    assertEquals (4, ptl1.value (it), 1e-5);
    assertEquals (4, ptl1.value (it.assignment ()), 1e-5);

    it = ptl1.varSet ().assignmentIterator ();
    assertEquals (0, it.indexOfCurrentAssn ());
    assertEquals (Double.NEGATIVE_INFINITY, ptl1.logValue (it), 1e-5);
    assertEquals (Double.NEGATIVE_INFINITY, ptl1.logValue (it.assignment ()), 1e-5);
    assertEquals (0, ptl1.value (it), 1e-5);
    assertEquals (0, ptl1.value (it.assignment ()), 1e-5);
  }

  public void testSparseMultiplyLogSpace ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    int[] idxs2 = new int[] { 0, 3 };
    double[] vals2 = new double [] { 0.5, 0.5 };

    double[] vals3 = new double [] { 1.0, 0, 4.0 };

    LogTableFactor ptl1 = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs1, vals1));
    LogTableFactor ptl2 = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs2, vals2));
    LogTableFactor ans = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs1, vals3));

    Factor ptl3 = ptl1.multiply (ptl2);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl3, ans.almostEquals (ptl3));
  }

  public void testSparseDivideLogSpace ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    int[] idxs2 = new int[] { 0, 3 };
    double[] vals2 = new double [] { 0.5, 0.5 };

    double[] vals3 = new double [] { 4.0, 0, 16.0 };

    LogTableFactor ptl1 = LogTableFactor.makeFromMatrix  (vars, new SparseMatrixn (szs, idxs1, vals1));
    LogTableFactor ptl2 = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs2, vals2));
    LogTableFactor ans = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs1, vals3));

    ptl1.divideBy (ptl2);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl1, ans.almostEquals (ptl1));
  }

  public void testSparseMarginalizeLogSpace ()
  {
    Variable[] vars = new Variable[] { new Variable (2), new Variable (2) };
    int[] szs = { 2, 2 };

    int[] idxs1 = new int[] { 0, 1, 3 };
    double[] vals1 = new double[]{ 2.0, 4.0, 8.0 };

    LogTableFactor ptl1 = LogTableFactor.makeFromMatrix (vars, new SparseMatrixn (szs, idxs1, vals1));
    LogTableFactor ans = LogTableFactor.makeFromValues (vars[0], new double[] { 6, 8 });

    Factor ptl2 = ptl1.marginalize (vars[0]);

    assertTrue ("Tast failed! Expected: "+ans+" Actual: "+ptl2+" Orig: "+ptl1, ans.almostEquals (ptl2));
  }

  public void testLogSample ()
  {
    Variable v = new Variable (2);
    double[] vals = new double[] { -30, 0 };
    LogTableFactor ptl = LogTableFactor.makeFromLogValues (v, vals);

    int idx = ptl.sampleLocation (new Randoms (43));
    assertEquals (1, idx);
  }


  public void testPlusEquals ()
  {
    Variable var = new Variable (4);
    // log 0, log 1, log 2, log 3
    double[] vals = new double[] { Double.NEGATIVE_INFINITY, 0, 0.6931471805599453, 1.0986122886681098 };

    LogTableFactor factor = LogTableFactor.makeFromLogValues (var, vals);
    factor.plusEquals (0.1);

    // log 0.1, log 1.1, log 2.1, log 3.1
    double[] expected = new double[] { -2.3025850929940455, 0.09531017980432493, 0.7419373447293773, 1.1314021114911006 };
    LogTableFactor ans = LogTableFactor.makeFromLogValues (var, expected);

    assertTrue ("Error: expected "+ans.dumpToString ()+" but was "+factor.dumpToString (), factor.almostEquals (ans));
  }


  public void testRecenter ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 2.0, 4.0, 6.0, 8.0 };

    LogTableFactor ltbl1 = LogTableFactor.makeFromValues (var, vals);
    ltbl1.recenter ();
    double[] expected = new double[] { Math.log (0.25), Math.log(0.5), Math.log (0.75), 0 };
    LogTableFactor ans = LogTableFactor.makeFromLogValues (var, expected);

    assertTrue ("Error: expected "+ans.dumpToString ()+"but was "+ltbl1.dumpToString (), ans.almostEquals (ltbl1));
  }

  public void testRecenter2 ()
  {
    Variable var = new Variable (4);
    double[] vals = new double[]{ 0, 1.4, 1.4, 0 };

    LogTableFactor ltbl1 = LogTableFactor.makeFromLogValues (var, vals);
    ltbl1.recenter ();

    double[] expected = new double[]{ -1.4, 0, 0, -1.4 };
    LogTableFactor ans = LogTableFactor.makeFromLogValues (var, expected);
    
    assertTrue (!ltbl1.isNaN ());
    assertTrue ("Error: expected "+ans.dumpToString ()+"but was "+ltbl1.dumpToString (), ans.almostEquals (ltbl1));
  }

  public static Test suite ()
  {
    return new TestSuite (TestLogTableFactor.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestLogTableFactor (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
