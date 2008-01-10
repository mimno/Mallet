/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types.tests;

import junit.framework.*;

import java.io.File;
import java.util.Iterator;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.PipeInputIterator;
import cc.mallet.pipe.iterator.RandomTokenSequenceIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Instance;
import cc.mallet.types.PagedInstanceList;
import cc.mallet.util.Randoms;

/**
 * Created: Apr 19, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestPagedInstanceList.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestPagedInstanceList extends TestCase {

  public TestPagedInstanceList (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite (TestPagedInstanceList.class);
  }


	private static Alphabet dictOfSize (int size)
	{
		Alphabet ret = new Alphabet ();
		for (int i = 0; i < size; i++)
			ret.lookupIndex ("feature"+i);
		return ret;
	}

  public void testRandomTrained ()
  {
    Pipe p = new SerialPipes (new Pipe[]	{
			new TokenSequence2FeatureSequence (),
			new FeatureSequence2FeatureVector (),
			new Target2Label()});

    double testAcc1 = testRandomTrainedOn (new InstanceList (p));
    double testAcc2 = testRandomTrainedOn (new PagedInstanceList (p, 700, 200, new File(".")));
    assertEquals (testAcc1, testAcc2, 0.01);
  }

  private double testRandomTrainedOn (InstanceList training)
  {
    ClassifierTrainer trainer = new MaxEntTrainer ();

    Alphabet fd = dictOfSize (3);
    String[] classNames = new String[] {"class0", "class1", "class2"};

    Randoms r = new Randoms (1);
    Iterator<Instance> iter = new RandomTokenSequenceIterator (r,  new Dirichlet(fd, 2.0),
          30, 0, 10, 200, classNames);
    training.add (iter);

    InstanceList testing = new InstanceList (training.getPipe ());
    testing.add (new RandomTokenSequenceIterator (r,  new Dirichlet(fd, 2.0),
          30, 0, 10, 200, classNames));

    System.out.println ("Training set size = "+training.size());
    System.out.println ("Testing set size = "+testing.size());

    Classifier classifier = trainer.train (training);

    System.out.println ("Accuracy on training set:");
    System.out.println (classifier.getClass().getName()
                          + ": " + new Trial (classifier, training).getAccuracy());

    System.out.println ("Accuracy on testing set:");
    double testAcc = new Trial (classifier, testing).getAccuracy();
    System.out.println (classifier.getClass().getName()
                          + ": " + testAcc);

    return testAcc;
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestPagedInstanceList (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
