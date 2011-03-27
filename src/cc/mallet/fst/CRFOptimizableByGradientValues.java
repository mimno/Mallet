package cc.mallet.fst;

import java.util.logging.Logger;

import cc.mallet.optimize.Optimizable;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;

/**
 * A CRF objective function that is the sum of multiple
 * objective functions that implement Optimizable.ByGradientValue.
 *
 * @author Gregory Druck
 * @author Gaurav Chandalia
 */

public class CRFOptimizableByGradientValues implements Optimizable.ByGradientValue {
  private static Logger logger = MalletLogger.getLogger(CRFOptimizableByGradientValues.class.getName());

  private int cachedValueWeightsStamp;
  private int cachedGradientWeightsStamp;
  private double cachedValue = Double.NEGATIVE_INFINITY;
  private double[] cachedGradient;
  private Optimizable.ByGradientValue[] optimizables;
	private CRF crf;
	
	/**
	 * @param crf CRF whose parameters we wish to estimate.
	 * @param opts Optimizable.ByGradientValue objective functions. 
	 * 
	 * Parameters are estimated by maximizing the sum of the individual
	 * objective functions.
	 */
	public CRFOptimizableByGradientValues (CRF crf, Optimizable.ByGradientValue[] opts) {
		this.crf = crf;
		this.optimizables = opts;
		this.cachedGradient = new double[crf.parameters.getNumFactors()];
		this.cachedValueWeightsStamp = -1;
		this.cachedGradientWeightsStamp = -1;
	}

	public int getNumParameters () {
		return crf.parameters.getNumFactors();
	}

	public void getParameters (double[] buffer) {
		crf.parameters.getParameters(buffer);
	}

	public double getParameter (int index) {
		return crf.parameters.getParameter(index);
	}

	public void setParameters (double [] buff) {
		crf.parameters.setParameters(buff);
		crf.weightsValueChanged();
	}

	public void setParameter (int index, double value) {
		crf.parameters.setParameter(index, value);
		crf.weightsValueChanged();
	}

	/** Returns the log probability of the training sequence labels and the prior over parameters. */
	public double getValue () {
		if (crf.weightsValueChangeStamp != cachedValueWeightsStamp) {
			// The cached value is not up to date; it was calculated for a different set of CRF weights.

			cachedValue = 0;
			for (int i = 0; i < optimizables.length; i++)
				cachedValue += optimizables[i].getValue();
			
			cachedValueWeightsStamp = crf.weightsValueChangeStamp;  // cachedValue is now no longer stale
			logger.info ("getValue() = "+cachedValue);
		}
		return cachedValue;
	}

	public void getValueGradient (double [] buffer) {
		if (cachedGradientWeightsStamp != crf.weightsValueChangeStamp) {
			getValue ();
			MatrixOps.setAll(cachedGradient, 0);
			double[] b2 = new double[buffer.length];
			for (int i = 0; i < optimizables.length; i++) {
				MatrixOps.setAll(b2, 0);
				optimizables[i].getValueGradient(b2);
				MatrixOps.plusEquals(cachedGradient, b2);
			}
			cachedGradientWeightsStamp = crf.weightsValueChangeStamp;
		}
		System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
  }
}
