/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types.tests;

import cc.mallet.types.Alphabet;
import cc.mallet.types.AugmentableFeatureVector;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.SparseVector;
import junit.framework.*;

/**
 * Created: Dec 30, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestAugmentableFeatureVector.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestAugmentableFeatureVector extends TestCase {

  public TestAugmentableFeatureVector (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite (TestAugmentableFeatureVector.class);
  }

  public void testDotProductBinaryToSV ()
  {
    SparseVector v = makeSparseVectorToN (5);
    AugmentableFeatureVector afv = makeAfv (new int[] { 1, 3 }, true);
    double dp = afv.dotProduct (v);
    assertEquals (4.0, dp, 1e-5);
    new AugmentableFeatureVector (new Alphabet(), true);
  }

  public void testDotProductSparseASVToSV ()
  {
    SparseVector v = makeSparseVectorToN (7);
    AugmentableFeatureVector afv = makeAfv (new int[] { 1, 3 }, false);
    double dp = afv.dotProduct (v);
    assertEquals (4.0, dp, 1e-5);

    afv = makeAfv (new int[] { 2, 5 }, false);
    dp = afv.dotProduct (v);
    assertEquals (7.0, dp, 1e-5);
  }

  private AugmentableFeatureVector makeAfv (int[] ints, boolean binary)
  {
    AugmentableFeatureVector afv = new AugmentableFeatureVector (new Alphabet(), binary);
    for (int i = 0; i < ints.length; i++) {
      int idx = ints[i];
      afv.add (idx, 1.0);
    }
    return afv;
  }

  private SparseVector makeSparseVectorToN (int N)
  {
    double[] vals = new double [N];
    for (int i = 0; i < N; i++) {
      vals [i] = i;
    }
    return new SparseVector (vals);
  }

  public void testAddWithPrefix ()
  {
    Alphabet dict = new Alphabet ();
    dict.lookupIndex ("ZERO");
    dict.lookupIndex ("ONE");
    dict.lookupIndex ("TWO");
    dict.lookupIndex ("THREE");

    FeatureVector fv = new FeatureVector (dict, new int[] { 1,3 });

    AugmentableFeatureVector afv = new AugmentableFeatureVector (new Alphabet (), true);
    afv.add (fv, "O:");

    assertEquals (4, dict.size());
    assertEquals (2, afv.getAlphabet ().size());
    assertEquals ("O:ONE\nO:THREE\n", afv.toString ());
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestAugmentableFeatureVector (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
