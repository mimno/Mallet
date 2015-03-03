/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning;


import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.CachingOptimizable;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.SparseVector;
import cc.mallet.util.MalletLogger;
import gnu.trove.map.hash.THashMap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created: Mar 15, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: PseudolikelihoodACRFTrainer.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class PseudolikelihoodACRFTrainer extends DefaultAcrfTrainer {

  private static final Logger logger = MalletLogger.getLogger (PseudolikelihoodACRFTrainer.class.getName());
  private static final boolean printGradient = false;

  /** Use per-variable pseudolikelihood.  This is the classical version of Besag. */
  public static final int BY_VARIABLE = 0;

  /** Use per-edge structured pseudolikelihood. */
  public static final int BY_EDGE = 1;

  private int structureType = BY_VARIABLE;

  public int getStructureType ()
  {
    return structureType;
  }

  public void setStructureType (int structureType)
  {
    this.structureType = structureType;
  }

  public Optimizable.ByGradientValue createOptimizable (ACRF acrf, InstanceList training)
  {
    return new Maxable (acrf, training);
  }

  // Controls the structuredness of pl.
  private static interface CliquesIterator {
    boolean hasNext ();
    void advance ();
    Factor localConditional ();
    ACRF.UnrolledVarSet[] cliques ();
  }

  private static class VariablesIterator implements CliquesIterator {

    private ACRF.UnrolledGraph graph;
    private Assignment observed;

    // cursors
    private int vidx = -1;
    private Factor ptl;
    private List[] cliquesByVar;

    public VariablesIterator (ACRF.UnrolledGraph acrf, Assignment observed)
    {
      this.graph = acrf;
      this.observed = observed;

      cliquesByVar = new List[graph.numVariables ()];
      for (int i = 0; i < cliquesByVar.length; i++) cliquesByVar[i] = new ArrayList ();

      for (Iterator it = acrf.unrolledVarSetIterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
        for (int vidx = 0; vidx < clique.size(); vidx++) {
          Variable var = clique.get(vidx);
          cliquesByVar[graph.getIndex (var)].add (clique);
        }
      }
    }

    public boolean hasNext ()
    {
      return vidx < graph.numVariables () - 1;
    }

    public void advance ()
    {
      vidx++;
      Variable var = graph.get (vidx);

      ptl = new TableFactor (var);
      for (Iterator it = cliquesByVar[vidx].iterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
        Factor cliquePtl = graph.factorOf (clique);
        if (cliquePtl == null)
          throw new IllegalStateException
           ("Could not find potential for clique "+clique);

        VarSet vs = new HashVarSet (cliquePtl.varSet ());
        vs.remove (var);
        Assignment nbrAssn = (Assignment) observed.marginalize (vs);

        Factor slice = cliquePtl.slice (nbrAssn);
        ptl.multiplyBy (slice);
      }
    }

    public Factor localConditional ()
    {
      return ptl;
    }

    public ACRF.UnrolledVarSet[] cliques ()
    {
      List cliques = cliquesByVar[vidx];
      return (ACRF.UnrolledVarSet[]) cliques.toArray (new ACRF.UnrolledVarSet [cliques.size()]);
    }
  }
  private static class EdgesIterator implements CliquesIterator {

    private ACRF.UnrolledGraph graph;
    private Assignment observed;

    // cursors
    private Iterator cursor;
    private List currentCliqueList;
    private Factor ptl;
    private THashMap cliquesByEdge;

    public EdgesIterator (ACRF.UnrolledGraph acrf, Assignment observed)
    {
      this.graph = acrf;
      this.observed = observed;

      cliquesByEdge = new THashMap();

      for (Iterator it = acrf.unrolledVarSetIterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
        for (int v1idx = 0; v1idx < clique.size(); v1idx++) {
          Variable v1 = clique.get(v1idx);
          List adjlist = graph.allFactorsContaining (v1);
          for (Iterator factorIt = adjlist.iterator(); factorIt.hasNext();) {
            Factor factor = (Factor) factorIt.next ();
            if (!cliquesByEdge.containsKey (factor)) { cliquesByEdge.put (factor, new ArrayList()); }
            List l = (List) cliquesByEdge.get (factor);
            if (!l.contains (clique)) { l.add (clique); }
          }
        }
      }

      cursor = cliquesByEdge.keySet().iterator ();
    }

    public boolean hasNext ()
    {
      return cursor.hasNext();
    }

    public void advance ()
    {
      Factor pairFactor  = (Factor) cursor.next ();
      VarSet pairVarSet = pairFactor.varSet ();
      assert pairVarSet.size() == 2;  // for now

      Variable v1 = pairVarSet.get (0);
      Variable v2 = pairVarSet.get (1);
      Variable[] vars = new Variable[] { v1, v2 };
      ptl = new TableFactor (vars);

      // set localObs to assignment to all data EXCEPT v1 and v2
      VarSet vs = new HashVarSet (observed.varSet ());
      vs.remove (v1);
      vs.remove (v2);
      Assignment localObs = (Assignment) observed.marginalize (vs);

      currentCliqueList = (List) cliquesByEdge.get (pairFactor);
      for (Iterator it = currentCliqueList.iterator (); it.hasNext();) {
        ACRF.UnrolledVarSet clique = (ACRF.UnrolledVarSet) it.next ();
        Factor cliquePtl = graph.factorOf (clique);
        if (cliquePtl == null)
          throw new IllegalStateException
           ("Could not find potential for clique "+clique);

        Factor slice;
        boolean hasV1 = clique.contains (v1);
        boolean hasV2 = clique.contains (v2);
        if (hasV1 && hasV2) {
          // fast special case
          if (cliquePtl.varSet().size() == 2) {
            slice = cliquePtl;
          } else {
            slice = cliquePtl.slice (localObs);
          }
        } else if (hasV1) { // && !hasV2
          slice = cliquePtl.slice (localObs);
        } else if (hasV2) { // && !hasV1
          slice = cliquePtl.slice (localObs);
        } else {
          throw new RuntimeException ("Illegal state: cliqu ehas neither edge variable");
        }

        ptl.multiplyBy (slice);
      }
    }

    public Factor localConditional ()
    {
      return ptl;
    }

    public ACRF.UnrolledVarSet[] cliques ()
    {
      List cliques = currentCliqueList;
      return (ACRF.UnrolledVarSet[]) cliques.toArray (new ACRF.UnrolledVarSet [cliques.size()]);
    }
  }

  private CliquesIterator makeCliquesIterator (ACRF.UnrolledGraph acrf, Assignment observed)
  {
    if (structureType == BY_VARIABLE) {
      return new VariablesIterator (acrf, observed);
    } else if (structureType == BY_EDGE) {
      return new EdgesIterator (acrf, observed);
    } else {
      throw new IllegalArgumentException ("Unknown structured pseudolikelihood type "+structureType);
    }
  }

  public class Maxable extends CachingOptimizable.ByGradient implements Serializable {

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
      // ugh!! There must be a way to abstract this back into ACRF, but I don't know the best way....
      //  problem is that this maxable doesn't extend the ACRF Maxiximable, so I can't just call its initWeights() method
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
        Instance instance = trainData.get(i);

        /* Compute marginals for each clique */
        long unrollStart = System.currentTimeMillis ();
        ACRF.UnrolledGraph unrolled = new ACRF.UnrolledGraph (instance, templates, fixedTmpls);
        long unrollEnd = System.currentTimeMillis ();
        unrollTime += (unrollEnd - unrollStart);

        if (unrolled.numVariables () == 0) continue;   // Happens if all nodes are pruned.

        /* Save the expected value of each feature for when we
           compute the gradient. */
        Assignment observations = unrolled.getAssignment ();
        double value = collectExpectationsAndValue (unrolled, observations);

        if (Double.isInfinite(value))
        {
          if (initializingInfiniteValues) {
            logger.warning ("Instance " + instance.getName() +
                            " has infinite value; skipping.");
            infiniteValues.set (i);
            continue;
          } else if (!infiniteValues.get(i)) {
            logger.warning ("Infinite value on instance "+instance.getName()+
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

      }

      /* Incorporate Gaussian prior on parameters. This means
         that for each weight, we will add w^2 / (2 * variance) to the
         log probability. */

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

      long end = System.currentTimeMillis ();
      logger.info ("ACRF Inference time (ms) = "+(end-start));
      logger.info ("ACRF unroll time (ms) = "+unrollTime);
      logger.info ("getValue (loglikelihood) = "+retval);

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
          grad [gidx++] = constraint - expectation - (weight / gaussianPriorVariance);
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
                         - (w/gaussianPriorVariance)
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
      for (CliquesIterator it = makeCliquesIterator (unrolled, observations); it.hasNext();) {
        it.advance ();

        TableFactor ptl = (TableFactor) it.localConditional ();
        double logZ = ptl.logsum ();
        ACRF.UnrolledVarSet[] cliques = it.cliques ();

        Assignment assn = (Assignment) observations.duplicate ();
        
        // for each assigment to the clique
        //  xxx SLOW this will need to be sparsified
        AssignmentIterator assnIt = ptl.assignmentIterator ();
        while (assnIt.hasNext ()) {
          double marginal = Math.exp (ptl.logValue (assnIt) - logZ);

          // This is ugly need to map from assignments to the single twiddled variable to clique assignments
          Assignment currentAssn = assnIt.assignment ();
          for (int vi = 0; vi < currentAssn.numVariables (); vi++) {
            Variable var = currentAssn.getVariable (vi);
            assn.setValue (0, var, currentAssn.get (var));
          }

          for (int cidx = 0; cidx < cliques.length; cidx++) {
            ACRF.UnrolledVarSet clique = cliques[cidx];
            int tidx = clique.getTemplate().index;
            if (tidx == -1) continue;

            int assnIdx = clique.lookupNumberOfAssignment (assn);
            expectations [tidx][assnIdx].plusEqualsSparse (clique.getFv (), marginal);
            if (defaultExpectations[tidx].location (assnIdx) != -1)
              defaultExpectations [tidx].incrementValue (assnIdx, marginal);
          }

          assnIt.advance ();
        }

        value += (ptl.logValue (observations) - logZ);
      }
      return value;
    }


    private void collectConstraintsForGraph (ACRF.UnrolledGraph unrolled, Assignment observations)
    {
      for (CliquesIterator it = makeCliquesIterator (unrolled, observations); it.hasNext();) {
        it.advance ();
        ACRF.UnrolledVarSet[] cliques = it.cliques ();
        for (int cidx = 0; cidx < cliques.length; cidx++) {
          ACRF.UnrolledVarSet clique = cliques[cidx];
          int tidx = clique.getTemplate().index;
          if (tidx < 0) continue;

          int assnIdx = clique.lookupNumberOfAssignment (observations);
          constraints [tidx][assnIdx].plusEqualsSparse (clique.getFv (), 1.0);
          if (defaultConstraints[tidx].location (assnIdx) != -1)
            defaultConstraints [tidx].incrementValue (assnIdx, 1.0);
        }
      }
    }

    public void collectConstraints (InstanceList ilist)
    {
      for (int inum = 0; inum < ilist.size(); inum++) {
        logger.finest ("*** Collecting constraints for instance "+inum);
        Instance inst = ilist.get (inum);
        ACRF.UnrolledGraph unrolled = new ACRF.UnrolledGraph (inst, templates, null, true);
        Assignment assn = unrolled.getAssignment ();
        collectConstraintsForGraph (unrolled, assn);
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
      for (Iterator it = unrolled.varSetIterator (); it.hasNext();) {
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

  } // OptimizableACRF

}
