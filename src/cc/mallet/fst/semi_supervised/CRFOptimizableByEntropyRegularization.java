package cc.mallet.fst.semi_supervised;

import java.io.Serializable;
import java.util.logging.Logger;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.InstanceList;
import cc.mallet.fst.CRF;
import cc.mallet.fst.SumLattice;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.CRF.Factors;
import cc.mallet.fst.CRF.Factors.Incrementor;
import cc.mallet.optimize.Optimizable;
import cc.mallet.util.MalletLogger;

/**
 * A CRF objective function that is the entropy of the CRF's
 * predictions on unlabeled data.
 * 
 * References:
 * Feng Jiao, Shaojun Wang, Chi-Hoon Lee, Russell Greiner, Dale Schuurmans
 * "Semi-supervised conditional random fields for improved sequence segmentation and labeling"
 * ACL 2006
 *
 * Gideon Mann, Andrew McCallum
 * "Efficient Computation of Entropy Gradient for Semi-Supervised Conditional Random Fields"
 * HLT/NAACL 2007
 *
 * @author Gaurav Chandalia
 * @author Gregory Druck
 */
public class CRFOptimizableByEntropyRegularization implements Optimizable.ByGradientValue,
                                                   Serializable {
  private static Logger logger = MalletLogger.getLogger(CRFOptimizableByEntropyRegularization.class.getName());

	private int cachedValueWeightsStamp = -1;
	private int cachedGradientWeightsStamp = -1;
  
  // model's expectations according to entropy reg.
  protected CRF.Factors expectations;
  // used to update gradient
  protected Transducer.Incrementor incrementor;

  // contains labeled and unlabeled data
  protected InstanceList data;
  // the model
  protected CRF crf;

  // scale entropy values,
  // typically, (entropyRegGamma * numLabeled / numUnlabeled)
  protected double scalingFactor;

  // log probability of all the sequences, this is also the entropy due to all
  // the instances (updated in computeExpectations())
  protected double cachedValue;
  // gradient due to this optimizable (updated in getValueGradient())
  protected double[] cachedGradient;

  /**
   * Initializes the structures.
   */
  public CRFOptimizableByEntropyRegularization(CRF crf, InstanceList ilist,
                                    double scalingFactor) {
    data = ilist;
    this.crf = crf;
    this.scalingFactor = scalingFactor;

    // initialize the expectations using the model
    expectations = new CRF.Factors(crf);
    incrementor = expectations.new Incrementor();

    cachedValue = 0.0;
    cachedGradient = new double[crf.getParameters().getNumFactors()];
  }

  /**
   * Initializes the structures (sets the scaling factor to 1.0).
   */
  public CRFOptimizableByEntropyRegularization(CRF crf, InstanceList ilist) {
    this(crf, ilist, 1.0);
  }

  public double getScalingFactor() {
    return scalingFactor;
  }

  public void setScalingFactor(double scalingFactor) {
    this.scalingFactor = scalingFactor;
  }

  /**
   * Resets, computes and fills expectations from all instances, also updating
   * the entropy value. <p>
   *
   * Analogous to <tt>CRFOptimizableByLabelLikelihood.getExpectationValue<tt>.
   */
  public void computeExpectations() {
  	expectations.zero();

    // now, update the expectations due to each instance for entropy reg.
    for (int ii = 0; ii < data.size(); ii++) {
      FeatureVectorSequence input = (FeatureVectorSequence) data.get(ii).getData();
      SumLattice lattice = new SumLatticeDefault(crf,input, true);

      // udpate the expectations
      EntropyLattice entropyLattice = new EntropyLattice(
          input, lattice.getGammas(), lattice.getXis(), crf,
          incrementor, scalingFactor);
      cachedValue += entropyLattice.getEntropy();
    }
  }

  public double getValue() {
		if (crf.getWeightsValueChangeStamp() != cachedValueWeightsStamp) {
		  // The cached value is not up to date; it was calculated for a different set of CRF weights.
		  cachedValueWeightsStamp = crf.getWeightsValueChangeStamp(); 
	  	
	  	cachedValue = 0;
	  	computeExpectations();
	    cachedValue = scalingFactor * cachedValue;
			assert(!Double.isNaN(cachedValue) && !Double.isInfinite(cachedValue))
	        : "Likelihood due to Entropy Regularization is NaN/Infinite";
	
	    logger.info("getValue() (entropy regularization) = " + cachedValue);
		}
  	return cachedValue;
  }

  public void getValueGradient(double[] buffer) {
		if (cachedGradientWeightsStamp != crf.getWeightsValueChangeStamp()) {
			cachedGradientWeightsStamp = crf.getWeightsValueChangeStamp(); // cachedGradient will soon no longer be stale
  	
  	  getValue();
  	
      // if this fails then look in computeExpectations
      expectations.assertNotNaNOrInfinite();
  	  // fill up gradient
  	  expectations.getParameters(cachedGradient);
		}
    System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
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

  // serialization stuff
  private static final long serialVersionUID = 1;
}
