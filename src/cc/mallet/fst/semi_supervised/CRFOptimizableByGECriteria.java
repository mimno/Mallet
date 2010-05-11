package cc.mallet.fst.semi_supervised;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

import cc.mallet.fst.CRF;
import cc.mallet.fst.SumLattice;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.Transducer;

import cc.mallet.optimize.Optimizable;

import cc.mallet.util.MalletLogger;

/**
 * GE criteria for training a linear chain CRF.
 *
 * @author Gaurav Chandalia
 * @author Gregory Druck
 */
public class CRFOptimizableByGECriteria implements Optimizable.ByGradientValue,
                                                    Serializable {
	
	private static double DEFAULT_GPV = Double.POSITIVE_INFINITY;
	
  private static final long serialVersionUID = 1;
  private static Logger logger =
      MalletLogger.getLogger(CRFOptimizableByGECriteria.class.getName());

  // unlabeled data
  protected InstanceList data;
  
	private int cachedValueWeightsStamp = -1;
	private int cachedGradientWeightsStamp = -1;

  // the model
  protected CRF crf;

  // GE criteria
  protected GECriteria geCriteria;

  // gradient of GE criteria
  protected CRF.Factors gradient;
  protected Transducer.Incrementor incrementor;

  // GE value
  protected double cachedValue;
  // GE gradient (double[] form)
  protected double[] cachedGradient;
  
  protected double priorVariance;

  // thread handler used to create lattices in new threads and update the
  // gradient
  protected transient LatticeCreationExecutor updateExecutor;

  /**
   * Initializes the structures.
   *
   * @param geCriteria GE criteria.
   * @param crf Model.
   * @param ilist Data used for training.
   */
  public CRFOptimizableByGECriteria(GECriteria geCriteria,
                                     CRF crf, InstanceList ilist,
                                     int numThreads) {
    this.data = ilist;
    this.crf = crf;
    this.geCriteria = geCriteria;

    // initialize 
    gradient = new CRF.Factors(crf);
    incrementor = gradient.new Incrementor();
    cachedValue = 0.0;
    cachedGradient = new double[crf.getParameters().getNumFactors()];
    priorVariance = DEFAULT_GPV;
    geCriteria.setConstraintBits(data, 0, data.size());

    updateExecutor = new LatticeCreationExecutor(numThreads);
  }

  public void shutdown() {
  	updateExecutor.shutdown();
  }
  
  public void setGaussianPriorVariance(double priorVariance) {
  	this.priorVariance = priorVariance;
  }
  
  public GECriteria getGECriteria() {
    return geCriteria;
  }

  /**
   * Initializes the gradient to zero and re-computes expectations
   * for a new iteration. <p>
   *
   * Also creates the executor to compute the gradient (if not done yet).
   */
  public void initialize(Map<Integer, SumLattice> lattices) {
  	assert(gradient.structureMatches(crf.getParameters()));
  	gradient.zero();
    updateExecutor.initialize();

    // compute the expected prior distribution over labels for all feature-label
    // pairs (constraints)
  	geCriteria.calculateExpectations(data, crf, lattices);
  }

  /**
   * Fills gradient from a single instance. <p>
   */
  public void computeGradient(FeatureVectorSequence input,
                                  double[][] gammas, double[][][] xis) {
    new GELattice(input, gammas, xis, crf, incrementor, geCriteria, false);
  }

  /**
   * Resets, computes and fills gradient from all instances. <p>
   *
   * Analogous to <tt>CRFOptimizableByLabelLikelihood.getExpectationValue<tt>.
   */
  public void computeGradient(Map<Integer, SumLattice> lattices) {
    this.initialize(lattices);
    logger.info("Updating gradient...");
    long time = System.currentTimeMillis();
    updateExecutor.computeGradient(lattices);
    time = (System.currentTimeMillis() - time) / 1000;
    logger.info(String.valueOf(time) + " secs.");
  }
  
  public double getValue() {
		if (crf.getWeightsValueChangeStamp() != cachedValueWeightsStamp) {
		  // The cached value is not up to date; it was calculated for a different set of CRF weights.
		  cachedValueWeightsStamp = crf.getWeightsValueChangeStamp(); 

	  	HashMap<Integer,SumLattice> lattices = new HashMap<Integer,SumLattice>();
	  	for (int ii = 0; ii < data.size(); ii++) {
	  		FeatureVectorSequence fvs = (FeatureVectorSequence)data.get(ii).getData();
	  		lattices.put(ii, new SumLatticeDefault(crf,fvs,true));
	  	}

	  	computeGradient(lattices);
	  	
	  	cachedValue = geCriteria.getGEValue();

	  	if (priorVariance != Double.POSITIVE_INFINITY) {
	      cachedValue += crf.getParameters().gaussianPrior(priorVariance);
	  	}
			assert(!Double.isNaN(cachedValue) && !Double.isInfinite(cachedValue))
	        : "Likelihood due to GE criteria is NaN/Infinite";
	    logger.info("getValue() (GE) = " + cachedValue);
		}
  	return cachedValue;
  }

  public void getValueGradient(double[] buffer) {
		if (cachedGradientWeightsStamp != crf.getWeightsValueChangeStamp()) {
			cachedGradientWeightsStamp = crf.getWeightsValueChangeStamp();

  	  getValue();
      gradient.assertNotNaNOrInfinite();
  	  // fill up gradient
      cachedGradient = new double[cachedGradient.length];
	  	if (priorVariance != Double.POSITIVE_INFINITY) {
				gradient.plusEqualsGaussianPriorGradient(crf.getParameters(), -priorVariance);
	  	}
  	  gradient.getParameters(cachedGradient);
      MatrixOps.timesEquals(cachedGradient, -1.0);
		}
    System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
  }

  public void printGradientAbsNorm() {
    logger.info("absNorm(GE Gradient): " + gradient.getParametersAbsNorm());
  }

  // some get/set methods that have to be implemented
  public int getNumParameters() {
    return crf.getParameters().getNumFactors();
  }

  public void getParameters(double[] buffer) {
    crf.getParameters().getParameters(buffer);
	}

  public void setParameters(double[] buffer) {
    crf.getParameters().setParameters(buffer);
		crf.weightsValueChanged();
	}

  public double getParameter(int index) {
    return crf.getParameters().getParameter(index);
  }

  public void setParameter(int index, double value) {
    crf.getParameters().setParameter(index, value);
    crf.weightsValueChanged();
  }

  /**
   * Computes GE gradient. <p>
   *
   * Uses multi-threading, each thread does computations for a subset of the
   * data. To reduce sharing, each thread has its own CRF.Factors structure for
   * gradient. The final gradient is obtained by combining from all
   * subset gradients.
   */
  private class LatticeCreationExecutor {
    // key: instance index, value: Lattice
    private Map<Integer, SumLattice> lattices;

    // number of data subsets == number of threads
    private int numSubsets;
    // gradient obtained from subsets of data
    private List<FactorsIncrementorPair> mtGradient;

    // thread pool to create lattices and update gradient
    private ThreadPoolExecutor executor;

    // milliseconds
    public static final int SLEEP_TIME = 1000;

    // key: unique integer identifying a thread running forward-backward, the
    // respective thread sets the bit when its computation is over, range: (0,
    // numSubsets - 1)
    private BitSet threadIds;

    /**
     * Initializes the executor with specified number of threads.
     */
    public LatticeCreationExecutor(int numThreads) {
      lattices = null;
      numSubsets = numThreads;
      mtGradient = new ArrayList<FactorsIncrementorPair>(numSubsets);
      // initialize from the main gradients object
      for (int i = 0; i < numSubsets; ++i) {
        mtGradient.add(new FactorsIncrementorPair(gradient));
      }
      logger.info("Creating " + numSubsets +
                   " threads for updating gradient...");
      executor =
          (ThreadPoolExecutor) Executors.newFixedThreadPool(numSubsets);
      threadIds = new BitSet(numSubsets);
    }

    /**
     * Initializes each thread's gradient to zero.
     */
    public void initialize() {
      for (int i = 0; i < mtGradient.size(); ++i) {
        FactorsIncrementorPair exp = mtGradient.get(i);
        exp.subsetFactors.zero();
      }
    }

    /**
     * Computes lattices and fills gradient from all instances. <p>
     */
    public void computeGradient(Map<Integer, SumLattice> lattices) {
      this.lattices = lattices;
      threadIds.clear();

      // number of instances per subset
      int numInstancesSubset = data.size() / numSubsets;
      // range of each subset
      int start = -1, end = -1;
      for (int i = 0; i < numSubsets; ++i) {
        // get the indices of subset
        if (i == 0) {
          start = 0;
          end = start + numInstancesSubset;
        } else if (i == numSubsets - 1) {
          start = end;
          end = data.size();
        } else {
          start = end;
          end = start + numInstancesSubset;
        }
        executor.execute(new LatticeHandler(i, start, end));
      }

      // wait till all threads finish
      int numSetBits = 0;
      while (numSetBits != numSubsets) {
        synchronized(this) {
        	numSetBits = threadIds.cardinality();
        }
        try {
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException ie) {
          ie.printStackTrace();
          System.exit(1);
        }
      }

      // update main gradient
      this.updateGradient();
      lattices = null;
    }

    /**
     * Aggregates all subset gradients into the main gradient object.
     */
    private void updateGradient() {
      for (int i = 0; i < mtGradient.size(); ++i) {
        CRF.Factors subsetGradient = mtGradient.get(i).subsetFactors;
        gradient.plusEquals(subsetGradient, 1.0);
      }
    }
    
    public void shutdown() {
    	executor.shutdown();
    }


    /**
     * Runs forward-backward for a subset of data in a new thread, uses
     * subset-specific incrementor.
     */
    private class LatticeHandler implements Runnable {
      // index to determine which incrementor to use
      private int index;

      // start, end indices of subset of data
      private int start;
      private int end;

      /**
       * Initializes the indices.
       */
      public LatticeHandler(int index, int startIndex, int endIndex) {
        this.index = index;
        this.start = startIndex;
        this.end = endIndex;
      }

      /**
       * Creates lattice, updates gradient.
       */
      public void run() {
        Transducer.Incrementor incrementor =
            mtGradient.get(index).subsetIncrementor;
        BitSet constraintBits = geCriteria.getConstraintBits();
        for (int i = start; i < end; ++i) {
          // skip if the instance doesn't have any constraints
          if (!constraintBits.get(i)) {
            continue;
          }
          FeatureVectorSequence fvs =
              (FeatureVectorSequence) data.get(i).getData();
          SumLattice lattice = lattices.get(i);
          assert(lattice != null)
              : "Lattice is null:: " + i + ", size: " + lattices.size();
          new GELattice(
              fvs, lattice.getGammas(), lattice.getXis(), crf, incrementor,
              geCriteria, false);
        }
        synchronized(LatticeCreationExecutor.this) {
          threadIds.set(index);
        }
      }
    }
  }
  
  private class FactorsIncrementorPair {
    // model's Factors from a subset of data
    public CRF.Factors subsetFactors;
    public Transducer.Incrementor subsetIncrementor;

    /**
     * Initialize Factors using the structure of main Factors object.
     */
    public FactorsIncrementorPair(CRF.Factors other) {
      subsetFactors = new CRF.Factors(other);
      subsetIncrementor = subsetFactors.new Incrementor();
    }
  }
}
