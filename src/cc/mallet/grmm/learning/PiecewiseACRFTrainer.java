/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning;


import cc.mallet.grmm.types.Assignment;
import cc.mallet.grmm.types.AssignmentIterator;
import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.Variable;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.SparseVector;
import cc.mallet.util.MalletLogger;
import cc.mallet.grmm.util.CachingOptimizable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.BitSet;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Created: Mar 15, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: CliquewiseACRFTrainer.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class PiecewiseACRFTrainer extends DefaultAcrfTrainer {

  private static final Logger logger = MalletLogger.getLogger (PiecewiseACRFTrainer.class.getName());
  private static final boolean printGradient = false;

  public Optimizable.ByGradientValue createOptimizable (ACRF acrf, InstanceList training)
  {
    return new Maxable (acrf, training);
  }

  public static class Maxable extends CachingOptimizable.ByGradient implements Serializable {

    private ACRF acrf;
    InstanceList trainData;

    private ACRF.Template[] templates;
    private ACRF.Template[] fixedTmpls;

    protected BitSet infiniteValues = null;
    private	int numParameters;

    private static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 10.0;

    public double getGaussianPriorVariance ()
    {
      return gaussianPriorVariance;
    }

    public void setGaussianPriorVariance (double gaussianPriorVariance)
    {
      this.gaussianPriorVariance = gaussianPriorVariance;
    }

    private double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;

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
        SparseVector defaults = templates[tidx].getDefaultWeights();
        defaultConstraints[tidx] = (SparseVector) defaults.cloneMatrixZeroed ();
        defaultExpectations[tidx] = (SparseVector) defaults.cloneMatrixZeroed ();
      }

      // And now the others
      constraints = new SparseVector [templates.length][];
      expectations = new SparseVector [templates.length][];
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates [tidx];
        SparseVector[] weights = tmpl.getWeights();
        constraints [tidx] = new SparseVector [weights.length];
        expectations [tidx] = new SparseVector [weights.length];

        for (int i = 0; i < weights.length; i++) {
          constraints[tidx][i] = (SparseVector) weights[i].cloneMatrixZeroed ();
          expectations[tidx][i] = (SparseVector) weights[i].cloneMatrixZeroed ();
        }
      }
    }

    /**
     * Set all expectations to 0 after they've been
     *    initialized.
     */
    void resetExpectations ()
    {
      for (int tidx = 0; tidx < expectations.length; tidx++) {
        defaultExpectations [tidx].setAll (0.0);
        for (int i = 0; i < expectations[tidx].length; i++) {
          expectations[tidx][i].setAll (0.0);
        }
      }
    }
    void resetConstraints ()
    {
      for (int tidx = 0; tidx < constraints.length; tidx++) {
        defaultConstraints [tidx].setAll (0.0);
        for (int i = 0; i < constraints[tidx].length; i++) {
          constraints[tidx][i].setAll (0.0);
        }
      }
    }

    protected Maxable (ACRF acrf, InstanceList ilist)
    {
      logger.finest ("Initializing OptimizableACRF.");

      this.acrf = acrf;
      templates = acrf.getTemplates ();
      fixedTmpls = acrf.getFixedTemplates ();

      /* allocate for weights, constraints and expectations */
      this.trainData = ilist;
      initWeights(trainData);
      initConstraintsExpectations();

      int numInstances = trainData.size();

      cachedValueStale = cachedGradientStale = true;

/*
	if (cacheUnrolledGraphs) {
	unrolledGraphs = new UnrolledGraph [numInstances];
	}
*/

      logger.info("Number of training instances = " + numInstances );
      logger.info("Number of parameters = " + numParameters );
      describePrior();

      logger.fine("Computing constraints");
      collectConstraints (trainData);
    }

    private void describePrior ()
    {
      logger.info ("Using gaussian prior with variance "+gaussianPriorVariance);
    }

    public int getNumParameters () { return numParameters; }

    /* Negate initialValue and finalValue because the parameters are in
     * terms of "weights", not "values".
     */
    public void getParameters (double[] buf) {

      if ( buf.length != numParameters )
        throw new IllegalArgumentException("Argument is not of the " +
                                           " correct dimensions");
      int idx = 0;
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates [tidx];
        SparseVector defaults = tmpl.getDefaultWeights ();
        double[] values = defaults.getValues();
        System.arraycopy (values, 0, buf, idx, values.length);
        idx += values.length;
      }

      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates [tidx];
        SparseVector[] weights = tmpl.getWeights();
        for (int assn = 0; assn < weights.length; assn++) {
          double[] values = weights [assn].getValues ();
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
        ACRF.Template tmpl = templates [tidx];
        SparseVector defaults = tmpl.getDefaultWeights();
        double[] values = defaults.getValues ();
        System.arraycopy (params, idx, values, 0, values.length);
        idx += values.length;
      }

      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates [tidx];
        SparseVector[] weights = tmpl.getWeights();
        for (int assn = 0; assn < weights.length; assn++) {
          double[] values = weights [assn].getValues ();
          System.arraycopy (params, idx, values, 0, values.length);
          idx += values.length;
        }
      }
    }

    // Functions for unit tests to get constraints and expectations
    //  I'm too lazy to make a deep copy.  Callers should not
    //  modify these.

    public SparseVector[] getExpectations (int cnum) { return expectations [cnum]; }
    public SparseVector[] getConstraints (int cnum) { return constraints [cnum]; }

    /** print weights */
    public void printParameters()
    {
      double[] buf = new double[numParameters];
      getParameters(buf);

      int len = buf.length;
      for (int w = 0; w < len; w++)
        System.out.print(buf[w] + "\t");
      System.out.println();
    }


    protected double computeValue () {
      double retval = 0.0;
      int numInstances = trainData.size();

      long start = System.currentTimeMillis();
      long unrollTime = 0;

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
      resetExpectations();

      /* Fill in expectations for each instance */
      for (int i = 0; i < numInstances; i++)
      {
        retval += computeValueForInstance (i);
      }

      retval += computePrior ();


      long end = System.currentTimeMillis ();
      logger.info ("ACRF Inference time (ms) = "+(end-start));
      logger.info ("ACRF unroll time (ms) = "+unrollTime);
      logger.info ("getValue (loglikelihood) = "+retval);

      return retval;
    }

    /* Incorporate Gaussian prior on parameters. This means
     that for each weight, we will add w^2 / (2 * variance) to the
     log probability. */
    private double computePrior ()
    {
      double retval = 0.0;
      
      double priorDenom = 2 * gaussianPriorVariance;

      for (int tidx = 0; tidx < templates.length; tidx++) {
        SparseVector[] weights = templates [tidx].getWeights ();
        for (int j = 0; j < weights.length; j++) {
          for (int fnum = 0; fnum < weights[j].numLocations(); fnum++) {
            double w = weights [j].valueAtLocation (fnum);
            if (weightValid (w, tidx, j)) {
              retval += -w*w/priorDenom;
            }
          }
        }
      }
      return retval;
    }

    private double computeValueForInstance (int i)
    {
      double retval = 0.0;

      Instance instance = trainData.get(i);

      /* Compute marginals for each clique */
      ACRF.UnrolledGraph unrolled = new ACRF.UnrolledGraph (instance, templates, fixedTmpls);

      if (unrolled.numVariables () == 0) return 0;   // Happens if all nodes are pruned.

      /* Save the expected value of each feature for when we
         compute the gradient. */
      Assignment observations = unrolled.getAssignment ();
      double value = collectExpectationsAndValue (unrolled, observations);

      if (Double.isNaN (value)) {
        System.out.println("NaN on instance "+i+" : "+instance.getName ());
        printDebugInfo (unrolled);
/*					throw new IllegalStateException
						("Value is NaN in ACRF.getValue() Instance "+i);
*/
        logger.warning ("Value is NaN in ACRF.getValue() Instance "+i+" : "+
                        "returning -infinity... ");
        return Double.NEGATIVE_INFINITY;
      } else {
        retval += value;
      }

      return retval;
    }


    /**
     *  Computes the gradient of the penalized log likelihood of the
     *   ACRF, and places it in cachedGradient[].
     *
     * Gradient is
     *   constraint - expectation - parameters/gaussianPriorVariance
     */
    protected void computeValueGradient (double[] grad)
    {
      computeValueGradient (grad, 1.0);
    }

    private void computeValueGradient (double[] grad, double priorScale)
    {
      /* Index into current element of cachedGradient[] array. */
      int gidx = 0;

      // First do gradient wrt defaultWeights
      for (int tidx = 0; tidx < templates.length; tidx++) {
        SparseVector theseWeights = templates[tidx].getDefaultWeights ();
        SparseVector theseConstraints = defaultConstraints [tidx];
        SparseVector theseExpectations = defaultExpectations [tidx];
        for (int j = 0; j < theseWeights.numLocations(); j++) {
          double weight = theseWeights.valueAtLocation (j);
          double constraint = theseConstraints.valueAtLocation (j);
          double expectation = theseExpectations.valueAtLocation (j);
          if (printGradient) {
            System.out.println(" gradient ["+gidx+"] = "+constraint+" (ctr) - "+expectation+" (exp) - "+
		  											 (weight / gaussianPriorVariance)+" (reg)  [feature=DEFAULT]");
          }
          grad [gidx++] = constraint - expectation - priorScale * (weight / gaussianPriorVariance);
        }
      }

      // Now do other weights
      for (int tidx = 0; tidx < templates.length; tidx++) {
        ACRF.Template tmpl = templates [tidx];
        SparseVector[] weights = tmpl.getWeights ();
        for (int i = 0; i < weights.length; i++) {
          SparseVector thisWeightVec = weights [i];
          SparseVector thisConstraintVec = constraints [tidx][i];
          SparseVector thisExpectationVec = expectations [tidx][i];

          for (int j = 0; j < thisWeightVec.numLocations(); j++) {
            double w = thisWeightVec.valueAtLocation (j);
            double gradient;  // Computed below

            double constraint = thisConstraintVec.valueAtLocation(j);
            double expectation = thisExpectationVec.valueAtLocation(j);

            /* A parameter may be set to -infinity by an external user.
             * We set gradient to 0 because the parameter's value can
             * never change anyway and it will mess up future calculations
             * on the matrix. */
            if (Double.isInfinite(w)) {
              logger.warning("Infinite weight for node index " +i+
                             " feature " +
                             acrf.getInputAlphabet().lookupObject(j) );
              gradient = 0.0;
            } else {
              gradient = constraint
                         - priorScale * (w/gaussianPriorVariance)
                         - expectation;
            }

            if (printGradient) {
               int idx = thisWeightVec.indexAtLocation (j);
               Object fname = acrf.getInputAlphabet ().lookupObject (idx);
               System.out.println(" gradient ["+gidx+"] = "+constraint+" (ctr) - "+expectation+" (exp) - "+
                                (w / gaussianPriorVariance)+" (reg)  [feature="+fname+"]");
             }

            grad [gidx++] = gradient;
          }
        }
      }
    }

    /**
     * For every feature f_k, computes the expected value of f_k
     *  aver all possible label sequences given the list of instances
     *  we have.
     *
     *  These values are stored in collector, that is,
     *    collector[i][j][k]  gets the expected value for the
     *    feature for clique i, label assignment j, and input features k.
     */
    private double collectExpectationsAndValue (ACRF.UnrolledGraph unrolled, Assignment observations)
    {
      double value = 0.0;
      for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next();
        int tidx = clique.getTemplate().index;
        if (tidx == -1) continue;

        Factor ptl = unrolled.factorOf (clique);
        double logZ = Math.log (ptl.sum ());

        // for each assigment to the clique
        //  xxx SLOW this will need to be sparsified
        AssignmentIterator assnIt = clique.assignmentIterator ();
        int i = 0;
        while (assnIt.hasNext ()) {
          double marginal = Math.exp (ptl.logValue (assnIt) - logZ);
          expectations [tidx][i].plusEqualsSparse (clique.getFv (), marginal);
          if (defaultExpectations[tidx].location (i) != -1)
            defaultExpectations [tidx].incrementValue (i, marginal);
          assnIt.advance (); i++;
        }

        value += (ptl.logValue (observations) - logZ);
      }
      return value;
    }


    public void collectConstraints (InstanceList ilist)
    {
      for (int inum = 0; inum < ilist.size(); inum++) {
        logger.finest ("*** Collecting constraints for instance "+inum);
        collectConstraintsForInstance (ilist, inum);
      }
    }

    private void collectConstraintsForInstance (InstanceList ilist, int inum)
    {
      Instance inst = ilist.get (inum);
      ACRF.UnrolledGraph unrolled = new ACRF.UnrolledGraph (inst, templates, null, false);
      for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next();
        int tidx = clique.getTemplate().index;
        if (tidx == -1) continue;

        int assn = clique.lookupAssignmentNumber ();
        constraints [tidx][assn].plusEqualsSparse (clique.getFv ());
        if (defaultConstraints[tidx].location (assn) != -1)
          defaultConstraints [tidx].incrementValue (assn, 1.0);
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
        System.err.println("Could not open output file.");
        e.printStackTrace ();
      }
    }

    void dumpDefaults ()
    {
      System.out.println("Default constraints");
      for (int i = 0; i < defaultConstraints.length; i++) {
        System.out.println("Template "+i);
        defaultConstraints[i].print ();
      }
      System.out.println("Default expectations");
      for (int i = 0; i < defaultExpectations.length; i++) {
        System.out.println("Template "+i);
        defaultExpectations[i].print ();
      }
    }

    void printDebugInfo (ACRF.UnrolledGraph unrolled)
    {
      acrf.print (System.err);
      Assignment assn = unrolled.getAssignment ();
      for (Iterator it = unrolled.unrolledVarSetIterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next();
        System.out.println("Clique "+clique);
        dumpAssnForClique (assn, clique);
        Factor ptl = unrolled.factorOf (clique);
        System.out.println("Value = "+ptl.value (assn));
        System.out.println(ptl);
      }
    }

    void dumpAssnForClique (Assignment assn, ACRF.UnrolledVarSet clique)
    {
      for (Iterator it = clique.iterator(); it.hasNext();) {
        Variable var = (Variable) it.next();
        System.out.println(var+" ==> "+assn.getObject (var)
          +"  ("+assn.get (var)+")");
      }
    }


    private boolean weightValid (double w, int cnum, int j)
    {
      if (Double.isInfinite (w)) {
        logger.warning ("Weight is infinite for clique "+cnum+"assignment "+j);
        return false;
      } else if (Double.isNaN (w)) {
        logger.warning ("Weight is Nan for clique "+cnum+"assignment "+j);
        return false;
      } else {
        return true;
      }
    }


    int numInBatch = 0;

    public double computeValueAndGradient (int instance)
    {
      numInBatch++;
      collectConstraintsForInstance (trainData, instance);
      double value = computeValueForInstance (instance);
      value += (computePrior () / trainData.size ());
      return value;
    }

    public int getNumInstances ()
    {
      return trainData.size ();
    }


    public void getCachedGradient (double[] grad)
    {
      computeValueGradient (grad, ((double)numInBatch) / trainData.size());
    }


    public void resetValueGradient ()
    {
      resetExpectations ();
      resetConstraints ();
    }



  } // OptimizableACRF

}
