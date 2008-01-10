/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.pipe.tsf.tests;

import junit.framework.*;

import java.io.PrintWriter;
import java.io.StringWriter;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.tsf.SequencePrintingPipe;
import cc.mallet.types.*;

/**
 * Created: Jul 8, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestSequencePrintingPipe.java,v 1.1 2007/10/22 21:37:57 mccallum Exp $
 */
public class TestSequencePrintingPipe extends TestCase {

  public TestSequencePrintingPipe (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite (TestSequencePrintingPipe.class);
  }

  public static void testPrinting ()
  {
    Alphabet dict = dictOfSize (3);
    FeatureVector[] vecs = new FeatureVector[] {
      new FeatureVector (dict, new int[] { 0, 1 }),
      new FeatureVector (dict, new int[] { 0, 2 }),
      new FeatureVector (dict, new int[] { 2 }),
      new FeatureVector (dict, new int[] { 1, 2 }),
    };

    LabelAlphabet ld = labelDictOfSize (3);
    LabelSequence lbls = new LabelSequence (ld, new int [] { 0, 2, 0, 1});

    FeatureVectorSequence fvs = new FeatureVectorSequence (vecs);
    StringWriter sw = new StringWriter ();
    PrintWriter w = new PrintWriter (sw);
    Pipe p = new SequencePrintingPipe (w);

    // pipe the instance
    p.instanceFrom(new Instance (fvs, lbls, null, null));

    // Do a second one
    FeatureVectorSequence fvs2 = new FeatureVectorSequence (new FeatureVector[] {
      new FeatureVector (dict, new int[] { 1 }),
      new FeatureVector (dict, new int[] { 0 }),
    });
    LabelSequence lbls2 = new LabelSequence (ld, new int[] { 2, 1 });
    p.instanceFrom(new Instance (fvs2, lbls2, null, null));

    w.close();

    assertEquals ("LABEL0 feature0 feature1\n" +
            "LABEL2 feature0 feature2\n" +
            "LABEL0 feature2\n" +
            "LABEL1 feature1 feature2\n" +
            "\n" +
            "LABEL2 feature1\n" +
            "LABEL1 feature0\n\n",
            sw.toString());
  }

  private static Alphabet dictOfSize (int n)
  {
    Alphabet dict = new Alphabet ();
    for (int i = 0; i < n; i++) {
      dict.lookupIndex ("feature"+i);
    }
    return dict;
  }

  private static LabelAlphabet labelDictOfSize (int n)
  {
    LabelAlphabet dict = new LabelAlphabet ();
    for (int i = 0; i < n; i++) {
      dict.lookupIndex ("LABEL"+i);
    }
    return dict;
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestSequencePrintingPipe (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
