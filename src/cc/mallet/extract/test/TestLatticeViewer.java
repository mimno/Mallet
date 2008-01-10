/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;

import cc.mallet.extract.CRFExtractor;
import cc.mallet.extract.Extraction;
import cc.mallet.extract.LatticeViewer;
import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFTrainerByLikelihood;
import cc.mallet.fst.MEMM;
import cc.mallet.fst.TokenAccuracyEvaluator;
import cc.mallet.fst.tests.TestCRF;
import cc.mallet.fst.tests.TestMEMM;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.types.InstanceList;

/**
 * Created: Oct 31, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestLatticeViewer.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class TestLatticeViewer extends TestCase {

  public TestLatticeViewer (String name)
  {
    super (name);
  }

  private static File htmlFile = new File ("errors.html");
  private static File latticeFile = new File ("lattice.html");
  private static File htmlDir = new File ("html/");

  public void testSpaceViewer () throws FileNotFoundException
  {
    Pipe pipe = TestMEMM.makeSpacePredictionPipe ();
    String[] data0 = { TestCRF.data[0] };
    String[] data1 = { TestCRF.data[1] };

    InstanceList training = new InstanceList (pipe);
    training.add (new ArrayIterator (data0));
    InstanceList testing = new InstanceList (pipe);
    testing.add (new ArrayIterator (data1));

    CRF crf = new CRF (pipe, null);
    crf.addFullyConnectedStatesForLabels ();
    CRFTrainerByLikelihood crft = new CRFTrainerByLikelihood (crf);
    crft.trainIncremental (training);

    CRFExtractor extor = hackCrfExtor (crf);
    Extraction extration = extor.extract (new ArrayIterator (data1));

    PrintStream out = new PrintStream (new FileOutputStream (htmlFile));
    LatticeViewer.extraction2html (extration, extor, out);
    out.close();

    out = new PrintStream (new FileOutputStream (latticeFile));
    LatticeViewer.extraction2html (extration, extor, out, true);
    out.close();


  }


  static CRFExtractor hackCrfExtor (CRF crf)
  {
    Pipe[] newPipes = new Pipe [3];

    SerialPipes pipes = (SerialPipes) crf.getInputPipe ();
    for (int i = 0; i < 3; i++) {
      Pipe p0 = pipes.getPipe (0);
      //pipes.removePipe (0);  TODO Fix me
      //p0.setParent (null);
      newPipes[i] = p0;
    }

    Pipe tokPipe = new SerialPipes (newPipes);

    CRFExtractor extor = new CRFExtractor (crf, (Pipe)tokPipe);
    return extor;
  }


  public void testDualSpaceViewer () throws IOException
  {
    Pipe pipe = TestMEMM.makeSpacePredictionPipe ();
    String[] data0 = { TestCRF.data[0] };
    String[] data1 = TestCRF.data;

    InstanceList training = new InstanceList (pipe);
    training.add (new ArrayIterator (data0));
    InstanceList testing = new InstanceList (pipe);
    testing.add (new ArrayIterator (data1));

    CRF crf = new CRF (pipe, null);
    crf.addFullyConnectedStatesForLabels ();
    CRFTrainerByLikelihood crft = new CRFTrainerByLikelihood (crf);
    TokenAccuracyEvaluator eval = new TokenAccuracyEvaluator (new InstanceList[] {training, testing}, new String[] {"Training", "Testing"});
    for (int i = 0; i < 5; i++) {
    	crft.train (training, 1);
    	eval.evaluate(crft);
    }

    CRFExtractor extor = hackCrfExtor (crf);
    Extraction e1 = extor.extract (new ArrayIterator (data1));

    Pipe pipe2 = TestMEMM.makeSpacePredictionPipe ();
    InstanceList training2 = new InstanceList (pipe2);
    training2.add (new ArrayIterator (data0));
    InstanceList testing2 = new InstanceList (pipe2);
    testing2.add (new ArrayIterator (data1));

    MEMM memm = new MEMM (pipe2, null);
    memm.addFullyConnectedStatesForLabels ();
    memm.train (training2, null, testing2, new TokenAccuracyEvaluator (new InstanceList[] {training2, testing2}, new String[] {"Training2", "Testing2"}), 5);

    CRFExtractor extor2 = hackCrfExtor (memm);
    Extraction e2 = extor2.extract (new ArrayIterator (data1));

    if (!htmlDir.exists ()) htmlDir.mkdir ();
    LatticeViewer.viewDualResults (htmlDir, e1, extor, e2, extor2);

  }

  public static Test suite ()
  {
    return new TestSuite (TestLatticeViewer.class);
  }


  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestLatticeViewer (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
