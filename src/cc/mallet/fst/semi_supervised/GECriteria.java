package cc.mallet.fst.semi_supervised;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import java.util.logging.Logger;

import cc.mallet.fst.SumLattice;
import cc.mallet.fst.Transducer;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;


/**
 * Represents GE criteria specified in the form of feature-label associations.
 *
 * @author Gaurav Chandalia
 * @author Gregory Druck
 */
public class GECriteria {
  private static Logger logger =
    MalletLogger.getLogger(GECriteria.class.getName());

  // number of states in the lattice
  protected int numStates;
  // mapping of states to labels (when using a custom FST in the transducer)
  protected StateLabelMap stateLabelMap;

  // key: feature index, value: FeatureInfo object that will hold for each
  // feature: the prior distribution over labels, gamma value, model's
  // expectation over labels, feature count across all instances
  protected Map<Integer, GECriterion> constraints;

  // number of bits == number of instances, a bit is set if the instance
  // contains at least one feature constraint
  protected BitSet constraintBits;

  // thread handler to calculate label expectations
  protected transient FeatureLabelExpExecutor labelExpExecutor;

  /**
   * Initializes the feature-label association constraints. <p>
   *
   * @param numStates Number of states in the lattice.
   * @param stateLabelMap Mapping of states to labels (used when a custom FST
   *        is used to train a CRF).
   * @param constraints Map, key: feature index, value: FeatureInfo object.
   */
  public GECriteria(int numStates, StateLabelMap stateLabelMap,
    Map<Integer, GECriterion> constraints) {
    this.numStates = numStates;
    this.stateLabelMap = stateLabelMap;
    this.constraints = constraints;
    // will be set later
    this.constraintBits = null;
    this.labelExpExecutor = new FeatureLabelExpExecutor();
  }

  /**
   * Returns the state-label mapping. <p>
   */
  public StateLabelMap getStateLabelMap() {
    return stateLabelMap;
  }

  /**
   * Returns the <tt>FeatureInfo</tt> object mapped to the feature index.
   *
   * <b>Note:</b> No check is performed to make sure feature index is valid.
   * Method can return <tt>null</tt>.
   */
  public GECriterion getConstraint(int featureIndex) {
    return constraints.get(featureIndex);
  }

  /**
   * Returns an iterator to the indices of the feature constraints.
   */
  public Iterator<Integer> getFeatureIndexIterator() {
    return constraints.keySet().iterator();
  }

  /**
   * Returns bits for all instances, each set if instance has at least one
   * feature constraint.
   */
  public BitSet getConstraintBits() {
    return constraintBits;
  }

  /**
   * Sets a bit for each instance if it has at least one feature constraint
   * (anywhere in the sequence).
   *
   * <tt>start, end</tt> indicate range of indices that will be used for semisup
   * computations.
   */
  public void setConstraintBits(InstanceList ilist, int start, int end) {
    logger.info("Setting bits for instances...");
    constraintBits = new BitSet(ilist.size());
    // feature indices
    Set<Integer> indices = constraints.keySet();
    // true if at least on feature constraint is present anywhere in the
    // *instance*
    boolean featurePresent = false;
    for (int i = start; i < end; ++i) {
      FeatureVectorSequence fvs =
          (FeatureVectorSequence) ilist.get(i).getData();
      featurePresent = false;
      for (int ip = 0; ip < fvs.size(); ++ip) {
        FeatureVector fv = fvs.getFeatureVector(ip);
        // set flag and bit if any constraint is present
        for (int index : indices) {
          if (fv.value(index) > 0.0) {
            featurePresent = true;
            break;
          }
        }
        if (featurePresent) {
          constraintBits.set(i);
          break;
        }
      }
    }
    logger.info("Number of instances with at least one GE constraint: " +
                 constraintBits.cardinality());
  }

  /**
   * Returns bits for an instance, each bit corresponds to a feature index and
   * is set if the feature is present in the instance.
   *
   * @return Constraint bits, size == number of feature constraints
   */
  public final BitSet getConstraintBitsForInstance(FeatureVectorSequence fvs) {
    BitSet constraintBits = new BitSet();
    // feature indices
    Set<Integer> indices = constraints.keySet();
    for (int index : indices) {
      for (int ip = 0; ip < fvs.size(); ++ip) {
        if (fvs.getFeatureVector(ip).value(index) > 0.0) {
          constraintBits.set(index);
          break;
        }
      }
    }
    return constraintBits;
  }

  /**
   * Returns the number of times the feature occurred in the sequence (an
   * instance). <p>
   *
   * Also updates the expectation of a feature in one instance.
   * 
   * @param featureIndex Feature to look for.
   * @param fvs Observation sequence.
   * @param gammas Log probability of being in state 'i' at input position 'j'.
   * @param expectation Model expectation (filled by this method).
   * @return Number of times the feature occurred in the input sequence.
   * @throws IndexOutOfBoundsException If an invalid feature index is specified.
   */
  protected final int getExpectationForInstance( 
      int featureIndex, FeatureVectorSequence fvs, double[][] gammas,
      double[] expectation) {
    int featureCount = 0;
    for (int ip = 0; ip < fvs.size(); ++ip) {
      if (fvs.getFeatureVector(ip).value(featureIndex) > 0.0) {
        ++featureCount;

        for (int s = 0; s < numStates; ++s) {
          int labelIndex = stateLabelMap.getLabelIndex(s);
          expectation[labelIndex] += Math.exp(gammas[ip+1][s]);
        }
      }
    }
    return featureCount;
  }

  /**
   * Returns the expectation of a feature in one instance. <p>
   *
   * *Note*: These expectations are not normalized.
   */
  public final double[] getExpectationForInstance( 
      int featureIndex, FeatureVectorSequence fvs, double[][] gammas) {
    double[] expectation = new double[stateLabelMap.getNumLabels()];
    this.getExpectationForInstance(featureIndex, fvs, gammas, expectation);
    return expectation;
  }

  /**
   * Calculates the model expectation of all feature constraints. <p>
   *
   * <tt>lattices</tt> contains the SumLattice objects of instances to be used
   * for semisup computations.
   */
  public void calculateExpectations(InstanceList ilist, Transducer transducer,
                                Map<Integer, SumLattice> lattices) {
    labelExpExecutor.calculateLabelExp(ilist, transducer, lattices);
    this.print(stateLabelMap.getLabelAlphabet());
  }

  /**
   * Computes sum of GE constraint values. <p>
   *
   * <b>Note:</b> Label expectations are <b>not</b> re-computed here. If
   * desired, then make a call to <tt>calculateLabelExp</tt>.
   */
  public double getGEValue() {
    double value = 0.0;
    for (int fi : constraints.keySet()) {
      GECriterion constraint = constraints.get(fi);
      if ( constraint.getCount() > 0.0) {
        double[] target = constraint.getTarget();
        double[] expectation = constraint.getExpectation();

        // value due to current constraint
        double featureValue = 0.0;
        for (int labelIndex = 0; labelIndex < stateLabelMap.getNumLabels();
             ++labelIndex) {
          if (expectation[labelIndex] > 0.0 && target[labelIndex] > 0.0) {
            // p*log(q) - p*log(p)
            featureValue +=
            	target[labelIndex] * Math.log(expectation[labelIndex]) -
            	target[labelIndex] * Math.log(target[labelIndex]);
          }
        }
  			assert(!Double.isNaN(featureValue) &&
               !Double.isInfinite(featureValue));

        value += featureValue *  constraint.getWeight();
      }
    }
    return value;
  }

  protected void assertLabelExpNonNull() {
    Iterator<Integer> iter = constraints.keySet().iterator();
    while (iter.hasNext()) {
      int fi = iter.next();
      assert(constraints.get(fi).getExpectation() != null)
          : "model exp null, fi: " + fi;
    }
  }

  /**
   * Prints the constraints.
   */
  public void print(Alphabet targetAlphabet) {
    StringBuilder sb = new StringBuilder(constraints.size() * 50);
    sb.append("Printing feature-label constraints...\n");
    Iterator<Map.Entry<Integer, GECriterion>> featureIter =
        constraints.entrySet().iterator();
    while (featureIter.hasNext()) {
      Map.Entry<Integer, GECriterion> entry = featureIter.next();

      int fi = entry.getKey();
      GECriterion constraint = entry.getValue();

      sb.append("index: " + fi + ", name: " + constraint.getName() +
                ", gamma: " + constraint.getWeight() +
                ", count: " + constraint.getCount() + "\n");

      double[] target = constraint.getTarget();
			for (int li = 0; li < target.length; ++li){
        sb.append("\t ");
        if (targetAlphabet != null) {
          sb.append(targetAlphabet.lookupObject(li) + "--");
        }
        sb.append(String.format("%1.4f", target[li]));
			}
			sb.append("\n");
      double[] expectation = constraint.getExpectation();
      if (expectation != null) {
        for (int li = 0; li < expectation.length; ++li){
          sb.append("\t ");
          if (targetAlphabet != null) {
            sb.append(targetAlphabet.lookupObject(li) + "--");
          }
          sb.append(String.format("%1.4f", expectation[li]));
        }
        sb.append("\n\t" + Maths.klDivergence(target, expectation) + "\n");
      }
    }
    System.out.println(sb.toString());
  }

  /**
   * Executes threads to calculate model expectations of all feature
   * constraints.
   */
  private class FeatureLabelExpExecutor {
    // key: instance index, value: already computed Lattice
    private Map<Integer, SumLattice> lattices;
    private InstanceList ilist;

    // all indices of feature constraints, used for multi-threading, initialized
    // in caclulateLabelExp
    private Set<Integer> featureIndices;

    // number of threads == number of feature constraints
    private int numThreads;
    // thread pool, each thread computes a feature constraint's label
    // expectations
    private ThreadPoolExecutor executor;

    // milliseconds
    public static final int SLEEP_TIME = 100;

    public FeatureLabelExpExecutor() {
      lattices = null;
      ilist = null;
      featureIndices = null;

      numThreads = constraints.size();
      logger.info("Creating " + numThreads +
                   " threads for calculating label expectations...");
      executor =
          (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);
    }

    public void calculateLabelExp(InstanceList ilist, Transducer transducer,
                                  Map<Integer, SumLattice> lattices) {
      this.lattices = lattices;
      this.ilist = ilist;

      featureIndices = new HashSet<Integer>(constraints.size());

      logger.info("Calculating label expectations...");
      long time = System.currentTimeMillis();
      for (int fi : constraints.keySet()) {
        executor.execute(new FeatureExpectationHandler(fi));
      }

      // wait for all constraints to finish
      int numConstraints = -1;
      while (numConstraints != constraints.size()) {
        synchronized(this) {
          numConstraints = featureIndices.size();
        }
        try {
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException ie) {
          ie.printStackTrace();
          System.exit(1);
        }
      }
      time = (System.currentTimeMillis() - time) / 1000;
      logger.info(String.valueOf(time) + " secs.");

      assertLabelExpNonNull();
      lattices = null;
      ilist = null;
      featureIndices = null;
    }

    /**
     * Calculates the model expectation of the feature in a new thread.
     */
    private class FeatureExpectationHandler implements Runnable {
      // feature constraint whose label expectations are to be computed
      private int fi;

      /**
       * Initializes the handler.
       *
       * @param fi Index of feature constraint whose expectations are to be
       *        computed
       */
      public FeatureExpectationHandler(int fi) {
        this.fi = fi;
      }

      /**
       * Calculates the model expectation of the feature.
       */
      public void run() {
        int numLabels = stateLabelMap.getNumLabels();
        double[] expectation = new double[numLabels];
        int featureCount = 0;
        SumLattice lattice = null;
        for (int i : lattices.keySet()) {
          // skip if the instance doesn't have any constraints
          if (!constraintBits.get(i)) {
            continue;
          }
          FeatureVectorSequence fvs = (FeatureVectorSequence) ilist.get(i).getData();
          lattice = lattices.get(i);
          assert(lattice != null)
              : "Lattice is null:: " + i + ", size: " + lattices.size();

          // update the number of times this feature occurred in the sequence
          // and the label expectations due to this sequence
          featureCount += getExpectationForInstance(
              fi, fvs, lattice.getGammas(), expectation);
        }
        assert(!MatrixOps.isNaNOrInfinite(expectation));
        
        if (MatrixOps.isNonZero(expectation)) {
          // normalizing label expectations
          MatrixOps.timesEquals(expectation, 1/MatrixOps.sum(expectation));
          GECriterion constraint = constraints.get(fi);
          constraint.setExpectation(expectation);
          constraint.setCount(featureCount);
        }
        else {
        	throw new RuntimeException("Feature " + fi + " does not occur!");
        }
        synchronized(FeatureLabelExpExecutor.this) {
          featureIndices.add(fi);
        }
      }
    }
  }

  /**
   * GE constraint for one input feature.
   */
  public static class GECriterion {
  	protected String name;
    protected double weight;

    // target expectation
    protected double[] target;
    // model expectation
    protected double[] expectation;

    protected double count;

    public GECriterion(String name, double[] target, double weight) {
    	this.name = name;
      this.weight = weight;
      this.target = target;
    }

    /**
     * Returns the constraint name.
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the weight (gamma) for the constraint.
     */
    public double getWeight() {
      return weight;
    }
    
    /**
     * Returns the target expectation for the feature.
     */
    public double[] getTarget() {
      return target;
    }

    /**
     * Returns the model expectation of the feature.
     */
    public double[] getExpectation() {
      return expectation;
    }

    protected void setExpectation(double[] expectation) {
      this.expectation = expectation;
    }

    /**
     * Returns the count of the feature.
     */
    public double getCount() {
      return count;
    }

    protected void setCount(double count) {
      this.count = count;
    }
    
    /**
     * Returns the target/expectation ratio required in lattice computations. <p>
     *
     * *Note*: The ratio is divided by the feature count if the label expectations
     * have been normalized.
     */
    protected double getTargetModelExpRatio(int labelIndex) {
      return target[labelIndex] / (expectation[labelIndex] * count);
    }
    
  }
}
