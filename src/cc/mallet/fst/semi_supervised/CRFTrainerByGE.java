/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import java.util.ArrayList;
import java.util.logging.Logger;

import cc.mallet.fst.CRF;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.fst.semi_supervised.constraints.GEConstraint;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

/**
 * Trains a CRF using Generalized Expectation constraints that
 * consider either a single label or a pair of labels of a linear chain CRF.
 * 
 * See:
 * "Generalized Expectation Criteria for Semi-Supervised Learning of Conditional Random Fields"
 * Gideon Mann and Andrew McCallum
 * ACL 2008
 *
 * @author Gregory Druck
 */
public class CRFTrainerByGE extends TransducerTrainer {

	private static Logger logger = MalletLogger.getLogger(CRFTrainerByGE.class.getName());
	
	private static final int DEFAULT_NUM_RESETS = 1;
	private static final int DEFAULT_GPV = 10;

	private boolean converged;
	private int iteration;
	private int numThreads;
	private int numResets;
	private double gaussianPriorVariance;
	private ArrayList<GEConstraint> constraints;
	private CRF crf;
	private StateLabelMap stateLabelMap;
	
	public CRFTrainerByGE(CRF crf, ArrayList<GEConstraint> constraints) {
	  this(crf,constraints,1);
	}
	
	public CRFTrainerByGE(CRF crf, ArrayList<GEConstraint> constraints, int numThreads) {
		this.converged = false;
		this.iteration = 0;
		this.constraints = constraints;
		this.crf = crf;
		this.numThreads = numThreads;
		this.numResets = DEFAULT_NUM_RESETS;
		this.gaussianPriorVariance = DEFAULT_GPV;
		// default one to one state label map
		// other maps can be set with setStateLabelMap
		this.stateLabelMap = new StateLabelMap(crf.getOutputAlphabet(),true);
	}
	
	@Override
	public int getIteration() {
		return iteration;
	}

	@Override
	public Transducer getTransducer() {
		return crf;
	}

	@Override
	public boolean isFinishedTraining() {
		return converged;
	}
	
	public void setGaussianPriorVariance(double gpv) {
		this.gaussianPriorVariance = gpv;
	}
	
	/**
	 * Sets number of resets of L-BFGS during
	 * optimization.  Resetting more times
	 * can be useful since the GE objective
	 * function is non-convex
	 * 
	 * @param numResets Number of resets of L-BFGS
	 */
	public void setNumResets(int numResets) {
	  this.numResets = numResets;
	}

	// map between states in CRF FST and labels
	public void setStateLabelMap(StateLabelMap map) {
		this.stateLabelMap = map;
	}
	
	@Override
	public boolean train(InstanceList unlabeledSet, int numIterations) {
    
    assert(constraints.size() > 0);
    if (constraints.size() == 0) {
    	throw new RuntimeException("No constraints specified!");
    }

    CRFOptimizableByGE ge = new CRFOptimizableByGE(crf, constraints, unlabeledSet, stateLabelMap,numThreads);
    ge.setGaussianPriorVariance(gaussianPriorVariance);
		
		LimitedMemoryBFGS bfgs = new LimitedMemoryBFGS(ge);
		
		converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		// sometimes resetting the optimizer helps to find
		// a better parameter setting
		int iter = 0;
		for (int reset = 0; reset < numResets + 1; reset++) {
			for (; iter < numIterations; iter++) {
				try {
					converged = bfgs.optimize (1);
					iteration++;
					logger.info ("CRF finished one iteration of maximizer, i="+iter);
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
					logger.info ("CRF training has converged, i="+iter);
					break;
				}
			}
			bfgs.reset();
		}
		
		// shutdown threads
		ge.shutdown();
		
		return converged;
	}
}
