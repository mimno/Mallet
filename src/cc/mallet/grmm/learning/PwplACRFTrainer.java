/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning;


import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.grmm.types.*;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.*;
import cc.mallet.util.FileUtils;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;
import cc.mallet.util.Timing;
import cc.mallet.grmm.util.CachingOptimizable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation of piecewise PL (Sutton and McCallum, 2007)
 *
 * NB The wrong-wrong options are for an extension that we tried that never quite worked
 *
 * Created: Mar 15, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: BiconditionalPiecewiseACRFTrainer.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class PwplACRFTrainer extends DefaultAcrfTrainer {

  private static final Logger logger = MalletLogger.getLogger (PwplACRFTrainer.class.getName ());
  public static boolean printGradient = false;

  public static final int NO_WRONG_WRONG = 0;
  public static final int CONDITION_WW = 1;
  private int wrongWrongType = NO_WRONG_WRONG;

  private int wrongWrongIter = 10;
  private double wrongWrongThreshold = 0.1;
  private File outputPrefix = new File (".");

  public Optimizable.ByGradient createOptimizable (ACRF acrf, InstanceList training)
  {
    return new PwplACRFTrainer.Maxable (acrf, training);
  }

  public double getWrongWrongThreshold ()
  {
    return wrongWrongThreshold;
  }

  public void setWrongWrongThreshold (double wrongWrongThreshold)
  {
    this.wrongWrongThreshold = wrongWrongThreshold;
  }

  public void setWrongWrongType (int wrongWrongType)
  {
    this.wrongWrongType = wrongWrongType;
  }

  public void setWrongWrongIter (int wrongWrongIter)
  {
    this.wrongWrongIter = wrongWrongIter;
  }

  public boolean train (ACRF acrf, InstanceList trainingList, InstanceList validationList, InstanceList testSet,
                        ACRFEvaluator eval, int numIter, Optimizable.ByGradientValue macrf)
  {
    if (wrongWrongType == NO_WRONG_WRONG) {
      return super.train (acrf, trainingList, validationList, testSet, eval, numIter, macrf);
    } else {
      Maxable bipwMaxable = (Maxable) macrf;
      // add wrong wrongs after 5 iterations
      logger.info ("BiconditionalPiecewiseACRFTrainer: Initial training");
      super.train (acrf, trainingList, validationList, testSet, eval, wrongWrongIter, macrf);
      FileUtils.writeGzippedObject (new File (outputPrefix, "initial-acrf.ser.gz"), acrf);
      logger.info ("BiconditionalPiecewiseACRFTrainer: Adding wrong-wrongs");
      bipwMaxable.addWrongWrong (trainingList);
      logger.info ("BiconditionalPiecewiseACRFTrainer: Adding wrong-wrongs");
      boolean converged = super.train (acrf, trainingList, validationList, testSet, eval, numIter, macrf);
      reportTrainingLikelihood (acrf, trainingList);
      return converged;
    }
  }

  // Reports true joint likelihood of estimated parameters on the training set.
  public static void reportTrainingLikelihood (ACRF acrf, InstanceList trainingList)
  {
    double total = 0;
    Inferencer inf = acrf.getInferencer ();
    for (int i = 0; i < trainingList.size (); i++) {
      Instance inst = trainingList.get (i);
      ACRF.UnrolledGraph unrolled = acrf.unroll (inst);
      inf.computeMarginals (unrolled);
      double lik = inf.lookupLogJoint (unrolled.getAssignment ());
      total += lik;
      logger.info ("...instance "+i+" likelihood = "+lik);
    }
    logger.info ("Unregularized joint likelihood = "+total);
  }

  public class Maxable extends CachingOptimizable.ByGradient {

    private ACRF acrf;
    InstanceList trainData;

    private ACRF.Template[] templates;

    protected BitSet infiniteValues = null;
    private int numParameters;

    private static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 10.0;

    public double getGaussianPriorVariance ()
    {
      return gaussianPriorVariance;
    }

    public void setGaussianPriorVariance (double gaussianPriorVariance)
    {
      this.gaussianPriorVariance = gaussianPriorVariance;
    }

    private double gaussianPriorVariance = PwplACRFTrainer.Maxable.DEFAULT_GAUSSIAN_PRIOR_VARIANCE;

    /* Vectors that contain the counts of features observed in the
       training data. Maps
       (clique-template x feature-number) => count
    */
    SparseVector constraints[][];

    /* Vectors that contain the expected value over the
     *  labels of all the features, have seen the training data
     *  (but not the training labels).
     */
    SparseVector expectations[][];

    SparseVector defaultConstraints[];
    SparseVector defaultExpectations[];

    private void initWeights (InstanceList training)
    {
      for (int tidx = 0; tidx < templates.length; tidx++) {
        numParameters += templates[tidx].initWeights (training);
      }
    }

    /* Initialize constraints[][] and expectations[][]
     *  to have the same dimensions as weights, but to
     *  be all zero.
     */
    private void initConstraintsExpectations ()
    {
      // Do the defaults first
      defaultConstraints = new SparseVector [templates.length];
      defaultExpectations = new SparseVector [templates.length];
      for (int tidx = 0; tidx < templates.length; tidx++) {
        SparseVector defaults = templates[tidx].getDefaultWeights ();
        defaultConstraints[tidx] = (SparseVector) defaults.cloneMatrixZeroed ();
        defaultExpectations[tidx] = (SparseVector) defaults.cloneMatrixZeroed ();
      }

      // And now the others
      constraints = new SparseVector [templates.length][];
      expectations = new SparseVector [templates.length][];
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates[tidx];
        SparseVector[] weights = tmpl.getWeights ();
        constraints[tidx] = new SparseVector [weights.length];
        expectations[tidx] = new SparseVector [weights.length];

        for (int i = 0; i < weights.length; i++) {
          constraints[tidx][i] = (SparseVector) weights[i].cloneMatrixZeroed ();
          expectations[tidx][i] = (SparseVector) weights[i].cloneMatrixZeroed ();
        }
      }
    }


    private int numCvgaCalls = 0;
    private long timePerCvgaCall = 0;

    void resetProfilingForCall ()
    {
      numCvgaCalls = 0;
      timePerCvgaCall = 0;
    }

    /**
     * Set all expectations to 0 after they've been
     * initialized.
     */
    void resetExpectations ()
    {
      for (int tidx = 0; tidx < expectations.length; tidx++) {
        defaultExpectations[tidx].setAll (0.0);
        for (int i = 0; i < expectations[tidx].length; i++) {
          expectations[tidx][i].setAll (0.0);
        }
      }
    }

    void resetConstraints ()
    {
      for (int tidx = 0; tidx < constraints.length; tidx++) {
        defaultConstraints[tidx].setAll (0.0);
        for (int i = 0; i < constraints[tidx].length; i++) {
          constraints[tidx][i].setAll (0.0);
        }
      }
    }

    protected Maxable (ACRF acrf, InstanceList ilist)
    {
      PwplACRFTrainer.logger.finest ("Initializing OptimizableACRF.");

      this.acrf = acrf;
      templates = acrf.getTemplates ();

      /* allocate for weights, constraints and expectations */
      this.trainData = ilist;
      initWeights (trainData);
      initConstraintsExpectations ();

      int numInstances = trainData.size ();

      cachedValueStale = cachedGradientStale = true;

/*
	if (cacheUnrolledGraphs) {
	unrolledGraphs = new UnrolledGraph [numInstances];
	}
*/

      PwplACRFTrainer.logger.info ("Number of training instances = " + numInstances);
      PwplACRFTrainer.logger.info ("Number of parameters = " + numParameters);
      describePrior ();

      PwplACRFTrainer.logger.fine ("Computing constraints");
      collectConstraints (trainData);
    }

    private void describePrior ()
    {
      PwplACRFTrainer.logger.info ("Using gaussian prior with variance " + gaussianPriorVariance);
    }

    public int getNumParameters () { return numParameters; }

    /* Negate initialValue and finalValue because the parameters are in
     * terms of "weights", not "values".
     */
    public void getParameters (double[] buf)
    {

      if (buf.length != numParameters) {
        throw new IllegalArgumentException ("Argument is not of the " +
                " correct dimensions");
      }
      int idx = 0;
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates[tidx];
        SparseVector defaults = tmpl.getDefaultWeights ();
        double[] values = defaults.getValues ();
        System.arraycopy (values, 0, buf, idx, values.length);
        idx += values.length;
      }

      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates[tidx];
        SparseVector[] weights = tmpl.getWeights ();
        for (int assn = 0; assn < weights.length; assn++) {
          double[] values = weights[assn].getValues ();
          System.arraycopy (values, 0, buf, idx, values.length);
          idx += values.length;
        }
      }

    }


    protected void setParametersInternal (double[] params)
    {
      cachedValueStale = cachedGradientStale = true;

      int idx = 0;
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates[tidx];
        SparseVector defaults = tmpl.getDefaultWeights ();
        double[] values = defaults.getValues ();
        System.arraycopy (params, idx, values, 0, values.length);
        idx += values.length;
      }

      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates[tidx];
        SparseVector[] weights = tmpl.getWeights ();
        for (int assn = 0; assn < weights.length; assn++) {
          double[] values = weights[assn].getValues ();
          System.arraycopy (params, idx, values, 0, values.length);
          idx += values.length;
        }
      }
    }

    // Functions for unit tests to get constraints and expectations
    //  I'm too lazy to make a deep copy.  Callers should not
    //  modify these.

    public SparseVector[] getExpectations (int cnum) { return expectations[cnum]; }

    public SparseVector[] getConstraints (int cnum) { return constraints[cnum]; }

    /**
     * print weights
     */
    public void printParameters ()
    {
      double[] buf = new double[numParameters];
      getParameters (buf);

      int len = buf.length;
      for (int w = 0; w < len; w++)
        System.out.print (buf[w] + "\t");
      System.out.println ();
    }


    protected double computeValue ()
    {
      double retval = 0.0;
      int numInstances = trainData.size ();

      long start = System.currentTimeMillis ();
      long unrollTime = 0;
      resetProfilingForCall ();

      /* Instance values must either always or never be included in
       * the total values; we can't just sometimes skip a value
       * because it is infinite, that throws off the total values.
       * We only allow an instance to have infinite value if it happens
       * from the start (we don't compute the value for the instance
       * after the first round. If any other instance has infinite
       * value after that it is an error. */

      boolean initializingInfiniteValues = false;

      if (infiniteValues == null) {
        /* We could initialize bitset with one slot for every
         * instance, but it is *probably* cheaper not to, taking the
         * time hit to allocate the space if a bit becomes
         * necessary. */
        infiniteValues = new BitSet ();
        initializingInfiniteValues = true;
      }

      /* Clear the sufficient statistics that we are about to fill */
      resetExpectations ();

      /* Fill in expectations for each instance */
      for (int i = 0; i < numInstances; i++) {
        Instance instance = trainData.get (i);

        /* Compute marginals for each clique */
        long unrollStart = System.currentTimeMillis ();
        ACRF.UnrolledGraph unrolled = acrf.unrollStructureOnly (instance);
//        ACRF.UnrolledGraph unrolled = new ACRF.UnrolledGraph (instance, templates, Arrays.asList (fixedTmpls), false);
        long unrollEnd = System.currentTimeMillis ();
        unrollTime += (unrollEnd - unrollStart);

//        if (unrolled.numVariables () == 0) continue;   // Happens if all nodes are pruned.

        /* Save the expected value of each feature for when we
           compute the gradient. */
        Assignment observations = unrolled.getAssignment ();
        double value = collectExpectationsAndValue (unrolled, observations, i);

        if (Double.isInfinite (value)) {
          if (initializingInfiniteValues) {
            PwplACRFTrainer.logger.warning ("Instance " + instance.getName () +
                    " has infinite value; skipping.");
            infiniteValues.set (i);
//            continue;
          } else if (!infiniteValues.get (i)) {
            PwplACRFTrainer.logger.warning ("Infinite value on instance " + instance.getName () +
                    "returning -infinity");
            return Double.NEGATIVE_INFINITY;
/*
						printDebugInfo (unrolled);
						throw new IllegalStateException
							("Instance " + instance.getName()+ " used to have non-infinite"
							 + " value, but now it has infinite value.");
*/
          }
        } else if (Double.isNaN (value)) {
          System.out.println ("NaN on instance " + i + " : " + instance.getName ());
          printDebugInfo (unrolled);
/*					throw new IllegalStateException
						("Value is NaN in ACRF.getValue() Instance "+i);
*/
          PwplACRFTrainer.logger.warning ("Value is NaN in ACRF.getValue() Instance " + i + " : " +
                  "returning -infinity... ");
          return Double.NEGATIVE_INFINITY;
        } else {
          retval += value;
        }

      }

      /* Incorporate Gaussian prior on parameters. This means
         that for each weight, we will add w^2 / (2 * variance) to the
         log probability. */

      double priorDenom = 2 * gaussianPriorVariance;

      for (int tidx = 0; tidx < templates.length; tidx++) {
        SparseVector[] weights = templates[tidx].getWeights ();
        for (int j = 0; j < weights.length; j++) {
          for (int fnum = 0; fnum < weights[j].numLocations (); fnum++) {
            double w = weights[j].valueAtLocation (fnum);
            if (weightValid (w, tidx, j)) {
              retval += -w * w / priorDenom;
            }
          }
        }
      }

      long end = System.currentTimeMillis ();
      PwplACRFTrainer.logger.info ("ACRF Inference time (ms) = " + (end - start));
      PwplACRFTrainer.logger.info ("ACRF unroll time (ms) = " + unrollTime);
      PwplACRFTrainer.logger.info ("getValue (loglikelihood) = " + retval);

      logger.info ("Number cVGA calls = " + numCvgaCalls);
      logger.info ("Total cVGA time (ms) = " + timePerCvgaCall);

      return retval;
    }


    /**
     * Computes the gradient of the penalized log likelihood of the
     * ACRF, and places it in cachedGradient[].
     * <p/>
     * Gradient is
     * constraint - expectation - parameters/gaussianPriorVariance
     */
    protected void computeValueGradient (double[] grad)
    {
      /* Index into current element of cachedGradient[] array. */
      int gidx = 0;

      // First do gradient wrt defaultWeights
      for (int tidx = 0; tidx < templates.length; tidx++) {
        SparseVector theseWeights = templates[tidx].getDefaultWeights ();
        SparseVector theseConstraints = defaultConstraints[tidx];
        SparseVector theseExpectations = defaultExpectations[tidx];
        for (int j = 0; j < theseWeights.numLocations (); j++) {
          double weight = theseWeights.valueAtLocation (j);
          double constraint = theseConstraints.valueAtLocation (j);
          double expectation = theseExpectations.valueAtLocation (j);
          if (PwplACRFTrainer.printGradient) {
            System.out.println (" gradient [" + gidx + "] = " + constraint + " (ctr) - " + expectation + " (exp) - " +
                    (weight / gaussianPriorVariance) + " (reg)  [feature=DEFAULT]");
          }
          grad[gidx++] = constraint - expectation - (weight / gaussianPriorVariance);
        }
      }

      // Now do other weights
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates[tidx];
        SparseVector[] weights = tmpl.getWeights ();
        for (int i = 0; i < weights.length; i++) {
          SparseVector thisWeightVec = weights[i];
          SparseVector thisConstraintVec = constraints[tidx][i];
          SparseVector thisExpectationVec = expectations[tidx][i];

          for (int j = 0; j < thisWeightVec.numLocations (); j++) {
            double w = thisWeightVec.valueAtLocation (j);
            double gradient;  // Computed below

            double constraint = thisConstraintVec.valueAtLocation (j);
            double expectation = thisExpectationVec.valueAtLocation (j);

            /* A parameter may be set to -infinity by an external user.
             * We set gradient to 0 because the parameter's value can
             * never change anyway and it will mess up future calculations
             * on the matrix. */
            if (Double.isInfinite (w)) {
              PwplACRFTrainer.logger.warning ("Infinite weight for node index " + i +
                      " feature " +
                      acrf.getInputAlphabet ().lookupObject (j));
              gradient = 0.0;
            } else {
              gradient = constraint
                      - (w / gaussianPriorVariance)
                      - expectation;
            }

            if (PwplACRFTrainer.printGradient) {
              int idx = thisWeightVec.indexAtLocation (j);
              Object fname = acrf.getInputAlphabet ().lookupObject (idx);
              System.out.println (" gradient [" + gidx + "] = " + constraint + " (ctr) - " + expectation + " (exp) - " +
                      (w / gaussianPriorVariance) + " (reg)  [feature=" + fname + "]");
            }

            grad[gidx++] = gradient;
          }
        }
      }
    }

    /**
     * For every feature f_k, computes the expected value of f_k
     * aver all possible label sequences given the list of instances
     * we have.
     * <p/>
     * These values are stored in collector, that is,
     * collector[i][j][k]  gets the expected value for the
     * feature for clique i, label assignment j, and input features k.
     */
    private double collectExpectationsAndValue (ACRF.UnrolledGraph unrolled, Assignment observations, int inum)
    {
      double value = 0.0;
      for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext ();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
        ACRF.Template tmpl = clique.getTemplate ();
        int tidx = tmpl.index;
        if (tidx == -1) continue;

        for (int vi = 0; vi < clique.size (); vi++) {
          Variable target = clique.get (vi);
          value += computeValueGradientForAssn (observations, clique, target);
        }
      }

      switch (wrongWrongType) {
        case NO_WRONG_WRONG:
          break;

        case CONDITION_WW:
          value += addConditionalWW (unrolled, inum);
          break;

        default:
          throw new IllegalStateException ();
      }

      return value;
    }

    private double addConditionalWW (ACRF.UnrolledGraph unrolled, int inum)
    {
      double value = 0;
      if (allWrongWrongs != null) {
        List wrongs = allWrongWrongs[inum];
        for (Iterator it = wrongs.iterator (); it.hasNext ();) {
          WrongWrong ww = (WrongWrong) it.next ();
          Variable target = ww.findVariable (unrolled);
          ACRF.UnrolledVarSet clique = ww.findVarSet (unrolled);
          Assignment wrong = Assignment.makeFromSingleIndex (clique, ww.assnIdx);
//          System.out.println ("Computing for WW: "+clique+" idx "+ww.assnIdx+" target "+target);
          value += computeValueGradientForAssn (wrong, clique, target);
        }
      }
      return value;
    }

    private double computeValueGradientForAssn (Assignment observations, ACRF.UnrolledVarSet clique, Variable target)
    {
      numCvgaCalls++;
      Timing timing = new Timing ();

      ACRF.Template tmpl = clique.getTemplate ();
      int tidx = tmpl.index;
      Assignment cliqueAssn = Assignment.restriction (observations, clique);
      int M = target.getNumOutcomes ();
      double[] vals = new double [M];
      int[] singles = new int [M];
      for (int assnIdx = 0; assnIdx < M; assnIdx++) {
        cliqueAssn.setValue (target, assnIdx);
        vals[assnIdx] = computeLogFactorValue (cliqueAssn, tmpl, clique.getFv ());
        singles[assnIdx] = cliqueAssn.singleIndex ();
      }
      double logZ = Maths.sumLogProb (vals);

      for (int assnIdx = 0; assnIdx < M; assnIdx++) {
        double marginal = Math.exp (vals[assnIdx] - logZ);
        int expIdx = singles[assnIdx];
        expectations[tidx][expIdx].plusEqualsSparse (clique.getFv (), marginal);
        if (defaultExpectations[tidx].location (expIdx) != -1) {
          defaultExpectations[tidx].incrementValue (expIdx, marginal);
        }
      }

      int observedVal = observations.get (target);

      timePerCvgaCall += timing.elapsedTime ();

      return vals[observedVal] - logZ;
    }

    private double computeLogFactorValue (Assignment cliqueAssn, ACRF.Template tmpl, FeatureVector fv)
    {
      SparseVector[] weights = tmpl.getWeights ();
      int idx = cliqueAssn.singleIndex ();
      SparseVector w = weights[idx];
      double dp = w.dotProduct (fv);
      dp += tmpl.getDefaultWeight (idx);
      return dp;
    }


    public void collectConstraints (InstanceList ilist)
    {
      for (int inum = 0; inum < ilist.size (); inum++) {
        PwplACRFTrainer.logger.finest ("*** Collecting constraints for instance " + inum);
        Instance inst = ilist.get (inum);
        ACRF.UnrolledGraph unrolled = new ACRF.UnrolledGraph (inst, templates, null, false);
        for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext ();) {
          ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
          int tidx = clique.getTemplate ().index;
          if (tidx == -1) continue;

          int assn = clique.lookupAssignmentNumber ();
          constraints[tidx][assn].plusEqualsSparse (clique.getFv (), clique.size ());
          if (defaultConstraints[tidx].location (assn) != -1) {
            defaultConstraints[tidx].incrementValue (assn, clique.size ());
          }
        }

        // constraints for wrong-wrongs for instance
        if (allWrongWrongs != null) {
          List wrongs = allWrongWrongs[inum];
          for (Iterator wwIt = wrongs.iterator (); wwIt.hasNext ();) {
            WrongWrong ww = (WrongWrong) wwIt.next ();
            ACRF.UnrolledVarSet clique = ww.findVarSet (unrolled);
            int tidx = clique.getTemplate ().index;
            int wrong2rightId = ww.assnIdx;
            constraints[tidx][wrong2rightId].plusEqualsSparse (clique.getFv (), 1.0);
            if (defaultConstraints[tidx].location (wrong2rightId) != -1) {
              defaultConstraints[tidx].incrementValue (wrong2rightId, 1.0);
            }
          }
        }
      }
    }

    void dumpGradientToFile (String fileName)
    {
      try {
        double[] grad = new double [getNumParameters ()];
        getValueGradient (grad);

        PrintStream w = new PrintStream (new FileOutputStream (fileName));
        for (int i = 0; i < numParameters; i++) {
          w.println (grad[i]);
        }
        w.close ();
      } catch (IOException e) {
        System.err.println ("Could not open output file.");
        e.printStackTrace ();
      }
    }

    void dumpDefaults ()
    {
      System.out.println ("Default constraints");
      for (int i = 0; i < defaultConstraints.length; i++) {
        System.out.println ("Template " + i);
        defaultConstraints[i].print ();
      }
      System.out.println ("Default expectations");
      for (int i = 0; i < defaultExpectations.length; i++) {
        System.out.println ("Template " + i);
        defaultExpectations[i].print ();
      }
    }

    void printDebugInfo (ACRF.UnrolledGraph unrolled)
    {
      acrf.print (System.err);
      Assignment assn = unrolled.getAssignment ();
      for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext ();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
        System.out.println ("Clique " + clique);
        dumpAssnForClique (assn, clique);
        Factor ptl = unrolled.factorOf (clique);
        System.out.println ("Value = " + ptl.value (assn));
        System.out.println (ptl);
      }
    }

    void dumpAssnForClique (Assignment assn, ACRF.UnrolledVarSet clique)
    {
      for (Iterator it = clique.iterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        System.out.println (var + " ==> " + assn.getObject (var)
                + "  (" + assn.get (var) + ")");
      }
    }


    private boolean weightValid (double w, int cnum, int j)
    {
      if (Double.isInfinite (w)) {
        PwplACRFTrainer.logger.warning ("Weight is infinite for clique " + cnum + "assignment " + j);
        return false;
      } else if (Double.isNaN (w)) {
        PwplACRFTrainer.logger.warning ("Weight is Nan for clique " + cnum + "assignment " + j);
        return false;
      } else {
        return true;
      }
    }

    //  WRONG WRONG HANDLING

    private class WrongWrong {
      int varIdx;
      int vsIdx;
      int assnIdx;

      public WrongWrong (ACRF.UnrolledGraph graph, VarSet vs, Variable var, int assnIdx)
      {
        varIdx = graph.getIndex (var);
        vsIdx = graph.getIndex (vs);
        this.assnIdx = assnIdx;
      }

      public ACRF.UnrolledVarSet findVarSet (ACRF.UnrolledGraph unrolled)
      {
        return unrolled.getUnrolledVarSet (vsIdx);
      }

      public Variable findVariable (ACRF.UnrolledGraph unrolled)
      {
        return unrolled.get (varIdx);
      }
    }

    private List allWrongWrongs[];

    private void addWrongWrong (InstanceList training)
    {
      allWrongWrongs = new List [training.size ()];
      int totalAdded = 0;

//      if (!acrf.isCacheUnrolledGraphs ()) {
//        throw new IllegalStateException ("Wrong-wrong won't work without caching unrolled graphs.");
//      }

      for (int i = 0; i < training.size (); i++) {
        allWrongWrongs[i] = new ArrayList ();
        int numAdded = 0;

        Instance instance = training.get (i);
        ACRF.UnrolledGraph unrolled = acrf.unroll (instance);
        if (unrolled.factors ().size () == 0) {
          System.err.println ("WARNING: FactorGraph for instance " + instance.getName () + " : no factors.");
          continue;
        }

        Inferencer inf = acrf.getInferencer ();
        inf.computeMarginals (unrolled);

        Assignment target = unrolled.getAssignment ();
        for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext ();) {
          ACRF.UnrolledVarSet vs = (ACRF.UnrolledVarSet) it.next ();
          Factor marg = inf.lookupMarginal (vs);
          for (AssignmentIterator assnIt = vs.assignmentIterator (); assnIt.hasNext (); assnIt.advance ()) {
            if (marg.value (assnIt) > wrongWrongThreshold) {
              Assignment assn = assnIt.assignment ();
              for (int vi = 0; vi < vs.size (); vi++) {
                Variable var = vs.get (vi);
                if (isWrong2RightAssn (target, assn, var)) {
                  int assnIdx = assn.singleIndex ();
//                  System.out.println ("Computing for WW: "+vs+" idx "+assnIdx+" target "+var);
                  allWrongWrongs[i].add (new WrongWrong (unrolled, vs, var, assnIdx));
                  numAdded++;
                }
              }
            }
          }

        }

        logger.info ("WrongWrongs: Instance " + i + " : " + instance.getName () + " Num added = " + numAdded);
        totalAdded += numAdded;
      }

      resetConstraints ();
      collectConstraints (training);
      forceStale ();

      logger.info ("Total timesteps = " + totalTimesteps (training));
      logger.info ("Total WrongWrongs = " + totalAdded);
    }

    private int totalTimesteps (InstanceList ilist)
    {
      int total = 0;
      for (int i = 0; i < ilist.size (); i++) {
        Instance inst = ilist.get (i);
        Sequence seq = (Sequence) inst.getData ();
        total += seq.size ();
      }
      return total;
    }

    private boolean isWrong2RightAssn (Assignment target, Assignment assn, Variable toExclude)
    {
      Variable[] vars = assn.getVars ();
      for (int i = 0; i < vars.length; i++) {
        Variable variable = vars[i];
        if ((variable != toExclude) && (assn.get (variable) != target.get (variable))) {
//          return true;
          return assn.get (toExclude) == target.get (toExclude);
        }
      }
      return false;
    }

  } // OptimizableACRF

}
