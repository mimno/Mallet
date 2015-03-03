/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.learning;

import gnu.trove.list.array.TIntArrayList;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import cc.mallet.grmm.learning.ACRF;
import cc.mallet.grmm.learning.ACRFEvaluator;
import cc.mallet.grmm.learning.ACRF.MaximizableACRF;
import cc.mallet.grmm.util.LabelsAssignment;
import cc.mallet.optimize.ConjugateGradient;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Timing;


/**
 * Class for training ACRFs.
 * <p/>
 * <p/>
 * Created: Thu Oct 16 17:53:14 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: DefaultAcrfTrainer.java,v 1.1 2007/10/22 21:37:43 mccallum Exp $
 */
public class DefaultAcrfTrainer implements ACRFTrainer {

  private static Logger logger = MalletLogger.getLogger (DefaultAcrfTrainer.class.getName ());
  private Optimizer maxer;
  private static boolean rethrowExceptions = false;

  public DefaultAcrfTrainer ()
  {

  } // ACRFTrainer constructor


  private File outputPrefix = new File ("");

  public void setOutputPrefix (File f)
  {
    outputPrefix = f;
  }


  public Optimizer getMaxer ()
  {
    return maxer;
  }

  public void setMaxer (Optimizer maxer)
  {
    this.maxer = maxer;
  }


  public static boolean isRethrowExceptions ()
  {
    return rethrowExceptions;
  }

  public static void setRethrowExceptions (boolean rethrowExceptions)
  {
    DefaultAcrfTrainer.rethrowExceptions = rethrowExceptions;
  }

  public boolean train (ACRF acrf, InstanceList training)
  {
    return train (acrf, training, null, null,
            new LogEvaluator (), 1);
  }

  public boolean train (ACRF acrf, InstanceList training, int numIter)
  {
    return train (acrf, training, null, null,
            new LogEvaluator (), numIter);
  }

  public boolean train (ACRF acrf, InstanceList training, ACRFEvaluator eval, int numIter)
  {
    return train (acrf, training, null, null, eval, numIter);
  }

  public boolean train (ACRF acrf,
                        InstanceList training,
                        InstanceList validation,
                        InstanceList testing,
                        int numIter)
  {
    return train (acrf, training, validation, testing,
            new LogEvaluator (), numIter);
  }

  public boolean train (ACRF acrf,
                        InstanceList trainingList,
                        InstanceList validationList,
                        InstanceList testSet,
                        ACRFEvaluator eval,
                        int numIter)
  {
    Optimizable.ByGradientValue macrf = createOptimizable (acrf, trainingList);
    return train (acrf, trainingList, validationList, testSet,
            eval, numIter, macrf);
  }

  protected Optimizable.ByGradientValue createOptimizable (ACRF acrf, InstanceList trainingList)
  {
    return acrf.getMaximizable (trainingList);
  }

/*
	public boolean threadedTrain (ACRF acrf,
																InstanceList trainingList,
																InstanceList validationList,
																InstanceList testSet,
																ACRFEvaluator eval,
																int numIter)
	{
		Maximizable.ByGradient macrf = acrf.getThreadedMaximizable (trainingList);
		return train (dcrf, trainingList, validationList, testSet,
													eval, numIter, mdcrf);
	}
*/

  public boolean incrementalTrain (ACRF acrf,
                                   InstanceList training,
                                   InstanceList validation,
                                   InstanceList testing,
                                   int numIter)
  {
    return incrementalTrain (acrf, training, validation, testing,
            new LogEvaluator (), numIter);
  }

  private static final double[] SIZE = new double[]{0.1, 0.5};
  private static final int SUBSET_ITER = 10;

  public boolean incrementalTrain (ACRF acrf,
                                   InstanceList training,
                                   InstanceList validation,
                                   InstanceList testing,
                                   ACRFEvaluator eval,
                                   int numIter)
  {
    long stime = new Date ().getTime ();
    for (int i = 0; i < SIZE.length; i++) {
      InstanceList subset = training.split (new double[]
              {SIZE[i], 1 - SIZE[i]})[0];
      logger.info ("Training on subset of size " + subset.size ());
      Optimizable.ByGradientValue subset_macrf = createOptimizable (acrf, subset);
      train (acrf, training, validation, null, eval,
              SUBSET_ITER, subset_macrf);
      logger.info ("Subset training " + i + " finished...");
    }
    long etime = new Date ().getTime ();
    logger.info ("All subset training finished.  Time = " + (etime - stime) + " ms.");
    return train (acrf, training, validation, testing, eval, numIter);
  }

  public boolean train (ACRF acrf,
                        InstanceList trainingList,
                        InstanceList validationList,
                        InstanceList testSet,
                        ACRFEvaluator eval,
                        int numIter,
                        Optimizable.ByGradientValue macrf)
  {
    Optimizer maximizer = createMaxer (macrf);
//		Maximizer.ByGradient maximizer = new BoldDriver ();
//		Maximizer.ByGradient maximizer = new GradientDescent ();
    boolean converged = false;
    boolean resetOnError = true;
    long stime = System.currentTimeMillis ();

    int numNodes = (macrf instanceof ACRF.MaximizableACRF) ? ((ACRF.MaximizableACRF) macrf).getTotalNodes () : 0;
    double thresh = 1e-5 * numNodes; // "early" stopping (reasonably conservative)

    if (testSet == null) {
      logger.warning ("ACRF trainer: No test set provided.");
    }

    double prevValue = Double.NEGATIVE_INFINITY;
    double currentValue;
    int iter;
    for (iter = 0; iter < numIter; iter++) {
      long etime = new java.util.Date ().getTime ();
      logger.info ("ACRF trainer iteration " + iter + " at time " + (etime - stime));

      try {
        converged = maximizer.optimize (1);
        converged |= callEvaluator (acrf, trainingList, validationList, testSet, iter, eval);

        if (converged) break;
        resetOnError = true;

      } catch (RuntimeException e) {
        e.printStackTrace ();

        // If we get a maximizing error, reset LBFGS memory and try
        // again.  If we get an error on the second try too, then just
        // give up.
        if (resetOnError) {
          logger.warning ("Exception in iteration " + iter + ":" + e + "\n  Resetting LBFGs and trying again...");
          if (maximizer instanceof LimitedMemoryBFGS) ((LimitedMemoryBFGS) maximizer).reset ();
          if (maximizer instanceof ConjugateGradient) ((ConjugateGradient) maximizer).reset ();
          resetOnError = false;
        } else {
          logger.warning ("Exception in iteration " + iter + ":" + e + "\n   Quitting and saying converged...");
          converged = true;
          if (rethrowExceptions) throw e;
          break;
        }
      }
      if (converged) break;

      // "early" stopping
      currentValue = macrf.getValue ();
      if (Math.abs (currentValue - prevValue) < thresh) {
        // ignore cutoff if we're about to reset L-BFGS
        if (resetOnError) {
          logger.info ("ACRFTrainer saying converged: " +
                  " Current value " + currentValue + ", previous " + prevValue +
                  "\n...threshold was " + thresh + " = 1e-5 * " + numNodes);
          converged = true;
          break;
        }
      } else {
        prevValue = currentValue;
      }
    }

    if (iter >= numIter) {
      logger.info ("ACRFTrainer: Too many iterations, stopping training.  maxIter = "+numIter);
    }

    long etime = System.currentTimeMillis ();
    logger.info ("ACRF training time (ms) = " + (etime - stime));

    if (macrf instanceof MaximizableACRF) {
      ((MaximizableACRF) macrf).report ();
    }

    if ((testSet != null) && (eval != null)) {
      // don't cache test set
      boolean oldCache = acrf.isCacheUnrolledGraphs ();
      acrf.setCacheUnrolledGraphs (false);
      eval.test (acrf, testSet, "Testing");
      acrf.setCacheUnrolledGraphs (oldCache);
    }

    return converged;
  }

  private Optimizer createMaxer (Optimizable.ByGradientValue macrf)
  {
    if (maxer == null) {
      return new LimitedMemoryBFGS (macrf);
    } else return maxer;
  }

  /**
   * @return true means stop, false means keep going (opposite of evaluators... ugh!)
   */
  protected boolean callEvaluator (ACRF acrf, InstanceList trainingList, InstanceList validationList,
                                 InstanceList testSet, int iter, ACRFEvaluator eval)
  {
    if (eval == null) return false;  // If no evaluator specified, keep going blindly

    eval.setOutputPrefix (outputPrefix);

    // don't cache test set
    boolean wasCached = acrf.isCacheUnrolledGraphs ();
    acrf.setCacheUnrolledGraphs (false);

    Timing timing = new Timing ();

    if (!eval.evaluate (acrf, iter+1, trainingList, validationList, testSet)) {
      logger.info ("ACRF trainer: evaluator returned false. Quitting.");
      timing.tick ("Evaluation time (iteration "+iter+")");
      return true;
    }

    timing.tick ("Evaluation time (iteration "+iter+")");

    // set test set caching back to normal
    acrf.setCacheUnrolledGraphs (wasCached);
    return false;
  }

  public boolean someUnsupportedTrain (ACRF acrf,
                        InstanceList trainingList,
                        InstanceList validationList,
                        InstanceList testSet,
                        ACRFEvaluator eval,
                        int numIter)
  {

    Optimizable.ByGradientValue macrf = createOptimizable (acrf, trainingList);
    train (acrf, trainingList, validationList, testSet, eval, 5, macrf);
    ACRF.Template[] tmpls = acrf.getTemplates ();
    for (int ti = 0; ti < tmpls.length; ti++)
      tmpls[ti].addSomeUnsupportedWeights (trainingList);
    logger.info ("Some unsupporetd weights initialized.  Training...");
    return train (acrf, trainingList, validationList, testSet, eval, numIter, macrf);
  }

  public void test (ACRF acrf, InstanceList testing, ACRFEvaluator eval)
  {
    test (acrf, testing, new ACRFEvaluator[]{eval});
  }

  public void test (ACRF acrf, InstanceList testing, ACRFEvaluator[] evals)
  {
    List pred = acrf.getBestLabels (testing);
    for (int i = 0; i < evals.length; i++) {
      evals[i].setOutputPrefix (outputPrefix);
      evals[i].test (testing, pred, "Testing");
    }
  }

  private static final Random r = new Random (1729);


  public static Random getRandom ()
  {
    return r;
  }

  public void train (ACRF acrf, InstanceList training, InstanceList validation, InstanceList testing,
                     ACRFEvaluator eval, double[] proportions, int iterPerProportion)
  {
    for (int i = 0; i < proportions.length; i++) {
      double proportion = proportions[i];
      InstanceList[] lists = training.split (r, new double[]{proportion, 1.0});
      logger.info ("ACRF trainer: Round " + i + ", training proportion = " + proportion);
      train (acrf, lists[0], validation, testing, eval, iterPerProportion);
    }

    logger.info ("ACRF trainer: Training on full data");
    train (acrf, training, validation, testing, eval, 99999);
  }

  public static class LogEvaluator extends ACRFEvaluator {

    private TestResults lastResults;

    public LogEvaluator ()
    {
    }

    ;

    public boolean evaluate (ACRF acrf, int iter,
                             InstanceList training,
                             InstanceList validation,
                             InstanceList testing)
    {
      if (shouldDoEvaluate (iter)) {
        if (training != null) { test (acrf, training, "Training"); }
        if (testing != null) { test (acrf, testing, "Testing"); }
      }
      return true;
    }

    public void test (InstanceList testList, List returnedList,
                      String description)
    {
      logger.info (description+": Number of instances = " + testList.size ());
      TestResults results = computeTestResults (testList, returnedList);
      results.log (description);
      lastResults = results;
//		results.printConfusion ();
    }

    public static TestResults computeTestResults (InstanceList testList, List returnedList)
    {
      TestResults results = new TestResults (testList);
      Iterator it1 = testList.iterator ();
      Iterator it2 = returnedList.iterator ();
      while (it1.hasNext ()) {
        Instance inst = (Instance) it1.next ();
//			System.out.println ("\n\nInstance");
        LabelsAssignment lblseq = (LabelsAssignment) inst.getTarget ();
        LabelsSequence target = lblseq.getLabelsSequence ();
        LabelsSequence returned = (LabelsSequence) it2.next ();
//			System.out.println (target);
        compareLabelings (results, returned, target);
      }

      results.computeStatistics ();
      return results;
    }


    static void compareLabelings (TestResults results,
                                  LabelsSequence returned,
                                  LabelsSequence target)
    {
      assert returned.size () == target.size ();
      for (int i = 0; i < returned.size (); i++) {
//			System.out.println ("Time "+i);
        Labels lblsReturned = returned.getLabels (i);
        Labels lblsTarget = target.getLabels (i);
        results.incrementCount (lblsReturned, lblsTarget);
      }
    }

    public double getJointAccuracy ()
    {
      return lastResults.getJointAccuracy ();
    }
  }

  public static class FileEvaluator extends ACRFEvaluator {

    private File file;

    public FileEvaluator (File file)
    {
      this.file = file;
    }

    ;

    public boolean evaluate (ACRF acrf, int iter,
                             InstanceList training,
                             InstanceList validation,
                             InstanceList testing)
    {
      if (shouldDoEvaluate (iter)) {
        test (acrf, testing, "Testing ");
      }
      return true;
    }

    public void test (InstanceList testList, List returnedList,
                      String description)
    {
      logger.info ("Number of testing instances = " + testList.size ());
      TestResults results = LogEvaluator.computeTestResults (testList, returnedList);

      try {
        PrintWriter writer = new PrintWriter (new FileWriter (file, true));
        results.print (description, writer);
        writer.close ();
      } catch (Exception e) {
        e.printStackTrace ();
      }
//		results.printConfusion ();
    }
  }

  public static class TestResults {

    public int[][] confusion;  // Confusion matrix
    public int numClasses;

    // Marginals of confusion matrix
    public int[] trueCounts;
    public int[] returnedCounts;

    // Per-class precision, recall, and F1.
    public double[] precision;
    public double[] recall;
    public double[] f1;

    // Measuring accuracy of each factor
    public TIntArrayList[] factors;

    // Measuring joint accuracy
    public int maxT = 0;
    public int correctT = 0;

    public Alphabet alphabet;

    TestResults (InstanceList ilist)
    {
      this (ilist.get (0));
    }

    TestResults (Instance inst)
    {
      alphabet = new Alphabet ();
      setupAlphabet (inst);

      numClasses = alphabet.size ();
      confusion = new int [numClasses][numClasses];
      precision = new double [numClasses];
      recall = new double [numClasses];
      f1 = new double [numClasses];
    }

    // This isn't pretty, but I swear there's
    //  not an easy way...
    private void setupAlphabet (Instance inst)
    {
      LabelsAssignment lblseq = (LabelsAssignment) inst.getTarget ();
      factors = new TIntArrayList [lblseq.numSlices ()];
      for (int i = 0; i < lblseq.numSlices (); i++) {
        LabelAlphabet dict = lblseq.getOutputAlphabet (i);
        factors[i] = new TIntArrayList (dict.size ());
        for (int j = 0; j < dict.size (); j++) {
          int idx = alphabet.lookupIndex (dict.lookupObject (j));
          factors[i].add (idx);
        }
      }
    }

    void incrementCount (Labels lblsReturned, Labels lblsTarget)
    {
      boolean allSame = true;

      // and per-label accuracy
      for (int j = 0; j < lblsReturned.size (); j++) {
        Label lret = lblsReturned.get (j);
        Label ltarget = lblsTarget.get (j);
//				System.out.println(ltarget+" vs. "+lret);
        int idxTrue = alphabet.lookupIndex (ltarget.getEntry ());
        int idxRet = alphabet.lookupIndex (lret.getEntry ());
        if (idxTrue != idxRet) allSame = false;
        confusion[idxTrue][idxRet]++;
      }

      // Measure joint accuracy
      maxT++;
      if (allSame) correctT++;
    }

    void computeStatistics ()
    {
      // Compute marginals of confusion matrix.
      //  Assumes that confusion[i][j] means true label i and
      //  returned label j
      trueCounts = new int [numClasses];
      returnedCounts = new int [numClasses];
      for (int i = 0; i < numClasses; i++) {
        for (int j = 0; j < numClasses; j++) {
          trueCounts[i] += confusion[i][j];
          returnedCounts[j] += confusion[i][j];
        }
      }

      // Compute per-class precision, recall, and F1
      for (int i = 0; i < numClasses; i++) {
        double correct = confusion[i][i];

        if (returnedCounts[i] == 0) {
          precision[i] = (correct == 0) ? 1.0 : 0.0;
        } else {
          precision[i] = correct / returnedCounts[i];
        }

        if (trueCounts[i] == 0) {
          recall[i] = 1.0;
        } else {
          recall[i] = correct / trueCounts[i];
        }

        f1[i] = (2 * precision[i] * recall[i]) / (precision[i] + recall[i]);
      }
    }

    public void log ()
    {
      log ("");
    }

    public void log (String desc)
    {
      logger.info (desc+":  i\tLabel\tN\tCorrect\tReturned\tP\tR\tF1");
      for (int i = 0; i < numClasses; i++) {
        logger.info (desc+":  "+i + "\t" + alphabet.lookupObject (i) + "\t"
                + trueCounts[i] + "\t"
                + confusion[i][i] + "\t"
                + returnedCounts[i] + "\t"
                + precision[i] + "\t"
                + recall[i] + "\t"
                + f1[i] + "\t");
      }
      for (int fnum = 0; fnum < factors.length; fnum++) {
        int correct = 0;
        int returned = 0;
        for (int i = 0; i < factors[fnum].size (); i++) {
          int lbl = factors[fnum].get (i);
          correct += confusion[lbl][lbl];
          returned += returnedCounts[lbl];
        }
        logger.info (desc + ":  Factor " + fnum + " accuracy: (" + correct + " " + returned + ") "
                + (correct / ((double) returned)));
      }

      logger.info (desc + " CorrectT " + correctT + "  maxt " + maxT);
      logger.info (desc + " Joint accuracy: " + ((double) correctT) / maxT);
    }

    public void print (String desc, PrintWriter out)
    {
      out.println ("i\tLabel\tN\tCorrect\tReturned\tP\tR\tF1");
      for (int i = 0; i < numClasses; i++) {
        out.println (i + "\t" + alphabet.lookupObject (i) + "\t"
                + trueCounts[i] + "\t"
                + confusion[i][i] + "\t"
                + returnedCounts[i] + "\t"
                + precision[i] + "\t"
                + recall[i] + "\t"
                + f1[i] + "\t");
      }
      for (int fnum = 0; fnum < factors.length; fnum++) {
        int correct = 0;
        int returned = 0;
        for (int i = 0; i < factors[fnum].size (); i++) {
          int lbl = factors[fnum].get (i);
          correct += confusion[lbl][lbl];
          returned += returnedCounts[lbl];
        }
        out.println (desc + " Factor " + fnum + " accuracy: (" + correct + " " + returned + ") "
                + (correct / ((double) returned)));
      }

      out.println (desc + " CorrectT " + correctT + "  maxt " + maxT);
      out.println (desc + " Joint accuracy: " + ((double) correctT) / maxT);
    }

    void printConfusion ()
    {
      System.out.println ("True\t\tReturned\tCount");
      for (int i = 0; i < numClasses; i++) {
        for (int j = 0; j < numClasses; j++) {
          System.out.println (i + "\t\t" + j + "\t" + confusion[i][j]);
        }
      }
    }

    public double getJointAccuracy ()
    {
      return ((double) correctT) / maxT;
    }

  } // TestResults


} // ACRFTrainer
