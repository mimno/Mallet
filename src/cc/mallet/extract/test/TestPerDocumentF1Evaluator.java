/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract.test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;

import cc.mallet.extract.DocumentExtraction;
import cc.mallet.extract.Extraction;
import cc.mallet.extract.PerDocumentF1Evaluator;
import cc.mallet.extract.PerFieldF1Evaluator;
import cc.mallet.extract.RegexFieldCleaner;
import cc.mallet.extract.Tokenization;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintInputAndTarget;
import cc.mallet.pipe.SGML2TokenSequence;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2LabelSequence;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;
import cc.mallet.util.CharSequenceLexer;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Created: Nov 18, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu"></A>
 * @version $Id: TestPerDocumentF1Evaluator.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class TestPerDocumentF1Evaluator extends TestCase {

  public TestPerDocumentF1Evaluator (String name)
  {
    super (name);
  }


  public static Test suite ()
  {
    return new TestSuite (TestPerDocumentF1Evaluator.class);
  }

  private static String[] testPred = {
    "<eater>the big red fox</eater> did it",
    "it was done by <meal>the dog</meal>",
    "<eater>the cat</eater> ate the <meal>canary</meal>",
    "<meal>the hamburger</meal> was eaten by the kid",
    "<eater>the dog</eater> was eaten with zest",
    "four score and seven years <meal>ago</meal>"

  };

  private static String[] testTrue = {
    "<eater>the big red fox</eater> did it",
    "it was done by <eater>the dog</eater>",
    "<eater>the cat</eater> ate <meal>the canary</meal>",
    "<meal>the hamburger</meal> was eaten by <eater>the kid</eater>",
    "<meal>the dog</meal> was eaten with zest",
    "four score and seven years ago"
  };


  private Extraction createExtractionFrom (String[] predStrings, String[] trueStrings)
  {
    Pipe pipe = new SerialPipes (new Pipe[] {
      new SGML2TokenSequence (new CharSequenceLexer (CharSequenceLexer.LEX_NONWHITESPACE_CLASSES	), "O"),
      new Target2LabelSequence (),
      new PrintInputAndTarget (),
    });

    InstanceList pred = new InstanceList (pipe);
    pred.addThruPipe (new ArrayIterator (predStrings));

    InstanceList targets = new InstanceList (pipe);
    targets.addThruPipe (new ArrayIterator (trueStrings));

    LabelAlphabet dict = (LabelAlphabet) pipe.getTargetAlphabet ();
    Extraction extraction = new Extraction (null, dict);

    for (int i = 0; i < pred.size(); i++) {
      Instance aPred = pred.get (i);
      Instance aTarget = targets.get (i);
      Tokenization input = (Tokenization) aPred.getData ();
      Sequence predSeq = (Sequence) aPred.getTarget ();
      Sequence targetSeq = (Sequence) aTarget.getTarget ();
      DocumentExtraction docextr = new DocumentExtraction ("TEST"+i, dict, input, predSeq, targetSeq, "O");
      extraction.addDocumentExtraction (docextr);
    }

    return extraction;
  }

  private static final String testAExpected = "Testing per-document F1\nName\tP\tR\tF1\n" +
          "eater\t0.6667\t0.5\t0.5714\n" +
          "O\t0\t1\t0\n" +
          "meal\t0.25\t0.3333\t0.2857\n" +
          "OVERALL (micro-averaged) P=0.4286 R=0.4286 F1=0.4286\n" +
          "OVERALL (macro-averaged) F1=0.4286\n\n";

  public void testPerDocEval ()
  {
    Extraction extraction = createExtractionFrom (testPred, testTrue);
    PerDocumentF1Evaluator eval = new PerDocumentF1Evaluator ();
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    eval.setErrorOutputStream (System.out);
    eval.evaluate ("Testing", extraction, new PrintWriter (new OutputStreamWriter (out), true));

    String output = out.toString ().replaceAll("\\r\\n?", "\n");
    assertEquals (testAExpected, output);
  }

  private static final String[] mpdPred = {
    "<title>Wizard of Oz</title> by <author>John Smith</author> and <author>Adam Felber</author>",
    "<title>Jisp Boo Fuzz by</title> the estimable <title>Rich Q. Doe</title> and <author>Frank Wilson</author>",
    "<title>Howdy Doody</title> if you think this is Mr. nonsense <author>don't you huh</author>",
  };

  private static final String[] mpdTrue = {
    "<title>Wizard of Oz</title> by <author>John Smith</author> and <author>Adam Felber</author>",
    "<title>Jisp Boo Fuzz</title> by the estimable <author>Rich Q. Doe</author> and <author>Frank Wilson</author>",
    "<title>Howdy Doody</title> if <title>you</title> think this is <title>Mr.</title> <author> nonsense don't you huh</author>",
  };

  private static final String mpdExpected = "Testing SEGMENT counts\nName\tCorrect\tPred\tTarget\n" +
          "title\t2\t4\t5\n" +
          "O\t0\t0\t0\n" +
          "author\t3\t4\t5\n" +
          "\nTesting per-field F1\n" +
          "Name\tP\tR\tF1\n" +
          "title\t0.5\t0.4\t0.4444\n" +
          "O\t0\t1\t0\n" +
          "author\t0.75\t0.6\t0.6667\n" +
          "OVERALL (micro-averaged) P=0.625 R=0.5 F1=0.5556\n" +
          "OVERALL (macro-averaged) F1=0.5556\n\n";

  public void testPerFieldEval ()
  {
    Extraction extraction = createExtractionFrom (mpdPred, mpdTrue);
    PerFieldF1Evaluator eval = new PerFieldF1Evaluator ();
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    eval.evaluate ("Testing", extraction, new PrintStream (out));
    assertEquals (mpdExpected, out.toString().replaceAll("\\r\\n?", "\n"));
  }

    public void testToStdout ()
  {
    Extraction extraction = createExtractionFrom (mpdPred, mpdTrue);
    PerFieldF1Evaluator eval = new PerFieldF1Evaluator ();
    eval.evaluate (extraction);
    System.out.println ("*** Please verify that something was output above.");
  }

  private static final String[] punctPred = {
    "<title>Wizard of Oz,</title> by <author>John Smith</author> and <author>Adam Felber</author>",
    "<title>Jisp Boo Fuzz by</title> the estimable <title>Rich Q. Doe</title> and <author>Frank Wilson</author>",
    "<title>Howdy Doody</title>!, if you think this is Mr. nonsense <author>don't you huh</author>",
  };

  private static final String[] punctTrue = {
    "<title>Wizard of Oz</title>, by <author>John Smith</author> and <author>Adam Felber</author>",
    "<title>Jisp Boo Fuzz</title> by the estimable <author>Rich Q. Doe</author> and <author>Frank Wilson</author>",
    "<title>Howdy Doody!</title>, if <title>you</title> think this is <title>Mr.</title> <author> nonsense don't you huh</author>",
  };

  //xxx  Currently fails because grabbing the field span for Howdy Doody! grabs the </title> as
  //  well.  I think this is because getting the text subspan goes to the start of the next,
  //  rather than the end of the last.  It seems like that should be changed, but I'd need to
  //  think about the ikmplications for Rexa before doing this.
  /*
  public void testPunctuationIgnoringEvaluator ()
  {
    Extraction extraction = createExtractionFrom (punctPred, punctTrue);
    PerFieldF1Evaluator eval = new PerFieldF1Evaluator ();
    eval.setComparator (new PunctuationIgnoringComparator ());
    eval.setErrorOutputStream (System.out);

    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    eval.evaluate ("Testing", extraction, new PrintStream (out));
    assertEquals (mpdExpected, out.toString());
  }*/

  public void testFieldCleaning ()
  {
    Extraction extraction = createExtractionFrom (punctPred, punctTrue);
    extraction.cleanFields (new RegexFieldCleaner ("<.*?>|,|!"));

    PerFieldF1Evaluator eval = new PerFieldF1Evaluator ();
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    eval.evaluate ("Testing", extraction, new PrintStream (out));
    assertEquals (mpdExpected, out.toString().replaceAll("\\r\\n?", "\n"));
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestPerDocumentF1Evaluator (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
