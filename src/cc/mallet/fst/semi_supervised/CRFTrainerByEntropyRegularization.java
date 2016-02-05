/* Copyright (C) 2009 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import java.util.logging.Logger;

import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFOptimizableByGradientValues;
import cc.mallet.fst.CRFOptimizableByLabelLikelihood;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

/**
 * A CRF trainer that maximizes the log-likelihood plus
 * a weighted entropy regularization term on unlabeled 
 * data.  Intuitively, it aims to make the CRF's predictions
 * on unlabeled data more confident.
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
 * @author Gregory Druck
 */

public class CRFTrainerByEntropyRegularization extends TransducerTrainer implements TransducerTrainer.ByOptimization {
	
	private static Logger logger = MalletLogger.getLogger(CRFTrainerByEntropyRegularization.class.getName());

	private static final int DEFAULT_NUM_RESETS = 1;
	private static final double DEFAULT_ER_SCALING_FACTOR = 1;
	private static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1;
	
	private boolean converged;
	private int iteration;
	private double entRegScalingFactor;
	private double gaussianPriorVariance;
	private CRF crf;
	private LimitedMemoryBFGS bfgs;
	
	public CRFTrainerByEntropyRegularization(CRF crf) {
		this.crf = crf;
		this.iteration = 0;
		this.entRegScalingFactor = DEFAULT_ER_SCALING_FACTOR;
		this.gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	}
	
	public void setGaussianPriorVariance(double variance) {
		this.gaussianPriorVariance = variance;
	}
	
	/**
	 * Sets the scaling factor for the entropy regularization term.
	 * In [Jiao et al. 06], this is gamma.
	 * 
	 * @param gamma
	 */
	public void setEntropyWeight(double gamma) {
		this.entRegScalingFactor = gamma;
	}
	
	@Override
	public int getIteration() {
		return this.iteration;
	}

	@Override
	public Transducer getTransducer() {
		return this.crf;
	}

	@Override
	public boolean isFinishedTraining() {
		return this.converged;
	}

  /* 
   * This is not used because we require both labeled and unlabeled data.
   */
  public boolean train(InstanceList trainingSet, int numIterations) {
  	throw new RuntimeException("Use train(InstanceList labeled, InstanceList unlabeled, int numIterations) instead.");
  }
	
	/**
	 * Performs CRF training with label likelihood and entropy regularization.  
	 * The CRF is first trained with label likelihood only.  This parameter 
	 * setting is used as a starting point for the combined optimization.
	 * 
	 * @param labeled Labeled data, only used for label likelihood term.
	 * @param unlabeled Unlabeled data, only used for entropy regularization term.
	 * @param numIterations Number of iterations.
	 * @return True if training has converged.
	 */
	public boolean train(InstanceList labeled, InstanceList unlabeled, int numIterations) {
		if (iteration == 0) {
			// train with log-likelihood only first
			CRFOptimizableByLabelLikelihood likelihood = 
				new CRFOptimizableByLabelLikelihood(crf, labeled);
			likelihood.setGaussianPriorVariance(gaussianPriorVariance);
			this.bfgs = new LimitedMemoryBFGS(likelihood);
			logger.info ("CRF about to train with "+numIterations+" iterations");
			for (int i = 0; i < numIterations; i++) {
				try {
					converged = bfgs.optimize(1);
					iteration++;
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
			iteration = 0;
	  }
		
		// train with log-likelihood + entropy regularization
		CRFOptimizableByLabelLikelihood likelihood = new CRFOptimizableByLabelLikelihood(crf, labeled);
		likelihood.setGaussianPriorVariance(gaussianPriorVariance);
		CRFOptimizableByEntropyRegularization regularization = new CRFOptimizableByEntropyRegularization(crf, unlabeled);
		regularization.setScalingFactor(this.entRegScalingFactor);
		
		CRFOptimizableByGradientValues regLikelihood = new CRFOptimizableByGradientValues(crf,
				new Optimizable.ByGradientValue[] { likelihood, regularization} );
		this.bfgs = new LimitedMemoryBFGS(regLikelihood);
		converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		// sometimes resetting the optimizer helps to find
		// a better parameter setting
		for (int reset = 0; reset < DEFAULT_NUM_RESETS + 1; reset++) {
			for (int i = 0; i < numIterations; i++) {
				try {
					converged = bfgs.optimize (1);
					iteration++;
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
			this.bfgs.reset();
		}
		return converged;
	}
	
	public Optimizer getOptimizer() {
		return bfgs;
	}
}