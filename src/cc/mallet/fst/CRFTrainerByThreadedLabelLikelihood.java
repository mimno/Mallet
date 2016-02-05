package cc.mallet.fst;

import java.util.Random;
import java.util.logging.Logger;

import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

/**
 * @author Gregory Druck gdruck@cs.umass.edu
 *
 * Multi-threaded version of CRF trainer.  Note that multi-threaded feature induction
 * and hyperbolic prior are not supported by this code.  
 */
public class CRFTrainerByThreadedLabelLikelihood extends TransducerTrainer implements TransducerTrainer.ByOptimization {
	private static Logger logger = MalletLogger.getLogger(CRFTrainerByThreadedLabelLikelihood.class.getName());

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1.0;

	private boolean useSparseWeights;
	private boolean useNoWeights;
	private transient boolean useSomeUnsupportedTrick;
	private boolean converged;
	private int numThreads;
	private int iterationCount;
	private double gaussianPriorVariance;
	private CRF crf;
	private CRFOptimizableByBatchLabelLikelihood optimizable;
	private ThreadedOptimizable threadedOptimizable;
	private Optimizer optimizer;
	private int cachedWeightsStructureStamp; 

	public CRFTrainerByThreadedLabelLikelihood (CRF crf, int numThreads) {
		this.crf = crf;
		this.useSparseWeights = true;
		this.useNoWeights = false;
		this.useSomeUnsupportedTrick = true;
		this.converged = false;
		this.numThreads = numThreads;
		this.iterationCount = 0;
		this.gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
		this.cachedWeightsStructureStamp = -1;
	}
	
	public Transducer getTransducer() { return crf; }
	public CRF getCRF () { return crf; }
	public Optimizer getOptimizer() { return optimizer; }
	public boolean isConverged() { return converged; }
	public boolean isFinishedTraining() { return converged; }
	public int getIteration () { return iterationCount; }
	public void setGaussianPriorVariance (double p) { gaussianPriorVariance = p; }
	public double getGaussianPriorVariance () { return gaussianPriorVariance; }
	public void setUseSparseWeights (boolean b) { useSparseWeights = b; }
	public boolean getUseSparseWeights () { return useSparseWeights; }

	/** Sets whether to use the 'some unsupported trick.' This trick is, if training a CRF
	 * where some training has been done and sparse weights are used, to add a few weights
	 * for feaures that do not occur in the tainig data.
	 * <p>
	 * This generally leads to better accuracy at only a  small memory cost.
	 *
	 * @param b Whether to use the trick
	 */
	public void setUseSomeUnsupportedTrick (boolean b) { useSomeUnsupportedTrick = b; }

	/**
	 * Use this method to specify whether or not factors
	 * are added to the CRF by this trainer.  If you have
	 * already setup the factors in your CRF, you may
	 * not want the trainer to add additional factors. 
	 * 
	 * @param flag If true, this trainer adds no factors to the CRF.
	 */
	public void setAddNoFactors(boolean flag) {
		this.useNoWeights = flag;
	}

	public void shutdown() {
		threadedOptimizable.shutdown();
	}
	
	public CRFOptimizableByBatchLabelLikelihood getOptimizableCRF (InstanceList trainingSet) {
		if (cachedWeightsStructureStamp != crf.weightsStructureChangeStamp) {
			if (!useNoWeights) {
				if (useSparseWeights) {
					crf.setWeightsDimensionAsIn (trainingSet, useSomeUnsupportedTrick);	
				}
				else { 
					crf.setWeightsDimensionDensely ();
				}
			}
			optimizable = null;
			cachedWeightsStructureStamp = crf.weightsStructureChangeStamp;
		}
		if (optimizable == null || optimizable.trainingSet != trainingSet) {
			optimizable = new CRFOptimizableByBatchLabelLikelihood(crf, trainingSet, numThreads);
			optimizable.setGaussianPriorVariance(gaussianPriorVariance);
			threadedOptimizable = new ThreadedOptimizable(optimizable, trainingSet, crf.getParameters().getNumFactors(),
	      new CRFCacheStaleIndicator(crf));
			optimizer = null;
		}
		return optimizable;
	}
	
	public Optimizer getOptimizer (InstanceList trainingSet) {
		getOptimizableCRF(trainingSet);
		if (optimizer == null || optimizable != optimizer.getOptimizable()) {
			optimizer = new LimitedMemoryBFGS(threadedOptimizable);
		}
		return optimizer;
	}
	
	public boolean trainIncremental (InstanceList training) {
		return train (training, Integer.MAX_VALUE);
	}

	public boolean train (InstanceList trainingSet, int numIterations) {
		if (numIterations <= 0) {
			return false;
		}
		assert (trainingSet.size() > 0);

		getOptimizableCRF(trainingSet); // This will set this.mcrf if necessary
		getOptimizer(trainingSet); // This will set this.opt if necessary

		boolean converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		for (int i = 0; i < numIterations; i++) {
			try {
				converged = optimizer.optimize (1);
				iterationCount++;
				logger.info ("CRF finished one iteration of maximizer, i="+i);
				runEvaluators();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
				converged = true;
			} catch (Exception e) {
				e.printStackTrace();
				logger.info("Catching exception; saying converged.");
				converged = true;
			}
			if (converged) {
				logger.info ("CRF training has converged, i="+i);
				break;
			}
		}
		return converged;
	}

	/**
	 * Train a CRF on various-sized subsets of the data.  This method is typically used to accelerate training by 
	 * quickly getting to reasonable parameters on only a subset of the parameters first, then on progressively more data. 
	 * @param training The training Instances.
	 * @param numIterationsPerProportion Maximum number of Maximizer iterations per training proportion.
	 * @param trainingProportions If non-null, train on increasingly
	 * larger portions of the data, e.g. new double[] {0.2, 0.5, 1.0}.  This can sometimes speedup convergence. 
	 * Be sure to end in 1.0 if you want to train on all the data in the end.  
	 * @return True if training has converged.
	 */
	public boolean train (InstanceList training, int numIterationsPerProportion, double[] trainingProportions)
	{
		int trainingIteration = 0;
		assert (trainingProportions.length > 0);
		boolean converged = false;
		for (int i = 0; i < trainingProportions.length; i++) {
			assert (trainingProportions[i] <= 1.0);
			logger.info ("Training on "+trainingProportions[i]+"% of the data this round.");
			if (trainingProportions[i] == 1.0) {
				converged = this.train (training, numIterationsPerProportion);
			}
			else { 
				converged = this.train (training.split (new Random(1),	
						new double[] {trainingProportions[i],	1-trainingProportions[i]})[0], numIterationsPerProportion);
			}
			trainingIteration += numIterationsPerProportion;
		}
		return converged;
	}
}
