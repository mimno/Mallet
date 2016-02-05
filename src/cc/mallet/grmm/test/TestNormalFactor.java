/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.test;

import cc.mallet.grmm.types.*;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.Randoms;
import junit.framework.*;
import gnu.trove.TDoubleArrayList;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.DenseMatrix;

/**
 * $Id: TestNormalFactor.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestNormalFactor extends TestCase {

  public TestNormalFactor (String name)
  {
    super (name);
  }

  public void testSample ()
  {
    Variable v1 = new Variable (Variable.CONTINUOUS);
    Variable v2 = new Variable (Variable.CONTINUOUS);
    Randoms r = new Randoms (2343);

    Vector mu = new DenseVector (new double[] { 1.0, 2.0 });
    Matrix var = new DenseMatrix (new double[][] {{ 0.5, 2.0 }, { 0, 1 }});
//    Matrix var = new DenseMatrix (new double[][] {{ 0.5, 2.0 }, { 2.0, 0.75 }});

    VarSet vars = new HashVarSet (new Variable[] { v1, v2 });
    Factor f = new NormalFactor (vars, mu, var);

    TDoubleArrayList v1lst = new TDoubleArrayList ();
    TDoubleArrayList v2lst = new TDoubleArrayList ();
    for (int i = 0; i < 100000; i++) {
      Assignment assn = f.sample (r);
      v1lst.add (assn.getDouble (v1));
      v2lst.add (assn.getDouble (v2));
    }

    checkMeanStd (v1lst, 1.0, Math.sqrt (1/0.5));
    checkMeanStd (v2lst, 2.0, Math.sqrt (1/0.75));
  }

  void checkMeanStd (TDoubleArrayList ell, double mu, double sigma)
  {
    double[] vals = ell.toNativeArray ();
    double mean1 = MatrixOps.mean (vals);
    double std1 = MatrixOps.stddev (vals);
    assertEquals (mu, mean1, 0.025);
    assertEquals (sigma, std1, 0.01);
  }


  /**
   * @return a <code>TestSuite</code>
   */
  public static TestSuite suite ()
  {
    return new TestSuite (TestNormalFactor.class);
  }

  public static void main (String[] args)
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestNormalFactor (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
