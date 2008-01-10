package cc.mallet.optimize;

import java.util.Arrays;
import java.util.logging.Logger;
import java.text.DecimalFormat;

import cc.mallet.optimize.Optimizer;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;


/**
 * @author Greg Druck
 * @author Kedar Bellare
 */
public class StochasticMetaAscent implements Optimizer.ByBatches {

	private static Logger logger = 
		MalletLogger.getLogger(StochasticMetaAscent.class.getName());
	
  private final int MAX_ITER = 200;
	private final double LAMBDA = 1.0;
	private final double TOLERANCE = 0.01;
  private final double EPS = 1e-10;

  private double mu = 0.1;
  private int totalIterations = 0;
  private double eta_init = 0.03;
  private boolean useHessian = true;
	private double[] gain;
	private double[] gradientTrace;
	
	Optimizable.ByBatchGradient maxable = null;
	
	public StochasticMetaAscent (Optimizable.ByBatchGradient maxable) {
		this.maxable = maxable;
	}

  public void setInitialStep(double step) {
    eta_init = step;
  }
  
  public void setMu(double m) {
    mu = m;
  }
  
  public void setUseHessian(boolean flag) {
    useHessian = flag;
  }
  
	public boolean optimize(int numBatches, int[] batchAssignments) {
		return optimize(MAX_ITER,numBatches,batchAssignments);
	}

	public boolean optimize(int numIterations, int numBatches, int[] batchAssignments) {
		
		int numParameters = maxable.getNumParameters();
		double[] parameters = new double[numParameters];
		double[] gradient = new double[numParameters];
    double[] hessianProduct = new double[numParameters];
		
		// Only initialize these if they are null
		// in case someone wants to optimize a 
		// few iterations at a time.  
		if (gain == null) {
      System.err.println ("StochasticMetaAscent: initialStep="+eta_init+"  metaStep="+mu);
      gain = new double[numParameters];
      Arrays.fill(gain, eta_init);
      gradientTrace = new double[numParameters];
		}

    maxable.getParameters(parameters);
		
		for (int iteration = 0; iteration < numIterations; iteration++) {
      double oldApproxValue = 0;
			double approxValue = 0;
			for (int batch = 0; batch < numBatches; batch++) {
				logger.info("Iteration " + (totalIterations + iteration) + ", batch " + batch + " of " + numBatches);
				
				// Get current parameters
				maxable.getParameters(parameters);

				// Update value and gradient for the current batch
        double initialValue = maxable.getBatchValue (batch, batchAssignments);
        oldApproxValue += initialValue;

        if (Double.isNaN (initialValue)) {
          throw new IllegalArgumentException ("NaN in value computation.  Probably you need to reduce initialStep or metaStep.");
        }

        maxable.getBatchValueGradient(gradient,batch,batchAssignments);

        // The code below was originally written for stochastic meta
        // descent.  We are maximizing, so we want ascent.  Flip the
        // signs on the gradient to make it point downhill.
        MatrixOps.timesEquals(gradient, -1);

        if (useHessian) {
          computeHessianProduct(maxable, parameters, batch, batchAssignments, gradient, gradientTrace, hessianProduct);
        }

        reportOnVec ("x", parameters);
        reportOnVec ("step", gain);
        reportOnVec ("grad", gradient);
        reportOnVec ("trace", gradientTrace);
          
				// Update learning rates for individual parameters
				for (int index = 0; index < numParameters; index++) {
          // for the first iteration, this will just be the initial step
          // since gradientTrace will be all zeros
					gain[index] *= Math.max(0.5, 1 - mu * gradient[index] * gradientTrace[index]);
          
					// adjust parameters based on direction
					parameters[index] -= gain[index] * gradient[index];
					
          if (useHessian) {
            // adjust gradient trace
            gradientTrace[index] = LAMBDA * gradientTrace[index] - gain[index] * 
						  (gradient[index] + LAMBDA * hessianProduct[index]);
          }
          else {
            // adjust gradient trace
            gradientTrace[index] = LAMBDA * gradientTrace[index] - gain[index] * 
            (gradient[index] + LAMBDA * gradientTrace[index]);
          }
				}
				
				// Set new parameters
				maxable.setParameters(parameters);

        double finalValue = maxable.getBatchValue (batch, batchAssignments);
        approxValue += finalValue;

        logger.info ("StochasticMetaAscent: initial value: "+initialValue+"  final value:"+finalValue);
      }

      logger.info("StochasticMetaDescent: Value at iteration (" + (totalIterations + iteration) + ")= " + approxValue);
      
      // converge criteria from GradientAscent and LimitedMemoryBFGS
      if (2.0*Math.abs(approxValue-oldApproxValue) <= 
        TOLERANCE*(Math.abs(approxValue)+Math.abs(oldApproxValue)+EPS)) {
        logger.info ("Stochastic Meta Ascent: Value difference "
            +Math.abs(approxValue-oldApproxValue)
            +" below " + "tolerance; saying converged.");
        totalIterations += iteration;
        return true;
      }

      oldApproxValue = approxValue;
		}
    
    totalIterations += numIterations;
		return false;
	}

  private void reportOnVec (String s, double[] v)
  {
    DecimalFormat f = new DecimalFormat ("0.####");
    System.out.println ("StochasticMetaAscent: "+s+":"+
            "  min "+ f.format(MatrixOps.min (v)) +
            "  max "+ f.format(MatrixOps.max (v)) +
            "  mean "+ f.format(MatrixOps.mean (v)) +
            "  2norm "+ f.format(MatrixOps.twoNorm (v)) +
            "  abs-norm "+ f.format(MatrixOps.absNorm (v))
    );
  }

  // compute finite difference approximation of the Hessian product
  private void computeHessianProduct(Optimizable.ByBatchGradient maxable, 
      double[] parameters, int batchIndex, int[] batchAssignments, 
      double[] currentGradient, double[] vector, double[] result) {
    
    int numParameters = maxable.getNumParameters();
    double eps = 1.0e-6;
    double[] epsGradient = new double[numParameters];
    double[] oldParameters = new double[numParameters];
    
    // adjust parameters by (eps * vector) and recompute gradient
    System.arraycopy(parameters,0,oldParameters,0,numParameters);

    MatrixOps.plusEquals(parameters, vector, eps);
    maxable.setParameters(parameters);
    maxable.getBatchValueGradient(epsGradient, batchIndex, batchAssignments);
    
    // restore old parameters
    maxable.setParameters(oldParameters);
    
    // calculate Hessian product
    for (int index = 0; index < result.length; index++) {
      result[index] = (-epsGradient[index] - currentGradient[index]) / eps;
    }
  }

}
